# RTranslator Library

A powerful, **offline**, **privacy-focused** Android library for neural machine translation and text-to-speech.

Built on Meta's **NLLB (No Language Left Behind)** model and OpenAI's **Whisper**, this library provides state-of-the-art translation quality that runs completely on-device.

## ✨ Features

- 🌍 **200+ Languages**: Supports translation between 200+ language pairs
- 🔒 **100% Offline**: No internet required, complete privacy
- 🧠 **State-of-the-art Quality**: Uses Meta's NLLB-600M model
- 🚀 **Optimized for Mobile**: Quantized INT8 models with KV cache
- 💬 **Simple API**: Clean, Kotlin-first API with Coroutines support
- 🗣️ **TTS Integration**: Built-in text-to-speech support
- 📦 **Self-contained**: All models bundled, no external dependencies

## 📋 Requirements

- **Android 7.0+** (API 24+)
- **Minimum 6GB RAM** (device requirement)
- **~1.2GB storage** for model files
- **ARM64-v8a** or other supported ABIs

## 📥 Installation

### Step 1: Add the library to your project

```kotlin
// In your app/build.gradle.kts
dependencies {
    implementation(project(":rtranslator-lib"))
}
```

### Step 2: Download model files

The library requires ~1.2GB of model files:
- `NLLB_encoder.onnx` (~350MB)
- `NLLB_decoder.onnx` (~350MB)
- `NLLB_embed_and_lm_head.onnx` (~200MB)
- `NLLB_cache_initializer.onnx` (~100MB)
- `sentencepiece_bpe.model` (~5MB)

