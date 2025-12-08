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

import com.rtranslator.common.Language

/**
 * Result of a translation operation.
 *
 * @property originalText The original input text
 * @property translatedText The translated text
 * @property sourceLanguage The source language
 * @property targetLanguage The target language
 * @property isFinal Whether this is the final result (for streaming translations)
 * @property translationTimeMs Time taken for translation in milliseconds
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val isFinal: Boolean = true,
    val translationTimeMs: Long = 0
) {
    /**
     * Whether the translation actually happened or if source and target languages were the same.
     */
    val wasTranslated: Boolean
        get() = sourceLanguage != targetLanguage

    override fun toString(): String {
        return "TranslationResult(" +
                "from='${sourceLanguage.code}', " +
                "to='${targetLanguage.code}', " +
                "original='$originalText', " +
                "translated='$translatedText', " +
                "time=${translationTimeMs}ms" +
                ")"
    }
}

