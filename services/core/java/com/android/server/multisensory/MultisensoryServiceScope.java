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

package com.android.server.multisensory;

import android.annotation.NonNull;
import android.content.ContentResolver;
import android.os.Vibrator;
import android.os.multisensory.IMultisensoryPlayer;

import com.android.internal.annotations.GuardedBy;
import com.android.server.multisensory.repository.MultisensoryRepository;

/**
 * The scope under which the {@link MultisensoryService} operates. It serves as an environment that
 * contains registered {@link IMultisensoryPlayer}s and any shared environment data for the
 * operation of the service.
 *
 * @hide
 */
public class MultisensoryServiceScope {

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private MultisensoryRepository mRepository;

    /**
     * Initialize the scope of the {@link MultisensoryService}
     *
     * @param deviceVibrator The device vibrator.
     * @param contentResolver The content resolver used to initialize the internal repository.
     */
    @GuardedBy("mLock")
    public void initializeLocked(
            @NonNull Vibrator deviceVibrator, @NonNull ContentResolver contentResolver) {
        synchronized (mLock) {
            mRepository = new MultisensoryRepository();
            mRepository.initialize(deviceVibrator, contentResolver);
        }
    }

    /** Returns true if the scope has been initialized. */
    public boolean isInitialized() {
        return mRepository != null;
    }
}
