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

import static com.android.server.serial.SerialConstants.SYSFS_DIR;

import android.annotation.NonNull;
import android.hardware.serial.SerialPortInfo;
import android.os.FileUtils;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class SerialDevicesEnumerator {
    private static final String TAG = "SerialDevicesEnumerator";

    @NonNull
    List<SerialPortInfo> enumerate(List<SerialTtyDriverInfo> drivers) {
        List<DeviceFileInfo> devices = getDevices();
        List<DeviceFileInfo> serialDevices = filterDevicesHavingSerialDrivers(devices, drivers);
        return readDeviceDetails(serialDevices);
    }

    @NonNull
    private List<DeviceFileInfo> getDevices() {
        File[] deviceFiles = new File(SYSFS_DIR).listFiles();
        List<DeviceFileInfo> result = new ArrayList<>(deviceFiles.length);
        for (int i = 0; i < deviceFiles.length; i++) {
            String name = deviceFiles[i].getName();
            DeviceFileInfo device = DeviceFileInfo.fromNameInSysfs(name);
            if (device != null) {
                result.add(device);
            }
        }
        return result;
    }

    @VisibleForTesting
    @NonNull
    List<DeviceFileInfo> filterDevicesHavingSerialDrivers(List<DeviceFileInfo> devices,
            List<SerialTtyDriverInfo> drivers) {
        List<DeviceFileInfo> result = new ArrayList<>(devices.size());
        for (int i = 0; i < devices.size(); i++) {
            DeviceFileInfo device = devices.get(i);
            if (matchesSerialDriver(device, drivers)) {
                result.add(device);
            }
        }
        return result;
    }

    @NonNull
    private List<SerialPortInfo> readDeviceDetails(List<DeviceFileInfo> devices) {
        List<SerialPortInfo> result = new ArrayList<>(devices.size());
        for (int i = 0; i < devices.size(); i++) {
            DeviceFileInfo device = devices.get(i);
            if (device.isUsb()) {
                SerialPortInfo serialPort = readSerialPortInfo(device);
                result.add(serialPort);
            }
        }
        return result;
    }

    @NonNull
    static SerialPortInfo readSerialPortInfo(DeviceFileInfo device) {
        int vendorId = -1;
        int productId = -1;
        if (device.isUsb()) {
            // Check each parent folder until we find idVendor file,
            // e.g. from /sys/devices/pci0000:00/0000:00:14.0/usb3/3-8/3-8:1.1/tty/ttyACM0
            // to /sys/devices/pci0000:00/0000:00:14.0/usb3/3-8
            try {
                Path fromDir = device.getSysfsLink().toRealPath().getParent();
                for (Path dir = fromDir; dir != null; dir = dir.getParent()) {
                    vendorId = readHexFile(dir.resolve("idVendor"));
                    if (vendorId >= 0) {
                        productId = readHexFile(dir.resolve("idProduct"));
                        break;
                    }
                }
            } catch (IOException e) {
                Slog.e(TAG, "Failed to follow the link " + device.getSysfsLink(), e);
            }
        }
        return new SerialPortInfo(device.mName, vendorId, productId);
    }

    private static int readHexFile(Path file) {
        if (!Files.exists(file)) {
            return -1;
        }
        try {
            String content = FileUtils.readTextFile(file.toFile(), 0, null);
            return Integer.parseInt(content.trim(), 16);
        } catch (IOException | NumberFormatException e) {
            Slog.e(TAG, "Failed to read hex file " + file, e);
            return -1;
        }
    }

    /** Check if major and minor device numbers match one of serial drivers. */
    boolean matchesSerialDriver(DeviceFileInfo device, List<SerialTtyDriverInfo> drivers) {
        int major = major(device.mNumber);
        int minor = minor(device.mNumber);
        for (int i = 0; i < drivers.size(); i++) {
            SerialTtyDriverInfo driver = drivers.get(i);
            if (major == driver.mMajorNumber && minor >= driver.mMinorNumberFrom
                    && minor <= driver.mMinorNumberTo) {
                return true;
            }
        }
        return false;
    }

    /**
     * Based on MAJOR() from kdev_t.h
     */
    private static int major(long dev) {
        return (int) (dev >>> 8);
    }

    /**
     * Based on MINOR() from kdev_t.h
     */
    private static int minor(long dev) {
        return (int) (dev & 0xff);
    }

}
