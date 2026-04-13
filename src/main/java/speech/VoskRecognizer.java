package speech;

import org.vosk.Model;
import org.vosk.Recognizer;
import utils.AudioUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.TargetDataLine;
import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Real-time microphone recognizer using offline Vosk.
 *
 * Emits:
 * - partial transcripts (live)
 * - final transcripts (end of utterance / silence boundary)
 */
public final class VoskRecognizer implements Closeable {
  private final Path modelDir;
  private final AudioFormat format;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread worker;

  private Model model;
  private TargetDataLine micLine;

  private Consumer<String> onPartial = s -> {};
  private Consumer<String> onFinal = s -> {};
  private Consumer<String> onStatus = s -> {};
  private Consumer<Throwable> onError = t -> {};

  public VoskRecognizer(Path modelDir) {
    this(modelDir, AudioUtils.defaultMonoPcm16k());
  }

  public VoskRecognizer(Path modelDir, AudioFormat format) {
    this.modelDir = Objects.requireNonNull(modelDir, "modelDir");
    this.format = Objects.requireNonNull(format, "format");
  }

  public void setOnPartial(Consumer<String> onPartial) {
    this.onPartial = onPartial == null ? (s -> {}) : onPartial;
  }

  public void setOnFinal(Consumer<String> onFinal) {
    this.onFinal = onFinal == null ? (s -> {}) : onFinal;
  }

  public void setOnStatus(Consumer<String> onStatus) {
    this.onStatus = onStatus == null ? (s -> {}) : onStatus;
  }

  public void setOnError(Consumer<Throwable> onError) {
    this.onError = onError == null ? (t -> {}) : onError;
  }

  public boolean isRunning() {
    return running.get();
  }

  /**
   * Starts a background thread that continuously reads the microphone.
   */
  public synchronized void start() {
    if (running.get()) return;
    running.set(true);

    worker = new Thread(this::runLoop, "vosk-mic-recognizer");
    worker.setDaemon(true);
    worker.start();
  }

  /**
   * Signals the background thread to stop and releases audio resources.
   */
  public synchronized void stop() {
    running.set(false);
    if (micLine != null) {
      try {
        micLine.stop();
      } catch (Exception ignored) {}
      try {
        micLine.close();
      } catch (Exception ignored) {}
      micLine = null;
    }
  }

  private void runLoop() {
    try {
      if (!Files.isDirectory(modelDir)) {
        throw new IllegalStateException("Vosk model folder not found: " + modelDir.toAbsolutePath());
      }

      onStatus.accept("Loading model...");
      model = new Model(modelDir.toString());

      onStatus.accept("Opening microphone...");
      micLine = AudioUtils.openDefaultMicLine(format);
      micLine.start();

      onStatus.accept("Listening");

      try (Recognizer recognizer = new Recognizer(model, format.getSampleRate())) {
        byte[] buffer = new byte[4096];

        while (running.get()) {
          int n = micLine.read(buffer, 0, buffer.length);
          if (n <= 0) continue;

          boolean isFinal = recognizer.acceptWaveForm(buffer, n);
          if (isFinal) {
            String json = recognizer.getResult();
            String text = VoskResultParser.extractText(json);
            if (!text.isBlank()) onFinal.accept(text);
          } else {
            String json = recognizer.getPartialResult();
            String partial = VoskResultParser.extractPartial(json);
            if (!partial.isBlank()) onPartial.accept(partial);
          }
        }
      }
    } catch (Throwable t) {
      onError.accept(t);
    } finally {
      stop();
      onStatus.accept("Idle");
    }
  }

  @Override
  public void close() {
    stop();
    if (model != null) {
      try {
        model.close();
      } catch (Exception ignored) {}
      model = null;
    }
  }
}

