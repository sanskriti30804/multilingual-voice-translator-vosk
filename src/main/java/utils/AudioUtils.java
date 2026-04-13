package utils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public final class AudioUtils {
  private AudioUtils() {}

  public static AudioFormat defaultMonoPcm16k() {
    // Vosk typically works best with 16kHz mono, 16-bit, little-endian PCM
    return new AudioFormat(16000.0f, 16, 1, true, false);
  }

  public static TargetDataLine openDefaultMicLine(AudioFormat format) throws Exception {
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
      throw new IllegalStateException("No supported microphone line for format: " + format);
    }
    TargetDataLine line = (TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
    line.open(format);
    return line;
  }
}

