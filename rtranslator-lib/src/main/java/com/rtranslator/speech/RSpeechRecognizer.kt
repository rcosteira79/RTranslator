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

package com.rtranslator.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.rtranslator.common.CustomLocale
import com.rtranslator.common.ErrorCodes
import com.rtranslator.common.Language
import com.rtranslator.common.RTranslatorException
import com.rtranslator.speech.internal.Recognizer
import com.rtranslator.speech.internal.RecognizerListener
import com.rtranslator.speech.internal.RecognizerMultiListener
import com.rtranslator.speech.internal.Recorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Main API for RTranslator Speech Recognition - Offline Speech-to-Text using OpenAI's Whisper.
 *
 * This library uses OpenAI's Whisper model (small variant) for high-quality
 * speech recognition that runs completely offline on-device.
 *
 * ## Basic Usage:
 * ```kotlin
 * val speechRecognizer = RSpeechRecognizer(context)
 *
 * // Initialize (loads Whisper model)
 * speechRecognizer.initialize()
 *
 * // Recognize speech from audio data
 * val result = speechRecognizer.recognize(
 *     audioData = floatArray,
 *     language = Language.ENGLISH
 * )
 * println(result.text) // "Hello world"
 *
 * // Or use flow for continuous recognition
 * speechRecognizer.recognizeFlow(Language.ENGLISH)
 *     .collect { result ->
 *         println("Recognized: ${result.text}")
 *     }
 *
 * // Clean up when done
 * speechRecognizer.close()
 * ```
 *
 * ## Required Permissions:
 * - `android.permission.RECORD_AUDIO` for microphone access
 *
 * ## Required Model Files:
 * The following Whisper ONNX model files must be downloaded to the app's files directory:
 * - Whisper_initializer.onnx
 * - Whisper_encoder.onnx
 * - Whisper_decoder.onnx
 * - Whisper_cache_initializer.onnx
 * - Whisper_cache_initializer_batch.onnx
 * - Whisper_detokenizer.onnx
 *
 * @property context Android application context
 * @property config Speech recognition configuration
 */
