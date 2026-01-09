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

package com.android.server.adb;

import android.content.Context;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adb.AdbDebuggingManager.AdbDebuggingHandler;

import java.util.Optional;

class AdbPairingThread extends Thread implements AdbdServicesManager.RegistrationCallback {
    private static final String TAG = AdbPairingThread.class.getSimpleName();
    private final Context mContext;
    private final String mPairingCode;
    private final String mGuid;
    private String mServiceName;

    // From RFC6763 (https://tools.ietf.org/html/rfc6763#section-7.2),
    // The rules for Service Names [RFC6335] state that they may be no more
    // than fifteen characters long (not counting the mandatory underscore),
    // consisting of only letters, digits, and hyphens, must begin and end
    // with a letter or digit, must not contain consecutive hyphens, and
    // must contain at least one letter.
    @VisibleForTesting static final String SERVICE_PROTOCOL = "adb-tls-pairing";
    private final String mServiceType = String.format("_%s._tcp.", SERVICE_PROTOCOL);
    private final AdbWifiPairingMethod mAdbWifiPairingMethod;

    private final Handler mHandler;

    AdbPairingThread(
            String pairingCode,
            String serviceName,
            Context context,
            AdbWifiPairingMethod adbWifiPairingMethod,
            Handler handler) {
        super(TAG);
        mPairingCode = pairingCode;
        mGuid = SystemProperties.get(AdbDebuggingManager.WIFI_PERSISTENT_GUID);
        mServiceName = serviceName;
        if (serviceName == null || serviceName.isEmpty()) {
            mServiceName = mGuid;
        }
        mContext = context;
        mAdbWifiPairingMethod = adbWifiPairingMethod;
        mHandler = handler;
    }

    @Override
    public void run() {
        AdbdServicesManager servicesManager = new AdbdServicesManager(mContext);
        int port = native_pairing_get_port();
        if (port <= 0) {
            Slog.e(TAG, "Pairing server has invalid port");
            return;
        }

        servicesManager.registerService(mServiceName, mServiceType, port, this);

        // Send pairing port to UI
        Message msg = mHandler.obtainMessage(AdbDebuggingHandler.MSG_RESPONSE_PAIRING_PORT);
        msg.obj = port;
        mHandler.sendMessage(msg);

        String publicKey = native_pairing_wait();
        if (publicKey != null) {
            Slog.i(TAG, "Pairing succeeded key=" + publicKey);
        } else {
            Slog.i(TAG, "Pairing failed");
        }

        servicesManager.unregisterService(mServiceName, mServiceType);

        Message message =
                Message.obtain(
                        mHandler,
                        AdbDebuggingHandler.MSG_RESPONSE_PAIRING_RESULT,
                        new AdbWifiPairingResult(
                                Optional.ofNullable(publicKey), mAdbWifiPairingMethod));
        mHandler.sendMessage(message);
    }

    @Override
    public void start() {
        /*
         * If a user is fast enough to click cancel, native_pairing_cancel can be invoked
         * while native_pairing_start is running which run the destruction of the object
         * while it is being constructed. Here we start the pairing server on foreground
         * Thread so native_pairing_cancel can never be called concurrently. Then we let
         * the pairing server run on a background Thread.
         */
        if (mGuid.isEmpty()) {
            Slog.e(TAG, "adbwifi guid was not set");
            return;
        }

        if (native_pairing_start(mGuid, mPairingCode) <= 0) {
            Slog.e(TAG, "Unable to start pairing server");
            return;
        }

        super.start();
    }

    public void cancelPairing() {
        native_pairing_cancel();
    }

    private native int native_pairing_start(String guid, String password);

    private native void native_pairing_cancel();

    private native String native_pairing_wait();

    private native int native_pairing_get_port();

    @Override
    public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
        cancelPairing();
    }

    @Override
    public void onServiceRegistered(NsdServiceInfo serviceInfo) {
        // nothing to do, pairing is in progress.
    }

    enum AdbWifiPairingMethod {
        QR_CODE,
        PAIRING_CODE
    }

    /**
     * Represents the result of a pairing operation.
     *
     * @param publicKey The key of the device that was paired. If not present, then the pairing
     *     failed.
     * @param adbWifiPairingMethod The pairing method used.
     */
    record AdbWifiPairingResult(
            Optional<String> publicKey, AdbWifiPairingMethod adbWifiPairingMethod) {}
}
