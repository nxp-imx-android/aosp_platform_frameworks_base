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
import android.telephony.MessagePromotionController.MessagePromotionCallback;

import com.android.internal.util.Preconditions;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Provides basic structure for platform to connect to the message promotion service.
 * <p>
 * Example:
 * <code>
 * MessagePromotionServiceWrapper messagePromotionServiceWrapper =
 *     new MessagePromotionServiceWrapper();
 * if (messagePromotionServiceWrapper.bindToMessagePromotionService(context, smsAppPackageName)) {
 *   // wait for onServiceReady callback
 * } else {
 *   // Unable to bind: handle error.
 * }
 * </code>
 * <p> Upon completion {@link #close} should be called to unbind the message promotion service.
 * @hide
 */
// TODO(b/474304887): Make this class thread-safe
public final class MessagePromotionServiceWrapper implements AutoCloseable {
    // Populated by bindToMessagePromotionService. bindToMessagePromotionService must complete
    // prior to calling close so that mServiceConnection is initialized.
    private volatile MessagePromotionServiceConnection
            mServiceConnection;

    private volatile IMessagePromotionService mIMessagePromotionService;
    private Runnable mOnServiceReadyCallback;
    private Executor mOnServiceReadyCallbackExecutor;
    private Context mContext;

    /**
     * Binds to the message promotion service under package {@code dmaPackageName}. This method
     * should be called exactly once.
     *
     * @param context the context
     * @param smsAppPackageName the default SMS app's package name
     * @param executor the executor to run the callback.
     * @param onServiceReadyCallback the callback when service becomes ready.
     * @return true upon successfully binding to a message promotion service, false otherwise
     * @hide
     */
    @RequiresPermission(anyOf = {android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            android.Manifest.permission.INTERACT_ACROSS_USERS,
            android.Manifest.permission.INTERACT_ACROSS_PROFILES})
    public boolean bindToMessagePromotionService(Context context,
            String smsAppPackageName,
            @CallbackExecutor Executor executor,
            Runnable onServiceReadyCallback) {
        Preconditions.checkState(mServiceConnection == null);
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(smsAppPackageName, "smsAppPackageName cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(onServiceReadyCallback, "onServiceReadyCallback cannot be null");

        Intent intent = new Intent(MessagePromotionService.SERVICE_INTERFACE);
        intent.setPackage(smsAppPackageName);
        mServiceConnection = new MessagePromotionServiceWrapper.MessagePromotionServiceConnection();
        mOnServiceReadyCallback = onServiceReadyCallback;
        mOnServiceReadyCallbackExecutor = executor;
        mContext = context;
        return context.bindServiceAsUser(intent, mServiceConnection, Context.BIND_AUTO_CREATE,
                UserHandle.CURRENT);
    }

    /**
     * Unbinds the message promotion service. This method should be called exactly once.
     *
     * @hide
     */
    public void disconnect() {
        if (mServiceConnection != null) {
            mContext.unbindService(mServiceConnection);
            mServiceConnection = null;
        }
        mIMessagePromotionService = null;
        mOnServiceReadyCallback = null;
        mOnServiceReadyCallbackExecutor = null;
    }

    /**
     * Promotes SMS/MMS message to the default SMS app supported transport.
     *
     * @param contentUri the content URI of the SMS/MMS message.
     * @param mClientCallbackExecutor the executor to run the callback on.
     * @param clientCallback the callback to notify about promotion status.
     *
     * @hide
     */
    public void promoteMessage(
            @NonNull Uri contentUri,
            @NonNull @CallbackExecutor Executor mClientCallbackExecutor,
            @NonNull MessagePromotionCallback clientCallback) {
        Objects.requireNonNull(mIMessagePromotionService);
        try {
            mIMessagePromotionService.promoteMessage(
                    contentUri,
                    new MessagePromotionCallbackInternal(mClientCallbackExecutor, clientCallback));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** @hide */
    @Override
    public void close() {
        disconnect();
    }

    /**
     * Called when connection with service is established.
     *
     * @param messagePromotionService the message promotion service interface
     */
    private void onServiceReady(IMessagePromotionService messagePromotionService) {
        mIMessagePromotionService = messagePromotionService;
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
    private final class MessagePromotionServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            onServiceReady(IMessagePromotionService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mIMessagePromotionService = null;
        }
    }

    private static final class MessagePromotionCallbackInternal
            extends IMessagePromotionCallback.Stub {
        private final Executor mClientCallbackExecutor;
        private final MessagePromotionCallback mClientCallback;

        private MessagePromotionCallbackInternal(
                Executor executor, MessagePromotionCallback callback) {
            mClientCallbackExecutor = executor;
            mClientCallback = callback;
        }

        @Override
        public void onPromotionStatusAvailable(int status) throws RemoteException {
            mClientCallbackExecutor.execute(() ->
                    mClientCallback.onPromotionStatusAvailable(status));
        }
    }
}
