/*
 * Copyright 2016 Luca Martino.
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

package com.rtranslator.speech.internal;

/**
 * Listener interface for speech recognition results.
 */
public interface RecognizerListener {
    /**
     * Called when speech has been recognized.
     *
     * @param text The recognized text
     * @param languageCode The language code of the recognized speech
     * @param confidenceScore The confidence score of the recognition
     * @param isFinal Whether this is the final result
     */
    void onSpeechRecognizedResult(String text, String languageCode, double confidenceScore, boolean isFinal);

    /**
     * Called when an error occurs during recognition.
     *
     * @param reasons Array of error codes
     * @param value Additional error value
     */
    void onError(int[] reasons, long value);
}
