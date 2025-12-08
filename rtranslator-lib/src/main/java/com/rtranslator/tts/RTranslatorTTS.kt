/*
 * Copyright 2024 RTranslator Library
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rtranslator.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rtranslator.common.Language
import com.rtranslator.common.RTranslatorException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Text-to-Speech wrapper for RTranslator.
 * Uses Android's system TTS engine (e.g., Google TTS).
 *
 * ## Basic Usage:
 * ```kotlin
 * val tts = RTranslatorTTS(context)
 *
 * // Initialize
 * tts.initialize()
 *
 * // Speak text
 * tts.speak("Hello, world!", Language.ENGLISH)
 *
 * // Clean up
 * tts.shutdown()
 * ```
 *
 * ## Suspend/Coroutine Usage:
 * ```kotlin
 * // Wait for speech to complete
 * tts.speakAndWait("Hello!", Language.ENGLISH)
 * ```
 *
 * @property context Android application context
 */
class RTranslatorTTS(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /**
     * Initialize the TTS engine.
     *
     * @throws RTranslatorException.TTSException if TTS initialization fails
     */
    suspend fun initialize() = suspendCancellableCoroutine { continuation ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    RTranslatorException.TTSException(
                        "Failed to initialize TTS engine. " +
                                "Make sure Google TTS or a compatible TTS is installed."
                    )
                )
            }
        }
    }

    /**
     * Check if TTS is initialized and ready.
     */
    fun isReady(): Boolean = isInitialized && tts != null

    /**
     * Speak text in the specified language.
     *
     * @param text The text to speak
     * @param language The language to speak in
     * @param queueMode QUEUE_ADD or QUEUE_FLUSH
     * @throws RTranslatorException.TTSException if TTS is not initialized or speaking fails
     */
    fun speak(
        text: String,
        language: Language,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ) {
        checkInitialized()

        tts?.apply {
            setLanguage(language.locale)
            speak(text, queueMode, null, "RTranslatorTTS_${System.currentTimeMillis()}")
        } ?: throw RTranslatorException.TTSException("TTS engine is not available")
    }

    /**
     * Speak text and suspend until speaking is complete.
     *
     * @param text The text to speak
     * @param language The language to speak in
     * @throws RTranslatorException.TTSException if TTS is not initialized or speaking fails
     */
    suspend fun speakAndWait(
        text: String,
        language: Language
    ) = suspendCancellableCoroutine { continuation ->
        checkInitialized()

        val utteranceId = "RTranslatorTTS_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(finishedUtteranceId: String?) {
                if (finishedUtteranceId == utteranceId) {
                    continuation.resume(Unit)
                }
            }

            override fun onError(utteranceId: String?) {
                continuation.resumeWithException(
                    RTranslatorException.TTSException("TTS speaking failed")
                )
            }
        })

        tts?.apply {
            setLanguage(language.locale)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } ?: continuation.resumeWithException(
            RTranslatorException.TTSException("TTS engine is not available")
        )
    }

    /**
     * Stop speaking immediately.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Check if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    /**
     * Get list of available languages supported by the TTS engine.
     */
    fun getAvailableLanguages(): Set<Locale> {
        return tts?.availableLanguages ?: emptySet()
    }

    /**
     * Shutdown the TTS engine and release resources.
     * Call this when you're done using TTS.
     */
    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun checkInitialized() {
        if (!isInitialized || tts == null) {
            throw RTranslatorException.TTSException(
                "TTS is not initialized. Call initialize() first."
            )
        }
    }
}

