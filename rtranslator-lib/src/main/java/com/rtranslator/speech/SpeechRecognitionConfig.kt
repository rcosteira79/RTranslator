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

/**
 * Configuration for the speech recognizer.
 *
 * @property beamSize Beam size for recognition (1-10). Higher = better quality but slower.
 * @property qualityLow If true, enables all Whisper languages. If false, only high-quality languages.
 * @property micSensitivity Microphone sensitivity threshold (400-15000). Default is 2000.
 * @property speechTimeoutMs Silence duration in ms to end recording (100-5000). Default is 1300.
 * @property prevVoiceDurationMs Duration in ms of audio before voice detection to include (100-1800). Default is 1300.
 */
data class SpeechRecognitionConfig(
    val beamSize: Int = 4,
    val qualityLow: Boolean = false,
    val micSensitivity: Int = DEFAULT_MIC_SENSITIVITY,
    val speechTimeoutMs: Int = DEFAULT_SPEECH_TIMEOUT_MS,
    val prevVoiceDurationMs: Int = DEFAULT_PREV_VOICE_DURATION_MS
) {
    init {
        require(beamSize in 1..10) { "beamSize must be between 1 and 10" }
        require(micSensitivity in MIN_MIC_SENSITIVITY..MAX_MIC_SENSITIVITY) {
            "micSensitivity must be between $MIN_MIC_SENSITIVITY and $MAX_MIC_SENSITIVITY"
        }
        require(speechTimeoutMs in MIN_SPEECH_TIMEOUT_MS..MAX_SPEECH_TIMEOUT_MS) {
            "speechTimeoutMs must be between $MIN_SPEECH_TIMEOUT_MS and $MAX_SPEECH_TIMEOUT_MS"
        }
        require(prevVoiceDurationMs in MIN_PREV_VOICE_DURATION_MS..MAX_PREV_VOICE_DURATION_MS) {
            "prevVoiceDurationMs must be between $MIN_PREV_VOICE_DURATION_MS and $MAX_PREV_VOICE_DURATION_MS"
        }
    }

    companion object {
        const val MIN_MIC_SENSITIVITY = 400
        const val DEFAULT_MIC_SENSITIVITY = 2000
        const val MAX_MIC_SENSITIVITY = 15000

        const val MIN_SPEECH_TIMEOUT_MS = 100
        const val DEFAULT_SPEECH_TIMEOUT_MS = 1300
        const val MAX_SPEECH_TIMEOUT_MS = 5000

        const val MIN_PREV_VOICE_DURATION_MS = 100
        const val DEFAULT_PREV_VOICE_DURATION_MS = 1300
        const val MAX_PREV_VOICE_DURATION_MS = 1800
    }
}
