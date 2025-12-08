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

package com.rtranslator.translation

import android.content.Context
import com.rtranslator.common.Language
import com.rtranslator.common.RTranslatorException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main API for RTranslator - Offline Neural Machine Translation Library.
 *
 * This library uses Meta's NLLB (No Language Left Behind) model for high-quality
 * translation that runs completely offline on-device.
 *
 * ## Basic Usage:
 * ```kotlin
 * val translator = RTranslator(context)
 *
 * // Initialize (download models if needed)
 * translator.initialize()
 *
 * // Translate text
 * val result = translator.translate(
 *     text = "Hello, world!",
 *     from = Language.ENGLISH,
 *     to = Language.SPANISH
 * )
 * println(result.translatedText) // "¡Hola, mundo!"
 *
 * // Clean up when done
 * translator.close()
 * ```
 *
 * ## Advanced Usage:
 * ```kotlin
 * // Use coroutine Flow for streaming results
 * translator.translateFlow(text, from, to)
 *     .collect { result ->
 *         println("Progress: ${result.translatedText}")
 *     }
 *
 * // Get supported languages
 * val languages = translator.getSupportedLanguages()
 * ```
 *
 * @property context Android application context
 * @property config Translation configuration (beam size, cache settings, etc.)
 */
class RTranslator(
    private val context: Context,
    private val config: TranslationConfig = TranslationConfig()
) {

    // TODO: Wire up to actual Translator.java implementation
    // For now, this is the API surface. Implementation will be added in next phase.

    private var isInitialized = false
    private val modelsDirectory: File
        get() = context.filesDir

    /**
     * Initialize the translator. This must be called before any translation.
     * Downloads models if not present (~1.2GB).
     *
     * @throws RTranslatorException.ModelNotFoundException if models cannot be loaded
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        // Check if model files exist
        val requiredModels = listOf(
            "NLLB_encoder.onnx",
            "NLLB_decoder.onnx",
            "NLLB_embed_and_lm_head.onnx",
            "NLLB_cache_initializer.onnx",
            "sentencepiece_bpe.model"
        )

        val missingModels = requiredModels.filter { modelName ->
            !File(modelsDirectory, modelName).exists()
        }

        if (missingModels.isNotEmpty()) {
            throw RTranslatorException.ModelNotFoundException(
                "Missing model files: ${missingModels.joinToString(", ")}. " +
                        "Please download them first."
            )
        }

        // TODO: Initialize actual Translator
        // translator = Translator(context, Translator.NLLB_CACHE, ...)

        isInitialized = true
    }

    /**
     * Check if the translator is initialized and ready to use.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Translate text from one language to another.
     *
     * @param text The text to translate
     * @param from Source language
     * @param to Target language
     * @param beamSize Beam size for translation (1-10). Higher = better quality but slower.
     *                 If null, uses the default from config.
     * @return TranslationResult containing the translated text and metadata
     * @throws RTranslatorException.NotInitializedException if not initialized
     * @throws RTranslatorException.LanguageNotSupportedException if language not supported
     * @throws RTranslatorException.TranslationFailedException if translation fails
     */
    suspend fun translate(
        text: String,
        from: Language,
        to: Language,
        beamSize: Int? = null
    ): TranslationResult = withContext(Dispatchers.Default) {
        checkInitialized()

        // If same language, return as-is
        if (from == to) {
            return@withContext TranslationResult(
                originalText = text,
                translatedText = text,
                sourceLanguage = from,
                targetLanguage = to,
                isFinal = true,
                translationTimeMs = 0
            )
        }

        val startTime = System.currentTimeMillis()

        // TODO: Wire up to actual translation
        // val translatedText = translator.performTextTranslation(...)

        val translatedText = "[TRANSLATED] $text" // Placeholder

        val endTime = System.currentTimeMillis()

        TranslationResult(
            originalText = text,
            translatedText = translatedText,
            sourceLanguage = from,
            targetLanguage = to,
            isFinal = true,
            translationTimeMs = endTime - startTime
        )
    }

    /**
     * Translate text with streaming results (for showing progress to users).
     *
     * @param text The text to translate
     * @param from Source language
     * @param to Target language
     * @param beamSize Beam size for translation
     * @return Flow of TranslationResult (intermediate and final results)
     */
    fun translateFlow(
        text: String,
        from: Language,
        to: Language,
        beamSize: Int? = null
    ): Flow<TranslationResult> = flow {
        checkInitialized()

        // If same language, return as-is
        if (from == to) {
            emit(
                TranslationResult(
                    originalText = text,
                    translatedText = text,
                    sourceLanguage = from,
                    targetLanguage = to,
                    isFinal = true,
                    translationTimeMs = 0
                )
            )
            return@flow
        }

        // TODO: Wire up streaming translation
        // For now, just emit final result
        val result = translate(text, from, to, beamSize)
        emit(result)
    }

    /**
     * Get list of all languages supported by the translator.
     * These are languages supported by both the translation model and speech recognition.
     *
     * @return List of supported languages
     */
    suspend fun getSupportedLanguages(): List<Language> = withContext(Dispatchers.IO) {
        // TODO: Get actual supported languages from Translator
        // For now, return common languages
        listOf(
            Language.ENGLISH,
            Language.SPANISH,
            Language.FRENCH,
            Language.GERMAN,
            Language.ITALIAN,
            Language.PORTUGUESE,
            Language.CHINESE,
            Language.JAPANESE,
            Language.KOREAN,
            Language.RUSSIAN,
            Language.ARABIC,
            Language.HINDI
        )
    }

    /**
     * Check if a specific language is supported.
     *
     * @param language The language to check
     * @return true if supported, false otherwise
     */
    suspend fun isLanguageSupported(language: Language): Boolean {
        val supported = getSupportedLanguages()
        return supported.contains(language)
    }

    /**
     * Get the models directory path.
     * Useful for downloading models to the correct location.
     */
    fun getModelsDirectory(): File = modelsDirectory

    /**
     * Check if all required model files are present.
     *
     * @return true if all models are downloaded, false otherwise
     */
    fun areModelsDownloaded(): Boolean {
        val requiredModels = listOf(
            "NLLB_encoder.onnx",
            "NLLB_decoder.onnx",
            "NLLB_embed_and_lm_head.onnx",
            "NLLB_cache_initializer.onnx",
            "sentencepiece_bpe.model"
        )

        return requiredModels.all { modelName ->
            File(modelsDirectory, modelName).exists()
        }
    }

    /**
     * Close the translator and release resources.
     * Call this when you're done using the translator to free memory.
     */
    fun close() {
        // TODO: Close actual Translator and release native resources
        isInitialized = false
    }

    private fun checkInitialized() {
        if (!isInitialized) {
            throw RTranslatorException.NotInitializedException()
        }
    }

    companion object {
        /**
         * Get the approximate size of the model files in bytes.
         */
        const val MODELS_SIZE_BYTES = 1_200_000_000L // ~1.2GB

        /**
         * Minimum required RAM in MB to use the translator.
         */
        const val MIN_REQUIRED_RAM_MB = 6000 // 6GB
    }
}

