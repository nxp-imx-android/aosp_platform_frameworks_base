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

import static com.android.server.serial.SerialConstants.DEV_DIR;
import static com.android.server.serial.SerialConstants.SYSFS_DIR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Represents device related files and links under /dev and /sys. */
class DeviceFileInfo {

    // Device name
    final String mName;

    // Device Number (`st_rdev` field of `struct stat`)
    final long mNumber;

    // Device info link under /sys
    private Path mSysfsLink;

    // Device subsystem
    private String mSubsystem;

    @VisibleForTesting
    DeviceFileInfo(String name, long number) {
        mName = name;
        mNumber = number;
        mSysfsLink = Paths.get(SYSFS_DIR).resolve(name);
    }

    /** Create from /dev/name (check if /sys/class/tty/name exists). */
    @Nullable
    static DeviceFileInfo fromNameInDev(String name) {
        if (!new File(SYSFS_DIR, name).exists()) {
            return null;
        }
        return fromName(name);
    }

    /** Create from /sys/class/tty/name (check if /dev/name exists). */
    @Nullable
    static DeviceFileInfo fromNameInSysfs(String name) {
        if (!new File(DEV_DIR, name).exists()) {
            return null;
        }
        return fromName(name);
    }

    @Nullable
    private static DeviceFileInfo fromName(String name) {
        try {
            StructStat stat = Os.stat(DEV_DIR + "/" + name);
            DeviceFileInfo device = new DeviceFileInfo(name, stat.st_rdev);
            if (device.getSubsystem().equals("platform")) {
                // Keep built-in UARTs hidden (for security reasons).
                return null;
            }
            return device;
        } catch (ErrnoException | IOException e) {
            return null;
        }
    }

    boolean isUsb() {
        try {
            return switch (getSubsystem()) {
                case "usb", "usb-serial" -> true;
                default -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    private String getSubsystem() throws IOException {
        if (mSubsystem == null) {
            // E.g. /sys/class/tty/ttyACM0/device/subsystem -> .../bus/usb
            Path subsystemLink = getSysfsLink().resolve("device/subsystem");
            if (!Files.exists(subsystemLink)) {
                mSubsystem = "virtual";
            }
            mSubsystem = subsystemLink.toRealPath().getFileName().toString();
        }
        return mSubsystem;
    }

    /** Device info link under /sys. */
    Path getSysfsLink() {
        return mSysfsLink;
    }

    @VisibleForTesting
    void setSysfsLink(Path sysfsLink) {
        mSysfsLink = sysfsLink;
    }

    @VisibleForTesting
    void setSubsystem(String subsystem) {
        mSubsystem = subsystem;
    }
}
