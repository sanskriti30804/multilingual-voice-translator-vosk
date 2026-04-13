package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

public class MainUI {
  private final BorderPane root = new BorderPane();

  private final Button startStopBtn = new Button("Start Recording");
  private final Label statusLabel = new Label("Idle");
  private final ComboBox<LanguageOption> sourceLang = new ComboBox<>();
  private final ListView<LanguageOption> targetLangs = new ListView<>();
  private final CheckBox autoSpeakToggle = new CheckBox("Auto Speak (TTS)");
  private final Button speakBtn = new Button("Speak Translation");
  private final Button saveBtn = new Button("Save to File");
  private final ToggleButton darkModeToggle = new ToggleButton("Dark Mode");

  private final TextArea originalText = new TextArea();
  private final TextArea translatedText = new TextArea();

  private final Controller controller;

  public MainUI() {
    controller = new Controller(this);
    build();
    controller.init();
  }

  private void build() {
    root.setPadding(new Insets(14));

    Label title = new Label("Real-Time Multi-Language Speech Translator (Vosk)");
    title.setFont(Font.font(18));

    statusLabel.setStyle("-fx-font-weight: bold;");
    statusLabel.setMinWidth(120);

    HBox header = new HBox(12, title, new Region(), new Label("Status:"), statusLabel);
    HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(0, 0, 12, 0));

    sourceLang.setPrefWidth(240);
    targetLangs.setPrefWidth(240);
    targetLangs.setPrefHeight(110);

    startStopBtn.setPrefWidth(160);
    speakBtn.setPrefWidth(160);
    saveBtn.setPrefWidth(160);

    HBox controls = new HBox(
        10,
        startStopBtn,
        new Separator(),
        new Label("Source:"),
        sourceLang,
        new Label("Targets:"),
        targetLangs,
        new Separator(),
        autoSpeakToggle,
        speakBtn,
        saveBtn,
        darkModeToggle
    );
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.setPadding(new Insets(0, 0, 12, 0));

    originalText.setPromptText("Original speech text will appear here...");
    originalText.setWrapText(true);

    translatedText.setPromptText("Translated text will appear here...");
    translatedText.setWrapText(true);

    VBox left = new VBox(8, new Label("Original (Live Transcript)"), originalText);
    VBox right = new VBox(8, new Label("Translated"), translatedText);
    left.setVgrow(originalText, Priority.ALWAYS);
    right.setVgrow(translatedText, Priority.ALWAYS);

    SplitPane splitPane = new SplitPane(left, right);
    splitPane.setDividerPositions(0.5);

    VBox content = new VBox(10, header, controls, splitPane);
    VBox.setVgrow(splitPane, Priority.ALWAYS);
    root.setCenter(content);
  }

  public Parent getRoot() {
    return root;
  }

  public Button getStartStopBtn() {
    return startStopBtn;
  }

  public Label getStatusLabel() {
    return statusLabel;
  }

  public ComboBox<LanguageOption> getSourceLang() {
    return sourceLang;
  }

  public ListView<LanguageOption> getTargetLangs() {
    return targetLangs;
  }

  public CheckBox getAutoSpeakToggle() {
    return autoSpeakToggle;
  }

  public Button getSpeakBtn() {
    return speakBtn;
  }

  public Button getSaveBtn() {
    return saveBtn;
  }

  public ToggleButton getDarkModeToggle() {
    return darkModeToggle;
  }

  public TextArea getOriginalText() {
    return originalText;
  }

  public TextArea getTranslatedText() {
    return translatedText;
  }

  public Controller getController() {
    return controller;
  }
}

