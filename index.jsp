<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>WebSpeech Translator</title>

<style>
    body {
        font-family: Arial, sans-serif;
        background: linear-gradient(135deg, #667eea, #764ba2);
        color: white;
        text-align: center;
        padding: 40px;
    }

    .container {
        background: rgba(255,255,255,0.1);
        padding: 30px;
        border-radius: 15px;
        width: 500px;
        margin: auto;
        backdrop-filter: blur(10px);
    }

    h1 {
        margin-bottom: 20px;
    }

    select, textarea, button {
        width: 90%;
        padding: 10px;
        margin: 10px;
        border-radius: 8px;
        border: none;
        font-size: 14px;
    }

    textarea {
        height: 80px;
        resize: none;
    }

    button {
        background: #ff7a18;
        color: white;
        font-weight: bold;
        cursor: pointer;
        transition: 0.3s;
    }

    button:hover {
        background: #ff4b2b;
    }

    .mic {
        font-size: 30px;
        cursor: pointer;
        margin: 15px;
    }
</style>
</head>

<body>

<div class="container">
    <h1>🎤 Multilingual Voice Translator</h1>

    <!-- Source Language -->
    <select id="sourceLang">
        <option value="en">English</option>
        <option value="hi">Hindi</option>
        <option value="fr">French</option>
        <option value="es">Spanish</option>
    </select>

    <!-- Target Language -->
    <select id="targetLang">
        <option value="hi">Hindi</option>
        <option value="en">English</option>
        <option value="fr">French</option>
        <option value="es">Spanish</option>
    </select>

    <!-- Mic Button -->
    <div class="mic" onclick="startListening()">🎙️ Tap to Speak</div>

    <!-- Recognized Text -->
    <textarea id="speechText" placeholder="Recognized speech will appear here..."></textarea>

    <!-- Translate Button -->
    <button onclick="translateText()">Translate</button>

    <!-- Translated Text -->
    <textarea id="translatedText" placeholder="Translated text will appear here..."></textarea>
</div>

<script>
function startListening() {
    alert("🎤 Microphone activated (connect Vosk or Web Speech API)");
}

function translateText() {
    const text = document.getElementById("speechText").value;

    // TODO: connect to SpeechServlet
    fetch("SpeechServlet", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "text=" + encodeURIComponent(text)
    })
    .then(res => res.text())
    .then(data => {
        document.getElementById("translatedText").value = data;
    })
    .catch(err => alert("Error: " + err));
}
</script>

</body>
</html>
