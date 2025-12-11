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
import static android.hardware.serial.SerialPort.OPEN_FLAG_READ_ONLY;
import static android.hardware.serial.SerialPort.OPEN_FLAG_READ_WRITE;
import static android.hardware.serial.SerialPort.OPEN_FLAG_SYNC;
import static android.hardware.serial.SerialPort.OPEN_FLAG_WRITE_ONLY;
import static android.hardware.serial.flags.Flags.enableWiredSerialApi;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.hardware.serial.ISerialManager;
import android.hardware.serial.ISerialPortListener;
import android.hardware.serial.ISerialPortResponseCallback;
import android.hardware.serial.ISerialPortResponseCallback.ErrorCode;
import android.hardware.serial.SerialPortInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.system.OsConstants;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DumpUtils;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * System service for managing wired serial ports.
 *
 * <p>This service acts as a facade for a native service that handles device enumeration and file
 * operations. This service manages user consent and proxies requests to the native service.
 */
public class SerialManagerService extends ISerialManager.Stub {
    private static final String TAG = "SerialManagerService";

    private static final int OPEN_MODE_BITS =
            OPEN_FLAG_READ_ONLY | OPEN_FLAG_WRITE_ONLY | OPEN_FLAG_READ_WRITE;
    private static final int FORBIDDEN_FLAG_BITS =
            ~(OPEN_FLAG_READ_ONLY | OPEN_FLAG_WRITE_ONLY | OPEN_FLAG_READ_WRITE | OPEN_FLAG_NONBLOCK
                    | OPEN_FLAG_DATA_SYNC | OPEN_FLAG_SYNC);

    // Name of the Native Serial Service that handles device enumeration and file operations.
    private static final String NATIVE_SERIAL_SERVICE_NAME = "native_serial";

    private static final String DEV_DIR_PREFIX = "/dev/";

    // keyed by the serial port name (eg. ttyS0)
    @GuardedBy("mLock")
    private final HashMap<String, SerialPortInfo> mSerialPorts = new HashMap<>();

    private final Object mLock = new Object();

    private final Context mContext;

    private final String[] mPortsInConfig;

    private final String mDialogComponent;

    private final Supplier<android.hardware.serialservice.ISerialManager> mNativeServiceSupplier;

    private final SerialDeviceFilterFactory mSerialDeviceFilterFactory;

    private final SerialUserAccessManagerFactory mAccessManagerFactory;

    // Binder proxy for the native serial service.
    @GuardedBy("mLock")
    private android.hardware.serialservice.ISerialManager mNativeService;

    @GuardedBy("mLock")
    private final SparseArray<SerialUserAccessManagerInterface> mAccessManagerPerUser =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final RemoteCallbackList<ISerialPortListener> mListeners = new RemoteCallbackList<>();

    @GuardedBy("mLock")
    private boolean mIsConnectedToNativeService;

    // PTY port is only exposed in CTS tests
    @GuardedBy("mLock")
    private boolean mIsPtyExposed;

    // This filter may allow PTY ports, depending on `mIsPtyExposed`
    @GuardedBy("mLock")
    private Predicate<android.hardware.serialservice.SerialPortInfo> mSerialDeviceFilter;


    private SerialManagerService(Context context) {
        this(context, context.getResources().getStringArray(R.array.config_serialPorts),
                context.getResources().getString(R.string.config_portAccessDialogComponent),
                createSerialDeviceFilterFactory(context),
                () -> android.hardware.serialservice.ISerialManager.Stub.asInterface(
                        ServiceManager.getService(NATIVE_SERIAL_SERVICE_NAME)),
                SerialUserAccessManager::new);
    }

    @VisibleForTesting
    SerialManagerService(Context context, String[] portsInConfig, String dialogComponent,
            SerialDeviceFilterFactory serialDeviceFilterFactory,
            Supplier<android.hardware.serialservice.ISerialManager> nativeServiceSupplier,
            SerialUserAccessManagerFactory accessManagerFactory) {
        mContext = context;
        mDialogComponent = dialogComponent;
        mPortsInConfig = stripDevPrefix(portsInConfig);
        mNativeServiceSupplier = nativeServiceSupplier;
        mSerialDeviceFilterFactory = serialDeviceFilterFactory;
        mAccessManagerFactory = accessManagerFactory;
    }

