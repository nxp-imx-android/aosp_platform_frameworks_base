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
package android.os;

import android.platform.test.ravenwood.RavenwoodEnvironment;
import android.platform.test.ravenwood.RavenwoodErrorHandler;

import java.io.PrintStream;

/**
 * Redirection target class from {@link MessageQueue}.
 *
 * TODO: Keep track of all sync barriers
 */
public class MessageQueue_ravenwood {
    private MessageQueue_ravenwood() {
    }

    private static boolean targetsAtLeast(int sdkVersion) {
        return RavenwoodEnvironment.getInstance().getTargetSdkLevel() >= sdkVersion;
    }

    /**
     * Used by the "combined" version.
     */
    static boolean computeUseConcurrent() {
        // On Ravenwood, @ChangeIds are not yet ready when this method is called,
        // so manually check the test's target SDK version.
        var def = targetsAtLeast(android.os.Build.VERSION_CODES.BAKLAVA);

        // Use "ravenwood.prop" to explicitly enable/disable for a specific test.
        return SystemProperties.getBoolean(
                "ravenwood.android.os.MessageQueue.useConcurrent", def);
    }

    /**
     * Used by the "combineddeli" version.
     */
    static boolean computeUseDeliQueue() {
        // On Ravenwood, @ChangeIds are not yet ready when this method is called,
        // so manually check the test's target SDK version.
        var def = targetsAtLeast(android.os.Build.VERSION_CODES.BAKLAVA);

        // Use "ravenwood.prop" to explicitly enable/disable for a specific test.
        return SystemProperties.getBoolean(
                "ravenwood.android.os.MessageQueue.useDeliQueue", def);
    }

    static void onResetForTestCalled() {
        RavenwoodErrorHandler.onWarningDetected("MessageQueue.resetForTest() called!");
    }

    public static void dumpSyncBarriers(PrintStream out) {
        // TODO: Implement it
    }
}

