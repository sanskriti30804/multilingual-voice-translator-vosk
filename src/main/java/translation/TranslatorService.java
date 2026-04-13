package translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Simple client for Google Translate unofficial endpoint.
 * Endpoint: GET /translate_a/single
 */
public final class TranslatorService {
  private final OkHttpClient http;
  private final String baseUrl;

  public TranslatorService(String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/+$", "");
    this.http = new OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .build();
  }

  public String translate(String text, String sourceLang, String targetLang) throws IOException {
    if (text == null || text.isBlank()) return "";
    if (targetLang == null || targetLang.isBlank()) return "";
    if (sourceLang == null || sourceLang.isBlank()) sourceLang = "auto";

    String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
    String url = baseUrl
        + "/translate_a/single?client=gtx&dt=t"
        + "&sl=" + URLEncoder.encode(sourceLang, StandardCharsets.UTF_8)
        + "&tl=" + URLEncoder.encode(targetLang, StandardCharsets.UTF_8)
        + "&q=" + encodedText;

    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    try (Response response = http.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String body = response.body() == null ? "" : response.body().string();
        throw new IOException("Translation API failed: HTTP " + response.code() + " " + body);
      }

      String body = response.body() == null ? "" : response.body().string();
      try {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) {
          throw new IOException("Unexpected translation response shape.");
        }

        JsonArray outer = root.getAsJsonArray();
        if (outer.isEmpty() || !outer.get(0).isJsonArray()) {
          throw new IOException("Missing translation payload.");
        }

        JsonArray segments = outer.get(0).getAsJsonArray();
        StringBuilder translated = new StringBuilder();
        for (JsonElement segmentElement : segments) {
          if (!segmentElement.isJsonArray()) continue;
          JsonArray segment = segmentElement.getAsJsonArray();
          if (segment.isEmpty() || segment.get(0).isJsonNull()) continue;
          translated.append(segment.get(0).getAsString());
        }

        if (!translated.isEmpty()) return translated.toString();
        throw new IOException("Translated text not found in response.");
      } catch (RuntimeException ex) {
        throw new IOException("Failed to parse translation response safely.", ex);
      }
    }
  }
}

