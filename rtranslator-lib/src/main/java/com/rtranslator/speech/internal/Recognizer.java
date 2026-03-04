/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rtranslator.speech.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.extensions.OrtxPackage;

import com.rtranslator.R;
import com.rtranslator.common.CustomLocale;
import com.rtranslator.common.ErrorCodes;
import com.rtranslator.common.nn.TensorUtils;
import com.rtranslator.common.nn.Utils;

/**
 * Speech recognizer using OpenAI's Whisper model for offline speech-to-text conversion.
 * This class manages the ONNX Runtime sessions for Whisper's encoder-decoder architecture.
 */
public class Recognizer {
    private static final String TAG = "Recognizer";
    private static final int MAX_TOKENS_PER_SECOND = 30;
    private static final int MAX_TOKENS = 445;   // Max tokens per transcription
    public static final String UNDEFINED_TEXT = "[(und)]";

    private final ArrayList<RecognizerListener> callbacks = new ArrayList<>();
    private final ArrayList<RecognizerMultiListener> multiCallbacks = new ArrayList<>();
    private boolean recognizing = false;
    private final ArrayDeque<DataContainer> dataToRecognize = new ArrayDeque<>();
    private final Object lock = new Object();
    private final Context context;
    private final long totalRamSize;
    private final boolean qualityLow;

    private static final String[] LANGUAGES = {
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr",
            "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
            "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
            "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
            "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk",
            "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw",
            "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc",
            "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo",
            "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl",
            "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue"
    };

    private final int START_TOKEN_ID = 50258;
    private final int TRANSCRIBE_TOKEN_ID = 50359;
    private final int NO_TIMESTAMPS_TOKEN_ID = 50363;

    private OrtSession initSession;
    private OrtSession encoderSession;
    private OrtSession cacheInitSession;
    private OrtSession cacheInitBatchSession;
    private OrtSession decoderSession;
    private OrtSession detokenizerSession;
    private OrtEnvironment onnxEnv;

    /**
     * Listener interface for initialization completion.
     */
    public interface InitListener {
        void onInitializationFinished();
        void onError(int[] reasons, long value);
    }

    /**
     * Creates a new Recognizer instance.
     *
     * @param context The application context
     * @param qualityLow If true, all Whisper languages are enabled. If false, only high-quality languages.
     * @param initListener Listener for initialization completion
     */
    public Recognizer(Context context, boolean qualityLow, InitListener initListener) {
        this.context = context.getApplicationContext();
        this.qualityLow = qualityLow;
        this.totalRamSize = getTotalRamSize();
        onnxEnv = OrtEnvironment.getEnvironment();

        String filesDir = context.getFilesDir().getPath();
        String modelInitPath = filesDir + "/Whisper_initializer.onnx";
        String encoderPath = filesDir + "/Whisper_encoder.onnx";
        String decoderPath = filesDir + "/Whisper_decoder.onnx";
        String cacheInitPath = filesDir + "/Whisper_cache_initializer.onnx";
        String cacheInitBatchPath = filesDir + "/Whisper_cache_initializer_batch.onnx";
        String detokenizerPath = filesDir + "/Whisper_detokenizer.onnx";

        new Thread(() -> {
            try {
                OrtSession.SessionOptions initSessionOptions = new OrtSession.SessionOptions();
                initSessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                initSessionOptions.setCPUArenaAllocator(false);
                initSessionOptions.setMemoryPatternOptimization(false);
                initSessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                initSession = onnxEnv.createSession(modelInitPath, initSessionOptions);

                OrtSession.SessionOptions encoderSessionOptions = new OrtSession.SessionOptions();
                encoderSessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                if (totalRamSize <= 7000) {
                    encoderSessionOptions.setCPUArenaAllocator(false);
                    encoderSessionOptions.setMemoryPatternOptimization(false);
                } else {
                    encoderSessionOptions.setCPUArenaAllocator(true);
                    encoderSessionOptions.setMemoryPatternOptimization(true);
                }
                encoderSessionOptions.setSymbolicDimensionValue("batch_size", 1);
                encoderSessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                encoderSession = onnxEnv.createSession(encoderPath, encoderSessionOptions);

                OrtSession.SessionOptions cacheSessionOptions = new OrtSession.SessionOptions();
                cacheSessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                cacheSessionOptions.setCPUArenaAllocator(false);
                cacheSessionOptions.setMemoryPatternOptimization(false);
                cacheSessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                cacheInitSession = onnxEnv.createSession(cacheInitPath, cacheSessionOptions);
                cacheInitBatchSession = onnxEnv.createSession(cacheInitBatchPath, cacheSessionOptions);

                OrtSession.SessionOptions decoderSessionOptions = new OrtSession.SessionOptions();
                decoderSessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                decoderSessionOptions.setCPUArenaAllocator(false);
                decoderSessionOptions.setMemoryPatternOptimization(false);
                decoderSessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                decoderSession = onnxEnv.createSession(decoderPath, decoderSessionOptions);

                OrtSession.SessionOptions detokenizerSessionOptions = new OrtSession.SessionOptions();
                detokenizerSessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                detokenizerSessionOptions.setCPUArenaAllocator(false);
                detokenizerSessionOptions.setMemoryPatternOptimization(false);
                detokenizerSession = onnxEnv.createSession(detokenizerPath, detokenizerSessionOptions);

                initListener.onInitializationFinished();
            } catch (OrtException e) {
                Log.e(TAG, "Failed to initialize Recognizer", e);
                initListener.onError(new int[]{ErrorCodes.ERROR_LOADING_MODEL}, 0);
            }
        }).start();
    }

