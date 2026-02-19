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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
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
    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final Object mMessageUpgradeLock = new Object();
    private final Context mContext;

    private final BroadcastReceiver mDefaultSmsAppChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SmsApplication.ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL.equals(
                    intent.getAction())) {
                mScheduler.execute(() -> {
                    updateCachedDefaultSmsPackageData();
                });
            }
        }
    };

    private final AlternativeMessageTransportServiceWrapper mServiceWrapper;

    @GuardedBy("mMessageUpgradeLock")
    private String mCachedDefaultSmsPackage;
    @GuardedBy("mMessageUpgradeLock")
    private boolean mCachedIsUpgradeSupported = false;

    /** @hide */
    public MessageUpgradeController(@NonNull Context context) {
        mContext = Objects.requireNonNull(context);
        mServiceWrapper = new AlternativeMessageTransportServiceWrapper(context, mScheduler);
        updateCachedDefaultSmsPackageData();
        registerOnSmsAppChangedReceiver();
    }

    /**
     * Upgrades a SMS/MMS message.
     *
     * @param messageUri The URI of the SMS/MMS message.
     * @param clientCallbackExecutor The executor to run the callback on.
     * @param clientCallback The callback to report the upgrade status.
     */
    public void upgradeMessage(
            @NonNull Uri messageUri,
            @NonNull Executor clientCallbackExecutor,
            @NonNull Consumer<Integer> clientCallback) {
        Objects.requireNonNull(messageUri, "messageUri cannot be null");
        Objects.requireNonNull(clientCallbackExecutor, "clientCallbackExecutor cannot be null");
        Objects.requireNonNull(clientCallback, "clientCallback cannot be null");

        if (!isMessageUpgradeSupported()) {
            rejectUpgradeRequest(clientCallbackExecutor, clientCallback);
            return;
        }

        String smsAppPackage = getCachedDefaultSmsAppPackage();
        if (TextUtils.isEmpty(smsAppPackage)) {
            Log.e(TAG, "No default SMS app found for user");
            rejectUpgradeRequest(clientCallbackExecutor, clientCallback);
            return;
        }

        mServiceWrapper.upgradeMessage(
                messageUri, smsAppPackage, clientCallbackExecutor, clientCallback);
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
        mServiceWrapper.close();
        mScheduler.shutdown();
    }

    @Nullable
    private String getCachedDefaultSmsAppPackage() {
        synchronized (mMessageUpgradeLock) {
            return mCachedDefaultSmsPackage;
        }
    }

    private void registerOnSmsAppChangedReceiver() {
        IntentFilter smsAppChangedFilter = new IntentFilter(
                SmsApplication.ACTION_DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL);
        mContext.registerReceiver(mDefaultSmsAppChangedReceiver, smsAppChangedFilter);
    }

    private void updateCachedDefaultSmsPackageData() {
        String cachedSmsPackage = getCachedDefaultSmsAppPackage();
        String currentSmsPackage = null;
        ComponentName component = SmsApplication.getDefaultSmsApplicationAsUser(
                mContext, false, mContext.getUser());
        if (component != null) {
            currentSmsPackage = component.getPackageName();
        }

        if (Objects.equals(cachedSmsPackage, currentSmsPackage)) {
            return;
        }

        Log.d(TAG, "SMS app changed, updating cache. current sms app:" + currentSmsPackage);
        boolean isSupported = false;
        if (!TextUtils.isEmpty(currentSmsPackage)) {
            Intent intent = new Intent(AlternativeMessageTransportService.SERVICE_INTERFACE);
            intent.setPackage(currentSmsPackage);

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
            mCachedDefaultSmsPackage = currentSmsPackage;
            mCachedIsUpgradeSupported = isSupported;
            Log.i(TAG, "Updated cached data for user " + mContext.getUserId() + ": package="
                    + currentSmsPackage + ", supported=" + isSupported);
        }
    }

    private void rejectUpgradeRequest(
            Executor callbackExecutor, Consumer<Integer> callback) {
        callbackExecutor.execute(() -> callback.accept(UPGRADE_STATUS_REJECTED));
    }
}
