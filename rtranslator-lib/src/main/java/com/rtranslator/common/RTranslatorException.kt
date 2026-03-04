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
    val errorCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception("Error $errorCode: $message", cause) {

    class ConnectionException(errorCode: Int, message: String) : RTranslatorException(errorCode, message)

    class ConfigurationException(errorCode: Int, message: String) : RTranslatorException(errorCode, message)

    class ServiceException(errorCode: Int, message: String) : RTranslatorException(errorCode, message)

    class TTSException(errorCode: Int, message: String, cause: Throwable? = null) :
        RTranslatorException(errorCode, message, cause)

    class ModelException(errorCode: Int, message: String) : RTranslatorException(errorCode, message)

    class LanguageException(errorCode: Int, message: String) : RTranslatorException(errorCode, message)

    class GenericException(errorCode: Int, message: String = "An unknown error occurred.", cause: Throwable? = null) :
        RTranslatorException(errorCode, message, cause)

    // Internal library states (not necessarily from ErrorCodes.java)
    class NotInitializedException(message: String = "Translator is not initialized.") :
        RTranslatorException(-1, message)

    class OutOfMemoryException(message: String = "Not enough memory to perform translation.") :
        RTranslatorException(-2, message)

    class RecognitionException(errorCode: Int, message: String, cause: Throwable? = null) :
        RTranslatorException(errorCode, message, cause)

    class PermissionDeniedException(val permission: String, message: String) :
        RTranslatorException(-3, message)
}
