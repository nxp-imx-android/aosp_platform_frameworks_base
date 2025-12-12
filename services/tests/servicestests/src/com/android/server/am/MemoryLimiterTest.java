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

package com.android.server.am;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Build/Install/Run:
 *  atest FrameworksServicesTests:MemoryLimiterTest
 */
@SmallTest
@Presubmit
public class MemoryLimiterTest {

    private static final String TAG = "MemoryLimiterTest";

    @Rule
    public final CheckFlagsRule mFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Our context, used for sending broadcasts.
    private Context mContext = ApplicationProvider.getApplicationContext();

    // The test application package and activity name.
    // LINT.IfChange(testapp)
    private static final String HELPER =
            "com.android.servicestests.apps.memorylimitertestapp";
    private static final String ACTIVITY = "TestActivity";
    // LINT.ThenChange(
    //   /services/tests/servicestests/test-apps/MemoryLimiterTestApp/AndroidManifest.xml:testapp
    // )

    private static String shellCommand(String cmd) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd);
        try (var result = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            return new String(result.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Error running command: '" + cmd + "'", e);
        }
        return null;
    }

    // A convenience constant: 1MB.
    static final int MEG = 1024 * 1024;

    // The mapping between process states and sizes is arbitrary.  Constants are declared to
    // make it more obvious in the test routine just what the memory limit will be.
    @ProcessState
    private static final int PROCESS_STATE_100M = ActivityManager.PROCESS_STATE_TOP;

    // The memory assigned to a process in PROCESS_STATE_100M.
    private static final Long sProcessMemory100M = (long) (100 * MEG);

    // Capture over-limit events.
    private static class EventCounter extends MemoryLimiter.ControllerEnabled {
        private final CountDownLatch mLatch;

        // The instance is created with the expected number of events.
        EventCounter(int expected) {
            super();
            mLatch = new CountDownLatch(expected);
        }

        // Wait for the counter to go to zero within timeout seconds.
        boolean await(long timeout) {
            try {
                return mLatch.await(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "exception while waiting for event", e);
                return false;
            }
        }

        // Get the memory limit for the process state.  Use one of the process states above.
        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return switch (newState) {
                case PROCESS_STATE_100M -> sProcessMemory100M;
                default -> {
                    fail("invalid state for testing: " + newState);
                    yield null;
                }
            };
        }

        // Capture an actual event.
        @Override
        public void onLimitExceeded(int pid, int uid, int limit) {
            Log.i(TAG, String.format("onLimitExceeded(%d, %d, %d)", pid, uid, limit));
            mLatch.countDown();
        }
    }

    // Remember the SELinux state before the test started, so it can be restored afterwards.
    static boolean sSELinuxEnforced = false;

    @BeforeClass
    public static void setUpClass() throws Exception {
        String status = shellCommand("getenforce");
        sSELinuxEnforced = status.startsWith("Enforcing");
        if (sSELinuxEnforced) {
            Log.i(TAG, "disabling selinux");
            shellCommand("setenforce 0");
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (sSELinuxEnforced) {
            Log.i(TAG, "enabling selinux");
            shellCommand("setenforce 1");
        }
    }

    @After
    public void tearDown() {
        shellCommand("am force-stop " + HELPER);
    }

    // Fetch the pid of the helper application.
    private int getHelperPid() throws NumberFormatException {
        String pid = shellCommand("pidof " + HELPER).trim();
        assertNotNull("Could not get PID for package " + HELPER, pid);
        return Integer.parseInt(pid);
    }

    // Fetch the uid of the helper application.
    private int getHelperUid() throws PackageManager.NameNotFoundException {
        PackageManager pm = mContext.getPackageManager();
        return pm.getApplicationInfo(HELPER, 0).uid;
    }

    // Prepare the helper group by allowing world access to the cgroup tree for the helper
    // UID.  The UID is specific to the helper package, so there is no impact to any other
    // functions on the system.  This must be done every time the helper app is started because
    // the cgroup path disappears if there are no processes with the UID.
    private int prepareHelperCgroup(int pid, int uid) throws Exception {
        String path = String.format("/sys/fs/cgroup/apps/uid_%d", uid);
        String r = shellCommand("chmod -R a+rw " + path);
        if (r != null && !r.trim().equals("")) {
            Log.i(TAG, "unprotecting " + path + ": \"" + r + "\"");
            fail("failed to prepare cgroup");
        }
        return currentMemory(pid, uid);
    }

    // Return the current memory of the helper app, as reported by memcg.
    private int currentMemory(int pid, int uid) throws Exception {
        String path = String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d/memory.current", uid, pid);
        Path filePath = Paths.get(path);
        // Always specify the Charset, e.g., UTF_8.  This may throw: allow the test to fail.
        return Integer.parseInt(Files.readString(filePath, StandardCharsets.UTF_8).trim());
    }

    // Send a request to the application to change its memory.
    private void appCommand(int size) {
        final Intent intent = new Intent(); // SendActivity.this, SendActivity.class);
        intent.setAction(HELPER + "MEMORY");
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra("size", Integer.toString(size));
        mContext.sendBroadcast(intent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiter() throws Exception {
        EventCounter counter = new EventCounter(1);
        try (MemoryLimiter controller = new MemoryLimiter(counter)) {
            MemoryLimiter.Limiter limiter = controller.newLimiter();

            // -S stops any existing activity before starting new, ensuring cold start
            String cmd = String.format("am start-activity -S -W -n %s/.%s", HELPER, ACTIVITY);
            shellCommand(cmd);

            int pid = getHelperPid();
            int uid = getHelperUid();
            Log.i(TAG, String.format("helper at pid=%d uid=%d", pid, uid));

            // Prepare the cgroup for testing.
            prepareHelperCgroup(pid, uid);

            // Set the limit and wait for the over-limit event.
            limiter.setPidUid(pid, uid);
            limiter.onProcStateUpdated(PROCESS_STATE_100M);

            // Grow the app by 100M.
            appCommand(100);
            assertTrue(counter.await(10));
        }
    }

    static {
        System.loadLibrary("servicestestjni");
    }
}
