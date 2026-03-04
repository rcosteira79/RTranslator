# RTranslator Library

A powerful, **offline**, **privacy-focused** Android library for neural machine translation, speech recognition, and text-to-speech.

Built on Meta's **NLLB (No Language Left Behind)** model for translation and OpenAI's **Whisper** model for speech recognition, this library provides state-of-the-art quality that runs completely on-device.

## Features

- 🌍 **200+ Languages** - Supports translation between 200+ language pairs
- 🎤 **Speech Recognition** - Offline speech-to-text using Whisper
- 🔒 **100% Offline** - No internet required, complete privacy
- 🧠 **State-of-the-art Quality** - Uses Meta's NLLB-600M and OpenAI's Whisper models
- 🚀 **Optimized for Mobile** - Quantized INT8 models with KV cache
- 💬 **Simple API** - Clean, Kotlin-first API with Coroutines support
- 🗣️ **TTS Integration** - Built-in text-to-speech support

## Requirements

- **Android 7.0+** (API 24+)
- **Minimum 6GB RAM** for translation (4GB for speech recognition)
- **~1.7GB storage** for all model files (~1.2GB translation + ~500MB speech)
- **ARM64-v8a** or other supported ABIs

## Installation

Add the library to your project:

```kotlin
// settings.gradle.kts
include(":rtranslator-lib")

// app/build.gradle.kts
dependencies {
    implementation(project(":rtranslator-lib"))
}
```

## Model Files

### Translation Models
Download model files (~1.2GB total) from [RTranslator Releases](https://github.com/niedev/RTranslator/releases):

- `NLLB_encoder.onnx` (~350MB)
- `NLLB_decoder.onnx` (~350MB)
- `NLLB_embed_and_lm_head.onnx` (~200MB)
- `NLLB_cache_initializer.onnx` (~100MB)
- `sentencepiece_bpe.model` (~5MB)

### Speech Recognition Models (Whisper)
Download Whisper model files (~500MB total):

- `Whisper_initializer.onnx`
- `Whisper_encoder.onnx`
- `Whisper_decoder.onnx`
- `Whisper_cache_initializer.onnx`
- `Whisper_cache_initializer_batch.onnx`
- `Whisper_detokenizer.onnx`

Place these in your app's internal files directory (`context.filesDir`).

## Quick Start

### Translation

```kotlin
import com.rtranslator.translation.RTranslator
import com.rtranslator.common.Language

// Create and initialize translator
val translator = RTranslator(context)
translator.initialize()

// Translate text
val result = translator.translate(
    text = "Hello, how are you?",
    from = Language.ENGLISH,
    to = Language.SPANISH
)

println(result.translatedText) // "Hola, ¿cómo estás?"

// Clean up when done
translator.close()
```

### Speech Recognition

```kotlin
import com.rtranslator.speech.RSpeechRecognizer
import com.rtranslator.common.Language

// Create and initialize speech recognizer
val speechRecognizer = RSpeechRecognizer(context)
speechRecognizer.initialize()

// Recognize speech from audio data (16kHz, mono, float)
val result = speechRecognizer.recognize(
    audioData = floatArray,
    language = Language.ENGLISH
)

println(result.text) // "Hello world"

// Or use flow for continuous recognition from microphone
speechRecognizer.recognizeFlow(Language.ENGLISH)
    .collect { result ->
        println("Recognized: ${result.text}")
    }

// Clean up when done
speechRecognizer.close()
```

## Configuration

### Translation Configuration

```kotlin
import com.rtranslator.translation.TranslationConfig

// Fast mode (beam size 1) - best for chat
val translator = RTranslator(
    context = context,
    config = TranslationConfig.FAST
)

// Quality mode (beam size 4) - slower but better quality
val translator = RTranslator(
    context = context,
    config = TranslationConfig.QUALITY
)
```

### Speech Recognition Configuration

```kotlin
import com.rtranslator.speech.SpeechRecognitionConfig

val speechRecognizer = RSpeechRecognizer(
    context = context,
    config = SpeechRecognitionConfig(
        beamSize = 4,                    // Higher = better quality, slower
        micSensitivity = 2000,           // Voice detection threshold
        speechTimeoutMs = 1300,          // Silence before ending recording
        prevVoiceDurationMs = 1300,      // Audio to include before voice detected
        qualityLow = false               // false = only high-quality languages
    )
)
```

## Text-to-Speech

```kotlin
import com.rtranslator.tts.RTranslatorTTS

val tts = RTranslatorTTS(context)
tts.initialize()

tts.speak("Hola, ¿cómo estás?", Language.SPANISH)

tts.shutdown()
```

## Error Handling

```kotlin
try {
    val result = translator.translate(text, from, to)
} catch (e: RTranslatorException) {
    when (e) {
        is RTranslatorException.NotInitializedException -> { /* ... */ }
        is RTranslatorException.ModelException -> { /* Missing/corrupt model */ }
        is RTranslatorException.LanguageException -> { /* Language not supported */ }
        is RTranslatorException.RecognitionException -> { /* Speech recognition failed */ }
        is RTranslatorException.PermissionDeniedException -> { /* Need RECORD_AUDIO */ }
        is RTranslatorException.OutOfMemoryException -> { /* ... */ }
    }
}
```

## Permissions

For speech recognition, you need the `RECORD_AUDIO` permission:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

## Supported Languages

### Translation
200+ languages including English, Spanish, French, German, Italian, Portuguese, Chinese, Japanese, Korean, Russian, Arabic, Hindi, and many more.

Get the full list: `translator.getSupportedLanguages()`

### Speech Recognition (Whisper)
30+ high-quality languages including: Arabic, Bulgarian, Catalan, Chinese, Czech, Danish, German, Greek, English, Spanish, Finnish, French, Galician, Croatian, Italian, Japanese, Korean, Macedonian, Malay, Norwegian, Dutch, Polish, Portuguese, Romanian, Russian, Slovak, Swedish, Tamil, Thai, Turkish, Ukrainian, Urdu, Vietnamese.

Get the full list: `speechRecognizer.getSupportedLanguages()`

## License

This library is based on [RTranslator](https://github.com/niedev/RTranslator) by Luca Martino.

Licensed under the Apache License, Version 2.0.

**Note**: 
- NLLB model is licensed for **non-commercial use only**. For commercial use, check Meta's licensing terms.
- Whisper model is under MIT license (commercially usable).

## Credits

- **NLLB Model** - Meta AI Research
- **Whisper Model** - OpenAI
- **ONNX Runtime** - Microsoft
- **SentencePiece** - Google
- **Original RTranslator** - Luca Martino ([@niedev](https://github.com/niedev))
