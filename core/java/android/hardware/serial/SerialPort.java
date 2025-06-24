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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * A class representing a Serial port.
 */
@FlaggedApi(android.hardware.serial.flags.Flags.FLAG_ENABLE_SERIAL_API)
public final class SerialPort {
    /**
     * Value returned by {@link #getVendorId()} and {@link #getProductId()} if this
     * serial port isn't a USB device.
     */
    public static final int INVALID_ID = -1;

    private final @NonNull SerialPortInfo mInfo;
    private final @NonNull ISerialManager mService;

    /** @hide */
    @VisibleForTesting
    public SerialPort(@NonNull SerialPortInfo info, @NonNull ISerialManager service) {
        mInfo = info;
        mService = service;
    }

    /**
     * Get the device name. It is the dev node name under /dev, e.g. ttyUSB0, ttyACM1.
     */
    public @NonNull String getName() {
        return mInfo.getName();
    }

    /**
     * Return the vendor ID of this serial port if it is a USB device. Otherwise, it
     * returns {@link #INVALID_ID}.
     */
    public int getVendorId() {
        return mInfo.getVendorId();
    }

    /**
     * Return the product ID of this serial port if it is a USB device. Otherwise, it
     * returns {@link #INVALID_ID}.
     */
    public int getProductId() {
        return mInfo.getProductId();
    }

    /**
     * Request to open the port. The flags must set
     * {@link android.system.OsConstants#O_NOCTTY}.
     *
     * Exceptions passed to {@code receiver} may be
     * <ul>
     * <li> {@link ErrnoException} with ENOENT if the port is detached or any syscall to open the
     * port fails that come with an errno</li>
     * <li> {@link IOException} if other required operations fail that don't come with errno</li>
     * <li> {@link SecurityException} if the user rejects the open request</li>
     * </ul>
     *
     * @param flags     flags passed to open(2)
     * @param exclusive whether the app needs exclusive access with TIOCEXCL(2const)
     * @param executor  the executor used to run receiver
     * @param receiver  the outcome receiver
     * @throws IllegalArgumentException if the flags doesn't set {@link OsConstants#O_NOCTTY}, or
     *                                  any other parameters are {@code null}.
     */
    public void requestOpen(int flags, boolean exclusive, @NonNull Executor executor,
            @NonNull OutcomeReceiver<SerialPortResponse, Exception> receiver) {
        if ((flags & OsConstants.O_NOCTTY) == 0) {
            throw new IllegalArgumentException("The flags must set OsConstants.O_NOCTTY");
        }
        Objects.requireNonNull(executor, "Executor must not be null");
        Objects.requireNonNull(receiver, "Receiver must not be null");
        try {
            mService.requestOpen(mInfo.getName(), flags, exclusive,
                    new SerialPortResponseCallback(executor, receiver));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private class SerialPortResponseCallback extends ISerialPortResponseCallback.Stub {

        private final @NonNull Executor mExecutor;
        private final @NonNull OutcomeReceiver<SerialPortResponse, Exception> mReceiver;

        private SerialPortResponseCallback(@NonNull Executor executor,
                @NonNull OutcomeReceiver<SerialPortResponse, Exception> receiver) {
            mExecutor = executor;
            mReceiver = receiver;
        }

        @Override
        public void onResult(SerialPortInfo info, ParcelFileDescriptor fileDescriptor) {
            mExecutor.execute(() -> mReceiver.onResult(
                    new SerialPortResponse(SerialPort.this, fileDescriptor)));
        }

        @Override
        public void onError(@ErrorCode int errorCode, int errno, String message) {
            mExecutor.execute(() -> mReceiver.onError(getException(errorCode, errno, message)));
        }

        @NonNull
        private static Exception getException(int errorCode, int errno, String message) {
            return switch (errorCode) {
                case ErrorCode.ERROR_READING_DRIVERS -> new IOException(message);
                case ErrorCode.ERROR_PORT_NOT_FOUND -> new ErrnoException(message,
                        OsConstants.ENOENT);
                case ErrorCode.ERROR_OPENING_PORT -> new ErrnoException(message, errno);
                default -> new IllegalStateException("Unexpected errorCode " + errorCode);
            };
        }
    }
}
