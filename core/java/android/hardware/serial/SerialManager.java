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

package android.hardware.serial;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * This class allows you to communicate with Serial ports.
 */
@SystemService(Context.SERIAL_SERVICE)
@FlaggedApi(android.hardware.serial.flags.Flags.FLAG_ENABLE_WIRED_SERIAL_API)
public final class SerialManager {
    /**
     * The request token for requesting serial port access.
     *
     * @hide
     */
    public static final String EXTRA_REQUEST_TOKEN = "android.hardware.serial.EXTRA_REQUEST_TOKEN";

    /**
     * The name of the serial port.
     *
     * @hide
     */
    public static final String EXTRA_PORT = "android.hardware.serial.EXTRA_PORT";

    /**
     * The package name of the application requesting serial port access.
     *
     * @hide
     */
    public static final String EXTRA_PACKAGE_NAME = "android.hardware.serial.EXTRA_PACKAGE_NAME";

    /**
     * The UID of the user requesting serial port access.
     *
     * @hide
     */
    public static final String EXTRA_UID = "android.hardware.serial.EXTRA_UID";

    private static final String TAG = "SerialManager";

    private final @NonNull Context mContext;
    private final @NonNull ISerialManager mService;

    @GuardedBy("mLock")
    private SerialPortServiceListener mServiceListener;

    @GuardedBy("mLock")
    private ArrayMap<SerialPortListener, Executor> mListeners;

    private final Object mLock = new Object();

    /** @hide */
    public SerialManager(@NonNull Context context, @NonNull ISerialManager service) {
        mContext = context;
        mService = service;
    }

    /**
     * Enumerates serial ports.
     */
    @NonNull
    public List<SerialPort> getPorts() {
        try {
            List<SerialPortInfo> infos = mService.getSerialPorts();
            List<SerialPort> ports = new ArrayList<>(infos.size());
            for (int i = 0; i < infos.size(); i++) {
                ports.add(new SerialPort(mContext, infos.get(i), mService));
            }
            return Collections.unmodifiableList(ports);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Register a listener to monitor serial port connections and disconnections.
     *
     * @throws IllegalStateException if this listener has already been registered.
     */
    public void registerSerialPortListener(@NonNull @CallbackExecutor Executor executor,
            @NonNull SerialPortListener listener) {
        synchronized (mLock) {
            if (mServiceListener == null) {
                mServiceListener = new SerialPortServiceListener();
                try {
                    mService.registerSerialPortListener(mServiceListener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
            if (mListeners == null) {
                mListeners = new ArrayMap<>();
            }
            if (mListeners.containsKey(listener)) {
                throw new IllegalStateException("Listener has already been registered.");
            }
            mListeners.put(listener, executor);
        }
    }

    /**
     * Unregister a listener that monitored serial port connections and disconnections.
     */
    public void unregisterSerialPortListener(@NonNull SerialPortListener listener) {
        synchronized (mLock) {
            if (mListeners == null) {
                return;
            }
            mListeners.remove(listener);
            if (mListeners.isEmpty()) {
                if (mServiceListener != null) {
                    try {
                        mService.unregisterSerialPortListener(mServiceListener);
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    } finally {
                        // If there was a RemoteException, the system server may have died,
                        // and this listener probably became unregistered, so clear it for
                        // re-registration.
                        mServiceListener = null;
                    }
                }
            }
        }
    }

    /**
     * Grants a specific UID access to a serial port.
     *
     * @param serialPort The name of the serial port.
     * @param uid The user ID to grant access to.
     * @param token An optional token associated with the grant.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    @TestApi
    public void grantSerialPortAccess(@NonNull String serialPort, int uid,
            @Nullable IBinder token) {
        try {
            mService.grantSerialPortAccess(serialPort, uid, token);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Revokes a specific UID's access to a serial port.
     *
     * @param serialPort The name of the serial port.
     * @param uid The user ID to revoke access from.
     * @param token An optional token associated with the revocation.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    @TestApi
    public void revokeSerialPortAccess(@NonNull String serialPort, int uid,
            @Nullable IBinder token) {
        try {
            mService.revokeSerialPortAccess(serialPort, uid, token);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private class SerialPortServiceListener extends ISerialPortListener.Stub {
        @Override
        public void onSerialPortConnected(SerialPortInfo info) {
            SerialPort port = new SerialPort(mContext, info, mService);
            synchronized (mLock) {
                for (Map.Entry<SerialPortListener, Executor> e : mListeners.entrySet()) {
                    Executor executor = e.getValue();
                    SerialPortListener listener = e.getKey();
                    try {
                        executor.execute(() -> listener.onSerialPortConnected(port));
                    } catch (RuntimeException e2) {
                        Slog.w(TAG, "Exception in listener", e2);
                    }
                }
            }
        }

        @Override
        public void onSerialPortDisconnected(SerialPortInfo info) {
            SerialPort port = new SerialPort(mContext, info, mService);
            synchronized (mLock) {
                for (Map.Entry<SerialPortListener, Executor> e : mListeners.entrySet()) {
                    Executor executor = e.getValue();
                    SerialPortListener listener = e.getKey();
                    try {
                        executor.execute(() -> listener.onSerialPortDisconnected(port));
                    } catch (RuntimeException e2) {
                        Slog.w(TAG, "Exception in listener", e2);
                    }
                }
            }
        }
    }
}
