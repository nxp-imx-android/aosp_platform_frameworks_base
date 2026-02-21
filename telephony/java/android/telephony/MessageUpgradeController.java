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

import static android.service.messaging.AlternativeMessageTransportService.UPGRADE_STATUS_REJECTED;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.provider.Telephony;
import android.service.messaging.AlternativeMessageTransportService;
import android.service.messaging.AlternativeMessageTransportServiceWrapper;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.telephony.SmsApplication;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Checks if message upgrade is supported and enables android system to bind with the
 * available {@link android.service.messaging.AlternativeMessageTransportService} in the default
 * SMS app to upgrade SMS/MMS messages to another transport.
 *
 * @hide
 */
// TODO(b/474304887): Make this class thread-safe
public final class MessageUpgradeController {

    private static final String TAG = "MsgUpgradeController";
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int SERVICE_BIND_TIMEOUT = 10; // Seconds

    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final Object mMessageUpgradeLock = new Object();
    private final Context mContext;
    private final AlternativeMessageTransportServiceWrapper mServiceWrapper =
            new AlternativeMessageTransportServiceWrapper();

    private final BroadcastReceiver mDefaultSmsAppChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SmsApplication.ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL.equals(
                    intent.getAction())) {
                mScheduler.execute(() -> {
                    String currentSmsPackage = Telephony.Sms.getDefaultSmsPackage(mContext);
                    if (!Objects.equals(getCachedDefaultSmsAppPackage(), currentSmsPackage)) {
                        Log.d(TAG, "SMS app changed. current:" + currentSmsPackage);
                        updateCachedDefaultSmsPackageData(currentSmsPackage);
                    }
                });
            }
        }
    };

    @GuardedBy("mMessageUpgradeLock")
    @Nullable private ScheduledFuture<?> mServiceCloseFuture;

    @GuardedBy("mMessageUpgradeLock")
    private String mCachedDefaultSmsPackage;
    @GuardedBy("mMessageUpgradeLock")
    private boolean mCachedIsUpgradeSupported = false;

    /** @hide */
    public MessageUpgradeController(@NonNull Context context) {
        mContext = Objects.requireNonNull(context);
        updateCachedDefaultSmsPackageData(null);
        registerOnSmsAppChangedReceiver();
    }

    /**
     * Upgrades a SMS/MMS message.
     *
     * @param contentUri The content URI of the SMS/MMS message.
     * @param clientCallbackExecutor The executor to run the callback on.
     * @param clientCallback The callback to report the upgrade status.
     */
    // TODO(b/473520736): Add timeout logic if service doesn't respond within specified duration
    public void upgradeMessage(
            @NonNull Uri contentUri,
            @NonNull Executor clientCallbackExecutor,
            @NonNull Consumer<Integer> clientCallback) {
        Objects.requireNonNull(contentUri, "contentUri cannot be null");
        Objects.requireNonNull(clientCallbackExecutor, "clientCallbackExecutor cannot be null");
        Objects.requireNonNull(clientCallback, "clientCallback cannot be null");

        if (!isMessageUpgradeSupported()) {
            clientCallbackExecutor.execute(() -> clientCallback.accept(UPGRADE_STATUS_REJECTED));
            return;
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            String smsAppPackage = getCachedDefaultSmsAppPackage();
            if (mServiceWrapper.bindToService(
                    mContext, smsAppPackage, Runnable::run,
                    () -> onServiceReady(contentUri, clientCallbackExecutor, clientCallback))) {
                if (VDBG) {
                    Log.v(TAG, "bindService() to the message upgrade service: "
                            + smsAppPackage + " succeeded.");
                }
                scheduleServiceClose();
            } else {
                Log.e(TAG, "bindService() to the message upgrade service: "
                        + smsAppPackage + " failed.");
                clientCallbackExecutor.execute(() ->
                        clientCallback.accept(UPGRADE_STATUS_REJECTED));
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void scheduleServiceClose() {
        synchronized (mMessageUpgradeLock) {
            if (mServiceCloseFuture != null) {
                Log.w(TAG, "Cancelling previous hard service close timer.");
                mServiceCloseFuture.cancel(false);
            }

            mServiceCloseFuture = mScheduler.schedule(
                    this::disposeServiceConnection,
                    SERVICE_BIND_TIMEOUT,
                    TimeUnit.SECONDS);
            Log.d(TAG, "Scheduled hard service close in " + SERVICE_BIND_TIMEOUT + "s.");
        }
    }

    /**
     * Checks if the calling package is not the default messaging app (DMA) and if the DMA has a
     * valid {@link AlternativeMessageTransportService}.
     *
     * @param callingPkg the calling app's package.
     * @return {@code true} if the calling package is not the default messaging app (DMA) and if the
     *         DMA has a valid {@link AlternativeMessageTransportService}, returns false otherise.
     */
    public boolean isMessageUpgradeSupportedAndNotDma(String callingPkg) {
        if (TextUtils.isEmpty(callingPkg)) {
            Log.e(TAG, "callingPkg is null or empty.");
            return false;
        }
        synchronized (mMessageUpgradeLock) {
            return mCachedIsUpgradeSupported && !callingPkg.equals(mCachedDefaultSmsPackage);
        }
    }

    /**
     * Checks if the default SMS app has implemented the
     * {@link android.service.messaging.AlternativeMessageTransportService}.
     *
     * @return {@code true} if the default SMS app has a AlternativeMessageTransportService.
     */
    private boolean isMessageUpgradeSupported() {
        synchronized (mMessageUpgradeLock) {
            return mCachedIsUpgradeSupported;
        }
    }

    /**
     * Clean up resources held by this controller instance. This should be called when the
     * corresponding user or profile is removed.
     */
    public void close() {
        Log.i(TAG, "Closing MessageUpgradeController for user " + mContext.getUserId());
        mContext.unregisterReceiver(mDefaultSmsAppChangedReceiver);
        mScheduler.shutdown();
        disposeServiceConnection();
    }

    /**
     * Disposes the service connection to the AlternativeMessageTransportService.
     */
    private void disposeServiceConnection() {
        Log.i(TAG, "disposeServiceConnection() called. Closing wrapper.");
        mServiceWrapper.close();
        synchronized (mMessageUpgradeLock) {
            mServiceCloseFuture = null;
        }
    }

    @Nullable
    private String getCachedDefaultSmsAppPackage() {
        synchronized (mMessageUpgradeLock) {
            return mCachedDefaultSmsPackage;
        }
    }

    private void onServiceReady(
            Uri contentUri, Executor clientCallbackExecutor,
            Consumer<Integer> clientCallback) {
        MessageUpgradeCallback controllerCallback = new MessageUpgradeControllerCallback(
                clientCallbackExecutor, clientCallback);
        try {
            mServiceWrapper.upgradeMessage(contentUri, Runnable::run, controllerCallback);
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception while upgrading message.", e);
            controllerCallback.onUpgradeStatusAvailable(UPGRADE_STATUS_REJECTED);
        }
    }

    private void registerOnSmsAppChangedReceiver() {
        IntentFilter smsAppChangedFilter = new IntentFilter(
                SmsApplication.ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL);
        mContext.registerReceiver(mDefaultSmsAppChangedReceiver, smsAppChangedFilter);
    }

    private void updateCachedDefaultSmsPackageData(@Nullable String defaultSmsPackage) {
        String smsAppPackage = defaultSmsPackage;
        if (TextUtils.isEmpty(smsAppPackage)) {
            smsAppPackage = Telephony.Sms.getDefaultSmsPackage(mContext);
        }

        boolean isSupported = false;
        if (!TextUtils.isEmpty(smsAppPackage)) {
            Intent intent = new Intent(AlternativeMessageTransportService.SERVICE_INTERFACE);
            intent.setPackage(smsAppPackage);

            List<ResolveInfo> services = mContext.getPackageManager().queryIntentServices(
                    intent, PackageManager.GET_META_DATA);

            for (ResolveInfo info : services) {
                if (info.serviceInfo != null
                        && Manifest.permission.BIND_ALTERNATIVE_MESSAGE_TRANSPORT_SERVICE.equals(
                        info.serviceInfo.permission)) {
                    isSupported = true;
                    break;
                }
            }
        } else {
            Log.e(TAG, "No default sms app found for user " + mContext.getUserId());
        }

        synchronized (mMessageUpgradeLock) {
            mCachedDefaultSmsPackage = smsAppPackage;
            mCachedIsUpgradeSupported = isSupported;
            Log.i(TAG, "Updated cached data for user " + mContext.getUserId() + ": package="
                    + smsAppPackage + ", supported=" + isSupported);
        }
    }

    /**
     * Callback wrapper used by {@link MessageUpgradeController} to report the message upgrade
     * status to the caller and manage service lifecycle.
     *
     * @hide
     */
    public interface MessageUpgradeCallback {
        /**
         * Called when the upgrade status is available.
         *
         * @param status the status of the upgrade request.
         */
        default void onUpgradeStatusAvailable(int status) {

        }
    }

    private final class MessageUpgradeControllerCallback implements MessageUpgradeCallback {
        private final Executor mClientCallbackExecutor;
        private final Consumer<Integer> mClientCallback;

        private MessageUpgradeControllerCallback(
                Executor executor, Consumer<Integer> callback) {
            mClientCallbackExecutor = executor;
            mClientCallback = callback;
        }

        @Override
        public void onUpgradeStatusAvailable(int status) {
            Binder.withCleanCallingIdentity(() ->
                    mClientCallbackExecutor.execute(() -> mClientCallback.accept(status))
            );
        }
    }
}
