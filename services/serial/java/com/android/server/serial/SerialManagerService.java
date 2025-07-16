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

import static android.hardware.serial.SerialPort.OPEN_FLAG_DATA_SYNC;
import static android.hardware.serial.SerialPort.OPEN_FLAG_NONBLOCK;
import static android.hardware.serial.SerialPort.OPEN_FLAG_SYNC;
import static android.hardware.serial.SerialPort.OPEN_FLAG_READ_ONLY;
import static android.hardware.serial.SerialPort.OPEN_FLAG_WRITE_ONLY;
import static android.hardware.serial.SerialPort.OPEN_FLAG_READ_WRITE;
import static android.hardware.serial.flags.Flags.enableWiredSerialApi;

import static com.android.server.serial.SerialConstants.DEV_DIR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.hardware.serial.ISerialManager;
import android.hardware.serial.ISerialPortListener;
import android.hardware.serial.ISerialPortResponseCallback;
import android.hardware.serial.ISerialPortResponseCallback.ErrorCode;
import android.hardware.serial.SerialPortInfo;
import android.os.Binder;
import android.os.FileObserver;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
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

    private static final int OPEN_MODE_BITS =
            OPEN_FLAG_READ_ONLY | OPEN_FLAG_WRITE_ONLY | OPEN_FLAG_READ_WRITE;
    private static final int FORBIDDEN_FLAG_BITS =
            ~(OPEN_FLAG_READ_ONLY | OPEN_FLAG_WRITE_ONLY | OPEN_FLAG_READ_WRITE | OPEN_FLAG_NONBLOCK
                    | OPEN_FLAG_DATA_SYNC | OPEN_FLAG_SYNC);

    // keyed by the serial port name (eg. ttyS0)
    @GuardedBy("mLock")
    private final HashMap<String, SerialPortInfo> mSerialPorts = new HashMap<>();

    private final Object mLock = new Object();

    private final Context mContext;

    private final String[] mPortsInConfig;

    @GuardedBy("mLock")
    private final SparseArray<SerialUserAccessManager> mAccessManagerPerUser = new SparseArray<>();

    private final RemoteCallbackList<ISerialPortListener> mListeners = new RemoteCallbackList<>();

    private final SerialDriversDiscovery mSerialDriversDiscovery = new SerialDriversDiscovery();

    private final SerialDevicesEnumerator mSerialDevicesEnumerator = new SerialDevicesEnumerator();

    @GuardedBy("mLock")
    private boolean mIsStarted;

    private SerialManagerService(Context context) {
        mContext = context;
        mPortsInConfig =
                stripDevPrefix(mContext.getResources().getStringArray(R.array.config_serialPorts));
    }

    private static String[] stripDevPrefix(String[] portPaths) {
        if (portPaths.length == 0) {
            return portPaths;
        }

        final String devDirPrefix = DEV_DIR + "/";
        ArrayList<String> portNames = new ArrayList<>();
        for (int i = 0; i < portPaths.length; ++i) {
            String portPath = portPaths[i];
            if (portPath.startsWith(devDirPrefix)) {
                portNames.add(portPath.substring(devDirPrefix.length()));
            } else {
                Slog.w(TAG, "Skipping port path not under /dev: " + portPath);
            }
        }
        return portNames.toArray(new String[0]);
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
    public void requestOpen(@NonNull String portName, int flags, boolean exclusive,
            @NonNull String packageName, @NonNull ISerialPortResponseCallback callback)
            throws RemoteException {
        final int callingPid = Binder.getCallingPid();
        final int callingUid = Binder.getCallingUid();
        final @UserIdInt int userId = UserHandle.getUserId(callingUid);

        synchronized (mLock) {
            try {
                startIfNeeded();
            } catch (IOException e) {
                Slog.e(TAG, "Error reading the list of serial drivers", e);
                deliverErrorToCallback(
                        callback, ErrorCode.ERROR_READING_DRIVERS, /* errno */ 0, e.getMessage());
                return;
            }
            SerialPortInfo port = mSerialPorts.get(portName);
            if (port == null) {
                deliverErrorToCallback(
                        callback, ErrorCode.ERROR_PORT_NOT_FOUND, /* errno */ 0, portName);
                return;
            }
            if (!mAccessManagerPerUser.contains(userId)) {
                mAccessManagerPerUser.put(
                        userId, new SerialUserAccessManager(mContext, mPortsInConfig));
            }
            final SerialUserAccessManager accessManager = mAccessManagerPerUser.get(userId);
            accessManager.requestAccess(portName, callingPid, callingUid,
                    (resultPort, pid, uid, granted) -> {
                        if (!granted) {
                            deliverErrorToCallback(
                                    callback, ErrorCode.ERROR_ACCESS_DENIED, /* errno */ 0,
                                    "User denied " + packageName + " access to " + portName);
                            return;
                        }

                        String path = DEV_DIR + "/" + portName;
                        try {
                            deliverResultToCallback(callback, port,
                                    Os.open(path, toOsConstants(flags), /* mode */ 0));
                        } catch (ErrnoException e) {
                            Slog.e(TAG, "Failed to open " + path, e);
                            deliverErrorToCallback(
                                    callback, ErrorCode.ERROR_OPENING_PORT, e.errno, "open");
                        }
                    });
        }
    }

    private void deliverResultToCallback(
            @NonNull ISerialPortResponseCallback callback, SerialPortInfo port, FileDescriptor fd) {
        try (var pfd = new ParcelFileDescriptor(fd)) {
            callback.onResult(port, pfd);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Error sending result to callback", e);
        } catch (IOException e) {
            Slog.w(TAG, "Error closing the file descriptor", e);
        }
    }

    private void deliverErrorToCallback(@NonNull ISerialPortResponseCallback callback,
            @ErrorCode int errorCode, int errno, String message) {
        try {
            callback.onError(errorCode, errno, message);
        } catch (RemoteException e) {
            Slog.e(TAG, "Error sending error to callback", e);
        }
    }

    @VisibleForTesting
    static int toOsConstants(int flags) {
        // Always open the device with O_NOCTTY flag, so that it will not become the process's
        // controlling terminal.
        int osFlags = OsConstants.O_NOCTTY;
        switch (flags & OPEN_MODE_BITS) {
            case OPEN_FLAG_READ_ONLY -> osFlags |= OsConstants.O_RDONLY;
            case OPEN_FLAG_WRITE_ONLY -> osFlags |= OsConstants.O_WRONLY;
            case OPEN_FLAG_READ_WRITE -> osFlags |= OsConstants.O_RDWR;
            default -> throw new IllegalArgumentException(
                    "Flags value " + flags + " must contain only one open mode flag");
        }
        if ((flags & OPEN_FLAG_NONBLOCK) != 0) {
            osFlags |= OsConstants.O_NONBLOCK;
        }
        if ((flags & OPEN_FLAG_DATA_SYNC) != 0) {
            osFlags |= OsConstants.O_DSYNC;
        }
        if ((flags & OPEN_FLAG_SYNC) != 0) {
            osFlags |= OsConstants.O_SYNC;
        }
        if ((flags & FORBIDDEN_FLAG_BITS) != 0) {
            throw new IllegalArgumentException(
                    "Flags value " + flags + " is not a combination of FLAG_* constants");
        }
        return osFlags;
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
        FileObserver devDirObserver = new FileObserver(new File(DEV_DIR),
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
        devDirObserver.startWatching();
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
        for (int i = mAccessManagerPerUser.size() - 1; i >= 0; --i) {
            mAccessManagerPerUser.valueAt(i).onPortRemoved(name);
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
        private final Context mContext;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onStart() {
            if (enableWiredSerialApi()) {
                publishBinderService(Context.SERIAL_SERVICE, new SerialManagerService(mContext));
            }
        }
    }
}