    /**
     * Recognizes speech audio data.
     *
     * @param data The audio data in float format (PCM_FLOAT)
     * @param beamSize Beam size for decoding
     * @param languageCode The expected language code
     */
    public void recognize(final float[] data, int beamSize, final String languageCode) {
        new Thread("recognizer") {
            @Override
            public void run() {
                synchronized (lock) {
                    Log.d(TAG, "recognizingCalled");
                    if (data != null) {
                        dataToRecognize.addLast(new DataContainer(data, beamSize, languageCode));
                        if (dataToRecognize.size() >= 1 && !recognizing) {
                            recognizeInternal();
                        }
                    }
                }
            }
        }.start();
    }

    /**
     * Recognizes speech audio data with two possible languages.
     *
     * @param data The audio data in float format (PCM_FLOAT)
     * @param beamSize Beam size for decoding
     * @param languageCode1 First possible language code
     * @param languageCode2 Second possible language code
     */
    public void recognize(final float[] data, int beamSize, final String languageCode1, final String languageCode2) {
        new Thread("recognizer") {
            @Override
            public void run() {
                synchronized (lock) {
                    Log.d(TAG, "recognizingCalled (multi-language)");
                    if (data != null) {
                        dataToRecognize.addLast(new DataContainer(data, beamSize, languageCode1, languageCode2));
                        if (dataToRecognize.size() >= 1 && !recognizing) {
                            recognizeInternal();
                        }
                    }
                }
            }
        }.start();
    }

