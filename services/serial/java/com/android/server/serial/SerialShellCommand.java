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

import android.os.ShellCommand;

import java.io.PrintWriter;

/**
 * Handles "adb shell cmd serial expose-pty" and "adb shell cmd serial hide-pty".
 */
class SerialShellCommand extends ShellCommand {
    private final SerialManagerService mSerialManagerService;

    SerialShellCommand(SerialManagerService serialManagerService) {
        mSerialManagerService = serialManagerService;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        int result = switch (cmd) {
            case "expose-pty" -> mSerialManagerService.setExposePty(true);
            case "hide-pty" -> mSerialManagerService.setExposePty(false);
            default -> handleDefaultCommands(cmd);
        };
        getOutPrintWriter().println(result == 0 ? "Success" : "Failed");
        return result;
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("cmd serial expose-pty");
        pw.println("    Expose the PTY port in the list of available serial ports");
        pw.println();
        pw.println("cmd serial hide-pty");
        pw.println("    Hide the PTY port from the list of available serial ports");
        pw.println();
    }
}
