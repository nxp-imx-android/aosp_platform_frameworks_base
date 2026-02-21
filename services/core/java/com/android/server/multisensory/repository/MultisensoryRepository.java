/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.server.multisensory.repository;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.os.Vibrator;
import android.os.multisensory.MultisensoryToken;
import android.provider.Settings;
import android.util.SparseArray;

import com.android.server.multisensory.repository.haptics.MultisensoryComposedEffect;
import com.android.server.multisensory.repository.haptics.MultisensoryHapticEffect;
import com.android.server.multisensory.repository.sound.MultisensorySoundData;

/**
 * The repository that contains and manages multisensory data
 *
 * @hide
 */
public class MultisensoryRepository {

    public static final @MultisensoryToken.Token int[] MULTISENSORY_TOKENS = {
        MultisensoryToken.FAILURE_HIGH_EMPHASIS,
        MultisensoryToken.FAILURE,
        MultisensoryToken.SUCCESS,
        MultisensoryToken.START,
        MultisensoryToken.PAUSE,
        MultisensoryToken.STOP,
        MultisensoryToken.CANCEL,
        MultisensoryToken.SWITCH_ON,
        MultisensoryToken.SWITCH_OFF,
        MultisensoryToken.UNLOCK,
        MultisensoryToken.LOCK,
        MultisensoryToken.LONG_PRESS,
        MultisensoryToken.SWIPE_INDICATOR_THRESHOLD_LIMIT,
        MultisensoryToken.TAP_HIGH_EMPHASIS,
        MultisensoryToken.TAP_MEDIUM_EMPHASIS,
        MultisensoryToken.DRAG_INDICATOR_THRESHOLD_LIMIT,
        MultisensoryToken.DRAG_INDICATOR_CONTINUOUS,
        MultisensoryToken.DRAG_INDICATOR_DISCRETE,
        MultisensoryToken.TAP_LOW_EMPHASIS,
        MultisensoryToken.KEYPRESS_STANDARD,
        MultisensoryToken.KEYPRESS_SPACEBAR,
        MultisensoryToken.KEYPRESS_RETURN,
        MultisensoryToken.KEYPRESS_DELETE
    };

    private final SparseArray<MultisensoryHapticEffect> mHapticEffects = new SparseArray<>();
    private final SparseArray<String> mSoundEffects = new SparseArray<>();

    /**
     * Initialize the repository by loading all audio and haptic data.
     *
     * @param deviceVibrator The device vibrator for haptic information.
     * @param contentResolver A content resolver to resolve audio URIs.
     */
    public void initialize(
            @NonNull Vibrator deviceVibrator, @NonNull ContentResolver contentResolver) {

        // Generate all data and store in memory for fast retrieval using token constants as ids.
        for (int token : MULTISENSORY_TOKENS) {
            mHapticEffects.put(
                    token,
                    MultisensoryComposedEffect.createHapticEffect(
                            token,
                            deviceVibrator.getInfo(),
                            deviceVibrator.getEnvelopeEffectInfo()));
            String soundResource = MultisensorySoundData.TOKEN_SOUND_NAMES.get(token);
            String soundUri;
            if (soundResource != null) {
                soundUri = Settings.Global.getString(contentResolver, soundResource);
            } else {
                soundUri = null;
            }
            mSoundEffects.put(token, soundUri);
        }
    }

    /** Get the haptic effect for a given token */
    public @NonNull MultisensoryHapticEffect getHapticEffect(@MultisensoryToken.Token int token) {
        return mHapticEffects.get(token);
    }

    /** Get the audio resource for a given token */
    public @Nullable String getSoundEffect(@MultisensoryToken.Token int token) {
        return mSoundEffects.get(token);
    }
}
