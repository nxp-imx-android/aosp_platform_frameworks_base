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

package com.android.server.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.app.KeyguardManager;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.wm.WindowManagerInternal;

/**
 * Handles creation and lifecycle of {@link ComputerControlSession}s.
 *
 * <p>This class enforces session creation policies, such as limiting the number of concurrent
 * sessions and preventing creation when the device is locked.
 */
public class ComputerControlSessionProcessor {

    private static final String TAG = ComputerControlSessionProcessor.class.getSimpleName();

    // TODO(b/419548594): Make this configurable.
    @VisibleForTesting
    static final int MAXIMUM_CONCURRENT_SESSIONS = 5;

    private final PackageManager mPackageManager;
    private final KeyguardManager mKeyguardManager;
    private final VirtualDeviceFactory mVirtualDeviceFactory;
    private final WindowManagerInternal mWindowManagerInternal;

    /** The binders of all currently active sessions. */
    private final ArraySet<IBinder> mSessions = new ArraySet<>();

    public ComputerControlSessionProcessor(
            Context context, VirtualDeviceFactory virtualDeviceFactory) {
        mVirtualDeviceFactory = virtualDeviceFactory;
        mPackageManager = context.getPackageManager();
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
    }

    /**
     * Process a new session creation request.
     *
     * <p>A new session will be created. In case of failure, the
     * {@link IComputerControlSessionCallback#onSessionCreationFailed} method on the provided
     * {@code callback} will be invoked.
     */
    public void processNewSessionRequest(
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        if (mKeyguardManager.isKeyguardLocked()) {
            dispatchSessionCreationFailed(
                    callback, params, ComputerControlSession.ERROR_KEYGUARD_LOCKED);
            return;
        }
        synchronized (mSessions) {
            if (mSessions.size() >= MAXIMUM_CONCURRENT_SESSIONS) {
                dispatchSessionCreationFailed(
                        callback, params, ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);
                return;
            }
            IComputerControlSession session = new ComputerControlSessionImpl(
                    callback.asBinder(), params, attributionSource, mPackageManager,
                    mVirtualDeviceFactory, mWindowManagerInternal,
                    new OnSessionClosedListener(params.getName(), callback));
            mSessions.add(session.asBinder());
            try {
                callback.onSessionCreated(session);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                        + " about session creation success");
            }
        }
    }

    /** Notifies the client that session creation failed. */
    private void dispatchSessionCreationFailed(IComputerControlSessionCallback callback,
            ComputerControlSessionParams params, int reason) {
        try {
            callback.onSessionCreationFailed(reason);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                    + " about session creation failure");
        }
    }

    /**
     * Listener for when a {@link ComputerControlSessionImpl} is closed.
     *
     * <p>Removes the session from the set of active sessions and notifies the client.
     */
    private class OnSessionClosedListener implements ComputerControlSessionImpl.OnClosedListener {
        private final String mSessionName;
        private final IComputerControlSessionCallback mAppCallback;

        OnSessionClosedListener(@NonNull String sessionName,
                @NonNull IComputerControlSessionCallback appCallback) {
            mSessionName = sessionName;
            mAppCallback = appCallback;
        }

        @Override
        public void onClosed(IBinder token) {
            synchronized (mSessions) {
                if (!mSessions.remove(token)) {
                    // The session was already removed, which can happen if close() is called
                    // multiple times.
                    return;
                }
            }
            try {
                mAppCallback.onSessionClosed();
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify ComputerControlSession " + mSessionName
                        + " about session closure");
            }
        }
    }

    /**
     * Interface for creating a virtual device for a computer control session.
     */
    public interface VirtualDeviceFactory {
        /**
         * Creates a new virtual device.
         */
        IVirtualDevice createVirtualDevice(
                IBinder token,
                AttributionSource attributionSource,
                VirtualDeviceParams params,
                IVirtualDeviceActivityListener activityListener);
    }
}
