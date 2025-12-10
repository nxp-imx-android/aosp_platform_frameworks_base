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

package com.android.server.serial;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.serial.SerialManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/**
 * This class manages app accesses to serial devices.
 */
class SerialUserAccessManager implements SerialUserAccessManagerInterface {
    private static final String TAG = "SerialUserAccessManager";

    private final Context mContext;

    /**
     * A list of serial port names configured in the internal configuration. Apps with
     * {@link Manifest.permission#SERIAL_PORT} are granted accesses to them automatically.
     */
    private final String[] mPortsInConfig;

    /**
     * Component name of the activity that shows the request for access to a serial port.
     */
    private final String mDialogComponent;

    /**
     * Mapping of serial port names to list of UIDs with accesses for the device Each entry last
     * until device is disconnected. There are accesses granted via access request dialog.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, SparseBooleanArray> mPortAccessMap = new ArrayMap<>();

    @GuardedBy("mLock")
    private final List<AccessToPortRequest> mPendingAccessRequests = new ArrayList<>();

    private final Object mLock = new Object();

    SerialUserAccessManager(Context context, String[] portsInConfig, String dialogComponent) {
        mContext = context;
        mPortsInConfig = portsInConfig;
        mDialogComponent = dialogComponent;
    }

    @Override
    public void requestAccess(String requestedPortName, int pid, int uid,
            String requestingPackageName, AccessToPortDecidedCallback callback) {
        if (hasAccessToPort(requestedPortName, pid, uid)) {
            callback.onAccessToPortDecided(requestedPortName, pid, uid, /* granted */ true);
            return;
        }

        final AccessToPortRequest request = new AccessToPortRequest(requestedPortName, pid, uid,
                requestingPackageName, callback);
        synchronized (mLock) {
            mPendingAccessRequests.add(request);
        }

        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startActivityAsUser(
                    request.getAccessDialogIntent(), UserHandle.of(UserHandle.getUserId(uid)));
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failed to start access dialog", e);
            synchronized (mLock) {
                mPendingAccessRequests.remove(request);
            }
            request.notifyRequestResult(/* granted */ false);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean hasAccessToPort(String requestedPortName, int pid, int uid) {
        if (canGrantPortAccessAutomatically(requestedPortName, pid, uid)) {
            return true;
        }

        synchronized (mLock) {
            final SparseBooleanArray uidToGranted = mPortAccessMap.get(requestedPortName);
            if (uidToGranted == null) {
                return false;
            }
            return uidToGranted.get(uid);
        }
    }

    @SuppressLint("AndroidFrameworkRequiresPermission")
    private boolean canGrantPortAccessAutomatically(String requestedPortName, int pid, int uid) {
        if (mContext.checkPermission(Manifest.permission.SERIAL_PORT, pid, uid)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        for (int i = 0; i < mPortsInConfig.length; ++i) {
            if (requestedPortName.equals(mPortsInConfig[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void grantAccess(String portName, int uid, @Nullable IBinder token) {
        updatePortAccess(portName, uid, token, /* granted */ true);
    }

    @Override
    public void revokeAccess(String portName, int uid, @Nullable IBinder token) {
        updatePortAccess(portName, uid, token, /* granted */ false);
    }

    /**
     * Updates the access to a serial port for a specific UID.
     * <p>
     * If token == null, the access lasts until the device is disconnected,
     * otherwise the access is used only once, for the caller with the given token.
     *
     * @param portName The name of the serial port.
     * @param uid The user ID to revoke access from.
     * @param token An optional token associated with the revocation.
     */
    private void updatePortAccess(
            String portName, int uid, @Nullable IBinder token, boolean granted) {
        Slog.d(TAG, "updatePortAccess(" + portName + ", " + uid + ", " + granted + ")");
        synchronized (mLock) {
            SparseBooleanArray uidToGranted = mPortAccessMap.get(portName);
            if (uidToGranted == null) {
                uidToGranted = new SparseBooleanArray();
                mPortAccessMap.put(portName, uidToGranted);
            }
            uidToGranted.put(uid, granted);

            AccessToPortRequest pendingRequest = findAndRemovePendingRequest(portName, uid, token);
            if (pendingRequest != null) {
                pendingRequest.notifyRequestResult(granted);
            }
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private AccessToPortRequest findAndRemovePendingRequest(
            String portName, int uid, @Nullable IBinder token) {
        if (token == null) {
            return null;
        }
        for (int i = 0; i < mPendingAccessRequests.size(); ++i) {
            final AccessToPortRequest request = mPendingAccessRequests.get(i);
            if (request.mToken == token) {
                if (request.mRequestingUid != uid || !portName.equals(request.mRequestedPort)) {
                    throw new IllegalStateException("Found wrong pending request");
                }
                mPendingAccessRequests.remove(i);
                return request;
            }
        }
        return null;
    }

    @Override
    public void onPortRemoved(String name) {
        synchronized (mLock) {
            mPortAccessMap.remove(name);
        }
    }

    private class AccessToPortRequest {
        /**
         * The unique token of this request
         */
        private final Binder mToken = new Binder();

        private final String mRequestedPort;

        private final int mRequestingPid;

        private final int mRequestingUid;

        private final String mRequestingPackageName;

        private final AccessToPortDecidedCallback mCallback;

        private AccessToPortRequest(String requestedPort, int requestingPid, int requestingUid,
                String requestingPackageName, AccessToPortDecidedCallback callback) {
            mRequestedPort = requestedPort;
            mRequestingPid = requestingPid;
            mRequestingUid = requestingUid;
            mRequestingPackageName = requestingPackageName;
            mCallback = callback;
        }

        private Intent getAccessDialogIntent() {
            Intent intent = new Intent();
            intent.setComponent(ComponentName.unflattenFromString(mDialogComponent));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle binderExtras = new Bundle();
            binderExtras.putBinder(SerialManager.EXTRA_REQUEST_TOKEN, mToken);
            intent.putExtras(binderExtras);
            intent.putExtra(SerialManager.EXTRA_PORT, mRequestedPort);
            intent.putExtra(SerialManager.EXTRA_PACKAGE_NAME, mRequestingPackageName);
            intent.putExtra(SerialManager.EXTRA_UID, mRequestingUid);
            return intent;
        }

        private void notifyRequestResult(boolean granted) {
            mCallback.onAccessToPortDecided(
                    mRequestedPort, mRequestingPid, mRequestingUid, granted);
        }
    }
}
