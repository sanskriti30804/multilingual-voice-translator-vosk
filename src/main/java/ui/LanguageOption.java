package ui;

public record LanguageOption(String code, String label) {
  @Override
  public String toString() {
    return label + " (" + code + ")";
  }
}

