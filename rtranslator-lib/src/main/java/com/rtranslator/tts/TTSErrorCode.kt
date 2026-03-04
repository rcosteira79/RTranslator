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

package com.rtranslator.tts

/**
 * Error codes specific to Text-to-Speech operations.
 */
enum class TTSErrorCode(val code: Int) {
    /**
     * TTS engine initialization failed.
     * This usually means Google TTS or a compatible TTS is not installed.
     */
    INITIALIZATION_FAILED(100),

    /**
     * TTS engine is not available or not initialized.
     */
    ENGINE_NOT_AVAILABLE(101),

    /**
     * TTS engine is not initialized. Call initialize() first.
     */
    NOT_INITIALIZED(102),

    /**
     * TTS speaking operation failed.
     */
    SPEAKING_FAILED(103)
}
