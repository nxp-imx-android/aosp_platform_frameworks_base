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

package com.android.server.privatecompute;

import static android.os.Process.SYSTEM_UID;

import android.annotation.RequiresNoPermission;
import android.app.privatecompute.IPccService;
import android.app.privatecompute.IResultCallback;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelableException;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local interface for the PccSandboxManager that is used by other system services to interact with
 * the PCC sandbox.
 *
 * @hide Only for use within system_server.
 */
public final class PccSandboxManagerInternal {
    private static final String TAG = PccSandboxManagerInternal.class.getSimpleName();

    private final PackageManagerInternal mPackageManagerInternal;
    private final Object mLock = new Object();
    @VisibleForTesting
    @GuardedBy("mLock")
    final Map<IBinder, PccServiceInfo> mPccServiceConnections = new ArrayMap<>();

    public PccSandboxManagerInternal() {
        this.mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
    }

    /**
     * Called when a new client binds to a PCC service.
     *
     * <p>This method tracks the new client connection. The client is untrusted and external to the
     * PCC sandbox, hence we return a wrapped {@link PccServiceProxy} binder that sanitizes the
     * input data.
     *
     * @param name      The ComponentName of the PCC service.
     * @param userId    The user ID of the client process.
     * @param intent    The Intent used to bind to the service.
     * @param binder    The raw IBinder of the PCC service.
     * @param clientUid The UID of the client process.
     * @return one of the following:
     * <ul>
     *     <li>The original binder if the client is trusted</li>
     *     <li>The wrapped {@link PccServiceProxy} binder if the client is untrusted and the
     *     binder extends {@link android.app.privatecompute.IPccService}</li>
     *     <li>null if the service doesn't extend {@link android.app.privatecompute.IPccService}
     *     </li>
     * </ul>
     */
    public IBinder createPccProxyIfNeeded(ComponentName name, int userId, Intent intent,
            IBinder binder, int clientUid) {
        if (isTrustedClient(clientUid)) {
            return binder;
        }

        binder = validatePccServiceBinder(binder);
        if (binder == null) {
            return null;
        }

        synchronized (mLock) {
            PccServiceConnectionInfo newConnectionInfo = new PccServiceConnectionInfo(name, userId,
                    intent);
            PccServiceInfo pccServiceInfo = mPccServiceConnections.get(binder);
            if (pccServiceInfo == null) {
                PccServiceProxy proxyBinder = new PccServiceProxy(binder);
                DeathRecipient deathRecipient = new DeathRecipient(binder);
                try {
                    binder.linkToDeath(deathRecipient, 0);
                    pccServiceInfo = new PccServiceInfo(proxyBinder, deathRecipient);
                    pccServiceInfo.mConnectionInfos.add(newConnectionInfo);
                    mPccServiceConnections.put(binder, pccServiceInfo);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to link to death recipient, service has died: " + binder,
                            e);
                    proxyBinder.destroy();
                    return null;
                }
            } else {
                pccServiceInfo.mConnectionInfos.add(newConnectionInfo);
            }

            return pccServiceInfo.getWrappedBinder();
        }
    }

    /**
     * Retrieves the existing PCC proxy binder associated with the given raw service binder.
     *
     * @param binder    The raw IBinder of the PCC service.
     * @param clientUid The UID of the client process.
     * @return one of the following:
     * <ul>
     *     <li>The original binder if the client is trusted</li>
     *     <li>The wrapped {@link PccServiceProxy} binder if the client is untrusted and proxy
     *     connection exists.</li>
     *     <li>null if the proxy connection doesn't exist.</li>
     * </ul>
     */
    public IBinder fetchPccProxyIfNeeded(IBinder binder, int clientUid) {
        if (isTrustedClient(clientUid)) {
            return binder;
        }

        synchronized (mLock) {
            PccServiceInfo pccServiceInfo = mPccServiceConnections.get(binder);
            if (pccServiceInfo != null) {
                return pccServiceInfo.getWrappedBinder();
            }
            return null;
        }
    }

    /**
     * Called when a client unbinds from a PCC service.
     *
     * <p>This method removes the client from the connection records. If it's the last client for a
     * given PCC service, it cleans up the associated resources, including destroying the proxy
     * binder to release binder references.
     *
     * @param name      The ComponentName of the PCC service.
     * @param userId    The user ID of the client process.
     * @param intent    The Intent used to bind to the service.
     * @param binder    The raw IBinder of the PCC service.
     * @param clientUid The UID of the client process.
     */
    public void removePccProxyIfNeeded(ComponentName name, int userId, Intent intent,
            IBinder binder, int clientUid) {
        if (isTrustedClient(clientUid)) {
            return;
        }

        synchronized (mLock) {
            if (!mPccServiceConnections.containsKey(binder)) {
                Slog.w(TAG, "Cannot find PCC connection for the binder: " + binder);
                return;
            }

            PccServiceInfo serviceInfo = mPccServiceConnections.get(binder);
            PccServiceConnectionInfo connectionInfo = new PccServiceConnectionInfo(name, userId,
                    intent);
            serviceInfo.mConnectionInfos.remove(connectionInfo);

            if (serviceInfo.mConnectionInfos.isEmpty()) {
                mPccServiceConnections.remove(binder);
                binder.unlinkToDeath(serviceInfo.mDeathRecipient, 0);
                serviceInfo.getWrappedBinder().destroy();
            }
        }
    }

    private IBinder validatePccServiceBinder(IBinder binder) {
        try {
            if (binder == null || !IPccService.DESCRIPTOR.equals(binder.getInterfaceDescriptor())) {
                return null;
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get interface descriptor for binder: " + binder, e);
            return null;
        }
        return binder;
    }

    private boolean isTrustedClient(int clientUid) {
        return Process.isPrivateComputeCoreUid(clientUid) || clientUid == SYSTEM_UID;
    }

    @VisibleForTesting
    final class PccServiceProxy extends IPccService.Stub {
        private volatile IBinder mRealBinder;

        PccServiceProxy(IBinder realBinder) {
            this.mRealBinder = realBinder;
        }

        @RequiresNoPermission
        @Override
        public void sendData(Bundle data, String packageName, IResultCallback callback) {
            try {
                if (mRealBinder == null) {
                    callback.onFailure(new ParcelableException(
                            new IllegalStateException("PCC service is already closed.")));
                    return;
                }

                IPccService realService = IPccService.Stub.asInterface(mRealBinder);

                final int callingUid = Binder.getCallingUid();

                if (mPackageManagerInternal.isSameApp(packageName, callingUid,
                        UserHandle.getUserId(callingUid))) {
                    try {
                        realService.sendData(data, packageName, null);
                    } catch (RemoteException e) {
                        callback.onFailure(new ParcelableException(e));
                        return;
                    }
                    callback.onSuccess();
                } else {
                    callback.onFailure(new ParcelableException(new SecurityException(
                            "Calling UID: " + callingUid + " is not associated with package: "
                                    + packageName)));
                }

            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to invoke " + IResultCallback.class.getSimpleName()
                        + " for client: " + packageName, e);
            }
        }

        /**
         * A cleanup method to clear the reference to the real binder once the service is brought
         * down. This is to reduce the memory footprint.
         */
        public void destroy() {
            mRealBinder = null;
        }

        @VisibleForTesting
        IBinder getRealBinder() {
            return mRealBinder;
        }
    }

    private record PccServiceConnectionInfo(ComponentName name, int userId,
                                            Intent.FilterComparison intentFilter) {
        PccServiceConnectionInfo(ComponentName name, int userId, Intent intent) {
            this(name, userId, new Intent.FilterComparison(intent));
        }
    }

    @VisibleForTesting
    static final class PccServiceInfo {
        private final List<PccServiceConnectionInfo> mConnectionInfos;
        private final PccServiceProxy mProxy;
        private final IBinder.DeathRecipient mDeathRecipient;

        PccServiceInfo(PccServiceProxy proxy, IBinder.DeathRecipient deathRecipient) {
            this.mProxy = proxy;
            this.mConnectionInfos = new ArrayList<>(1);
            this.mDeathRecipient = deathRecipient;
        }

        public PccServiceProxy getWrappedBinder() {
            return mProxy;
        }

        @VisibleForTesting
        int getConnectionCount() {
            return mConnectionInfos.size();
        }

        @VisibleForTesting
        IBinder.DeathRecipient getDeathRecipient() {
            return mDeathRecipient;
        }

    }

    private final class DeathRecipient implements IBinder.DeathRecipient {
        private final IBinder mRealBinder;

        DeathRecipient(IBinder realBinder) {
            this.mRealBinder = realBinder;
        }

        @Override
        public void binderDied() {
            synchronized (mLock) {
                PccServiceInfo serviceInfo = mPccServiceConnections.remove(mRealBinder);
                if (serviceInfo != null) {
                    serviceInfo.getWrappedBinder().destroy();
                }
            }
        }
    }

}
