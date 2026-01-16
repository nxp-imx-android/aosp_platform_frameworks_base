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

import static com.google.common.truth.Truth.assertThat;

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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.am.MemoryLimiter.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build/Install/Run:
 *  atest MemoryLimiterTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class MemoryLimiterTest {

    private static final String TAG = "MemoryLimiterTest";

    @Rule
    public final CheckFlagsRule mFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Our context, used for sending broadcasts.
    private Context mContext = ApplicationProvider.getApplicationContext();

    // The test application package and activity name.
    // LINT.IfChange(testapp)
    private static final String HELPER =
            "com.android.memorylimitertests.apps.memorylimitertestapp";
    private static final String ACTIVITY = "TestActivity";
    // LINT.ThenChange(
    // /services/tests/MemoryLimiterTests/test-app/MemoryLimiterTestApp/AndroidManifest.xml:testapp
    // )

    // The location of data files on the device.
    private static final String DATA_DIR = "/data/local/tmp/cts/memorylimiter/";

    // The UID of the test application.  This can change every time the package is installed but
    // should not change for the duration of the test.
    private int mUid;

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
    private static final int PROCESS_STATE_100M = ActivityManager.PROCESS_STATE_SERVICE;
    @ProcessState
    private static final int PROCESS_STATE_10M = ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND;
    @ProcessState
    private static final int PROCESS_STATE_MAX = ActivityManager.PROCESS_STATE_TOP;

    // The memory assigned to a process in PROCESS_STATE_10M.
    private static final Long sProcessMemory10M = (long) (10 * MEG);

    // The memory assigned to a process in PROCESS_STATE_100M.
    private static final Long sProcessMemory100M = (long) (100 * MEG);

    // The memory assigned to a process in PROCESS_STATE_MAX.  Any negative value turns into
    // maximum memory in the native handlers, but the test uses -1 for consistency.
    private static final Long sProcessMemoryMax = (long) (-1);

    // Capture over-limit events.
    private static class EventCounter extends MemoryLimiter.ControllerEnabled {
        // A lock that ensures the countdown latch and the event count are consistent.
        private final Object mLock = new Object();

        // A countdown latch that tests can wait on.  This is atomic so that changes to attribute
        // made in the test thread are visible in the callback thread.
        private final CountDownLatch mLatch;

        // A single over-limit event.
        record Event(int pid, int uid, int limit, String pkg) {}

        // The events received by this counter.
        final ArrayList<Event> mEvents = new ArrayList<>();

        // The instance is created with the expected number of events.  Do not load any
        // configuration file.
        EventCounter(int expected) {
            this(expected, null);
        }

        // The instance is created with the expected number of events.  The supplied configuration
        // file is parsed.  To simplify life, this method accepts the basename of the
        // configuration file.  It quietly prepends the path component.
        EventCounter(int expected, String config) {
            super((config != null) ? DATA_DIR + config : null);
            mLatch = new CountDownLatch(expected);
        }

        // Wait for the counter to go to zero within timeout seconds.  This cannot take the lock!
        boolean await(long timeout) {
            try {
                return mLatch.await(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "exception while waiting for event", e);
                return false;
            }
        }

        // Return the total number of events ever received by this object.
        int eventCount() {
            synchronized (mLock) {
                return mEvents.size();
            }
        }

        // Match the n'th event.  Fail the test if there is no match.
        void expect(int index, int pid, int uid, int limit, String pkg) {
            final Event event = mEvents.get(index);
            assertThat(event.pid).isEqualTo(pid);
            assertThat(event.uid).isEqualTo(uid);
            assertThat(event.limit).isEqualTo(limit);
            assertThat(event.pkg).isEqualTo(pkg);
        }

        // Get the memory limit for the process state.  Use one of the process states above.
        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return switch (newState) {
                case PROCESS_STATE_10M -> sProcessMemory10M;
                case PROCESS_STATE_100M -> sProcessMemory100M;
                case PROCESS_STATE_MAX -> sProcessMemoryMax;
                default -> {
                    fail("invalid state for testing: " + newState);
                    yield null;
                }
            };
        }

        // Capture an actual event.
        @Override
        public void onLimitExceeded(int pid, int uid, int type, long limit, String pkg) {
            synchronized (mLock) {
                mLatch.countDown();
                mEvents.add(new Event(pid, uid, type, pkg));
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mUid = getHelperUid();
        blockSystemLimiter(mUid, true);
    }

    @After
    public void tearDown() throws Exception {
        shellCommand("am force-stop " + HELPER);
        blockSystemLimiter(mUid, false);
    }

    // Fetch the pid of the helper application.
    private static int getHelperPid() throws NumberFormatException {
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
    private static long prepareHelperCgroup(int pid, int uid) throws Exception {
        String path = String.format("/sys/fs/cgroup/apps/uid_%d", uid);
        String r = shellCommand("chmod -R a+rw " + path);
        if (r != null && !r.trim().equals("")) {
            Log.i(TAG, "unprotecting " + path + ": \"" + r + "\"");
            fail("failed to prepare cgroup");
        }
        return currentMemory(pid, uid);
    }

    // Prepare the helper app.  Start the process, get its pid, and prepare the cgroup.  Then
    // return the pid.
    private static int prepareHelper(int uid) throws Exception {
        // -S stops any existing activity before starting new, ensuring cold start
        final String cmd = String.format("am start-activity -S -W -n %s/.%s", HELPER, ACTIVITY);
        shellCommand(cmd);

        int pid = getHelperPid();
        Log.i(TAG, String.format("helper at pid=%d uid=%d", pid, uid));

        // Prepare the cgroup for testing.
        prepareHelperCgroup(pid, uid);
        return pid;
    }

    // Disable the system MemoryLimiter for the UID under test.
    private static void blockSystemLimiter(int uid, boolean blocked) throws Exception {
        shellCommand("am memory-limiter ignore " + ((blocked) ? Integer.toString(uid) : "none"));
    }

    // Return the current memory of the helper app, as reported by memcg.
    private static long currentMemory(int pid, int uid) throws Exception {
        String path = String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d/memory.current", uid, pid);
        Path filePath = Paths.get(path);
        // Always specify the Charset, e.g., UTF_8.  This may throw: allow the test to fail.
        String value = Files.readString(filePath, StandardCharsets.UTF_8).trim();

        return Long.parseLong(value);
    }

    // Return the current memory.high limit of the helper app, as reported by memcg.
    private static long currentLimit(int pid, int uid) throws Exception {
        String path = String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d/memory.high", uid, pid);
        Path filePath = Paths.get(path);

        // Always specify the Charset, e.g., UTF_8.  This may throw: allow the test to fail.
        String value = Files.readString(filePath, StandardCharsets.UTF_8).trim();

        // The limit can be the string "max"; if so, return -1.  Otherwise, the string must be a
        // valid integer.
        if (value.equals("max")) {
            return sProcessMemoryMax;
        } else {
            return Long.parseLong(value);
        }
    }

    // Return the current value for high events.
    private static long currentEvents(int pid, int uid) throws Exception {
        String path = String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d/memory.events", uid, pid);
        Path filePath = Paths.get(path);

        // The system resets the protections of the memory.events file every time it changes.
        prepareHelperCgroup(pid, uid);

        // Always specify the Charset, e.g., UTF_8.  This may throw: allow the test to fail.
        final String value = Files.readString(filePath, StandardCharsets.UTF_8).trim();

        Matcher high = Pattern.compile("high (\\d+)").matcher(value);
        if (high.find()) {
            return Integer.parseInt(high.group(1));
        } else {
            throw new IllegalArgumentException("bad memory.events");
        }
    }

    // Send a request to the application to change its memory.  The size has unit MB.
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
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter(HELPER);

                int pid = prepareHelper(mUid);
                limiter.setUid(mUid);
                limiter.setPid(pid);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                appCommand(100);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, pid, mUid, MemoryLimiter.MEMORY_LIMIT_TYPE, HELPER);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiterAfter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter(HELPER);

                int pid = prepareHelper(mUid);
                limiter.setUid(mUid);
                limiter.setPid(pid);

                // Grow the app by 100M, set the limit, and wait for the over-limit event.
                appCommand(100);
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, pid, mUid, MemoryLimiter.MEMORY_LIMIT_TYPE, HELPER);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testNullPackage() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter(null);

                int pid = prepareHelper(mUid);
                limiter.setUid(mUid);
                limiter.setPid(pid);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                appCommand(100);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, pid, mUid, MemoryLimiter.MEMORY_LIMIT_TYPE, null);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testEmptyPackage() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter("");

                int pid = prepareHelper(mUid);
                limiter.setUid(mUid);
                limiter.setPid(pid);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                appCommand(100);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, pid, mUid, MemoryLimiter.MEMORY_LIMIT_TYPE, "");
            }
        }
    }

    // Compare the two fields of a Configuration to the inputs.
    private static void testConfig(Configuration cfg, int visible, int notVisible) {
        assertThat(cfg.visible()).isEqualTo(visible);
        assertThat(cfg.notVisible()).isEqualTo(notVisible);
    }

    private static void testConfig(Configuration cfg, Configuration ref) {
        testConfig(cfg, ref.visible(), ref.notVisible());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testConfigDefaults() throws Exception {
        // Fetch the default configuration and verify its fields.
        Configuration cfg = MemoryLimiter.sDefaultConfig;
        if (Flags.memoryLimiterDefaultAppLimits()) {
            testConfig(cfg, 50, 25);
        } else {
            testConfig(cfg, 100, 100);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testXmlConfig() throws Exception {
        Configuration cfg;

        // The default case.
        cfg = MemoryLimiter.getConfiguration(null);
        testConfig(cfg, MemoryLimiter.sDefaultConfig);

        // A valid configuration file that specifies the defaults.
        cfg = MemoryLimiter.getConfiguration(DATA_DIR + "config-default.xml");
        testConfig(cfg, 40, 30);

        // Parse an invalid XML file.  There must be an error.
        try {
            cfg = MemoryLimiter.getConfiguration(DATA_DIR + "config-error.xml");
            fail("failed to detect XML parse error");
        } catch (IllegalArgumentException e) {
            // Success.
        }
    }

    /**
     * This test starts the test application but instead of taking control from ActivityManager,
     * it verifies that the application's limits are set as expected. The test assumes that the
     * test app is not moved out of a foreground (visible) procstate before the memory limit is
     * checked.
     */
    @RequiresFlagsEnabled({Flags.FLAG_MEMORY_LIMITER_ENABLE,
            Flags.FLAG_MEMORY_LIMITER_DEFAULT_APP_LIMITS})
    @Test
    public void testOperation() throws Exception {
        // Use the default "enabled" controller to fetch the limit.  There is no need for a full
        // MemoryLimiter.
        final long expectedLimit;
        try (MemoryLimiter.Controller controller = new MemoryLimiter.ControllerEnabled()) {
            expectedLimit = controller.getStateLimit(ActivityManager.PROCESS_STATE_TOP);
        }

        // Start by enabling system_server control over the app.
        blockSystemLimiter(mUid, false);

        int pid = prepareHelper(mUid);

        // Poll until the limit is set by system_server.
        for (int i = 0; i < 100 && currentLimit(pid, mUid) != expectedLimit; i++) {
            Thread.sleep(100); // Wait a bit before polling again.
        }
        assertThat(currentLimit(pid, mUid)).isEqualTo(expectedLimit);
    }

    /**
     * This test starts the test application with system_server in control.  As soon as
     * system_server has configured a limit (the app should be TOP at this point), the test takes
     * control back, configures a much lower limit, and forces the app over-limit.  system_server
     * will generate a statsd atom in response.
     */
    @RequiresFlagsEnabled({Flags.FLAG_MEMORY_LIMITER_ENABLE,
            Flags.FLAG_MEMORY_LIMITER_DEFAULT_APP_LIMITS})
    @Test
    public void testStatsdAtom() throws Exception {
        // Start by enabling system_server control over the app.
        blockSystemLimiter(mUid, false);

        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter("");

                int pid = prepareHelper(mUid);
                limiter.setUid(mUid);
                limiter.setPid(pid);

                // Wait for the system server to set its limit.  Any limit that is not "max" is
                // okay.  This test only runs if default app limits are configured, and none of
                // thse are "max".
                for (int i = 0; i < 100 && currentLimit(pid, mUid) != sProcessMemoryMax; i++) {
                    Thread.sleep(100); // Wait a bit before polling again.
                }

                // Now make system server stop watching the UID.  The native layer may still be
                // watching the pid; that's fine.
                blockSystemLimiter(mUid, true);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                appCommand(100);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, pid, mUid, MemoryLimiter.MEMORY_LIMIT_TYPE, "");
            }
        }
    }

    /**
     * Test the statsd rate limiter.  Care is taken always to use the
     */
    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testStatsdAtomRateLimiter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            long clock = 0;

            // Start by verifying that the system allows MAX_TOKEN events at the beginning of
            // time.
            int total;
            for (total = 0; total < 100 && counter.shouldLogAtom(clock); total++) {
                // Nothing to do - this is just counting the number of permitted tokens.
            }
            assertThat(total).isEqualTo(EventCounter.MAX_TOKENS);

            // Advance the clock to 1.5 periods.  Verify that a single event is permitted.
            clock = (long) (EventCounter.TOKEN_PERIOD_MS * 1.5);
            assertThat(counter.shouldLogAtom(clock)).isTrue();
            assertThat(counter.shouldLogAtom(clock)).isFalse();

            // Advance the clock to 1.75 periods.  Verify that no event is permitted.
            clock = (long) (EventCounter.TOKEN_PERIOD_MS * 1.75);
            assertThat(counter.shouldLogAtom(clock)).isFalse();

            // Advance the clock to 2.5 periods.  Verify that a single event is permitted.
            clock = (long) (EventCounter.TOKEN_PERIOD_MS * 2.5);
            assertThat(counter.shouldLogAtom(clock)).isTrue();
            assertThat(counter.shouldLogAtom(clock)).isFalse();

            // Advance the clock to 50 periods.  Verify that MAX_TOKEN events are permitted.
            clock = EventCounter.TOKEN_PERIOD_MS * 100;
            for (total = 0; total < 100 && counter.shouldLogAtom(clock); total++) {
                // Nothing to do - this is just counting the number of permitted tokens.
            }
            assertThat(total).isEqualTo(EventCounter.MAX_TOKENS);
        }
    }

    static {
        System.loadLibrary("servicestestjni");
    }
}
