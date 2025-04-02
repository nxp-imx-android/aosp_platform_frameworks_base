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

import android.os.FileUtils;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SerialDriversDiscovery {

    private static final String TAG = SerialDriversDiscovery.class.getSimpleName();
    private static final String PROC_TTY_DRIVERS_FILE = "/proc/tty/drivers";
    private static final String SERIAL_DRIVER_TYPE = "serial";

    private final String mDriversFile;

    SerialDriversDiscovery() {
        this(PROC_TTY_DRIVERS_FILE);
    }

    @VisibleForTesting
    SerialDriversDiscovery(String driversFile) {
        mDriversFile = driversFile;
    }

    List<SerialTtyDriverInfo> discover() throws IOException {
        String[] entries = readDriverFile();
        List<SerialTtyDriverInfo> driversList = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i];
            Optional<SerialTtyDriverInfo> driver = parseDriverInfoIfSerial(entry);
            if (driver.isPresent()) {
                driversList.add(driver.get());
            }
        }
        return driversList;
    }

    private String[] readDriverFile() throws IOException {
        File f = new File(mDriversFile);
        String fileContent = FileUtils.readTextFile(f, 0, null);
        return fileContent.split("\n");
    }

    private static Optional<SerialTtyDriverInfo> parseDriverInfoIfSerial(String entry) {
        // e.g.: entry="usbserial    /dev/ttyUSB   188    0-254    serial"
        String[] parts = entry.split("\\s+");
        if (parts.length != 5) {
            Slog.e(TAG, TextUtils.formatSimple("Failed parsing line in '%s'", entry));
            return Optional.empty();
        }

        if (!SERIAL_DRIVER_TYPE.equals(parts[4])) {
            // not a serial driver
            return Optional.empty();
        }

        try {
            String path = parts[1];
            int majorNumber = Integer.parseInt(parts[2]);
            String[] minorSplit = parts[3].split("-");
            int minorFrom = Integer.parseInt(minorSplit[0]);
            int minorTo = minorSplit.length > 1 ? Integer.parseInt(minorSplit[1]) : minorFrom;
            return Optional.of(new SerialTtyDriverInfo(path, majorNumber, minorFrom, minorTo));
        } catch (NumberFormatException e) {
            Slog.e(TAG, TextUtils.formatSimple("Failed paring number in '%s'", entry));
            return Optional.empty();
        }
    }
}
