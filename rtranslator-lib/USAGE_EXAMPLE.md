# RTranslator Library - Usage Example

This guide shows you how to integrate RTranslator into a chat application for message translation.

## Example: Chat App with Translation

### 1. Setup in your app's build.gradle

```kotlin
dependencies {
    implementation(project(":rtranslator-lib"))
}
```

### 2. Create a Translation Repository

```kotlin
package com.example.chatapp.translation

import android.content.Context
import com.rtranslator.common.Language
import com.rtranslator.translation.RTranslator
import com.rtranslator.translation.TranslationConfig
import com.rtranslator.translation.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepository @Inject constructor(
    private val context: Context
) {
    private val translator = RTranslator(
        context = context,
        config = TranslationConfig.FAST // Use fast mode for chat
    )

    private var isInitialized = false

    suspend fun initialize() {
        if (!isInitialized) {
            translator.initialize()
            isInitialized = true
        }
    }

    suspend fun translateMessage(
        text: String,
        fromLanguage: Language,
        toLanguage: Language
    ): TranslationResult = withContext(Dispatchers.Default) {
        translator.translate(
            text = text,
            from = fromLanguage,
            to = toLanguage
        )
    }

    fun getSupportedLanguages() = translator.getSupportedLanguages()

    fun close() {
        translator.close()
        isInitialized = false
    }
}
```

### 3. Use in ViewModel

```kotlin
package com.example.chatapp.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.translation.TranslationRepository
import com.rtranslator.common.Language
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val translationRepository: TranslationRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()

    // User's preferred language
    private val userLanguage = Language.ENGLISH

    init {
        viewModelScope.launch {
            // Initialize translator on app start
            translationRepository.initialize()
        }
    }

    fun onMessageReceived(message: ChatMessage) {
        viewModelScope.launch {
            // If message is in different language, translate it
            if (message.language != userLanguage) {
                _isTranslating.value = true

                try {
                    val result = translationRepository.translateMessage(
                        text = message.content,
                        fromLanguage = message.language,
                        toLanguage = userLanguage
                    )

                    // Update message with translation
                    val translatedMessage = message.copy(
                        translatedContent = result.translatedText,
                        showTranslation = true
                    )

                    _messages.value = _messages.value + translatedMessage

                } catch (e: Exception) {
                    // Show original message if translation fails
                    _messages.value = _messages.value + message.copy(
                        translationError = e.message
                    )
                } finally {
                    _isTranslating.value = false
                }
            } else {
                // Same language, no translation needed
                _messages.value = _messages.value + message
            }
        }
    }

    fun sendMessage(text: String, recipientLanguage: Language) {
        viewModelScope.launch {
            val myMessage = ChatMessage(
                id = generateId(),
                content = text,
                language = userLanguage,
                isMine = true
            )

            // Add to UI immediately
            _messages.value = _messages.value + myMessage

            // Translate for recipient if needed
            if (userLanguage != recipientLanguage) {
                _isTranslating.value = true

                try {
                    val result = translationRepository.translateMessage(
                        text = text,
                        fromLanguage = userLanguage,
                        toLanguage = recipientLanguage
                    )

                    // Send translated text to recipient
                    sendToRecipient(result.translatedText)

                } catch (e: Exception) {
                    // Handle error
                    showError("Translation failed: ${e.message}")
                } finally {
                    _isTranslating.value = false
                }
            } else {
                // Send original text
                sendToRecipient(text)
            }
        }
    }

    private fun sendToRecipient(text: String) {
        // Your chat logic here
    }

    private fun showError(message: String) {
        // Show error to user
    }

    private fun generateId() = System.currentTimeMillis().toString()

    override fun onCleared() {
        translationRepository.close()
        super.onCleared()
    }
}

data class ChatMessage(
    val id: String,
    val content: String,
    val language: Language,
    val isMine: Boolean,
    val translatedContent: String? = null,
    val showTranslation: Boolean = false,
    val translationError: String? = null
)
```

### 4. UI with Jetpack Compose

```kotlin
package com.example.chatapp.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(message = message)
            }
        }

        // Translation indicator
        if (isTranslating) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Input field
        ChatInputField(
            onSendMessage = { text ->
                viewModel.sendMessage(text, recipientLanguage = Language.SPANISH)
            }
        )
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Original message
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge
            )

            // Translation
            if (message.showTranslation && message.translatedContent != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Divider()
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.translatedContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Translated from ${message.language.code}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Error
            if (message.translationError != null) {
                Text(
                    text = "Translation failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ChatInputField(
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onSendMessage(text)
                    text = ""
                }
            }
        ) {
            Text("Send")
        }
    }
}
```

### 5. Initialize in Application class

```kotlin
package com.example.chatapp

import android.app.Application
import androidx.lifecycle.lifecycleScope
import com.example.chatapp.translation.TranslationRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChatApplication : Application() {

    @Inject
    lateinit var translationRepository: TranslationRepository

    override fun onCreate() {
        super.onCreate()

        // Pre-initialize translator in background
        lifecycleScope.launch {
            try {
                translationRepository.initialize()
            } catch (e: Exception) {
                // Handle initialization error
                // Maybe show a dialog to download models
            }
        }
    }
}
```

## Result

Now your chat app will:
- ✅ Automatically translate incoming messages to user's language
- ✅ Translate outgoing messages to recipient's language
- ✅ Show original + translation
- ✅ Work completely offline
- ✅ Provide high-quality translations with NLLB

## Performance Tips for Chat Apps

1. **Use FAST config**: `TranslationConfig.FAST` for real-time chat
2. **Cache translations**: Same messages often repeated
3. **Batch translate**: When loading message history
4. **Background init**: Initialize translator on app start
5. **Release on pause**: Free memory when app backgrounded

Enjoy translating! 🌍

