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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.am.MemoryLimiter.Configuration;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build/Install/Run:
 *  atest MemoryLimiterTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
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

    // An Injector for testing.
    private static class TestInjector extends MemoryLimiter.Injector {

        private final String mConfigFile;

        TestInjector(String config) {
            mConfigFile = (config != null) ? DATA_DIR + config : super.configFile();
        }

        TestInjector() {
            this(null);
        }

        @Override
        boolean isMonitoringEnabled() {
            return true;
        }

        @Override
        String configFile() {
            return mConfigFile;
        }

        @Override
        String getPackageNameForPid(int pid) {
            return "Package" + Integer.toString(pid);
        }
    }

    // Capture over-limit events.
    private static class EventCounter extends MemoryLimiter.ControllerEnabled {
        // A lock that ensures the countdown latch and the event count are consistent.
        private final Object mLock = new Object();

        // A countdown latch that tests can wait on.  This is atomic so that changes to attribute
        // made in the test thread are visible in the callback thread.
        private final CountDownLatch mLatch;

        // A single over-limit event.
        record Event(int pid, int uid, int limit) {}

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
            super(new TestInjector(config));
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
        void expect(int index, Helper helper, int limit) {
            final Event event = mEvents.get(index);
            assertThat(event.pid).isEqualTo(helper.getPid());
            assertThat(event.uid).isEqualTo(helper.getUid());
            assertThat(event.limit).isEqualTo(limit);
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
                mEvents.add(new Event(pid, uid, type));
            }
        }

        /**
         * Fetch the status from a MemoryLimiter and return the native statistics as a hash.
         * Values that are not long are discarded.
         */
        ArrayMap<String, Long> stats() {
            // Fetch the native string.
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            dump(pw);
            pw.close();
            String statsString = sw.getBuffer().toString();

            ArrayMap<String, Long> stats = new ArrayMap<>();

            // Now dice it for the hashmap
            for (String word: statsString.split("\\s+")) {
                String[] kv = word.split("=");
                if (kv.length != 2) {
                    continue;
                }
                try {
                    stats.put(kv[0], Long.parseLong(kv[1]));
                } catch (NumberFormatException e) {
                    // Do nothing.  This is not a native statistic.
                }
            }
            return stats;
        }

        // Fetch the named statistics from the native layer.  On any error, this returns zero.
        // That simplifies the test code below.
        long stat(String key) {
            return stats().get(key);
        }
    }

    @BeforeClass
    public static void setUpEarly() throws Exception {
        shellCommand("am force-stop " + HELPER);
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

    // Fetch the uid of the helper application.
    private int getHelperUid() throws PackageManager.NameNotFoundException {
        PackageManager pm = mContext.getPackageManager();
        return pm.getApplicationInfo(HELPER, 0).uid;
    }

    // A single instance of a helper application.
    private class Helper {

        // The helper pid.  The uid is inherited from the parent.
        final int mPid;

        Helper() throws Exception {
            final String args = "-S -W --dismiss-keyguard";
            final String cmd = String.format("am start-activity %s -n %s/.%s",
                    args, HELPER, ACTIVITY);
            final String ans = shellCommand(cmd);
            if (!ans.contains("LaunchState: COLD")) {
                // If the app was not launched cold then it did not initialize as expected and the
                // test cannot run.  This seems to be an issue that is not related to the feature,
                // so the test is skipped with assumption-failed rather than test-failed.  The
                // results of launching the application are logged for debugging.
                Log.i(TAG, "app failed to launch: " + ans);
                assumeTrue(ans.contains("LaunchState: COLD"));
            }

            mPid = findPid();
            if (mPid < 0) {
                throw new IllegalStateException("helper process not found");
            }
            Log.i(TAG, String.format("helper at pid=%d uid=%d", mPid, mUid));
            prepareCgroup();
        }

        // Fetch the pid of the helper application.
        int findPid() {
            String pid = shellCommand("pidof " + HELPER).trim();
            if (pid == null || pid.equals("")) {
                return -1;
            }
            try {
                return Integer.parseInt(pid);
            } catch (NumberFormatException e) {
                Log.i(TAG, "helper pid not a number: " + pid);
                return -1;
            }
        }

        int getPid() {
            return mPid;
        }

        int getUid() {
            return mUid;
        }

        // Attach the Helper to the Limiter
        void attach(MemoryLimiter.Limiter limiter) {
            limiter.setUid(mUid);
            limiter.setPid(mPid);
        }

        // Return true if the process exists.  The process must have existed for the constructor
        // to succeed, but it might no longer exist.
        boolean exists() {
            return findPid() > 0;
        }

        // Prepare the helper group by allowing world access to the cgroup tree for the helper
        // UID.  The UID is specific to the helper package, so there is no impact to any other
        // functions on the system.  This must be done every time the helper app is started because
        // the cgroup path disappears if there are no processes with the UID.
        long prepareCgroup() throws Exception {
            String path = String.format("/sys/fs/cgroup/apps/uid_%d", mUid);
            String r = shellCommand("chmod -R a+rw " + path);
            if (r != null && !r.trim().equals("")) {
                Log.i(TAG, "unprotecting " + path + ": \"" + r + "\"");
                fail("failed to prepare cgroup");
            }
            return currentMemory();
        }

        // Return the path to the memcg file.  A null file returns the directory.
        String cgroupFile(String file) {
            if (file == null) {
                return String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d", mUid, mPid);
            } else {
                return String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d/%s", mUid, mPid, file);
            }
        }

        // Return true if the cgroup directory exists.
        boolean cgroupExists() throws Exception {
            String path = cgroupFile(null);
            return Files.exists(Paths.get(path));
        }

        // Return the current memory of the helper app, as reported by memcg.
        long currentMemory() throws Exception {
            String path = cgroupFile("memory.current");
            Path filePath = Paths.get(path);
            // Always specify the Charset, e.g., UTF_8.  This may throw: allow the test to fail.
            String value = Files.readString(filePath, StandardCharsets.UTF_8).trim();
            return Long.parseLong(value);
        }

        // Return the current memory.high limit of the helper app, as reported by memcg.
        private long currentLimit() throws Exception {
            String path = cgroupFile("memory.high");
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
        private long currentEvents() throws Exception {
            String path = cgroupFile("memory.events");
            Path filePath = Paths.get(path);

            // The system resets the protections of the memory.events file every time it changes.
            prepareCgroup();

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
        void resize(int size) {
            final Intent intent = new Intent(); // SendActivity.this, SendActivity.class);
            intent.setAction(HELPER + ".MEMORY");
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            intent.putExtra("size", size);
            mContext.sendBroadcast(intent);
        }

        // Send a request to the application to exit.
        private void exit() {
            final Intent intent = new Intent(); // SendActivity.this, SendActivity.class);
            intent.setAction(HELPER + ".EXIT");
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            mContext.sendBroadcast(intent);
        }
    }

    // Disable the system MemoryLimiter for the UID under test.
    private static void blockSystemLimiter(int uid, boolean blocked) throws Exception {
        shellCommand("am memory-limiter ignore " + ((blocked) ? Integer.toString(uid) : "none"));
    }

    // Spin until the predicate returns true or <timeout> milliseconds have elapsed.  Return true
    // if the predicate returned true and false on timeout.  This polls every 10ms.  The function
    // throws if the sleep() throws.
    private static boolean waitUntil(BooleanSupplier f, long timeoutMs) {
        final long start = SystemClock.uptimeMillis();
        while (true) {
            if (f.getAsBoolean()) {
                return true;
            }
            final long now = SystemClock.uptimeMillis();
            if (now - start > timeoutMs) {
                return false;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // An early wake-up is not a problem.  Continue with the loop.
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                helper.resize(100);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.MEMORY_LIMIT_TYPE);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiterAfter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Grow the app by 100M, set the limit, and wait for the over-limit event.
                helper.resize(100);
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.MEMORY_LIMIT_TYPE);
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

    /**
     * Test that process exit is captured.
     */
    @Test
    public void testProcessExit() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                assertThat(counter.stat("started")).isEqualTo(0);
                assertThat(counter.stat("processes")).isEqualTo(0);

                Helper helper = new Helper();
                helper.attach(limiter);
                // This call causes the native layer to start monitoring the process.  The process
                // is not monitored until the first time a limit is configured.
                limiter.onProcStateUpdated(PROCESS_STATE_MAX);

                BooleanSupplier one = () -> {
                    return counter.stat("processes") == 1;
                };
                assertThat(waitUntil(one, 4000)).isTrue();
                assertThat(counter.stat("started")).isEqualTo(1);
                assertThat(counter.stat("process-hwm")).isEqualTo(1);
                assertThat(counter.stat("watched")).isEqualTo(1);

                helper.exit();
                BooleanSupplier exit = () -> {
                    return !helper.exists();
                };
                waitUntil(exit, 4000);

                BooleanSupplier zero = () -> {
                    return counter.stat("processes") == 0;
                };
                assertThat(waitUntil(zero, 4000)).isTrue();

                var stats = counter.stats();
                Log.i(TAG, counter.stats().toString());
                assertThat(stats.get("started")).isEqualTo(1);
                assertThat(stats.get("process-hwm")).isEqualTo(1);
                assertThat(stats.get("watched")).isEqualTo(1);
                assertThat(stats.get("processes")).isEqualTo(0);
            }
        }
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
        final TestInjector inj = new TestInjector();
        try (MemoryLimiter.Controller controller = new MemoryLimiter.ControllerEnabled(inj)) {
            expectedLimit = controller.getStateLimit(ActivityManager.PROCESS_STATE_TOP);
        }

        // Start by enabling system_server control over the app.
        blockSystemLimiter(mUid, false);

        Helper helper = new Helper();

        // Poll until the limit is set by system_server.
        for (int i = 0; i < 100 && helper.currentLimit() != expectedLimit; i++) {
            Thread.sleep(100); // Wait a bit before polling again.
        }
        assertThat(helper.currentLimit()).isEqualTo(expectedLimit);
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
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Wait for the system server to set its limit.  Any limit that is not "max" is
                // okay.  This test only runs if default app limits are configured, and none of
                // thse are "max".
                for (int i = 0; i < 100 && helper.currentLimit() != sProcessMemoryMax; i++) {
                    Thread.sleep(100); // Wait a bit before polling again.
                }

                // Now make system server stop watching the UID.  The native layer may still be
                // watching the pid; that's fine.
                blockSystemLimiter(mUid, true);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_100M);
                helper.resize(100);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.MEMORY_LIMIT_TYPE);
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

    /**
     * Verifies that setting a manual limit is a no-op when the controller is disabled.
     */
    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void setManualLimit_isNoOpWhenControllerIsDisabled() throws Exception {
        try (MemoryLimiter.ControllerDisabled controllerDisabled =
                new MemoryLimiter.ControllerDisabled()) {
            try (MemoryLimiter controller = new MemoryLimiter(controllerDisabled)) {
                Helper helper = new Helper();
                // The limit should be "max" initially because the test setup blocks the system
                // limiter from acting on the test UID.
                assertThat(helper.currentLimit()).isEqualTo(sProcessMemoryMax);

                // Attempt to set a manual limit. Since the controller is disabled, this should be
                // a no-op.
                controller.setManualLimit(helper.getPid(), helper.getUid(), 20);

                // The call is a synchronous no-op, so the limit should be unchanged immediately.
                assertThat(helper.currentLimit()).isEqualTo(sProcessMemoryMax);
            }
        }
    }

    static {
        System.loadLibrary("servicestestjni");
    }
}