class RSpeechRecognizer(
    private val context: Context,
    private val config: SpeechRecognitionConfig = SpeechRecognitionConfig()
) {
    private var recognizer: Recognizer? = null
    private var recorder: Recorder? = null
    private var isInitialized: Boolean = false

    /**
     * The directory where model files should be stored.
     */
    private val modelsDirectory: File
        get() = context.filesDir

    /**
     * Initialize the speech recognizer. This must be called before any recognition.
     * Loads the Whisper model files.
     *
     * @throws RTranslatorException.ModelNotFoundException if models cannot be loaded
     */
    @WorkerThread
    suspend fun initialize(): Unit = suspendCancellableCoroutine { continuation ->
        if (isReady()) {
            Log.d(TAG, "Speech recognizer already initialized")
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val missingModels = REQUIRED_WHISPER_MODELS.filter { modelName ->
            !File(modelsDirectory, modelName).exists()
        }

        if (missingModels.isNotEmpty()) {
            Log.e(TAG, "Missing Whisper model files: ${missingModels.joinToString(", ")}")
            continuation.resumeWithException(
                RTranslatorException.ModelException(
                    errorCode = ErrorCodes.ERROR_LOADING_MODEL,
                    message = "Missing Whisper model files: ${missingModels.joinToString(", ")}. " +
                            "Please download them first."
                )
            )
            return@suspendCancellableCoroutine
        }

        Log.d(TAG, "All Whisper model files present, creating Recognizer instance...")

        recognizer = Recognizer(
            context,
            config.qualityLow,
            object : Recognizer.InitListener {
                override fun onInitializationFinished() {
                    isInitialized = true
                    Log.d(TAG, "Speech recognizer initialization finished successfully")
                    continuation.resume(Unit)
                }

                override fun onError(reasons: IntArray?, value: Long) {
                    Log.e(TAG, "Speech recognizer initialization failed with error code: ${reasons?.firstOrNull()}")
                    continuation.resumeWithException(
                        RTranslatorException.ModelException(
                            errorCode = reasons?.firstOrNull() ?: ErrorCodes.ERROR_LOADING_MODEL,
                            message = "Failed to initialize speech recognizer"
                        )
                    )
                }
            }
        )
    }

    /**
     * Check if the speech recognizer is initialized and ready to use.
     */
    fun isReady(): Boolean = isInitialized && recognizer != null

    /**
     * Recognize speech from audio data.
     *
     * @param audioData Audio data in float format (PCM_FLOAT, 16kHz, mono)
     * @param language Expected language of the speech
     * @param beamSize Beam size for decoding (higher = better quality but slower)
     * @return SpeechRecognitionResult containing the recognized text
     * @throws RTranslatorException.NotInitializedException if not initialized
     * @throws RTranslatorException.RecognitionFailedException if recognition fails
     */
    suspend fun recognize(
        audioData: FloatArray,
        language: Language,
        beamSize: Int = config.beamSize
    ): SpeechRecognitionResult = suspendCancellableCoroutine { continuation ->
        checkInitialized()

        val startTime = System.currentTimeMillis()
        val languageCode = CustomLocale(language.locale).code

        val listener = object : RecognizerListener {
            override fun onSpeechRecognizedResult(
                text: String?,
                languageCode: String?,
                confidenceScore: Double,
                isFinal: Boolean
            ) {
                if (isFinal) {
                    recognizer?.removeCallback(this)
                    val endTime = System.currentTimeMillis()
                    continuation.resume(
                        SpeechRecognitionResult(
                            text = text ?: "",
                            language = language,
                            confidenceScore = confidenceScore,
                            isFinal = true,
                            recognitionTimeMs = endTime - startTime
                        )
                    )
                }
            }

            override fun onError(reasons: IntArray?, value: Long) {
                recognizer?.removeCallback(this)
                continuation.resumeWithException(
                    RTranslatorException.RecognitionException(
                        errorCode = reasons?.firstOrNull() ?: ErrorCodes.ERROR_EXECUTING_MODEL,
                        message = "Speech recognition failed"
                    )
                )
            }
        }

        recognizer?.addCallback(listener)
        recognizer?.recognize(audioData, beamSize, languageCode)

        continuation.invokeOnCancellation {
            recognizer?.removeCallback(listener)
        }
    }

    /**
     * Recognize speech with two possible languages.
     * Useful when the language of the speaker is uncertain.
     *
     * @param audioData Audio data in float format
     * @param language1 First possible language
     * @param language2 Second possible language
     * @param beamSize Beam size for decoding
     * @return MultiLanguageSpeechResult with results for both languages
     */
    suspend fun recognizeMultiLanguage(
        audioData: FloatArray,
        language1: Language,
        language2: Language,
        beamSize: Int = config.beamSize
    ): MultiLanguageSpeechResult = suspendCancellableCoroutine { continuation ->
        checkInitialized()

        val startTime = System.currentTimeMillis()
        val languageCode1 = CustomLocale(language1.locale).code
        val languageCode2 = CustomLocale(language2.locale).code

        val listener = object : RecognizerMultiListener {
            override fun onSpeechRecognizedResult(
                text1: String?,
                languageCode1: String?,
                confidenceScore1: Double,
                text2: String?,
                languageCode2: String?,
                confidenceScore2: Double
            ) {
                recognizer?.removeMultiCallback(this)
                val endTime = System.currentTimeMillis()
                continuation.resume(
                    MultiLanguageSpeechResult(
                        result1 = SpeechRecognitionResult(
                            text = text1 ?: "",
                            language = language1,
                            confidenceScore = confidenceScore1,
                            isFinal = true,
                            recognitionTimeMs = endTime - startTime
                        ),
                        result2 = SpeechRecognitionResult(
                            text = text2 ?: "",
                            language = language2,
                            confidenceScore = confidenceScore2,
                            isFinal = true,
                            recognitionTimeMs = endTime - startTime
                        )
                    )
                )
            }

            override fun onError(reasons: IntArray?, value: Long) {
                recognizer?.removeMultiCallback(this)
                continuation.resumeWithException(
                    RTranslatorException.RecognitionException(
                        errorCode = reasons?.firstOrNull() ?: ErrorCodes.ERROR_EXECUTING_MODEL,
                        message = "Multi-language speech recognition failed"
                    )
                )
            }
        }

        recognizer?.addMultiCallback(listener)
        recognizer?.recognize(audioData, beamSize, languageCode1, languageCode2)

        continuation.invokeOnCancellation {
            recognizer?.removeMultiCallback(listener)
        }
    }

    /**
     * Start continuous speech recognition from the microphone.
     * Emits speech recognition results as they become available.
     *
     * @param language Expected language of the speech
     * @param useBluetoothHeadset Whether to use Bluetooth headset if available
     * @return Flow of SpeechRecognitionResult
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun recognizeFlow(
        language: Language,
        useBluetoothHeadset: Boolean = false
    ): Flow<SpeechRecognitionResult> = callbackFlow {
        checkInitialized()
        checkAudioPermission()

        val languageCode = CustomLocale(language.locale).code
        var startTime = 0L

        val recognizerListener = object : RecognizerListener {
            override fun onSpeechRecognizedResult(
                text: String?,
                languageCode: String?,
                confidenceScore: Double,
                isFinal: Boolean
            ) {
                if (text != null && text.isNotEmpty() && text != Recognizer.UNDEFINED_TEXT) {
                    val endTime = System.currentTimeMillis()
                    trySend(
                        SpeechRecognitionResult(
                            text = text,
                            language = language,
                            confidenceScore = confidenceScore,
                            isFinal = isFinal,
                            recognitionTimeMs = if (isFinal) endTime - startTime else 0
                        )
                    )
                }
            }

            override fun onError(reasons: IntArray?, value: Long) {
                close(
                    RTranslatorException.RecognitionException(
                        errorCode = reasons?.firstOrNull() ?: ErrorCodes.ERROR_EXECUTING_MODEL,
                        message = "Speech recognition failed"
                    )
                )
            }
        }

        val recorderCallback = object : Recorder.Callback() {
            override fun onVoiceStart() {
                super.onVoiceStart()
                startTime = System.currentTimeMillis()
            }

            override fun onVoice(data: FloatArray, size: Int) {
                super.onVoice(data, size)
                recognizer?.recognize(data, config.beamSize, languageCode)
            }

            override fun onVoiceEnd() {
                super.onVoiceEnd()
            }

            override fun onVolumeLevel(volumeLevel: Float) {
                super.onVolumeLevel(volumeLevel)
            }
        }

        recognizer?.addCallback(recognizerListener)

        recorder = Recorder(
            context,
            useBluetoothHeadset,
            recorderCallback,
            null,
            config
        )
        recorder?.start()

        awaitClose {
            recorder?.stop()
            recorder?.destroy()
            recorder = null
            recognizer?.removeCallback(recognizerListener)
        }
    }

    /**
     * Get list of all languages supported for speech recognition.
     *
     * @return List of supported languages
     */
    fun getSupportedLanguages(): List<Language> {
        val locales = Recognizer.getSupportedLanguages(context, config.qualityLow)
        return locales.mapNotNull { locale ->
            try {
                Language.fromLocale(locale.locale)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Check if a specific language is supported for speech recognition.
     *
     * @param language The language to check
     * @return true if supported
     */
    fun isLanguageSupported(language: Language): Boolean {
        return getSupportedLanguages().contains(language)
    }

    /**
     * Check if all required Whisper model files are present.
     *
     * @return true if all models are downloaded
     */
    fun areModelsDownloaded(): Boolean {
        return REQUIRED_WHISPER_MODELS.all { modelName ->
            File(modelsDirectory, modelName).exists()
        }
    }

    /**
     * Release all resources held by this speech recognizer.
     */
    fun close() {
        recorder?.stop()
        recorder?.destroy()
        recorder = null
        recognizer?.destroy()
        recognizer = null
        isInitialized = false
    }

    private fun checkInitialized() {
        if (!isReady()) {
            throw RTranslatorException.NotInitializedException()
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            throw RTranslatorException.PermissionDeniedException(
                permission = Manifest.permission.RECORD_AUDIO,
                message = "RECORD_AUDIO permission is required for speech recognition"
            )
        }
    }

    companion object {
        private const val TAG = "RSpeechRecognizer"

        /**
         * Required Whisper model files.
         */
        val REQUIRED_WHISPER_MODELS = listOf(
            "Whisper_initializer.onnx",
            "Whisper_encoder.onnx",
            "Whisper_decoder.onnx",
            "Whisper_cache_initializer.onnx",
            "Whisper_cache_initializer_batch.onnx",
            "Whisper_detokenizer.onnx"
        )

        /**
         * Approximate total size of Whisper model files in bytes.
         */
        const val MODELS_SIZE_BYTES = 500_000_000L // ~500MB

        /**
         * Sample rate required for audio input (Hz).
         */
        const val REQUIRED_SAMPLE_RATE = 16000

        /**
         * Minimum required RAM in MB to use the speech recognizer.
         */
        const val MIN_REQUIRED_RAM_MB = 4000 // 4GB
    }
}