    @SuppressWarnings("unchecked")
    private void recognizeInternal() {
        recognizing = true;
        DataContainer data = dataToRecognize.pollFirst();

        if (initSession == null || encoderSession == null || cacheInitSession == null ||
            decoderSession == null || detokenizerSession == null) {
            notifyError(new int[]{ErrorCodes.ERROR_LOADING_MODEL}, 0);
            recognizing = false;
            return;
        }

        if (data != null) {
            try {
                FloatBuffer floatAudioDataBuffer = FloatBuffer.wrap(data.data);
                OnnxTensor audioTensor = OnnxTensor.createTensor(onnxEnv, floatAudioDataBuffer,
                    TensorUtils.tensorShape(1L, (long) data.data.length));

                int maxTokens = (data.data.length / Recorder.SAMPLE_RATE) * MAX_TOKENS_PER_SECOND;
                if (maxTokens > MAX_TOKENS) {
                    maxTokens = MAX_TOKENS;
                }
                boolean execution1HitMaxLength = false;
                boolean execution2HitMaxLength = false;

                // Execution of pre ops
                long startTimeInMs = SystemClock.elapsedRealtime();
                Map<String, OnnxTensor> initInputs = new LinkedHashMap<>();
                initInputs.put("audio_pcm", audioTensor);
                OrtSession.Result outputsInit = initSession.run(initInputs);
                OnnxTensor outputInit = (OnnxTensor) outputsInit.get(0);
                Log.d(TAG, "Pre ops done in: " + (SystemClock.elapsedRealtime() - startTimeInMs) + "ms");

                // Execution of the encoder
                Map<String, OnnxTensor> encoderInputs = new LinkedHashMap<>();
                encoderInputs.put("input_features", outputInit);
                OrtSession.Result outputs = encoderSession.run(encoderInputs);
                OnnxTensor outputEncoder = (OnnxTensor) outputs.get(0);
                Log.d(TAG, "Encoder done in: " + (SystemClock.elapsedRealtime() - startTimeInMs) + "ms");

                // Execution of decoder
                final int eos = 50257;
                ArrayList<Integer> completeOutput = new ArrayList<>();
                ArrayList<Integer> completeOutput2 = new ArrayList<>();
                double outputProbability1 = 0;
                double outputProbability2 = 0;
                boolean finished1 = false;
                boolean finished2 = false;

                OnnxTensor inputIDsTensor;
                OnnxTensor decoderOutput;
                Map<String, OnnxTensor> decoderInput;
                float[][][] value;
                float[] outputValues;
                float[] outputValues2 = null;
                int batchSize = 1;

                if (data.languageCode2 != null) {
                    batchSize = 2;
                }

                // Prepare the cache initializer input and execute it
                Map<String, OnnxTensor> initInput = new HashMap<>();
                OrtSession.Result initResult;
                if (batchSize == 1) {
                    initInput.put("encoder_hidden_states", outputEncoder);
                    initResult = cacheInitSession.run(initInput);
                } else {
                    float[][] outputEncoderValue = ((float[][][]) outputEncoder.getValue())[0];
                    float[][][] outputEncoderFlatBatched = TensorUtils.batchTensor(outputEncoderValue, 2);
                    OnnxTensor outputEncoderBatched = TensorUtils.createFloatTensor(onnxEnv, outputEncoderFlatBatched,
                        new long[]{2, outputEncoderValue.length, outputEncoderValue[0].length}, new long[]{0});
                    initInput.put("encoder_hidden_states", outputEncoderBatched);
                    initResult = cacheInitBatchSession.run(initInput);
                }

                // Start the iterative execution of the decoder
                OrtSession.Result result = null;
                OrtSession.Result oldResult = null;
                int max = -1;
                int max2 = eos;
                boolean isFirstIteration = true;
                int j = 1;

                int languageID = getLanguageID(data.languageCode);
                int languageID2 = -1;
                if (batchSize == 2) {
                    languageID2 = getLanguageID(data.languageCode2);
                }
                int[] decoderInitialInputIDs = {START_TOKEN_ID, languageID, TRANSCRIBE_TOKEN_ID, NO_TIMESTAMPS_TOKEN_ID};
                int[] decoderInitialInputIDs2 = {START_TOKEN_ID, languageID2, TRANSCRIBE_TOKEN_ID, NO_TIMESTAMPS_TOKEN_ID};

                while (!(max == eos && max2 == eos)) {
                    if (j <= 4) {
                        if (batchSize == 1) {
                            inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{decoderInitialInputIDs[j - 1]});
                        } else {
                            inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv,
                                new int[]{decoderInitialInputIDs[j - 1], decoderInitialInputIDs2[j - 1]}, new long[]{2, 1});
                        }
                    } else {
                        if (batchSize == 1) {
                            inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{max});
                        } else {
                            inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{max, max2}, new long[]{2, 1});
                        }
                    }

                    decoderInput = new HashMap<>();
                    decoderInput.put("input_ids", inputIDsTensor);

