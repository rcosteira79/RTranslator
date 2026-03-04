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
 * Listener interface for multi-language speech recognition results.
 * Used when recognizing speech that might be in one of two languages.
 */
public interface RecognizerMultiListener {
    /**
     * Called when speech has been recognized with two possible language results.
     *
     * @param text1 The recognized text for language 1
     * @param languageCode1 The language code for result 1
     * @param confidenceScore1 The confidence score for result 1
     * @param text2 The recognized text for language 2
     * @param languageCode2 The language code for result 2
     * @param confidenceScore2 The confidence score for result 2
     */
    void onSpeechRecognizedResult(String text1, String languageCode1, double confidenceScore1,
                                   String text2, String languageCode2, double confidenceScore2);

    /**
     * Called when an error occurs during recognition.
     *
     * @param reasons Array of error codes
     * @param value Additional error value
     */
    void onError(int[] reasons, long value);
}
