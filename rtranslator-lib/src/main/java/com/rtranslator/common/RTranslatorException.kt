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

package com.rtranslator.common

/**
 * Base exception for all RTranslator errors.
 */
sealed class RTranslatorException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Exception thrown when the translator is not initialized.
     */
    class NotInitializedException(
        message: String = "Translator is not initialized. Call initialize() first."
    ) : RTranslatorException(message)

    /**
     * Exception thrown when model files are missing or corrupted.
     */
    class ModelNotFoundException(
        message: String = "Translation model files not found. Please download them first."
    ) : RTranslatorException(message)

    /**
     * Exception thrown when a language is not supported.
     */
    class LanguageNotSupportedException(
        language: Language,
        message: String = "Language ${language.code} is not supported for translation."
    ) : RTranslatorException(message)

    /**
     * Exception thrown when translation fails during execution.
     */
    class TranslationFailedException(
        message: String = "Translation failed during execution.",
        cause: Throwable? = null
    ) : RTranslatorException(message, cause)

    /**
     * Exception thrown when there's not enough memory to perform translation.
     */
    class OutOfMemoryException(
        message: String = "Not enough memory to perform translation. Consider releasing resources."
    ) : RTranslatorException(message)

    /**
     * Exception thrown when TTS is not available or fails.
     */
    class TTSException(
        message: String,
        cause: Throwable? = null
    ) : RTranslatorException(message, cause)
}

