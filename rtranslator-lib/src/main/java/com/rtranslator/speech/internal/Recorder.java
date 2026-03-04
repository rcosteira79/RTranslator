/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

import static android.media.AudioManager.GET_DEVICES_INPUTS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.rtranslator.speech.SpeechRecognitionConfig;

/**
 * Continuously records audio and notifies the {@link Recorder.Callback} when voice (or any
 * sound) is heard. Manages audio input from the microphone, including optional Bluetooth headset support.
 *
 * <p>The recorded audio format is always {@link AudioFormat#ENCODING_PCM_FLOAT} and
 * {@link AudioFormat#CHANNEL_IN_MONO}. The sample rate is 16000 Hz.</p>
 */
public class Recorder {
    private static final String TAG = "Recorder";

    public static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_FLOAT;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 29 * 1000;

    private final Context context;
    private final Callback callback;
    private final SpeechRecognitionConfig config;
    private final boolean useBluetoothHeadset;

    private boolean isRecording;
    private boolean isManualMode = false;
    private int sampleRate;
    @Nullable
    private AudioRecord audioRecord;
    private Thread processingThread;
    private float[] buffer;
    private int readSize;
    private int headIndex;
    private int tailIndex;
    private int startVoiceIndex;

    private long lastVoiceHeardMillis = Long.MAX_VALUE;
    private long voiceStartedMillis;

    private AudioDeviceInfo connectedBleHeadset = null;
    private AudioDeviceCallback audioDeviceCallback;
    private AudioManager audioManager;

    /**
     * Callback interface for Bluetooth headset events.
     */
    public interface BluetoothHeadsetCallback {
        void onScoAudioConnected();
        void onScoAudioDisconnected();
    }

