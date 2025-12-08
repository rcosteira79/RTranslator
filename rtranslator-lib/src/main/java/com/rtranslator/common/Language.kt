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

import java.util.Locale

/**
 * Represents a language supported by RTranslator.
 *
 * @property code The ISO 639-1 language code (e.g., "en", "es", "fr")
 * @property displayName The human-readable name of the language
 * @property locale The Android Locale representation
 */
data class Language(
    val code: String,
    val displayName: String,
    val locale: Locale
) {
    companion object {
        /**
         * Creates a Language from a language code.
         * The display name will be auto-generated from the locale.
         */
        fun fromCode(code: String): Language {
            val locale = Locale(code)
            return Language(
                code = code,
                displayName = locale.displayLanguage,
                locale = locale
            )
        }

        /**
         * Gets the device's default language.
         */
        fun getDefault(): Language {
            val locale = Locale.getDefault()
            return Language(
                code = locale.language,
                displayName = locale.displayLanguage,
                locale = locale
            )
        }

        // Common languages for convenience
        val ENGLISH = fromCode("en")
        val SPANISH = fromCode("es")
        val FRENCH = fromCode("fr")
        val GERMAN = fromCode("de")
        val ITALIAN = fromCode("it")
        val PORTUGUESE = fromCode("pt")
        val CHINESE = fromCode("zh")
        val JAPANESE = fromCode("ja")
        val KOREAN = fromCode("ko")
        val RUSSIAN = fromCode("ru")
        val ARABIC = fromCode("ar")
        val HINDI = fromCode("hi")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Language) return false
        return code == other.code
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }

    override fun toString(): String {
        return "$displayName ($code)"
    }
}

