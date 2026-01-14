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

import static android.Manifest.permission.REMOTE_MULTISENSORY_PLAYBACK;

import android.annotation.EnforcePermission;
import android.annotation.FlaggedApi;
import android.annotation.RequiresNoPermission;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.RemoteException;
import android.os.VibratorManager;
import android.os.multisensory.Flags;
import android.os.multisensory.IMultisensoryPlayer;
import android.os.multisensory.IMultisensoryService;
import android.os.multisensory.MultisensoryContinuousEffectModifier;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.List;

/**
 * The Multisensory system service in charge of playing audio-haptic feedback in the Multisensory
 * Design System
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_MULTISENSORY_FEEDBACK)
public class MultisensoryService extends IMultisensoryService.Stub {

    public static final String TAG = "MultisensoryService";

    public static class Lifecycle extends SystemService {
        private MultisensoryService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mService = new MultisensoryService(getContext());
        }

        @VisibleForTesting
        MultisensoryService getService() {
            return mService;
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == SystemService.PHASE_DEVICE_SPECIFIC_SERVICES_READY) {
                mService.initialize();
            }
        }
    }

    private final Context mContext;
    private MultisensoryServiceScope mServiceScope;

    private MultisensoryService(Context context) {
        mContext = context;
    }

    private void initialize() {
        VibratorManager vibratorManager = mContext.getSystemService(VibratorManager.class);
        mServiceScope = new MultisensoryServiceScope();
        mServiceScope.initializeLocked(
                vibratorManager.getDefaultVibrator(), mContext.getContentResolver());
    }

    /** Return whether the service has initialized */
    public boolean isInitialized() {
        if (mServiceScope != null) {
            return mServiceScope.isInitialized();
        }
        return false;
    }

    @Override
    @RequiresNoPermission
    public void playToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599246): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void openContinuousFeedbackForToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void startContinuousFeedbackForToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void modifyContinuousFeedbackForToken(
            int tokenConstant, List<MultisensoryContinuousEffectModifier> modifiers)
            throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @RequiresNoPermission
    public void stopContinuousFeedbackForToken(int tokenConstant) throws RemoteException {
        // TODO(b/475599755): Implement this API
    }

    @Override
    @EnforcePermission(REMOTE_MULTISENSORY_PLAYBACK)
    public void setPlayer(IMultisensoryPlayer player) throws RemoteException {
        setPlayer_enforcePermission();
        // TODO(b/475599246): Implement this API
    }
}