    /**
     * Creates a new Recorder instance.
     *
     * @param context The application context
     * @param useBluetoothHeadset Whether to use Bluetooth headset for input
     * @param callback The callback for voice events
     * @param bluetoothHeadsetCallback Optional callback for Bluetooth events
     * @param config Speech recognition configuration
     */
    public Recorder(
            @NonNull Context context,
            boolean useBluetoothHeadset,
            @NonNull Callback callback,
            @Nullable BluetoothHeadsetCallback bluetoothHeadsetCallback,
            @NonNull SpeechRecognitionConfig config
    ) {
        this.context = context.getApplicationContext();
        this.useBluetoothHeadset = useBluetoothHeadset;
        this.callback = callback;
        this.config = config;
        this.headIndex = 0;
        this.tailIndex = 0;

        callback.setRecorder(this);
        audioRecord = createAudioRecord();

        if (audioRecord == null) {
            Log.e(TAG, "Cannot instantiate Recorder - missing permissions?");
        }

        if (useBluetoothHeadset) {
            this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            boolean success = setBLEHeadsetConnection();
            if (success && bluetoothHeadsetCallback != null) {
                bluetoothHeadsetCallback.onScoAudioConnected();
            }

            this.audioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    if (connectedBleHeadset == null) {
                        boolean connected = setBLEHeadsetConnection();
                        if (connected && bluetoothHeadsetCallback != null) {
                            bluetoothHeadsetCallback.onScoAudioConnected();
                        }
                    }
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    boolean found = false;
                    for (AudioDeviceInfo removedDevice : removedDevices) {
                        if (removedDevice.equals(connectedBleHeadset)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        connectedBleHeadset = null;
                        audioManager.stopBluetoothSco();
                        if (bluetoothHeadsetCallback != null) {
                            bluetoothHeadsetCallback.onScoAudioDisconnected();
                        }
                    }
                }
            };
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
        }
    }

    /**
     * Starts recording audio.
     * The caller is responsible for calling {@link #stop()} later.
     */
    public void start() {
        stop();
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
        processingThread = new Thread(new ProcessVoice(), "processVoice");
        processingThread.start();
    }

    /**
     * Stops recording audio.
     */
    public void stop() {
        if (processingThread != null) {
            processingThread.interrupt();
            processingThread = null;
        }
        if (audioRecord != null) {
            audioRecord.stop();
        }
        dismiss();
        headIndex = 0;
        tailIndex = 0;
    }

    /**
     * Dismisses the currently ongoing utterance.
     */
    public void dismiss() {
        if (lastVoiceHeardMillis != Long.MAX_VALUE) {
            lastVoiceHeardMillis = Long.MAX_VALUE;
        }
    }

    /**
     * Ends the current recording segment and triggers voice end callback.
     */
    public void end() {
        int voiceLength = getMBufferRangeSize(startVoiceIndex, tailIndex);
        float[] data = new float[voiceLength];
        int circularIndex = startVoiceIndex;
        for (int i = 0; i < voiceLength; i++) {
            data[i] = buffer[circularIndex];
            if (circularIndex < buffer.length - 1) {
                circularIndex++;
            } else {
                circularIndex = 0;
            }
        }
        callback.onVoice(data, voiceLength);
        startVoiceIndex = 0;
        lastVoiceHeardMillis = Long.MAX_VALUE;
        callback.onVoiceEnd();
    }

    /**
     * Releases all resources.
     */
    public void destroy() {
        if (useBluetoothHeadset && audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            if (connectedBleHeadset != null) {
                audioManager.stopBluetoothSco();
            }
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
    }

    /**
     * Gets the sample rate of recorded audio.
     *
     * @return The sample rate in Hz
     */
    public int getSampleRate() {
        if (audioRecord != null) {
            return audioRecord.getSampleRate();
        }
        return 0;
    }

    /**
     * Checks if currently using Bluetooth headset for input.
     *
     * @return true if using Bluetooth headset
     */
    public boolean isOnHeadsetSco() {
        return connectedBleHeadset != null;
    }

    public boolean isManualMode() {
        return isManualMode;
    }

    public void setManualMode(boolean manualMode) {
        if (isManualMode != manualMode) {
            isManualMode = manualMode;
            if (isRecording) {
                callback.onVoiceEnd();
            }
            if (isManualMode) {
                Log.d(TAG, "Manual mode activated");
                stop();
            } else {
                start();
                Log.d(TAG, "Manual mode deactivated");
            }
        }
    }

    public void startRecording() {
        start();
    }

    public void stopRecording() {
        end();
        stop();
    }

    public boolean isRecording() {
        return isRecording;
    }

    @SuppressLint("MissingPermission")
    private AudioRecord createAudioRecord() {
        final int minSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (minSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
            return null;
        }
        this.sampleRate = SAMPLE_RATE;
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                minSizeInBytes
        );
        if (record.getState() == AudioRecord.STATE_INITIALIZED) {
            readSize = (minSizeInBytes / 4) * 2;
            buffer = new float[((MAX_SPEECH_LENGTH_MILLIS + 1000) / 1000) * SAMPLE_RATE];
            return record;
        } else {
            record.release();
            return null;
        }
    }

    private boolean setBLEHeadsetConnection() {
        AudioDeviceInfo[] allDeviceInfo = audioManager.getDevices(GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : allDeviceInfo) {
            int deviceType = device.getType();
            if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                if (audioRecord != null) {
                    audioManager.startBluetoothSco();
                    connectedBleHeadset = device;
                }
                return true;
            }
            if (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET || deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                if (audioRecord != null) {
                    boolean success = audioRecord.setPreferredDevice(device);
                    if (success) {
                        connectedBleHeadset = device;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int getMBufferSize() {
        return getMBufferRangeSize(headIndex, tailIndex);
    }

    private int getMBufferRangeSize(int begin, int end) {
        if (begin <= end) {
            return end - begin;
        } else {
            return (buffer.length - begin) + end;
        }
    }

    private boolean isHearingVoice(float[] buf, int begin, int end) {
        if (!isManualMode) {
            int numberOfThreshold = 15;
            int count = begin;
            while (count != end) {
                float s = Math.abs(buf[count]) * 32767;
                if (s > config.getMicSensitivity()) {
                    numberOfThreshold--;
                }
                if (count < buf.length - 1) {
                    count++;
                } else {
                    count = 0;
                }
            }
            return numberOfThreshold <= 0;
        } else {
            return true;
        }
    }

    private void notifyVolumeLevel(float[] buf, int begin, int end) {
        if (isRecording) {
            float[] amplifiedBuffer = new float[getMBufferRangeSize(begin, end)];
            int count = begin;
            int linearCount = 0;
            float amplification = (32767f / config.getMicSensitivity()) * 2;
            while (count != end) {
                amplifiedBuffer[linearCount] = (float) (Math.abs(buf[count]) * amplification);
                if (count < buf.length - 1) {
                    count++;
                } else {
                    count = 0;
                }
                linearCount++;
            }

            float sum = 0;
            for (float v : amplifiedBuffer) {
                sum += v;
            }
            float average = sum / amplifiedBuffer.length;

            if (average > 1) {
                float surplus = average - 1f;
                surplus = surplus / 5;
                average = 0.8f + surplus;
            }
            average = Math.max(0, Math.min(1, average));

            callback.onVolumeLevel(average);
        }
    }

    private class ProcessVoice implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                if (audioRecord != null) {
                    int prevVoiceLength;
                    if (isManualMode) {
                        prevVoiceLength = (int) (0.1 * sampleRate);
                    } else {
                        prevVoiceLength = (config.getPrevVoiceDurationMs() / 1000) * sampleRate;
                    }
                    int size;
                    int oldTailIndex = tailIndex;
                    boolean jumped;
                    if (tailIndex + readSize < buffer.length) {
                        size = audioRecord.read(buffer, tailIndex, readSize, AudioRecord.READ_BLOCKING);
                        tailIndex = tailIndex + size;
                        jumped = false;
                    } else {
                        size = audioRecord.read(buffer, tailIndex, buffer.length - tailIndex, AudioRecord.READ_BLOCKING);
                        tailIndex = 0;
                        int size2 = audioRecord.read(buffer, tailIndex, readSize - size, AudioRecord.READ_BLOCKING);
                        tailIndex = size2;
                        size = size + size2;
                        jumped = true;
                    }
                    if ((oldTailIndex < headIndex && tailIndex > headIndex) ||
                        (oldTailIndex > headIndex && tailIndex > headIndex && jumped)) {
                        headIndex = tailIndex + 1;
                    }

                    notifyVolumeLevel(buffer, oldTailIndex, tailIndex);

                    final long now = System.currentTimeMillis();
                    if (isHearingVoice(buffer, oldTailIndex, tailIndex)) {
                        if (lastVoiceHeardMillis == Long.MAX_VALUE) {
                            voiceStartedMillis = now;
                            if (!Thread.currentThread().isInterrupted()) {
                                callback.onVoiceStart();
                            }
                            if (getMBufferSize() > prevVoiceLength) {
                                if (tailIndex - prevVoiceLength >= 0) {
                                    startVoiceIndex = tailIndex - prevVoiceLength;
                                } else {
                                    startVoiceIndex = buffer.length + (tailIndex - prevVoiceLength);
                                }
                            } else {
                                startVoiceIndex = headIndex;
                            }
                        }
                        lastVoiceHeardMillis = now;
                        if (now - (voiceStartedMillis - config.getPrevVoiceDurationMs()) > MAX_SPEECH_LENGTH_MILLIS) {
                            if (!Thread.currentThread().isInterrupted()) {
                                end();
                            }
                        }
                    } else if (lastVoiceHeardMillis != Long.MAX_VALUE) {
                        if (now - lastVoiceHeardMillis > config.getSpeechTimeoutMs()) {
                            if (!Thread.currentThread().isInterrupted()) {
                                end();
                            }
                        }
                    }
                }
            }
            dismiss();
            headIndex = 0;
            tailIndex = 0;
        }
    }

    /**
     * Callback interface for voice recording events.
     */
    public static abstract class Callback {
        private Recorder recorder;

        void setRecorder(Recorder recorder) {
            this.recorder = recorder;
        }

        /**
         * Called when voice starts being detected.
         */
        public void onVoiceStart() {
            if (recorder != null) {
                recorder.isRecording = true;
            }
            Log.d(TAG, "onVoiceStart");
        }

        /**
         * Called when voice data is available.
         *
         * @param data The audio data
         * @param size The size of actual data
         */
        public void onVoice(@NonNull float[] data, int size) {
            Log.d(TAG, "onVoice");
        }

        /**
         * Called when voice ends.
         */
        public void onVoiceEnd() {
            if (recorder != null) {
                recorder.isRecording = false;
            }
            Log.d(TAG, "onVoiceEnd");
        }

        /**
         * Called continuously when recording with the current volume level.
         *
         * @param volumeLevel A value between 0 and 1 representing volume
         */
        public void onVolumeLevel(float volumeLevel) {
        }
    }
}
