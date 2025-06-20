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

import static android.hardware.serial.flags.Flags.enableSerialApi;

import static com.android.server.serial.SerialConstants.DEV_DIR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.serial.ISerialManager;
import android.hardware.serial.ISerialPortListener;
import android.hardware.serial.ISerialPortResponseCallback;
import android.hardware.serial.ISerialPortResponseCallback.ErrorCode;
import android.hardware.serial.SerialPortInfo;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.server.SystemService;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class SerialManagerService extends ISerialManager.Stub {
    private static final String TAG = "SerialManagerService";

    // keyed by the serial port name (eg. ttyS0)
    @GuardedBy("mLock")
    private final HashMap<String, SerialPortInfo> mSerialPorts = new HashMap<>();

    @GuardedBy("mLock")
    private boolean mIsStarted;

    private final Object mLock = new Object();

    private final RemoteCallbackList<ISerialPortListener> mListeners = new RemoteCallbackList<>();

    private final SerialDriversDiscovery mSerialDriversDiscovery = new SerialDriversDiscovery();

    private final SerialDevicesEnumerator mSerialDevicesEnumerator = new SerialDevicesEnumerator();

    private FileObserver mDevDirObserver;

    private SerialManagerService() {
    }

    @Override
    public List<SerialPortInfo> getSerialPorts() throws RemoteException {
        synchronized (mLock) {
            try {
                startIfNeeded();
            } catch (IOException e) {
                Slog.e(TAG, "Error reading the list of serial drivers", e);
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(mSerialPorts.values()));
        }
    }

    @Override
    public void registerSerialPortListener(@NonNull ISerialPortListener listener) {
        synchronized (mLock) {
            mListeners.register(listener);
        }
    }

    @Override
    public void unregisterSerialPortListener(@NonNull ISerialPortListener listener) {
        synchronized (mLock) {
            mListeners.unregister(listener);
        }
    }

    @Override
    public void requestOpen(@NonNull String portName, int flags,
            @NonNull ISerialPortResponseCallback callback) throws RemoteException {
        synchronized (mLock) {
            try {
                startIfNeeded();
            } catch (IOException e) {
                Slog.e(TAG, "Error reading the list of serial drivers", e);
                callback.onError(ErrorCode.ERROR_READING_DRIVERS, 0, e.getMessage());
                return;
            }
            SerialPortInfo port = mSerialPorts.get(portName);
            if (port == null) {
                try {
                    callback.onError(ErrorCode.ERROR_PORT_NOT_FOUND, 0, portName);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error sending error to callback", e);
                }
                return;
            }
            String path = DEV_DIR + "/" + portName;
            try {
                FileDescriptor fd = Os.open(path, flags, /* mode= */ 0);
                try (var pfd = new ParcelFileDescriptor(fd)) {
                    callback.onResult(port, pfd);
                } catch (RemoteException | RuntimeException e) {
                    Slog.e(TAG, "Error sending result to callback", e);
                } catch (IOException e) {
                    Slog.w(TAG, "Error closing the file descriptor", e);
                }
            } catch (ErrnoException e) {
                Slog.e(TAG, "Failed to open " + path, e);
                try {
                    callback.onError(ErrorCode.ERROR_OPENING_PORT, e.errno, "open");
                } catch (RemoteException e2) {
                    Slog.e(TAG, "Error sending error to callback", e2);
                }
            }
        }
    }

    private void startIfNeeded() throws IOException {
        if (mIsStarted) {
            return;
        }
        watchDevicesDir();
        enumerateSerialDevices();
        mIsStarted = true;
    }

    private void watchDevicesDir() {
        mDevDirObserver = new FileObserver(new File(DEV_DIR),
                FileObserver.CREATE | FileObserver.DELETE) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if (path == null) {
                    return;
                }
                if (event == FileObserver.CREATE) {
                    try {
                        addSerialDevice(path);
                    } catch (IOException e) {
                        Slog.e(TAG, "Error reading the list of serial drivers", e);
                    }
                } else {
                    removeSerialDevice(path);
                }
            }
        };
        mDevDirObserver.startWatching();
    }

    private void enumerateSerialDevices() throws IOException {
        synchronized (mLock) {
            mSerialPorts.clear();
            List<SerialTtyDriverInfo> serialDrivers = mSerialDriversDiscovery.discover();
            List<SerialPortInfo> serialPorts = mSerialDevicesEnumerator.enumerate(serialDrivers);
            for (int i = 0; i < serialPorts.size(); i++) {
                SerialPortInfo serialPort = serialPorts.get(i);
                mSerialPorts.put(serialPort.getName(), serialPort);
            }
        }
        Slog.d(TAG, "Found serial devices: " + mSerialPorts);
    }

    private void addSerialDevice(String name) throws IOException {
        SerialPortInfo port;
        synchronized (mLock) {
            if (mSerialPorts.containsKey(name)) {
                return;
            }
            DeviceFileInfo device = DeviceFileInfo.fromNameInDev(name);
            if (device == null) {
                return;
            }
            Slog.d(TAG, "Added serial device " + name);
            List<SerialTtyDriverInfo> serialDrivers = mSerialDriversDiscovery.discover();
            if (!mSerialDevicesEnumerator.matchesSerialDriver(device, serialDrivers)) {
                Slog.d(TAG, "Serial driver not found for device " + name);
                return;
            }
            port = SerialDevicesEnumerator.readSerialPortInfo(device);
            mSerialPorts.put(name, port);
        }
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onSerialPortConnected(port);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error notifying listener", e);
            }
        }
        mListeners.finishBroadcast();
    }

    private void removeSerialDevice(String name) {
        SerialPortInfo port;
        synchronized (mLock) {
            port = mSerialPorts.remove(name);
            if (port == null) {
                return;
            }
        }
        Slog.d(TAG, "Removed serial device " + name);
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onSerialPortConnected(port);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error notifying listener", e);
            }
        }
        mListeners.finishBroadcast();
    }

    public static class Lifecycle extends SystemService {

        public Lifecycle(@NonNull Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            if (enableSerialApi()) {
                publishBinderService(Context.SERIAL_SERVICE, new SerialManagerService());
            }
        }
    }
}
