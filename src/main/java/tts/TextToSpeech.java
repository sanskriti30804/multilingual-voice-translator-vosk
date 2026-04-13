package tts;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple FreeTTS wrapper.
 *
 * Notes:
 * - FreeTTS is older, but works offline and is beginner-friendly.
 * - We run speech synthesis on a single background thread to avoid blocking the UI.
 */
public final class TextToSpeech implements AutoCloseable {
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "tts-worker");
    t.setDaemon(true);
    return t;
  });

  private final Voice voice;

  public TextToSpeech() {
    // Common default voice shipped with FreeTTS
    voice = VoiceManager.getInstance().getVoice("kevin16");
    if (voice == null) {
      throw new IllegalStateException("FreeTTS voice 'kevin16' not found. Check FreeTTS dependency.");
    }
    voice.allocate();
  }

  public void speakAsync(String text) {
    if (text == null || text.isBlank()) return;
    executor.submit(() -> {
      synchronized (voice) {
        voice.speak(text);
      }
    });
  }

  @Override
  public void close() {
    executor.shutdownNow();
    try {
      voice.deallocate();
    } catch (Exception ignored) {}
  }
}

