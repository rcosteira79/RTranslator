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
import android.util.Log
import androidx.annotation.WorkerThread
import com.rtranslator.common.CustomLocale
import com.rtranslator.common.ErrorCodes
import com.rtranslator.common.ErrorMapper
import com.rtranslator.common.Language
import com.rtranslator.common.RTranslatorException
import com.rtranslator.translation.internal.NeuralNetworkApi
import com.rtranslator.translation.internal.NeuralNetworkApiText
import com.rtranslator.translation.internal.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private lateinit var translator: Translator

    /**
     * The directory where model files are stored.
     * Useful for downloading models to the correct location.
     */
    private val modelsDirectory: File
        get() = context.filesDir

    /**
     * Initialize the translator. This must be called before any translation.
     * Downloads models if not present (~1.2GB).
     *
     * @throws RTranslatorException.ModelNotFoundException if models cannot be loaded
     */
    @WorkerThread
    suspend fun initialize() = suspendCancellableCoroutine { continuation ->
        if (isReady()) {
            Log.d("RTranslator", "Translator already initialized")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        // Check if downloaded model files exist (sentencepiece_bpe.model is in assets, not downloaded)
        val requiredDownloadedModels = listOf(
            "NLLB_encoder.onnx",
            "NLLB_decoder.onnx",
            "NLLB_embed_and_lm_head.onnx",
            "NLLB_cache_initializer.onnx"
        )

        val missingModels = requiredDownloadedModels.filter { modelName ->
            !File(modelsDirectory, modelName).exists()
        }

        if (missingModels.isNotEmpty()) {
            Log.e("RTranslator", "Missing model files: ${missingModels.joinToString(", ")}")
            continuation.resumeWithException(
                RTranslatorException.ModelException(
                    errorCode = ErrorCodes.ERROR_LOADING_MODEL,
                    message = "Missing model files: ${missingModels.joinToString(", ")}. " +
                            "Please download them first."
                )
            )
            return@suspendCancellableCoroutine
        }

        Log.d("RTranslator", "All model files present, creating Translator instance...")

        translator = Translator(
            context,
            Translator.NLLB_CACHE,
            object : NeuralNetworkApi.InitListener {
                override fun onInitializationFinished() {
                    // Initialization completed successfully
                    Log.d("RTranslator", "Translator initialization finished successfully")
                    continuation.resume(Unit)
                }

                override fun onError(reasons: IntArray?, value: Long) {
                    // Initialization failed
                    Log.e(
                        "RTranslator",
                        "Translator initialization failed with error code: ${reasons?.firstOrNull()}"
                    )
                    continuation.resumeWithException(
                        RTranslatorException.ModelException(
                            errorCode = reasons?.firstOrNull() ?: ErrorCodes.ERROR_LOADING_MODEL,
                            message = "Failed to initialize translator"
                        )
                    )
                }
            }
        )
    }

    /**
     * Check if the translator is initialized and ready to use.
     */
    fun isReady(): Boolean = ::translator.isInitialized

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
        beamSize: Int = 5
    ): TranslationResult = suspendCancellableCoroutine { continuation ->
        checkInitialized()

        // If same language, return as-is
        if (from == to) {
            return@suspendCancellableCoroutine continuation.resume(
                TranslationResult(
                    originalText = text,
                    translatedText = text,
                    sourceLanguage = from,
                    targetLanguage = to,
                    isFinal = true,
                    translationTimeMs = 0
                )
            ) { _, _, _ -> }
        }

        val startTime = System.currentTimeMillis()
        val originalLocale = CustomLocale(from.locale)

        translator.translateMessage(
            NeuralNetworkApiText(text, originalLocale),
            CustomLocale(to.locale),
            beamSize,
            object : Translator.TranslateMessageListener {
                override fun onFailure(reasons: IntArray?, value: Long) {
                    continuation.resumeWithException(ErrorMapper.fromErrorCode(errorCode = reasons?.first() ?: -1))
                }

                override fun onTranslatedMessage(message: NeuralNetworkApiText, messageID: Long, isFinal: Boolean) {
                    val endTime = System.currentTimeMillis()

                    continuation.resume(
                        TranslationResult(
                            originalText = text,
                            translatedText = message.text,
                            sourceLanguage = from,
                            targetLanguage = to,
                            isFinal = isFinal,
                            translationTimeMs = endTime - startTime
                        )
                    )
                }
            }
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
        val result = translate(text, from, to, beamSize ?: 1)
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
            Language.PORTUGUESE,
            Language.POLISH,
            Language.SPANISH,
            Language.FRENCH,
            Language.GERMAN,
            Language.ITALIAN,
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
     * Check if all required model files are present.
     *
     * @return true if all models are downloaded, false otherwise
     */
    fun areModelsDownloaded(): Boolean {
        // Only check downloaded models (sentencepiece_bpe.model is in assets, not downloaded)
        val requiredDownloadedModels = listOf(
            "NLLB_encoder.onnx",
            "NLLB_decoder.onnx",
            "NLLB_embed_and_lm_head.onnx",
            "NLLB_cache_initializer.onnx"
        )

        return requiredDownloadedModels.all { modelName ->
            File(modelsDirectory, modelName).exists()
        }
    }

    private fun checkInitialized() {
        if (!isReady()) {
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