    private static SerialDeviceFilterFactory createSerialDeviceFilterFactory(Context context) {
        var blockedPorts = context.getResources().getStringArray(R.array.config_blockedSerialPorts);
        var blocklistFiler = new BlockedSerialPortsFilter(stripDevPrefix(blockedPorts));
        return isPtyExposed -> {
            Predicate<android.hardware.serialservice.SerialPortInfo> filter =
                    new SerialDeviceFilter();
            if (isPtyExposed) {
                filter = filter.or(new PtyPortFilter());
            }
            return filter.and(blocklistFiler);
        };
    }


    private static String[] stripDevPrefix(String[] portPaths) {
        if (portPaths.length == 0) {
            return portPaths;
        }

        ArrayList<String> portNames = new ArrayList<>();
        for (int i = 0; i < portPaths.length; ++i) {
            String portPath = portPaths[i];
            if (portPath.startsWith(DEV_DIR_PREFIX)) {
                portNames.add(portPath.substring(DEV_DIR_PREFIX.length()));
            } else {
                Slog.w(TAG, "Skipping port path not under /dev: " + portPath);
            }
        }
        return portNames.toArray(new String[0]);
    }

    @Override
    public List<SerialPortInfo> getSerialPorts() {
        synchronized (mLock) {
            if (!connectToNativeService()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(mSerialPorts.values()));
        }
    }

