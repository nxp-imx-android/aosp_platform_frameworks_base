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

package android.app.memory.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

@RunWith(Parameterized.class)
public class AppMemoryTest {

    private static final String TAG = "AppMemoryTest";

    // The well-known test directory.
    private final String mRootPath = "/data/local/tmp/appmemorytest/";

    // The helper application APK, located in the test directory.
    private static final String HELPER_APK = "AppMemoryTest_Helper.apk";

    // The application that boots and then generates its own heap dump.
    private static final String HELPER = "android.app.memory.testhelper";

    private static String runShellCommandWithResult(String cmd) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd);
        try (var result = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] response = new byte[100];
            int len = result.read(response);
            return new String(response, 0, len);
        } catch (IOException e) {
            // Ignore the exception.
        }
        return null;
    }

    private static void uninstallHelper() {
        // The result string is not important but the pm executable will fail if the result is not
        // consumed.
        String r = runShellCommandWithResult("pm uninstall " + HELPER);
        Log.i(TAG, "uninstalled " + HELPER + ": " + r);
    }

    @BeforeClass
    public static void setUpForTest() {
        // Ensure that the helper app is uninstalled before the first test runs.  Note that the
        // app will normally not be installed, so the command will log an error into logcat;
        // ignore the logcat - it is expected.
        uninstallHelper();
    }

    @Before
    public void setUp() {
        final String apk = mRootPath + HELPER_APK;
        File apkFile = new File(apk);
        assertTrue("apk not found", apkFile.exists());
        // The result string is not important but the pm executable will fail if the result is not
        // consumed.
        String r = runShellCommandWithResult("pm install -t " + apk);
        Log.i(TAG, "installed " + apk + ": " + r);
    }

    @After
    public void tearDown() {
        uninstallHelper();
    }

    // How long the process should wait before triggering a heap dump.  This delay should be long
    // enough that all allocations have completed.
    private static final long DELAY_MS = 10 * 1000;

    // Set the variable true to enable a calibration run, which repeats the test with a series of
    // ever larger allocations in the app.  The results should reflect the extra allocations quite
    // closely.
    private static boolean sCalibration = false;

    // The interesting parameter is the "extra-size"
    @Parameter(0)
    public int mExtraSize;

    /**
     * Return the parameter list for the run.  The result depends on sCalibration: if it is false,
     * run a baseline test (no extra memory).  If it is true, then run a sequence of tests with
     * increasing extra allocations.
     */
    @Parameters(name = "extra-size: {0}")
    public static Iterable<? extends Object> data() {
        if (sCalibration) {
            return Arrays.asList(new Object[][]{ {0}, {1}, {256}, {512}, {1024}, {4096} });
        } else {
            return Arrays.asList(new Object[][]{ {0} });
        }
    }

    // Launch the test app with the specified parameters and create one heap profile.  Metrics are
    // extracted and reported.
    private void generateOneProfile(long delay, int extra) throws Exception {
        // Compute the suffix for this particular test.  The suffix is just the amount of extra
        // memory divided by 1024.
        final String suffix = String.format("-%04d", extra / 1024);

        final String path = mRootPath + "jheap" + suffix;

        // Where the dump will be found.  First verify that it is not present.
        final File profile = new File(path + ".hprof");
        assertFalse(profile.exists());

        String cmd = new String("am start-activity -S --track-allocation ")
                     + String.format("-n %s/.EmptyActivity ", HELPER)
                     + String.format("--es dump-path %s ", path)
                     + String.format("--el soak-delay %d ", delay)
                     + String.format("--ei extra-size %d", extra);

        Log.i(TAG, "starting helper activity");
        Log.i(TAG, cmd);
        runShellCommandWithResult(cmd);

        Log.i(TAG, "waiting for helper activity");
        // Wait up to 120 seconds for the profile to be created.
        for (int i = 0; i < 120; i++) {
            if (profile.exists() && profile.canRead()) break;
            Thread.sleep(1000);
        }

        // Now verify that the dump was created and is readable.
        assertTrue("heap profile does not exist", profile.exists());
        assertTrue("heap profile is not readable", profile.canRead());

        // Read the summary file.  Let exceptions roll up and fail the test.
        HashMap<String, String> appStats = new HashMap<>();
        try (BufferedReader ifile = new BufferedReader(new FileReader(path + ".stats"))) {
            String line;
            while ((line = ifile.readLine()) != null) {
                String[] keyval = line.split(" ");
                if (keyval.length == 2) {
                    appStats.put(keyval[0], keyval[1]);
                }
            }
        }

        // Extract metrics and report them.
        Profile p = new Profile(profile);
        final long p_allocated = p.size();
        final long d_allocated = Long.parseLong(appStats.get("art.gc.bytes-allocated"));
        // Log the sizes for debugging purposes.
        Log.i(TAG, String.format("allocated p:%d d:%d", p_allocated, d_allocated));

        try (FileWriter ofile = new FileWriter(path + ".stats", true)) {
            ofile.write("profile.heap " + p_allocated + "\n");
        }

        // Send metrics to the automation system.
        Bundle stats = new Bundle();
        String key;
        key = "PSize" + suffix;
        stats.putLong(key, p_allocated);
        stats.putString(Instrumentation.REPORT_KEY_STREAMRESULT, key + ": " + p_allocated);
        key = "DSize" + suffix;
        stats.putLong(key, d_allocated);
        stats.putString(Instrumentation.REPORT_KEY_STREAMRESULT, key + ": " + d_allocated);
        InstrumentationRegistry.getInstrumentation().sendStatus(Activity.RESULT_OK, stats);
    }

    // Test the basic app with the specified extra memory.
    @Test
    public void testApp() throws Exception {
        generateOneProfile(DELAY_MS, mExtraSize * 1024);
    }
}
