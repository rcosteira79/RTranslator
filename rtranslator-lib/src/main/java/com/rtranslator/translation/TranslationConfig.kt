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

/**
 * Configuration for the RTranslator.
 *
 * @property defaultBeamSize The default beam size for translation (1-10). Higher values may produce
 *                           better translations but are slower. Default is 1 for speed.
 * @property useCache Whether to use KV cache for faster subsequent translations. Default is true.
 * @property maxMemoryUsageMB Maximum memory usage in MB. Default is 1500MB.
 */
data class TranslationConfig(
    val defaultBeamSize: Int = 1,
    val useCache: Boolean = true,
    val maxMemoryUsageMB: Int = 1500
) {
    init {
        require(defaultBeamSize in 1..10) {
            "Beam size must be between 1 and 10"
        }
        require(maxMemoryUsageMB >= 1000) {
            "Max memory usage must be at least 1000MB"
        }
    }

    companion object {
        /**
         * Configuration optimized for speed (beam size 1).
         */
        val FAST = TranslationConfig(defaultBeamSize = 1)

        /**
         * Configuration balanced between speed and quality (beam size 2).
         */
        val BALANCED = TranslationConfig(defaultBeamSize = 2)

        /**
         * Configuration optimized for quality (beam size 4).
         */
        val QUALITY = TranslationConfig(defaultBeamSize = 4)
    }
}

