package speech;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class VoskResultParser {
  private VoskResultParser() {}

  static String extractText(String json) {
    // Example: {"text" : "hello world"}
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      if (obj.has("text") && !obj.get("text").isJsonNull()) {
        return obj.get("text").getAsString().trim();
      }
    } catch (Exception ignored) {}
    return "";
  }

  static String extractPartial(String json) {
    // Example: {"partial" : "hello wor"}
    try {
      JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
      if (obj.has("partial") && !obj.get("partial").isJsonNull()) {
        return obj.get("partial").getAsString().trim();
      }
    } catch (Exception ignored) {}
    return "";
  }
}