                    if (isFirstIteration) {
                        long[] shape = {batchSize, 12, 0, 64};
                        OnnxTensor decoderPastTensor = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, shape);
                        for (int i = 0; i < 12; i++) {
                            decoderInput.put("past_key_values." + i + ".decoder.key", decoderPastTensor);
                            decoderInput.put("past_key_values." + i + ".decoder.value", decoderPastTensor);
                            decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResult.get("present." + i + ".encoder.key").get());
                            decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResult.get("present." + i + ".encoder.value").get());
                        }
                        isFirstIteration = false;
                    } else {
                        for (int i = 0; i < 12; i++) {
                            decoderInput.put("past_key_values." + i + ".decoder.key", (OnnxTensor) result.get("present." + i + ".decoder.key").get());
                            decoderInput.put("past_key_values." + i + ".decoder.value", (OnnxTensor) result.get("present." + i + ".decoder.value").get());
                            decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResult.get("present." + i + ".encoder.key").get());
                            decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResult.get("present." + i + ".encoder.value").get());
                        }
                    }

                    oldResult = result;
                    result = decoderSession.run(decoderInput);

                    if (oldResult != null) {
                        oldResult.close();
                    }

                    decoderOutput = (OnnxTensor) result.get("logits").get();
                    value = (float[][][]) decoderOutput.getValue();
                    outputValues = value[0][0];

                    if (!finished1) {
                        max = Utils.getIndexOfLargest(outputValues);
                        completeOutput.add(max);
                    }

                    if (batchSize == 2) {
                        outputValues2 = value[1][0];
                        if (!finished2) {
                            max2 = Utils.getIndexOfLargest(outputValues2);
                            completeOutput2.add(max2);
                        }
                    }

                    if (batchSize == 2) {
                        if (!finished1) {
                            outputProbability1 = outputProbability1 + Math.log(Utils.softmax(outputValues[max], outputValues));
                        }
                        if (!finished2) {
                            outputProbability2 = outputProbability2 + Math.log(Utils.softmax(outputValues2[max2], outputValues2));
                        }
                    }

                    if (j >= maxTokens) {
                        if (!finished1) {
                            execution1HitMaxLength = true;
                            max = eos;
                        }
                        if (!finished2) {
                            execution2HitMaxLength = true;
                            max2 = eos;
                        }
                    }

                    if (max == eos) {
                        finished1 = true;
                    }
                    if (max2 == eos) {
                        finished2 = true;
                    }

                    j++;
                }

                if (batchSize == 2) {
                    outputProbability1 = outputProbability1 / completeOutput.size();
                    outputProbability2 = outputProbability2 / completeOutput2.size();
                }

                // Execution of the detokenizer
                Map<String, OnnxTensor> detokenizerInputs = new LinkedHashMap<>();
                if (batchSize == 1) {
                    String finalText = UNDEFINED_TEXT;
                    if (!execution1HitMaxLength) {
                        int[] sequences = completeOutput.stream().mapToInt(i -> i).toArray();
                        detokenizerInputs.put("sequences", TensorUtils.createInt32Tensor(onnxEnv, sequences, new long[]{1, 1, sequences.length}));
                        OrtSession.Result detokenizerOutputs = detokenizerSession.run(detokenizerInputs);
                        Object finalTextResult = detokenizerOutputs.get(0).getValue();
                        finalText = ((String[][]) finalTextResult)[0][0];
                        detokenizerOutputs.close();
                    }
                    Log.d(TAG, "Result: " + correctText(finalText));

                    outputs.close();
                    notifyResult(correctText(finalText), data.languageCode, outputProbability1, true);
                } else {
                    String firstText = UNDEFINED_TEXT;
                    if (!execution1HitMaxLength) {
                        int[] sequence1 = completeOutput.stream().mapToInt(i -> i).toArray();
                        detokenizerInputs.put("sequences", OnnxTensor.createTensor(onnxEnv, IntBuffer.wrap(sequence1), TensorUtils.tensorShape(1, 1, sequence1.length)));
                        OrtSession.Result detokenizerOutputs = detokenizerSession.run(detokenizerInputs);
                        Object firstTextResult = detokenizerOutputs.get(0).getValue();
                        firstText = ((String[][]) firstTextResult)[0][0];
                        detokenizerOutputs.close();
                    }

                    String secondText = UNDEFINED_TEXT;
                    if (!execution2HitMaxLength) {
                        int[] sequence2 = completeOutput2.stream().mapToInt(i -> i).toArray();
                        detokenizerInputs = new LinkedHashMap<>();
                        detokenizerInputs.put("sequences", OnnxTensor.createTensor(onnxEnv, IntBuffer.wrap(sequence2), TensorUtils.tensorShape(1, 1, sequence2.length)));
                        OrtSession.Result detokenizerOutputs2 = detokenizerSession.run(detokenizerInputs);
                        Object secondTextResult = detokenizerOutputs2.get(0).getValue();
                        secondText = ((String[][]) secondTextResult)[0][0];
                        detokenizerOutputs2.close();
                    }

                    notifyMultiResult(correctText(firstText), data.languageCode, outputProbability1,
                            correctText(secondText), data.languageCode2, outputProbability2);
                }

                outputs.close();
                outputInit.close();
                initResult.close();

                Log.d(TAG, "SPEECH RECOGNITION DONE IN: " + (SystemClock.elapsedRealtime() - startTimeInMs) + "ms");

            } catch (OrtException e) {
                Log.e(TAG, "Recognition error", e);
                notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0);
            }
        }

        if (!dataToRecognize.isEmpty()) {
            recognizeInternal();
        } else {
            recognizing = false;
        }
    }

    private String correctText(String text) {
        String correctedText = text;

        // Remove timestamps that Whisper sometimes inserts
        String regex = "<\\|[^>]*\\|> ";
        correctedText = correctedText.replaceAll(regex, "");

        correctedText = correctedText.trim();

        if (correctedText.length() >= 2) {
            char firstChar = correctedText.charAt(0);
            if (Character.isLowerCase(firstChar)) {
                StringBuilder sb = new StringBuilder(correctedText);
                sb.setCharAt(0, Character.toUpperCase(firstChar));
                correctedText = sb.toString();
            }
            correctedText = correctedText.replace("...", "");
        }
        return correctedText;
    }

    /**
     * Gets the list of supported languages for speech recognition.
     *
     * @param context The application context
     * @param qualityLow If true, returns all Whisper languages. If false, only high-quality languages.
     * @return List of supported CustomLocale objects
     */
    public static ArrayList<CustomLocale> getSupportedLanguages(Context context, boolean qualityLow) {
        ArrayList<CustomLocale> languages = new ArrayList<>();
        if (!qualityLow) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document document = documentBuilder.parse(context.getResources().openRawResource(R.raw.whisper_supported_languages));
                NodeList list = document.getElementsByTagName("code");
                for (int i = 0; i < list.getLength(); i++) {
                    languages.add(CustomLocale.getInstance(list.item(i).getTextContent()));
                }
            } catch (IOException | SAXException | ParserConfigurationException e) {
                Log.e(TAG, "Error parsing supported languages", e);
            }
        } else {
            for (String language : LANGUAGES) {
                languages.add(CustomLocale.getInstance(language));
            }
        }
        return languages;
    }

    /**
     * Releases all resources held by this Recognizer.
     */
    public void destroy() {
        try {
            if (initSession != null) initSession.close();
            if (encoderSession != null) encoderSession.close();
            if (cacheInitSession != null) cacheInitSession.close();
            if (cacheInitBatchSession != null) cacheInitBatchSession.close();
            if (decoderSession != null) decoderSession.close();
            if (detokenizerSession != null) detokenizerSession.close();
        } catch (OrtException e) {
            Log.e(TAG, "Error closing sessions", e);
        }
    }

    private int getLanguageID(String language) {
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(language)) {
                return START_TOKEN_ID + i + 1;
            }
        }
        Log.e(TAG, "Error converting language code " + language + " to Whisper code");
        return -1;
    }

    public void addCallback(RecognizerListener callback) {
        callbacks.add(callback);
    }

    public void removeCallback(RecognizerListener callback) {
        callbacks.remove(callback);
    }

    public void addMultiCallback(RecognizerMultiListener callback) {
        multiCallbacks.add(callback);
    }

    public void removeMultiCallback(RecognizerMultiListener callback) {
        multiCallbacks.remove(callback);
    }

    private void notifyResult(String text, String languageCode, double confidenceScore, boolean isFinal) {
        for (RecognizerListener callback : callbacks) {
            callback.onSpeechRecognizedResult(text, languageCode, confidenceScore, isFinal);
        }
    }

    private void notifyMultiResult(String text1, String languageCode1, double confidenceScore1,
                                   String text2, String languageCode2, double confidenceScore2) {
        for (RecognizerMultiListener callback : multiCallbacks) {
            callback.onSpeechRecognizedResult(text1, languageCode1, confidenceScore1, text2, languageCode2, confidenceScore2);
        }
    }

    private void notifyError(int[] reasons, long value) {
        for (RecognizerListener callback : callbacks) {
            callback.onError(reasons, value);
        }
        for (RecognizerMultiListener callback : multiCallbacks) {
            callback.onError(reasons, value);
        }
    }

    private long getTotalRamSize() {
        android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memoryInfo = new android.app.ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem / (1024 * 1024); // Return in MB
    }

    private static class DataContainer {
        final float[] data;
        final String languageCode;
        final String languageCode2;
        final int beamSize;

        DataContainer(float[] data, int beamSize, String languageCode) {
            this.data = data;
            this.beamSize = beamSize;
            this.languageCode = languageCode;
            this.languageCode2 = null;
        }

        DataContainer(float[] data, int beamSize, String languageCode, String languageCode2) {
            this.data = data;
            this.beamSize = beamSize;
            this.languageCode = languageCode;
            this.languageCode2 = languageCode2;
        }
    }
}
