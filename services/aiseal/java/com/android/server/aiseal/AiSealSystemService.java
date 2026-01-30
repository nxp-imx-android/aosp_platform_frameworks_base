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

package com.android.server.aiseal;

import android.content.Context;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.format.DateUtils;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.aiseal.IAiSealInternalService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;
import com.android.server.SystemService.TargetUser;

import java.util.Set;

/** AiSeal system service. */
public class AiSealSystemService extends SystemService {

    private static final String TAG = "AiSealSystemService";

    // Synchronizes user state changes with the AiSeal internal service connection.
    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private IAiSealInternalService mAiSealInternalService;

    @GuardedBy("sLock")
    private final Set<Integer> mUnlockedUsers = new ArraySet<>();

    public AiSealSystemService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        connectAiSealInternalService();
    }

    @Override
    public void onUserUnlocking(TargetUser user) {
        synchronized (sLock) {
            onUserUnlockingLocked(user.getUserIdentifier());
        }
    }

    @Override
    public void onUserStopped(TargetUser user) {
        synchronized (sLock) {
            onUserStoppedLocked(user.getUserIdentifier());
        }
    }

    private void connectAiSealInternalService() {
        synchronized (sLock) {
            connectAiSealInternalServiceLocked();
        }
    }

    @GuardedBy("sLock")
    private void connectAiSealInternalServiceLocked() {
        // Reset the service to null if we are currently connected to it.
        mAiSealInternalService = null;

        Slog.i(TAG, "Connecting to AiSeal internal service");
        IBinder binder = ServiceManager.getService("aiseal_internal");
        if (binder != null) {
            try {
                binder.linkToDeath(
                        new DeathRecipient() {
                            @Override
                            public void binderDied() {
                                Slog.w(TAG, "AiSeal died; reconnecting");
                                connectAiSealInternalService();
                            }
                        },
                        0);
            } catch (RemoteException e) {
                binder = null;
            }
        }

        if (binder != null) {
            mAiSealInternalService = IAiSealInternalService.Stub.asInterface(binder);
        } else {
            Slog.w(TAG, "AiSeal internal service not yet available; trying again");
        }

        if (mAiSealInternalService == null) {
            BackgroundThread.getHandler()
                    .postDelayed(
                            () -> {
                                connectAiSealInternalService();
                            },
                            DateUtils.SECOND_IN_MILLIS);
        } else {
            onAiSealInternalServiceConnectedLocked();
        }
    }

    @GuardedBy("sLock")
    private void onAiSealInternalServiceConnectedLocked() {
        for (int userId : mUnlockedUsers) {
            try {
                mAiSealInternalService.onUserUnlocking(userId);
            } catch (Exception e) {
                Slog.wtf(TAG, "Unable to unlock user " + userId, e);
            }
        }
    }

    @GuardedBy("sLock")
    private void onUserUnlockingLocked(int userId) {
        mUnlockedUsers.add(userId);
        if (mAiSealInternalService != null) {
            notifyUserUnlockingLocked(userId);
        } else {
            // The user will be unlocked in onAiSealInternalServiceConnected().
            Slog.i(TAG, "Not yet connected to AiSeal to unlock user " + userId);
        }
    }

    @GuardedBy("sLock")
    private void onUserStoppedLocked(int userId) {
        mUnlockedUsers.remove(userId);
        if (mAiSealInternalService != null) {
            notifyUserStoppedLocked(userId);
        } else {
            Slog.i(TAG, "Not yet connected to AiSeal to stop user " + userId);
        }
    }

    /** AiSeal notifyUserUnlockingLocked */
    @GuardedBy("sLock")
    public void notifyUserUnlockingLocked(int userId) {
        try {
            mAiSealInternalService.onUserUnlocking(userId);
        } catch (Exception e) {
            Slog.wtf(TAG, "Unable to unlock user " + userId, e);
        }
    }

    /** AiSeal notifyUserStoppedLocked */
    @GuardedBy("sLock")
    public void notifyUserStoppedLocked(int userId) {
        try {
            mAiSealInternalService.onUserStopped(userId);
        } catch (Exception e) {
            Slog.wtf(TAG, "Unable to stop user " + userId, e);
        }
    }
}
