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

package android.service.messaging;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.MessageUpgradeController.MessageUpgradeCallback;

import com.android.internal.util.Preconditions;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Provides basic structure for platform to connect to the
 * {@link AlternativeMessageTransportService}.
 *
 * <p>
 * Example:
 * <code>
 * AlternativeMessageTransportServiceWrapper serviceWrapper =
 *     new AlternativeMessageTransportServiceWrapper();
 * if (serviceWrapper.bindToService(context, smsAppPackageName)) {
 *   // wait for onServiceReady callback
 * } else {
 *   // Unable to bind: handle error.
 * }
 * </code>
 * <p> Upon completion {@link #close} should be called to unbind the AMTS service.
 * @hide
 */
// TODO(b/474304887): Make this class thread-safe
public final class AlternativeMessageTransportServiceWrapper implements AutoCloseable {
    // Populated by bindToService. bindToService must complete
    // prior to calling close so that mServiceConnection is initialized.
    private volatile MessageUpgradeServiceConnection
            mServiceConnection;

    private volatile IAlternativeMessageTransportService mAlternativeMessageTransportService;
    private Runnable mOnServiceReadyCallback;
    private Executor mOnServiceReadyCallbackExecutor;
    private Context mContext;

    /**
     * Binds to the {@link AlternativeMessageTransportService} under package
     * {@code smsAppPackageName}. This method should be called exactly once.
     *
     * @param context the context
     * @param smsAppPackageName the default SMS app's package name
     * @param executor the executor to run the callback.
     * @param onServiceReadyCallback the callback when service becomes ready.
     * @return true upon successfully binding to a message upgrade service, false otherwise
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            android.Manifest.permission.INTERACT_ACROSS_USERS,
            android.Manifest.permission.INTERACT_ACROSS_PROFILES})
    public boolean bindToService(Context context,
            String smsAppPackageName,
            @CallbackExecutor Executor executor,
            Runnable onServiceReadyCallback) {
        Preconditions.checkState(mServiceConnection == null);
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(smsAppPackageName, "smsAppPackageName cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(onServiceReadyCallback, "onServiceReadyCallback cannot be null");

        Intent intent = new Intent(AlternativeMessageTransportService.SERVICE_INTERFACE);
        intent.setPackage(smsAppPackageName);
        mServiceConnection = new MessageUpgradeServiceConnection();
        mOnServiceReadyCallback = onServiceReadyCallback;
        mOnServiceReadyCallbackExecutor = executor;
        mContext = context;
        return context.bindServiceAsUser(intent, mServiceConnection, Context.BIND_AUTO_CREATE,
                UserHandle.CURRENT);
    }

    /**
     * Unbinds the {@link AlternativeMessageTransportService}. This method should be called exactly
     * once.
     *
     * @hide
     */
    public void disconnect() {
        if (mServiceConnection != null) {
            mContext.unbindService(mServiceConnection);
            mServiceConnection = null;
        }
        mAlternativeMessageTransportService = null;
        mOnServiceReadyCallback = null;
        mOnServiceReadyCallbackExecutor = null;
    }

    /**
     * Upgrades SMS/MMS message to the default SMS app supported transport.
     *
     * @param contentUri the content URI of the SMS/MMS message.
     * @param mClientCallbackExecutor the executor to run the callback on.
     * @param clientCallback the callback to notify about the upgrade status.
     *
     * @hide
     */
    public void upgradeMessage(
            @NonNull Uri contentUri,
            @NonNull @CallbackExecutor Executor mClientCallbackExecutor,
            @NonNull MessageUpgradeCallback clientCallback) {
        Objects.requireNonNull(mAlternativeMessageTransportService);
        try {
            mAlternativeMessageTransportService.upgradeMessage(
                    contentUri,
                    new MessageUpgradeCallbackInternal(mClientCallbackExecutor, clientCallback));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** @hide */
    @Override
    public void close() {
        disconnect();
    }

    private void onServiceReady(
            IAlternativeMessageTransportService alternativeMessageTransportService) {
        mAlternativeMessageTransportService = alternativeMessageTransportService;
        if (mOnServiceReadyCallback != null && mOnServiceReadyCallbackExecutor != null) {
            final long identity = Binder.clearCallingIdentity();
            try {
                mOnServiceReadyCallbackExecutor.execute(mOnServiceReadyCallback);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    /**
     * A basic {@link ServiceConnection}.
     */
    private final class MessageUpgradeServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            onServiceReady(IAlternativeMessageTransportService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mAlternativeMessageTransportService = null;
        }
    }

    private static final class MessageUpgradeCallbackInternal
            extends IMessageUpgradeCallback.Stub {
        private final Executor mClientCallbackExecutor;
        private final MessageUpgradeCallback mClientCallback;

        private MessageUpgradeCallbackInternal(
                Executor executor, MessageUpgradeCallback callback) {
            mClientCallbackExecutor = executor;
            mClientCallback = callback;
        }

        @Override
        public void onUpgradeStatusAvailable(int status) throws RemoteException {
            mClientCallbackExecutor.execute(() ->
                    mClientCallback.onUpgradeStatusAvailable(status));
        }
    }
}