**Download from**: [RTranslator Releases](https://github.com/niedev/RTranslator/releases)

Place these files in your app's internal files directory:
```kotlin
context.filesDir // /data/data/your.package/files/
```

Or implement a download manager to fetch them on first launch.

## 🚀 Quick Start

### Basic Translation

```kotlin
import com.rtranslator.translation.RTranslator
import com.rtranslator.common.Language

// Create translator instance
val translator = RTranslator(context)

// Initialize (checks for models)
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

### With Kotlin Coroutines

```kotlin
class ChatViewModel : ViewModel() {
    private val translator = RTranslator(context)
    
    init {
        viewModelScope.launch {
            translator.initialize()
        }
    }
    
    fun translateMessage(message: String, targetLanguage: Language) {
        viewModelScope.launch {
            try {
                val result = translator.translate(
                    text = message,
                    from = Language.ENGLISH,
                    to = targetLanguage
                )
                
                // Update UI with translation
                _translatedMessage.value = result.translatedText
                
            } catch (e: RTranslatorException) {
                // Handle error
                _error.value = e.message
            }
        }
    }
    
    override fun onCleared() {
        translator.close()
        super.onCleared()
    }
}
```

### Text-to-Speech

```kotlin
import com.rtranslator.tts.RTranslatorTTS

val tts = RTranslatorTTS(context)

// Initialize
tts.initialize()

// Speak translated text
tts.speak(
    text = "Hola, ¿cómo estás?",
    language = Language.SPANISH
)

// Or wait for speech to complete
suspend fun speakAndWait() {
    tts.speakAndWait("Hello!", Language.ENGLISH)
    println("Speaking finished!")
}

// Clean up
tts.shutdown()
```

## 📚 Advanced Usage

### Custom Configuration

```kotlin
import com.rtranslator.translation.TranslationConfig

// Fast mode (beam size 1)
val fastTranslator = RTranslator(
    context = context,
    config = TranslationConfig.FAST
)

// Quality mode (beam size 4, slower but better)
val qualityTranslator = RTranslator(
    context = context,
    config = TranslationConfig.QUALITY
)

// Custom configuration
val customConfig = TranslationConfig(
    defaultBeamSize = 2,
    useCache = true,
    maxMemoryUsageMB = 1500
)
```

### Streaming Translation

```kotlin
translator.translateFlow(
    text = longText,
    from = Language.ENGLISH,
    to = Language.FRENCH
).collect { result ->
    // Update UI with intermediate results
    println("Progress: ${result.translatedText}")
    
    if (result.isFinal) {
        println("Translation complete!")
    }
}
```

### Check Language Support

```kotlin
// Get all supported languages
val languages = translator.getSupportedLanguages()
println("Supported: ${languages.size} languages")

// Check specific language
if (translator.isLanguageSupported(Language.ARABIC)) {
    println("Arabic is supported!")
}
```

### Cache Translations

```kotlin
// Cache translations in Room database
@Entity
data class CachedTranslation(
    @PrimaryKey val id: String, // hash of text + languages
    val originalText: String,
    val translatedText: String,
    val fromLanguage: String,
    val toLanguage: String,
    val timestamp: Long
)

suspend fun translateWithCache(text: String, from: Language, to: Language): String {
    val cacheKey = "$text:${from.code}:${to.code}".hashCode().toString()
    
    // Check cache first
    val cached = database.translationDao().getTranslation(cacheKey)
    if (cached != null) {
        return cached.translatedText
    }
    
    // Translate and cache
    val result = translator.translate(text, from, to)
    database.translationDao().insert(
        CachedTranslation(
            id = cacheKey,
            originalText = text,
            translatedText = result.translatedText,
            fromLanguage = from.code,
            toLanguage = to.code,
            timestamp = System.currentTimeMillis()
        )
    )
    
    return result.translatedText
}
```

## 🎯 Use Cases

### Chat App with Translation

```kotlin
class ChatRepository(
    private val translator: RTranslator
) {
    suspend fun sendTranslatedMessage(
        message: String,
        recipientLanguage: Language
    ) {
        val result = translator.translate(
            text = message,
            from = Language.getDefault(),
            to = recipientLanguage
        )
        
        // Send translated message
        chatService.sendMessage(result.translatedText)
    }
}
```

### Real-time Translation

```kotlin
class TranslationService {
    private val translator = RTranslator(context)
    
    fun translateInBackground(messages: List<Message>) {
        CoroutineScope(Dispatchers.Default).launch {
            messages.forEach { message ->
                val translated = translator.translate(
                    text = message.content,
                    from = message.sourceLanguage,
                    to = userPreferredLanguage
                )
                
                // Update message in database
                database.updateMessage(message.id, translated.translatedText)
            }
        }
    }
}
```

## ⚙️ Configuration

### Memory Management

Translation uses ~1.3GB RAM. For optimal performance:

```kotlin
// Release translator when not in use
fun onAppBackground() {
    translator.close()
}

// Re-initialize when needed
fun onAppForeground() {
    lifecycleScope.launch {
        translator.initialize()
    }
}
```

### Performance Tips

1. **Use beam size 1** for fast chat translations
2. **Enable caching** to avoid re-translating the same text
3. **Batch translations** when possible
4. **Release resources** when app is backgrounded
5. **Avoid translating very long texts** (split into sentences)

## 🐛 Error Handling

```kotlin
try {
    val result = translator.translate(text, from, to)
} catch (e: RTranslatorException) {
    when (e) {
        is RTranslatorException.NotInitializedException -> {
            // Translator not initialized
        }
        is RTranslatorException.ModelNotFoundException -> {
            // Model files missing - prompt user to download
        }
        is RTranslatorException.LanguageNotSupportedException -> {
            // Language not supported
        }
        is RTranslatorException.TranslationFailedException -> {
            // Translation failed during execution
        }
        is RTranslatorException.OutOfMemoryException -> {
            // Not enough memory - try releasing resources
        }
        is RTranslatorException.TTSException -> {
            // TTS error
        }
    }
}
```

## 📊 Supported Languages

The library supports 200+ languages including:

- 🇬🇧 English
- 🇪🇸 Spanish
- 🇫🇷 French
- 🇩🇪 German
- 🇮🇹 Italian
- 🇵🇹 Portuguese
- 🇨🇳 Chinese
- 🇯🇵 Japanese
- 🇰🇷 Korean
- 🇷🇺 Russian
- 🇸🇦 Arabic
- 🇮🇳 Hindi
- And 188+ more...

Get the full list: `translator.getSupportedLanguages()`

## 📄 License

This library is based on [RTranslator](https://github.com/niedev/RTranslator) by Luca Martino.

```
Copyright 2024 RTranslator Library

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

**Note**: NLLB model is licensed for **non-commercial use only**. For commercial use, check Meta's licensing terms.

## 🙏 Credits

- **NLLB Model**: Meta AI Research
- **ONNX Runtime**: Microsoft
- **SentencePiece**: Google
- **Original RTranslator**: Luca Martino ([@niedev](https://github.com/niedev))

## 🔗 Links

- [RTranslator Original Project](https://github.com/niedev/RTranslator)
- [NLLB Model](https://ai.meta.com/research/no-language-left-behind/)
- [ONNX Runtime](https://onnxruntime.ai/)

---

Made with ❤️ for the Android community

