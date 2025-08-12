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

public class ComputerControlSessionProcessor {

    private static final String TAG = ComputerControlSessionProcessor.class.getSimpleName();

    // TODO(b/419548594): Make this configurable.
    @VisibleForTesting
    static final int MAXIMUM_CONCURRENT_SESSIONS = 5;

    private final PackageManager mPackageManager;
    private final VirtualDeviceFactory mVirtualDeviceFactory;
    private final WindowManagerInternal mWindowManagerInternal;
    private final ArraySet<IBinder> mSessions = new ArraySet<>();

    public ComputerControlSessionProcessor(
            Context context, VirtualDeviceFactory virtualDeviceFactory) {
        mVirtualDeviceFactory = virtualDeviceFactory;
        mPackageManager = context.getPackageManager();
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
    }

    /**
     * Process a new session creation request.
     */
    public void processNewSessionRequest(
            @NonNull AttributionSource attributionSource,
            @NonNull ComputerControlSessionParams params,
            @NonNull IComputerControlSessionCallback callback) {
        synchronized (mSessions) {
            if (mSessions.size() >= MAXIMUM_CONCURRENT_SESSIONS) {
                try {
                    callback.onSessionCreationFailed(
                            ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to notify ComputerControlSession " + params.getName()
                            + " about session creation failure");
                }
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
