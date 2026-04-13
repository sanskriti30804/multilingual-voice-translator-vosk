package ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.SelectionMode;
import javafx.stage.FileChooser;
import speech.VoskRecognizer;
import translation.TranslatorService;
import tts.TextToSpeech;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public final class Controller {
  private static final Path EN_MODEL_DIR = Path.of("model", "vosk-model-small-en-us-0.15");
  private static final Path HI_MODEL_DIR = Path.of("model", "vosk-model-small-hi-0.22");
  private static final String DEFAULT_TRANSLATE_BASE_URL = "https://translate.googleapis.com";
  private static final String DARK_STYLESHEET = "/ui/dark.css";

  private final MainUI ui;

  private final ExecutorService translationExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "translation-worker");
    t.setDaemon(true);
    return t;
  });

  private final TranslatorService translator;
  private final TextToSpeech tts;
  private final boolean ttsAvailable;

  private VoskRecognizer recognizer;
  private final StringBuilder finalTranscript = new StringBuilder();
  private final AtomicReference<String> lastSpokenText = new AtomicReference<>("");
  private final Map<String, String> latestTranslationsByLang = new ConcurrentHashMap<>();
  private final Map<String, Future<?>> inflightTranslations = new ConcurrentHashMap<>();

  public Controller(MainUI ui) {
    this.ui = ui;
    String baseUrl = System.getenv().getOrDefault("GOOGLE_TRANSLATE_URL", DEFAULT_TRANSLATE_BASE_URL);
    this.translator = new TranslatorService(baseUrl);
    TextToSpeech ttsInstance = null;
    boolean available = false;
    try {
      ttsInstance = new TextToSpeech();
      available = true;
    } catch (Exception ignored) {
      // Keep app usable even when TTS voice/runtime is unavailable.
    }
    this.tts = ttsInstance;
    this.ttsAvailable = available;
  }

  public void init() {
    setupLanguages();
    ui.getSpeakBtn().setDisable(true);
    ui.getAutoSpeakToggle().setDisable(!ttsAvailable);
    Platform.runLater(this::selfCheck);

    ui.getStartStopBtn().setOnAction(e -> {
      if (recognizer != null && recognizer.isRunning()) {
        stopRecognition();
      } else {
        startRecognition();
      }
    });

    ui.getSpeakBtn().setOnAction(e -> {
      if (!ttsAvailable || tts == null) return;
      String speakText = pickTextToSpeak();
      tts.speakAsync(speakText);
    });

    ui.getTargetLangs().getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    ui.getTargetLangs().getSelectionModel().getSelectedItems().addListener((ListChangeListener<LanguageOption>) c -> {
      // Re-translate latest line into all currently selected targets
      String latestFinal = getLatestFinalLine();
      if (latestFinal != null && !latestFinal.isBlank()) translateAsync(latestFinal);
      renderTranslations();
    });

    ui.getSaveBtn().setOnAction(e -> saveToFile());

    ui.getDarkModeToggle().selectedProperty().addListener((obs, oldV, isDark) -> applyDarkMode(isDark));
    Platform.runLater(() -> applyDarkMode(ui.getDarkModeToggle().isSelected()));
  }

  private void setupLanguages() {
    // Beginner-friendly starter set. LibreTranslate codes are typically: en, es, fr, de, hi, ta, etc.
    List<LanguageOption> options = List.of(
        new LanguageOption("auto", "Auto Detect"),
        new LanguageOption("en", "English"),
        new LanguageOption("hi", "Hindi"),
        new LanguageOption("ta", "Tamil"),
        new LanguageOption("te", "Telugu"),
        new LanguageOption("mr", "Marathi"),
        new LanguageOption("bn", "Bengali"),
        new LanguageOption("gu", "Gujarati"),
        new LanguageOption("pa", "Punjabi"),
        new LanguageOption("ur", "Urdu"),
        new LanguageOption("es", "Spanish"),
        new LanguageOption("fr", "French"),
        new LanguageOption("de", "German")
    );

    ui.getSourceLang().getItems().setAll(options);
    List<LanguageOption> targets = options.stream().filter(o -> !o.code().equals("auto")).toList();
    ui.getTargetLangs().setItems(FXCollections.observableArrayList(targets));

    ui.getSourceLang().getSelectionModel().select(0); // auto
    // Default multi-target selection
    Platform.runLater(() -> {
      ui.getTargetLangs().getSelectionModel().clearSelection();
      selectTargetsByCode(List.of("hi", "fr"), targets);
    });
  }

  private void startRecognition() {
    ui.getStartStopBtn().setText("Stop Recording");
    setStatus("Listening");
    ui.getOriginalText().clear();
    ui.getTranslatedText().clear();
    ui.getSpeakBtn().setDisable(true);
    finalTranscript.setLength(0);
    lastSpokenText.set("");
    latestTranslationsByLang.clear();
    inflightTranslations.values().forEach(f -> f.cancel(true));
    inflightTranslations.clear();

    Path selectedModelDir = getModelDirForSource(getSelectedSourceLanguageCode());
    recognizer = new VoskRecognizer(selectedModelDir);
    recognizer.setOnStatus(this::setStatus);
    recognizer.setOnError(t -> Platform.runLater(() -> {
      setStatus("Idle");
      ui.getStartStopBtn().setText("Start Recording");
      ui.getOriginalText().appendText("\n\n[Error] " + t.getMessage());
    }));

    recognizer.setOnPartial(partial -> Platform.runLater(() -> {
      String display = finalTranscript.toString();
      if (!display.isBlank()) display = display + "\n";
      ui.getOriginalText().setText(display + partial);
    }));

    recognizer.setOnFinal(text -> {
      if (text == null || text.isBlank()) return;
      synchronized (finalTranscript) {
        if (!finalTranscript.isEmpty()) finalTranscript.append('\n');
        finalTranscript.append(text);
      }

      Platform.runLater(() -> ui.getOriginalText().setText(finalTranscript.toString()));
      translateAsync(text);
    });

    recognizer.start();
  }

  private void stopRecognition() {
    ui.getStartStopBtn().setText("Start Recording");
    setStatus("Idle");
    if (recognizer != null) {
      recognizer.close();
      recognizer = null;
    }
  }

  private void translateAsync(String text) {
    LanguageOption src = ui.getSourceLang().getValue();
    String source = (src == null) ? "auto" : src.code();
    List<LanguageOption> selectedTargets = ui.getTargetLangs().getSelectionModel().getSelectedItems();
    if (selectedTargets == null || selectedTargets.isEmpty()) {
      Platform.runLater(() -> {
        ui.getTranslatedText().setText("[Select at least one target language to translate]");
        ui.getSpeakBtn().setDisable(true);
      });
      return;
    }

    // Translate to each selected target sequentially on a worker thread (keeps design simple).
    // If you want true parallel, use a fixed thread pool and aggregate results.
    Future<?> job = translationExecutor.submit(() -> {
      try {
        setStatus("Translating...");
        for (LanguageOption tgt : selectedTargets) {
          if (tgt == null || tgt.code().isBlank()) continue;

          String translated = translator.translate(text, source, tgt.code());
          latestTranslationsByLang.put(tgt.code(), translated);
          Platform.runLater(this::renderTranslations);
        }

        String speakText = pickTextToSpeak();
        Platform.runLater(() -> ui.getSpeakBtn().setDisable(speakText.isBlank()));

        if (ttsAvailable && tts != null && ui.getAutoSpeakToggle().isSelected() && !speakText.isBlank()) {
          lastSpokenText.set(speakText);
          tts.speakAsync(speakText);
        }
      } catch (Exception ex) {
        Platform.runLater(() -> ui.getTranslatedText().setText("[Translation error] " + ex.getMessage()));
      } finally {
        setStatus((recognizer != null && recognizer.isRunning()) ? "Listening" : "Idle");
      }
    });

    inflightTranslations.put("last", job);
  }

  private void setStatus(String status) {
    Platform.runLater(() -> ui.getStatusLabel().setText(status));
  }

  private String getLatestFinalLine() {
    synchronized (finalTranscript) {
      if (finalTranscript.isEmpty()) return "";
      String all = finalTranscript.toString();
      int idx = all.lastIndexOf('\n');
      return idx >= 0 ? all.substring(idx + 1) : all;
    }
  }

  private void renderTranslations() {
    List<LanguageOption> selected = ui.getTargetLangs().getSelectionModel().getSelectedItems();
    if (selected == null || selected.isEmpty()) {
      ui.getTranslatedText().setText("");
      ui.getSpeakBtn().setDisable(true);
      return;
    }

    StringBuilder sb = new StringBuilder();
    for (LanguageOption opt : selected) {
      if (opt == null) continue;
      String translated = latestTranslationsByLang.getOrDefault(opt.code(), "");
      if (!sb.isEmpty()) sb.append("\n\n");
      sb.append("[").append(opt.label()).append(" (").append(opt.code()).append(")]\n");
      sb.append(translated.isBlank() ? "…" : translated);
    }
    ui.getTranslatedText().setText(sb.toString());
  }

  private String pickTextToSpeak() {
    // Speak the first selected target language's latest translation (simple and predictable).
    List<LanguageOption> selected = ui.getTargetLangs().getSelectionModel().getSelectedItems();
    if (selected == null || selected.isEmpty()) return "";
    LanguageOption first = selected.get(0);
    if (first == null) return "";
    return latestTranslationsByLang.getOrDefault(first.code(), "").trim();
  }

  private void saveToFile() {
    try {
      Scene scene = ui.getRoot().getScene();
      if (scene == null) return;

      FileChooser chooser = new FileChooser();
      chooser.setTitle("Save Transcript + Translations");
      chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Text file (*.txt)", "*.txt"));
      String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      chooser.setInitialFileName("translation_" + ts + ".txt");

      File file = chooser.showSaveDialog(scene.getWindow());
      if (file == null) return;

      String content = buildSaveContent();
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
      setStatus("Saved");
    } catch (Exception ex) {
      setStatus("Save failed");
      Platform.runLater(() -> ui.getTranslatedText().setText("[Save error] " + ex.getMessage()));
    }
  }

  private String buildSaveContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Real-Time Speech Translator Output ===\n");
    sb.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

    sb.append("=== Original Transcript ===\n");
    sb.append(ui.getOriginalText().getText()).append("\n\n");

    sb.append("=== Translations ===\n");
    sb.append(ui.getTranslatedText().getText()).append("\n");
    return sb.toString();
  }

  private void applyDarkMode(boolean enabled) {
    Scene scene = ui.getRoot().getScene();
    if (scene == null) return;
    var res = getClass().getResource(DARK_STYLESHEET);
    String url = (res == null) ? null : res.toExternalForm();
    if (url == null) return;

    if (enabled) {
      if (!scene.getStylesheets().contains(url)) scene.getStylesheets().add(url);
    } else {
      scene.getStylesheets().remove(url);
    }
  }

  private void selectTargetsByCode(List<String> codes, List<LanguageOption> allTargets) {
    if (codes == null || codes.isEmpty()) return;
    if (allTargets == null || allTargets.isEmpty()) return;

    ui.getTargetLangs().getSelectionModel().clearSelection();
    for (int i = 0; i < allTargets.size(); i++) {
      LanguageOption opt = allTargets.get(i);
      if (opt != null && codes.contains(opt.code())) {
        ui.getTargetLangs().getSelectionModel().select(i);
      }
    }
  }

  /**
   * Quick “final check” to confirm the app is initialized and key runtime prerequisites are present.
   * This does not start the microphone (that happens only on Start Recording).
   */
  public void selfCheck() {
    StringBuilder issues = new StringBuilder();

    if (ui.getSourceLang().getItems().isEmpty()) issues.append("- Source languages not loaded\n");
    if (ui.getTargetLangs().getItems().isEmpty()) issues.append("- Target languages not loaded\n");

    Path selectedModelDir = getModelDirForSource(getSelectedSourceLanguageCode());
    if (!Files.isDirectory(selectedModelDir)) {
      issues.append("- Vosk model not found at: ").append(selectedModelDir.toAbsolutePath()).append("\n");
      issues.append("  Download and extract the model into the 'model' folder.\n");
    }

    // Translate URL is configurable via env var; we just show what will be used.
    String baseUrl = System.getenv().getOrDefault("GOOGLE_TRANSLATE_URL", DEFAULT_TRANSLATE_BASE_URL);
    if (baseUrl.isBlank()) issues.append("- Translate URL is empty\n");

    if (!ttsAvailable) {
      issues.append("- TTS unavailable (FreeTTS voice 'kevin16' not found). Translation still works.\n");
    }

    if (issues.isEmpty()) {
      setStatus("Ready");
    } else {
      setStatus("Setup needed");
      ui.getTranslatedText().setText("""
          [Self-check]
          Some setup is required before full functionality will work:

          """ + issues);
    }
  }

  private String getSelectedSourceLanguageCode() {
    LanguageOption src = ui.getSourceLang().getValue();
    if (src == null || src.code() == null || src.code().isBlank()) return "auto";
    return src.code();
  }

  private Path getModelDirForSource(String sourceCode) {
    return "hi".equalsIgnoreCase(sourceCode) ? HI_MODEL_DIR : EN_MODEL_DIR;
  }
}

