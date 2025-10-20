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

package com.android.server.usb;

import android.annotation.UserIdInt;
import android.content.Context;
import android.hardware.usb.IUsbAuthManager;
import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationStatus;
import android.hardware.usb.UsbAuthorizationSystemState;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;
import android.content.pm.UserInfo;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.FgThread;
import com.android.server.SystemService;
import com.android.server.SystemServerInitThreadPool;
import android.util.SparseBooleanArray;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UsbAuthManager implements IBinder.DeathRecipient {
    private static final String TAG = "UsbAuthManager";
    private IUsbAuthManager mService;
    private final Context mContext;
    private final UserManager mUserManager;
    private static final String USB_AUTH_SERVICE_NAME = "usb_auth";

    @GuardedBy("mLock")
    private @UserIdInt int mCurrentUserId;

    @GuardedBy("mLock")
    private boolean mIsScreenLocked;

    @GuardedBy("mLock")
    private SparseBooleanArray mFullUsersLoggedIn = new SparseBooleanArray();

    @GuardedBy("mLock")
    private @UsbAuthorizationSystemState int mCurrentState = -1;

    private final Object mLock = new Object();

    public UsbAuthManager(Context context, UserManager userManager) {
        mUserManager = userManager;
        mContext = context;
        listenForService();
    }

    /** Constructor for testing. */
    @VisibleForTesting
    public UsbAuthManager(Context context, UserManager userManager, IUsbAuthManager service) {
        mUserManager = userManager;
        mContext = context;
        setAndLinkService(service);
    }

    private void listenForService() {
        SystemServerInitThreadPool.submit(
                () -> {
                    IBinder serviceBinder = ServiceManager.waitForService(USB_AUTH_SERVICE_NAME);
                    FgThread.getExecutor()
                            .execute(
                                    () -> {
                                        Slog.d(TAG, "UsbAuthService arrived");
                                        setAndLinkService(
                                                IUsbAuthManager.Stub.asInterface(serviceBinder));
                                    });
                },
                "UsbAuthManager#listenForService");
    }

    private void setAndLinkService(IUsbAuthManager service) {
        synchronized (mLock) {
            if (mService != null) {
                mService.asBinder().unlinkToDeath(this, 0);
            }
            mService = service;
            if (mService != null) {
                try {
                    mService.asBinder().linkToDeath(this, 0);
                    updateSystemStateInternal(); // Update system state after linking to service
                } catch (RemoteException e) {
                    Slog.e(TAG, "linkToDeath failed", e);
                    mService = null;
                }
            }
        }
    }

    @Override
    public void binderDied() {
        mService = null;
        listenForService();
    }

    private IUsbAuthManager getService() {
        synchronized (mLock) {
            if (mService == null) {
                IUsbAuthManager service =
                        IUsbAuthManager.Stub.asInterface(
                                ServiceManager.getService(USB_AUTH_SERVICE_NAME));
                setAndLinkService(service);
            }
            return mService;
        }
    }

    public void setSystemState(int state) {
        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                service.setSystemState(state);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set system state, binder died", e);
            binderDied(); // Explicitly call binderDied to clean up and re-listen for the service
        }
    }

    public void onUpdateScreenLockedState(boolean locked) {
        Slog.d(TAG, "updateLockState: " + locked);

        synchronized (mLock) {
            mIsScreenLocked = locked;
        }

        updateSystemStateInternal();
    }

    /**
     * Update the logged in state of a user. This method updates the internal state related to user
     * logins and logouts. It specifically tracks full or admin users: If a full or admin user logs
     * in, their ID is added to an internal map of logged-in full users. If a full or admin user
     * logs out, their ID is removed from this map.
     *
     * <p>When any user logs in, their ID is also set as the current user ID.
     *
     * @param loggedIn True when a user logs in, False when a user is stopped.
     * @param userId User that is the target of this state change.
     */
    public void onUpdateLoggedInState(boolean loggedIn, int userId) {
        Slog.d(TAG, "updateLoggedInState: " + loggedIn + " " + userId);

        synchronized (mLock) {
            // Update the current user id to the logged in user.
            if (loggedIn) {
                mCurrentUserId = userId;
            }

            UserInfo userInfo = mUserManager.getUserInfo(userId);
            if (userInfo == null) {
                Slog.e(TAG, "onUpdateLoggedInState: user not found");
                return;
            }
            // Update mFullUsersLoggedIn map
            if (userInfo.isFull() || userInfo.isAdmin()) {
                // If the user is a full user or an admin, add or remove them from the map based on
                // the loggedIn state.
                if (loggedIn) {
                    mFullUsersLoggedIn.put(userId, true);
                } else {
                    mFullUsersLoggedIn.delete(userId);
                }
            }
        }
        updateSystemStateInternal();
    }

    private void updateSystemStateInternal() {
        @UsbAuthorizationSystemState int state;
        synchronized (mLock) {
            if (mFullUsersLoggedIn.size() == 0) {
                // No full users logged in
                state = UsbAuthorizationSystemState.BOOTED;
            } else if (!mIsScreenLocked && mFullUsersLoggedIn.get(mCurrentUserId, false)) {
                // Active user is a full user, and screen is unlocked
                state = UsbAuthorizationSystemState.LOGGED_IN;
            } else {
                // There are full users logged in, but either screen is locked
                // or the active user is not a full user (e.g., guest) (I.e. guest user logs in)
                state = UsbAuthorizationSystemState.SCREEN_LOCKED;
            }
            if (mCurrentState == state) {
                return;
            }
            mCurrentState = state;
        }
        setSystemState(state);
    }
}
