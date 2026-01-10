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

package com.android.server.multisensory.repository.sound;

import android.os.multisensory.MultisensoryToken;
import android.provider.Settings;
import android.util.SparseArray;

/**
 * A static container of sound data in the {@link
 * com.android.server.multisensory.repository.MultisensoryRepository}
 *
 * @hide
 */
public class MultisensorySoundData {

    public static final SparseArray<String> TOKEN_SOUND_NAMES = new SparseArray<>();

    // TODO (b/462748962): We only have LOCK and UNLOCK MSDS sounds in the system right now.
    static {
        TOKEN_SOUND_NAMES.put(MultisensoryToken.FAILURE_HIGH_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.FAILURE, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.SUCCESS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.START, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.PAUSE, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.STOP, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.CANCEL, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.SWITCH_ON, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.SWITCH_OFF, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.UNLOCK, Settings.Global.UNLOCK_SOUND);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.LOCK, Settings.Global.LOCK_SOUND);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.LONG_PRESS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.SWIPE_INDICATOR_THRESHOLD_LIMIT, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.TAP_HIGH_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.TAP_MEDIUM_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.DRAG_INDICATOR_THRESHOLD_LIMIT, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.DRAG_INDICATOR_CONTINUOUS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.DRAG_INDICATOR_DISCRETE, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.TAP_LOW_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.KEYPRESS_STANDARD, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.KEYPRESS_SPACEBAR, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.KEYPRESS_RETURN, null);
        TOKEN_SOUND_NAMES.put(MultisensoryToken.KEYPRESS_DELETE, null);
    }
}
