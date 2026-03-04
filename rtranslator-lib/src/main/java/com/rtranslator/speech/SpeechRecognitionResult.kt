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

import com.rtranslator.common.Language

/**
 * Represents the result of speech recognition.
 *
 * @property text The recognized text from speech
 * @property language The detected/specified language of the speech
 * @property confidenceScore The confidence score of the recognition (0.0 to 1.0)
 * @property isFinal Whether this is the final result or an intermediate result
 * @property recognitionTimeMs Time taken for recognition in milliseconds
 */
data class SpeechRecognitionResult(
    val text: String,
    val language: Language,
    val confidenceScore: Double,
    val isFinal: Boolean,
    val recognitionTimeMs: Long = 0
)

/**
 * Represents a multi-language speech recognition result.
 * Used when recognizing speech that might be in one of two languages.
 *
 * @property result1 First recognition result
 * @property result2 Second recognition result (different language)
 */
data class MultiLanguageSpeechResult(
    val result1: SpeechRecognitionResult,
    val result2: SpeechRecognitionResult
)
