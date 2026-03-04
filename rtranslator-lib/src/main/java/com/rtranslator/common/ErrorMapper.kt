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
 * Utility to map error codes to [RTranslatorException].
 */
object ErrorMapper {

    /**
     * Maps an error code from [ErrorCodes] to a specific [RTranslatorException].
     */
    fun fromErrorCode(errorCode: Int): RTranslatorException {
        return when (errorCode) {
            ErrorCodes.MISSED_CONNECTION -> RTranslatorException.ConnectionException(errorCode, "Missed connection.")
            
            ErrorCodes.MISSED_ARGUMENT -> RTranslatorException.ConfigurationException(errorCode, "Missing argument.")
            ErrorCodes.MISSED_CREDENTIALS -> RTranslatorException.ConfigurationException(errorCode, "Missing credentials.")
            ErrorCodes.MAX_CREDIT_OFFET_REACHED -> RTranslatorException.ConfigurationException(errorCode, "Max credit offset reached.")
            
            ErrorCodes.SAFETY_NET_EXCEPTION -> RTranslatorException.ServiceException(errorCode, "SafetyNet exception occurred.")
            ErrorCodes.MISSING_PLAY_SERVICES -> RTranslatorException.ServiceException(errorCode, "Google Play Services are missing.")
            
            ErrorCodes.GOOGLE_TTS_ERROR -> RTranslatorException.TTSException(errorCode, "Google TTS error.")
            ErrorCodes.MISSING_GOOGLE_TTS -> RTranslatorException.TTSException(errorCode, "Google TTS is missing.")
            
            ErrorCodes.ERROR_LOADING_MODEL -> RTranslatorException.ModelException(errorCode, "Error loading translation model.")
            ErrorCodes.ERROR_EXECUTING_MODEL -> RTranslatorException.ModelException(errorCode, "Error executing translation model.")
            
            ErrorCodes.LANGUAGE_UNKNOWN -> RTranslatorException.LanguageException(errorCode, "Unknown language.")
            ErrorCodes.FIRST_RESULT_FAIL -> RTranslatorException.LanguageException(errorCode, "First language identification result failed.")
            ErrorCodes.SECOND_RESULT_FAIL -> RTranslatorException.LanguageException(errorCode, "Second language identification result failed.")
            ErrorCodes.BOTH_RESULTS_FAIL -> RTranslatorException.LanguageException(errorCode, "Both language identification results failed.")
            ErrorCodes.BOTH_RESULTS_SUCCESS -> RTranslatorException.LanguageException(errorCode, "Ambiguous language identification: both results succeeded.")
            
            ErrorCodes.ERROR -> RTranslatorException.GenericException(errorCode, "General error occurred.")
            else -> RTranslatorException.GenericException(errorCode, "Unknown error code: $errorCode")
        }
    }
}
