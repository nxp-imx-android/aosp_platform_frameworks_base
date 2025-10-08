/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdbdServicesManager {

    private static final String TAG = AdbDebuggingManager.class.getSimpleName();

    // The manager we use to publish/unpublish services
    private final NsdManager mNsdManager;

    // We keep track of all services we register in this map. Keys are generated via the
    // keyForService method.
    private final Map<String, AdbdRegistrationListener> mRegisteredServices = new HashMap<>();

    private final ContentResolver mContentResolver;

    AdbdServicesManager(Context context) {
        mNsdManager = context.getSystemService(NsdManager.class);
        mContentResolver = context.getContentResolver();
    }

    private String keyForService(String instanceName, String serviceType) {
        return instanceName + "." + serviceType;
    }

    void registerService(String instanceName, String serviceType, int port) {
        String key = keyForService(instanceName, serviceType);
        if (mRegisteredServices.containsKey(key)) {
            AdbdRegistrationListener listener = mRegisteredServices.get(key);
            unregisterService(listener.mInstanceName, listener.mServiceType);
        }

        Slog.i(TAG, "Registering service '" + key + ":" + port + "' with Framework");

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(instanceName);
        serviceInfo.setServiceType(serviceType);
        serviceInfo.setPort(port);
        if (AdbDebuggingManager.wifiLifeCycleOverAdbdauthSupported()) {
            serviceInfo.setAttribute("v", "2.0");
            serviceInfo.setAttribute("name", SystemProperties.get("ro.product.model", ""));
            serviceInfo.setAttribute("api", SystemProperties.get("ro.build.version.sdk_full", ""));
            serviceInfo.setAttribute(
                    "given_name",
                    Settings.Global.getString(mContentResolver, Settings.Global.DEVICE_NAME));
            serviceInfo.setAttribute("serial", SystemProperties.get("ro.serialno", ""));
        } else {
            serviceInfo.setAttribute("v", "1");
        }

        AdbdRegistrationListener listener =
                new AdbdRegistrationListener(instanceName, serviceType, port);

        try {
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            Slog.e(TAG, "Unable to register " + key, e);
        }

        mRegisteredServices.put(key, listener);
    }

    void unregisterService(String instanceName, String serviceType) {
        String key = keyForService(instanceName, serviceType);
        if (!mRegisteredServices.containsKey(key)) {
            Slog.i(TAG, "unregister service noop for" + key);
            return;
        }

        Slog.i(TAG, "Unregister service '" + key + "' with Framework");
        try {
            mNsdManager.unregisterService(mRegisteredServices.get(key));
        } catch (Exception e) {
            Slog.e(TAG, "Unable to unregister " + key, e);
        }
        mRegisteredServices.remove(key);
    }

    void unregisterAll() {
        for (AdbdRegistrationListener service : mRegisteredServices.values()) {
            unregisterService(service.mInstanceName, service.mServiceType);
        }
    }

    // When an attribute has changed, we cannot just update the TXT since NsdManager does not
    // supports it. Instead, we republish all services (see b/445548047).
    void onAttributeChanged() {
        List<AdbdRegistrationListener> services = new ArrayList<>(mRegisteredServices.values());
        for (AdbdRegistrationListener service : services) {
            unregisterService(service.mInstanceName, service.mServiceType);
        }
        for (AdbdRegistrationListener service : services) {
            registerService(service.mInstanceName, service.mServiceType, service.mPort);
        }
    }

    private static class AdbdRegistrationListener implements NsdManager.RegistrationListener {
        final String mInstanceName;
        final String mServiceType;
        final int mPort;

        private AdbdRegistrationListener(String instanceName, String serviceType, int port) {
            mInstanceName = instanceName;
            mServiceType = serviceType;
            mPort = port;
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Slog.e(TAG, "Failed to register service (err=" + errorCode + "): " + serviceInfo);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Slog.e(TAG, "Failed to unregister service (err=" + errorCode + "): " + serviceInfo);
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Slog.i(TAG, "Registered service '" + serviceInfo + "'");
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Slog.i(TAG, "Unregistered service '" + serviceInfo + "'");
        }
    }
}
