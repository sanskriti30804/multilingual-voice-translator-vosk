# Real-Time Multi-Language Speech Translator (Vosk + JavaFX)

## Requirements
- JDK **17+**
- Maven (**mvn** must work in your terminal)
- Microphone access enabled in Windows privacy settings

## 1) Download the Vosk model (offline speech recognition)
1. Download the small English model: `vosk-model-small-en-us-0.15`
2. Extract it into this exact folder (relative to project root):

`model/vosk-model-small-en-us-0.15/`

Your folder should contain subfolders like `am/`, `conf/`, etc.

## 2) Run LibreTranslate (translation API)
The app calls LibreTranslate at:
- Default: `http://localhost:5000`
- Override: set environment variable `LIBRETRANSLATE_URL`

Example (PowerShell):

```powershell
$env:LIBRETRANSLATE_URL="http://localhost:5000"
```

## 3) Run the JavaFX app
From the project root:

```powershell
mvn clean javafx:run
```

## Notes
- **Start Recording**: microphone → Vosk live transcript → translation(s) → optional TTS
- **Targets** list supports multi-select (Ctrl/Shift)
- **Dark Mode** applies a CSS theme
- **Save to File** exports transcript + translations into a `.txt`