    @Override
    public void registerSerialPortListener(@NonNull ISerialPortListener listener) {
        synchronized (mLock) {
            connectToNativeService();
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
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    public void grantSerialPortAccess(
            @NonNull String serialPort, int uid, @Nullable IBinder token) {
        mContext.enforceCallingPermission(
                Manifest.permission.MANAGE_SERIAL_PORTS,
                "The caller doesn't have MANAGE_SERIAL_PORTS permission.");
        synchronized (mLock) {
            if (!connectToNativeService()) {
                Slog.w(TAG, "Not able to connect to native service. Not granting port access to "
                        + serialPort);
                return;
            }
            if (!mSerialPorts.containsKey(serialPort)) {
                Slog.w(TAG, "Not granting access to missing port " + serialPort);
                return;
            }

            final @UserIdInt int userId = UserHandle.getUserId(uid);
            final SerialUserAccessManagerInterface accessManager = getOrCreateAccessManager(userId);
            accessManager.grantAccess(serialPort, uid, token);
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    public void revokeSerialPortAccess(
            @NonNull String serialPort, int uid, @Nullable IBinder token) {
        mContext.enforceCallingPermission(
                Manifest.permission.MANAGE_SERIAL_PORTS,
                "The caller doesn't have MANAGE_SERIAL_PORTS permission.");
        synchronized (mLock) {
            // We always allow to revoke access to a port, even if it is unplugged.
            final @UserIdInt int userId = UserHandle.getUserId(uid);
            final SerialUserAccessManagerInterface accessManager = getOrCreateAccessManager(userId);
            accessManager.revokeAccess(serialPort, uid, token);
        }
    }

    @Override
    public void requestOpen(@NonNull String portName, int flags, boolean exclusive,
            @NonNull String packageName, @NonNull ISerialPortResponseCallback callback) {
        final int callingPid = Binder.getCallingPid();
        final int callingUid = Binder.getCallingUid();
        final @UserIdInt int userId = UserHandle.getUserId(callingUid);
        final PackageManagerInternal pm = LocalServices.getService(PackageManagerInternal.class);
        if (!pm.isSameApp(packageName, callingUid, userId)) {
            // We can never allow an app to impersonate any other apps.
            Slog.w(TAG, "Package " + packageName + " doesn't match the calling UID " + callingUid);
            deliverErrorToCallback(callback, ErrorCode.ERROR_ACCESS_DENIED, portName);
            return;
        }

        synchronized (mLock) {
            if (!connectToNativeService()) {
                deliverErrorToCallback(callback, ErrorCode.ERROR_PORT_NOT_FOUND, portName);
                return;
            }
            SerialPortInfo port = mSerialPorts.get(portName);
            if (port == null) {
                deliverErrorToCallback(callback, ErrorCode.ERROR_PORT_NOT_FOUND, portName);
                return;
            }
            final SerialUserAccessManagerInterface accessManager = getOrCreateAccessManager(userId);
            accessManager.requestAccess(portName, callingPid, callingUid, packageName,
                    (resultPort, pid, uid, granted) -> {
                        if (!granted) {
                            deliverErrorToCallback(callback, ErrorCode.ERROR_ACCESS_DENIED,
                                    "User denied " + packageName + " access to " + portName);
                            return;
                        }
                        nativeOpen(port, toOsConstants(flags), exclusive, callback);
                    });
        }
    }

    @GuardedBy("mLock")
    private SerialUserAccessManagerInterface getOrCreateAccessManager(int userId) {
        SerialUserAccessManagerInterface accessManager = mAccessManagerPerUser.get(userId);
        if (accessManager != null) {
            return accessManager;
        }
        accessManager = mAccessManagerFactory.create(mContext, mPortsInConfig, mDialogComponent);
        mAccessManagerPerUser.put(userId, accessManager);
        return accessManager;
    }

    /**
     * Opens the serial port by calling the native serial service.
     */
    private void nativeOpen(SerialPortInfo port, int flags, boolean exclusive,
            @NonNull ISerialPortResponseCallback callback) {
        try (ParcelFileDescriptor pfd = mNativeService.requestOpen(port.getName(), flags,
                exclusive)) {
            deliverResultToCallback(callback, port, pfd);
        } catch (RemoteException | RuntimeException e) {
            deliverErrorToCallback(callback, ErrorCode.ERROR_OPENING_PORT,
                    "Error opening serial port " + port.getName() + ": " + e.getMessage());
        } catch (IOException e) {
            Slog.w(TAG, "Error closing the file descriptor", e);
        }
    }

    private void deliverResultToCallback(@NonNull ISerialPortResponseCallback callback,
            SerialPortInfo port, ParcelFileDescriptor pfd) {
        try {
            callback.onResult(port, pfd);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Error sending result to callback", e);
        }
    }

    private void deliverErrorToCallback(@NonNull ISerialPortResponseCallback callback,
            @ErrorCode int errorCode, String message) {
        try {
            callback.onError(errorCode, message);
        } catch (RemoteException | RuntimeException e) {
            Slog.e(TAG, "Error sending error to callback", e);
        }
    }

    /**
     * Converts the public API flags from {@link android.hardware.serial.SerialPort} to the
     * corresponding {@link OsConstants} flags used for the {@code open(2)} syscall.
     *
     * @param flags A combination of {@code SerialPort.OPEN_FLAG_*} constants.
     * @return The corresponding {@code OsConstants.O_*} flags.
     * @throws IllegalArgumentException if the flags are invalid.
     */
    private static int toOsConstants(int flags) {
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

    /**
     * Connects to the native serial service if not already connected.
     *
     * <p>This method retrieves the native service binder, registers a listener for port
     * connect/disconnect events, and populates the initial list of serial ports.
     *
     * @return {@code true} if the service is connected to the native service and the serial port
     * list is successfully populated/updated, {@code false} otherwise.
     */
    @GuardedBy("mLock")
    private boolean connectToNativeService() {
        if (mIsConnectedToNativeService) {
            return true;
        }
        mNativeService = mNativeServiceSupplier.get();
        if (mNativeService == null) {
            Slog.e(TAG, "Native Serial Service not found");
            return false;
        }
        if (!filterSerialPorts()) {
            return false;
        }
        try {
            mNativeService.registerSerialPortListener(new SerialPortListener());
        } catch (RemoteException e) {
            Slog.e(TAG, "Error registering serial port listener with native service", e);
            return false;
        }
        mIsConnectedToNativeService = true;
        return true;
    }

    /**
     * Filters the list of serial ports obtained from the native service according to current
     * filtering conditions.
     *
     * @return true if this operation succeeded, false otherwise.
     */
    @GuardedBy("mLock")
    private boolean filterSerialPorts() {
        mSerialDeviceFilter = mSerialDeviceFilterFactory.createFilter(mIsPtyExposed);
        try {
            var ports = mNativeService.getSerialPorts();
            for (int i = 0; i < ports.size(); i++) {
                addSerialDevice(ports.get(i));
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Error getting serial ports from native service", e);
            return false;
        }
        return true;
    }

    /**
     * Refreshes the list of available serial ports, to be called when the filtering conditions
     * change.
     *
     * @return true if this operation succeeded, false otherwise.
     */
    @GuardedBy("mLock")
    private boolean refilterSerialPorts() {
        mSerialPorts.clear();
        return mIsConnectedToNativeService ? filterSerialPorts() : connectToNativeService();
    }

    private void addSerialDevice(android.hardware.serialservice.SerialPortInfo info) {
        if (!mSerialDeviceFilter.test(info)) {
            return;
        }

        // Convert from the native service's AIDL type to the framework's AIDL type.
        var port = new SerialPortInfo(info.name, info.vendorId, info.productId);
        synchronized (mLock) {
            if (mSerialPorts.containsKey(port.getName())) {
                return;
            }
            mSerialPorts.put(port.getName(), port);
        }
        Slog.d(TAG, "Added serial device " + port.getName());
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
                mListeners.getBroadcastItem(i).onSerialPortDisconnected(port);
            } catch (RemoteException e) {
                Slog.e(TAG, "Error notifying listener", e);
            }
        }
        mListeners.finishBroadcast();
    }

    /**
     * For CTS tests: expose or hide PTY via adb shell command.
     *
     * Side effect: clean granted access for all users.
     *
     * @return 0 if this operation succeeded, 1 otherwise.
     */
    int setExposePty(boolean isPtyExposed) {
        synchronized (mLock) {
            mAccessManagerPerUser.clear();
            if (mIsPtyExposed == isPtyExposed) {
                return 0;
            }
            mIsPtyExposed = isPtyExposed;
            return refilterSerialPorts() ? 0 : 1;
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) {
            return;
        }
        pw.println("Available ports (" + mSerialPorts.size() + "):");
        for (SerialPortInfo port : mSerialPorts.values()) {
            pw.println("Port " + port.getName() + ":");
            pw.println("   Vendor ID: " + Integer.toHexString(port.getVendorId()));
            pw.println("   Product ID: " + Integer.toHexString(port.getProductId()));
        }
    }

    /**
     * Shell support (for CTS tests).
     */
    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SERIAL_PORTS)
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
        mContext.enforceCallingPermission(Manifest.permission.MANAGE_SERIAL_PORTS,
                "The caller doesn't have MANAGE_SERIAL_PORTS permission.");
        (new SerialShellCommand(this)).exec(this, in, out, err, args, callback, resultReceiver);
    }

    /**
     * Listener for serial port connection events from the native service.
     */
    private class SerialPortListener extends
            android.hardware.serialservice.ISerialPortListener.Stub {

        @RequiresNoPermission
        public void onSerialPortConnected(android.hardware.serialservice.SerialPortInfo info) {
            addSerialDevice(info);
        }

        @RequiresNoPermission
        public void onSerialPortDisconnected(android.hardware.serialservice.SerialPortInfo info) {
            removeSerialDevice(info.name);
        }
    }

    interface SerialUserAccessManagerFactory {
        SerialUserAccessManagerInterface create(Context context, String[] portsInConfig,
                String dialogComponent);
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

    /**
     * Creates a filter for the list of serial devices obtained from the native service.
     */
    interface SerialDeviceFilterFactory {

        /**
         * Create a serial device filter.
         *
         * @param isPtyExposed The value of the system parameter used to expose PTY port for CTS
         *                     tests.
         * @return a predicate for filtering serial devices
         */
        Predicate<android.hardware.serialservice.SerialPortInfo> createFilter(boolean isPtyExposed);
    }
}
