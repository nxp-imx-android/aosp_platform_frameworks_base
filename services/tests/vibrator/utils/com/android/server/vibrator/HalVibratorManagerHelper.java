/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.vibrator;

import android.hardware.vibrator.IVibratorManager;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;
import java.util.List;

/**
 * Provides real {@link HalVibratorManager} implementations for testing, backed with fake and
 * configurable hardware capabilities.
 */
public class HalVibratorManagerHelper {
    private final Handler mHandler;
    private final FakeNativeWrapper mNativeWrapper;

    private int mConnectCount;
    private int mPrepareSyncedCount;
    private int mTriggerSyncedCount;
    private int mCancelSyncedCount;
    private int mStartSessionCount;
    private int mEndSessionCount;
    private int mAbortSessionCount;
    private int mClearSessionsCount;

    private long mCapabilities;
    private int[] mVibratorIds;
    private long mSessionEndDelayMs = Long.MAX_VALUE;
    private boolean mPrepareSyncedShouldFail = false;
    private boolean mTriggerSyncedShouldFail = false;
    private boolean mStartSessionShouldFail = false;

    public HalVibratorManagerHelper(Looper looper) {
        mHandler = new Handler(looper);
        mNativeWrapper = new FakeNativeWrapper();
    }

    /** Create new {@link VibratorManagerService.NativeHalVibratorManager} for testing. */
    public VibratorManagerService.NativeHalVibratorManager newNativeHalVibratorManager() {
        return new VibratorManagerService.NativeHalVibratorManager(mNativeWrapper);
    }

    public void setCapabilities(long capabilities) {
        mCapabilities = capabilities;
    }

    public void setVibratorIds(int[] vibratorIds) {
        mVibratorIds = vibratorIds;
    }

    public void setSessionEndDelayMs(long sessionEndDelayMs) {
        mSessionEndDelayMs = sessionEndDelayMs;
    }

    /** Make all prepare synced calls fail. */
    public void setPrepareSyncedToFail() {
        mPrepareSyncedShouldFail = true;
    }

    /** Make all trigger synced calls fail. */
    public void setTriggerSyncedToFail() {
        mTriggerSyncedShouldFail = true;
    }

    /** Make all start session calls fail. */
    public void setStartSessionToFail() {
        mStartSessionShouldFail = true;
    }

    /** Trigger session callback for given session id. */
    public void endSessionAbruptly(long sessionId) {
        mHandler.post(() -> mNativeWrapper.mCallbacks.onVibrationSessionComplete(sessionId));
    }

    public int getConnectCount() {
        return mConnectCount;
    }

    public int getPrepareSyncedCount() {
        return mPrepareSyncedCount;
    }

    public int getTriggerSyncedCount() {
        return mTriggerSyncedCount;
    }

    public int getCancelSyncedCount() {
        return mCancelSyncedCount;
    }

    public int getStartSessionCount() {
        return mStartSessionCount;
    }

    public int getEndSessionCount() {
        return mEndSessionCount;
    }

    public int getAbortSessionCount() {
        return mAbortSessionCount;
    }

    public int getClearSessionsCount() {
        return mClearSessionsCount;
    }

    private boolean hasCapability(long capability) {
        return (mCapabilities & capability) == capability;
    }

    private boolean areVibratorIdsValid(int[] ids) {
        if (mVibratorIds == null) {
            return false;
        }
        List<Integer> vibratorIds = Arrays.stream(mVibratorIds).boxed().toList();
        long validIdCount = Arrays.stream(ids).filter(vibratorIds::contains).count();
        return validIdCount > 0 && validIdCount == ids.length;
    }

    /** Provides fake implementation of {@link VibratorManagerService.NativeWrapper} for testing. */
    public final class FakeNativeWrapper extends VibratorManagerService.NativeWrapper {
        private HalVibratorManager.Callbacks mCallbacks;

        @Override
        public void init(HalVibratorManager.Callbacks callback) {
            mConnectCount++;
            mCallbacks = callback;
        }

        @Override
        public long getCapabilities() {
            return mCapabilities;
        }

        @Override
        public int[] getVibratorIds() {
            return mVibratorIds;
        }

        @Override
        public boolean prepareSynced(int[] vibratorIds) {
            if (mPrepareSyncedShouldFail) {
                return false;
            }
            mPrepareSyncedCount++;
            return hasCapability(IVibratorManager.CAP_SYNC) && areVibratorIdsValid(vibratorIds);
        }

        @Override
        public boolean triggerSynced(long vibrationId) {
            if (mTriggerSyncedShouldFail) {
                return false;
            }
            mTriggerSyncedCount++;
            if (hasCapability(IVibratorManager.CAP_SYNC)) {
                mHandler.post(() -> mCallbacks.onSyncedVibrationComplete(vibrationId));
                return true;
            }
            return false;
        }

        @Override
        public void cancelSynced() {
            mCancelSyncedCount++;
        }

        @Override
        public boolean startSession(long sessionId, int[] vibratorIds) {
            if (mStartSessionShouldFail) {
                return false;
            }
            mStartSessionCount++;
            return hasCapability(IVibratorManager.CAP_START_SESSIONS)
                    && areVibratorIdsValid(vibratorIds);
        }

        @Override
        public void endSession(long sessionId, boolean shouldAbort) {
            if (shouldAbort) {
                mAbortSessionCount++;
            } else {
                mEndSessionCount++;
            }
            mHandler.postDelayed(() -> mCallbacks.onVibrationSessionComplete(sessionId),
                    shouldAbort ? 0 : mSessionEndDelayMs);
        }

        @Override
        public void clearSessions() {
            mClearSessionsCount++;
        }
    }
}
