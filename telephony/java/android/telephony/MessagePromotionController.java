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

package android.telephony;

import static android.service.messaging.MessagePromotionService.PROMOTION_STATUS_REJECTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.provider.Telephony;
import android.service.messaging.MessagePromotionService;
import android.service.messaging.MessagePromotionServiceWrapper;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Checks if message promotion is supported and enables android system to bind with the
 * available {@link MessagePromotionService} in the default SMS app to promote messages.
 *
 * @hide
 */
// TODO(b/474304887): Make this class thread-safe
public final class MessagePromotionController {

    private static final String TAG = "MsgPromotionController";
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);
    private final Context mContext;
    private final MessagePromotionServiceWrapper mServiceWrapper =
            new MessagePromotionServiceWrapper();

    /** @hide */
    public MessagePromotionController(@NonNull Context context) {
        mContext = Objects.requireNonNull(context);
    }

    /**
     * Promotes a SMS/MMS message.
     *
     * @param contentUri The content URI of the SMS/MMS message.
     * @param clientCallbackExecutor The executor to run the callback on.
     * @param clientCallback The callback to report the promotion status.
     */
    // TODO(b/473520736): Add timeout logic if service doesn't respond within specified duration
    public void promoteMessage(
            @NonNull Uri contentUri,
            @NonNull Executor clientCallbackExecutor,
            @NonNull Consumer<Integer> clientCallback) {
        Objects.requireNonNull(contentUri, "contentUri cannot be null");
        Objects.requireNonNull(clientCallbackExecutor, "clientCallbackExecutor cannot be null");
        Objects.requireNonNull(clientCallback, "clientCallback cannot be null");

        if (!isMessagePromotionSupported()) {
            clientCallbackExecutor.execute(() -> clientCallback.accept(PROMOTION_STATUS_REJECTED));
            return;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            String smsAppPackage = getDefaultSmsAppPackage();
            if (mServiceWrapper.bindToMessagePromotionService(
                    mContext, smsAppPackage, Runnable::run,
                    () -> onServiceReady(contentUri, clientCallbackExecutor, clientCallback))) {
                if (VDBG) {
                    Log.v(TAG, "bindService() to the message promotion service: "
                            + smsAppPackage + " succeeded.");
                }
            } else {
                Log.e(TAG, "bindService() to the message promotion service: "
                        + smsAppPackage + " failed.");
                clientCallbackExecutor.execute(() ->
                        clientCallback.accept(PROMOTION_STATUS_REJECTED));
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Checks if the default SMS app has implemented the {@link MessagePromotionService}.
     *
     * @return {@code true} if the default SMS app has a message promotion service.
     */
    // TODO(b/470708258): cache the result of message promotion supported check
    public boolean isMessagePromotionSupported() {
        String smsAppPackage = getDefaultSmsAppPackage();
        if (TextUtils.isEmpty(smsAppPackage)) {
            Log.e(TAG, "No default sms app found.");
            return false;
        }

        Intent intent = new Intent(MessagePromotionService.SERVICE_INTERFACE);
        intent.setPackage(smsAppPackage);

        List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(
                intent, PackageManager.GET_META_DATA);

        for (int i = 0; i < services.size(); i++) {
            ResolveInfo info = services.get(i);
            if (info.serviceInfo != null
                    && android.Manifest.permission.BIND_MESSAGE_PROMOTION_SERVICE.equals(
                            info.serviceInfo.permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given package is the default SMS app for the current user.
     *
     * @param packageName The package name to check.
     * @return true if the packageName is the default SMS app.
     */
    public boolean isDefaultSmsApp(@NonNull String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }

        return packageName.equals(getDefaultSmsAppPackage());
    }

    /**
     * Disposes the service connection to the message promotion service.
     */
    private void disposeServiceConnection() {
        mServiceWrapper.close();
    }

    // TODO(b/473718205): cache default sms package and update on sms app change
    @Nullable
    private String getDefaultSmsAppPackage() {
        return Telephony.Sms.getDefaultSmsPackage(mContext);
    }

    private void onServiceReady(
            Uri contentUri, Executor clientCallbackExecutor,
            Consumer<Integer> clientCallback) {
        MessagePromotionCallback controllerCallback = new MessagePromotionControllerCallback(
                clientCallbackExecutor, clientCallback);
        try {
            mServiceWrapper.promoteMessage(contentUri, Runnable::run, controllerCallback);
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception while promoting mms message.", e);
            controllerCallback.onPromotionStatusAvailable(PROMOTION_STATUS_REJECTED);
        }
    }

    /**
     * Callback wrapper used by {@link MessagePromotionController} to report the promotion status
     * to the caller and manage service lifecycle.
     *
     * @hide
     */
    public interface MessagePromotionCallback {
        /**
         * Called when the promotion status is available.
         *
         * @param status the status of the promotion request.
         */
        default void onPromotionStatusAvailable(int status) {

        }
    }

    private final class MessagePromotionControllerCallback implements MessagePromotionCallback {
        private final Executor mClientCallbackExecutor;
        private final Consumer<Integer> mClientCallback;

        private MessagePromotionControllerCallback(
                Executor executor, Consumer<Integer> callback) {
            mClientCallbackExecutor = executor;
            mClientCallback = callback;
        }

        @Override
        public void onPromotionStatusAvailable(int status) {
            try {
                mClientCallbackExecutor.execute(() -> mClientCallback.accept(status));
            } finally {
                disposeServiceConnection();
            }
        }
    }
}
