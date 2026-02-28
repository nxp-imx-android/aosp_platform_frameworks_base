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

import static android.os.Process.INVALID_PID;
import static android.os.Process.INVALID_UID;
import static android.os.Process.FIRST_APPLICATION_UID;
import static android.text.TextUtils.formatSimple;

import static com.android.internal.util.Preconditions.checkArgumentInRange;
import static com.android.internal.util.Preconditions.checkArgumentNonNegative;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.app.ActivityManagerInternal;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.ProfilingServiceHelper;
import android.os.ProfilingTrigger;
import android.system.Os;
import android.system.OsConstants;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.MemInfoReader;
import com.android.server.LocalServices;
import com.android.server.am.memorylimiter.config.MemoryLimiterConfig;
import com.android.server.am.memorylimiter.config.XmlParser;
import com.android.tools.r8.keepanno.annotations.UsedByNative;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.datatype.DatatypeConfigurationException;

/**
 * This class monitors the amount of memory used by application processes.  Debug data is
 * collected if a process exceeds its limits, and the process may be killed.  The limits (and
 * action) vary by process category.  The java class sends process information into the native
 * layer where the limits are applied.  The native layer notifies the java layer when limits are
 * exceeded.
 *
 * An instance allocates native resources that are only released when the instance is closed.  This
 * behavior supports testing.  In production use, the instance attached to ActivityManagerService
 * lasts until the process exits, which releases all native resources back to the kernel.
 *
 * This class is not thread-safe.  Production code should call APIs while holding the AMS lock.
 * Test code may be single-threaded or must include its own lock.
 */
class MemoryLimiter implements AutoCloseable {
    // The standard logcat tag for this module.
    private static final String TAG = "MemoryLimiter";

    // The limits that this feature monitors.
    // LINT.IfChange(limitTypes)
    // A limit has been breached but which limit is unknown.
    static final int UNKNOWN_LIMIT_TYPE = 0;
    // The memory.high limit has been breached.
    static final int MEMORY_LIMIT_TYPE = 1;
    // LINT.ThenChange(/services/core/jni/com_android_server_am_MemoryLimiter.cpp:limitTypes)

    // A discriminated value to mean "all UIDs".  It does not overlay INVALID_UID or any legal
    // UID.
    static final int ALL_UIDS = -2;

    /**
     * A convenience function that maps limit types to strings.
     */
    static String limitTypeToString(int type) {
        return switch (type) {
            case UNKNOWN_LIMIT_TYPE -> "unknown";
            case MEMORY_LIMIT_TYPE -> "memory.high";
            default -> "unexpected";
        };
    }

    /**
     * A convenience function that maps a limit type to a statsd enum.
     */
    static int limitTypeToAtom(int type) {
        return switch (type) {
            case UNKNOWN_LIMIT_TYPE ->
                    FrameworkStatsLog.MEMORY_LIMITER_OVER_LIMIT_EVENT__TYPE__UNKNOWN;
            case MEMORY_LIMIT_TYPE ->
                    FrameworkStatsLog.MEMORY_LIMITER_OVER_LIMIT_EVENT__TYPE__HIGH;
            default -> FrameworkStatsLog.MEMORY_LIMITER_OVER_LIMIT_EVENT__TYPE__UNKNOWN;
        };
    }

    /**
     * A configuration object.  The object contains the configuration parameters (memory assigned
     * to the visible and notVisible proc states as well as a string that identifies the source of
     * the parameters.
     * Version zero is reserved for the "no limits" case, when default limits are disabled.
     */
    record Configuration(String source, int version, int visible, int notVisible) {
        Configuration {
            checkArgumentNonNegative(version, "version must be non-negative");
            checkArgumentInRange(visible, 1, 100, "visible");
            checkArgumentInRange(notVisible, 1, 100, "notVisible");
        }

        @Override
        public String toString() {
            return formatSimple("(%s, %d, %d, %d)", source, version, visible, notVisible);
        }
    }

    /**
     * The default configuration limits visible processes to 50% and non-visible processes to 25%
     * of available memory.
     */
    @VisibleForTesting
    static final Configuration sDefaultConfig =
            Flags.memoryLimiterDefaultAppLimits()
            ? new Configuration("default", 1, 50, 25)
            : new Configuration("disabled", 0, 100, 100);

    /**
     * The location of the option configuration file.
     */
    static final String CONFIG_PATH = "/vendor/etc/memory-limiter-config.xml";

    /**
     * A controller specializes the behavior of an individual MemoryLimiter.
     */
    @UsedByNative
    interface Controller extends AutoCloseable {
         /**
         * Returns true if this controller is enabled and actively managing memory limits.  This
         * can be overridden in test implementations to force the controller to be enabled or
         * disabled, regardless of the feature flag.
         */
        boolean isEnabled();

        // The pid or uid of the object has changed.  Push the update to the native layer.
        void setPidUid(int pid, int uid);

        // The process limit has changed.  Push the update to the native layer.
        void setLimit(int pid, int uid, Long limit);

        // Get the memory limit for the process state.
        Long getStateLimit(@ProcessState int newState);

        // Block or unblock the limiter from monitoring/configuring the UID.
        void ignoreUid(int uid, boolean ignore);

        /**
         * Manually set a limit for a process. This is used for testing.
         *
         * @param pid The pid of the process to set the limit for.
         * @param uid The uid of the process to set the limit for.
         * @param limitPercent The limit percentage (1-100) to set for the process. A negative value
         *     sets the limit to the maximum value (i.e. unlimited).
         */
        void setManualLimit(int pid, int uid, int limitPercent);

        // The controller status, for debug and reports.
        void dump(PrintWriter pw);
    }

    /**
     * The controller for the disabled state is a bunch of no-ops.
     */
    static class ControllerDisabled implements Controller {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void setPidUid(int pid, int uid) {
        }

        @Override
        public void setLimit(int pid, int uid, Long limit) {
        }

        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return null;
        }

        @Override
        public void ignoreUid(int uid, boolean ignore) {
        }

        @Override
        public void setManualLimit(int pid, int uid, int limitPercent) {
        }

        @Override
        public void close() {
        }

        @Override
        public void dump(PrintWriter pw) {
            pw.println("disabled");
        }
    }

    /**
     * An Injector class for ControllerEnabled.  This class allows test code to override the
     * default behavior.
     */
    static class Injector {
        // Return true if the monitoring is enabled.  The default behavior returns the value of
        // the feature flag.
        boolean isMonitoringEnabled() {
            return Flags.memoryLimiterTrigger();
        }

        // True if running in test mode.  The default implementation assumes that test mode is
        // false if running under the system UID and true otherwise.
        boolean isTestMode() {
            return Process.myUid() != Process.SYSTEM_UID;
        }

        // The configuration file.
        @NonNull
        String configFile() {
            return CONFIG_PATH;
        }

        // The ActivityManagerInternal that supplies the package name.  The value is not assigned
        // in the constructor because that may be too early in system startup.
        private final AtomicReference<ActivityManagerInternal> mAm = new AtomicReference<>();

        // Fetch the package name for a PID.  The default behavior queries ActivityManager.
        @Nullable
        String getPackageNameForPid(int pid) {
            // This logic is safe because the local service is a singleton.
            if (mAm.get() == null) {
                mAm.set(LocalServices.getService(ActivityManagerInternal.class));
            }
            return mAm.get().getPackageNameByPid(pid);
        }
    }

    /**
     * The controller for production use when the flag is enabled.  This uses a message queue to
     * handle process updates, which allows the updates to happen outside the AMS lock.  Since
     * everything happens in the message handler, no locks are required.
     */
    @UsedByNative
    static class ControllerEnabled implements Controller {

        // The Injector to modify behavior.
        private final Injector mInjector;

        // The message queue that distributes calls into the native layer.
        private final Handler mQueue;

        // The opcode to start a process.
        private static final int MESSAGE_START = 0;

        // The opcode to configure a process.  The configuration data is in 'obj'.
        private static final int MESSAGE_CONFIG = 1;

        // The opcode to ignore a UID.  Whatever is in arg2 (the uid field) is ignored.  Pass a
        // negative value to ignore nothing (since real UIDs are non-negative).
        private static final int MESSAGE_IGNORE = 2;

        // The opcode to close the controller.
        private static final int MESSAGE_CLOSE = 3;

        // Well-known memory limits.
        private static final Long MAX_MEMORY = -1L;     // Maximum memory
        private final Long mMemoryVisible;
        private final Long mMemoryNotVisible;

        private final long mVmem;
        private final long mPageSize;

        // An array of limits, indexed by proc state.
        private final Long[] mStateLimit = new Long[ActivityManager.MAX_PROCESS_STATE + 1];

        // The ignore list.  The code supports exactly one ignored uid.  The invalid uid never
        // matches a uid, so that value turns off ignoring.  It is set and read by the handler
        // code and read by the dump() method.
        private final AtomicInteger mIgnoredUid = new AtomicInteger(INVALID_UID);

        // The native pointer.  It is set and read by the handler code and read by the dump()
        // method.
        private final AtomicLong mNative = new AtomicLong(0);

        // A mutex to ensure that the native layer is not closed underneath a call to dump().
        // dump() is the only operation against the native service that is not single-threaded.
        private final Object mDumpLock = new Object();

        /**
         * In the constructor, create the native peer and the message queue that will handle all
         * requests directed to the native layer.
         */
        ControllerEnabled(@NonNull Injector injector) {
            mInjector = injector;

            mNative.set(initLimiter(ControllerEnabled.this, mInjector.isMonitoringEnabled(),
                            mInjector.isTestMode()));

            mQueue = new Handler(BackgroundThread.getHandler().getLooper()) {
                    // Toggles to false once the Controller is closed.
                    private boolean mOpen = true;

                    @Override
                    public void handleMessage(Message msg) {
                        if (!mOpen) {
                            // Getting a message after the controller has been closed is
                            // unexpected but only happens during testing.  Silently ignore
                            // it.
                            return;
                        }
                        final long service = mNative.get();
                        final int pid = msg.arg1;
                        final int uid = msg.arg2;
                        final int op = msg.what;
                        switch (op) {
                            case MESSAGE_START -> {
                                if (!shouldIgnore(uid)) {
                                    onProcessStarted(service, pid, uid);
                                }
                            }

                            case MESSAGE_CONFIG -> {
                                if (msg.obj != null && !shouldIgnore(uid)) {
                                    long limit = (Long) msg.obj;
                                    configureLimit(service, pid, uid, limit);
                                }
                            }

                            case MESSAGE_IGNORE -> {
                                // This message is only issued during testing.
                                String oldValue = ignoredUid();
                                Boolean ignored = (Boolean) msg.obj;
                                // Normalize the UID to INVALID if no UID is being ignored.
                                mIgnoredUid.set(ignored ? uid : INVALID_UID);
                                Slog.i(TAG, "ignoring " + ignoredUid() + " was " + oldValue);
                            }

                            case MESSAGE_CLOSE -> {
                                synchronized (mDumpLock) {
                                    mNative.set(0);
                                    closeLimiter(service);
                                }
                                mOpen = false;
                            }

                            default ->
                                    Slog.e(TAG, "invalid message: op=" + op);
                        }
                    }
                };

            MemInfoReader memInfo = new MemInfoReader();
            memInfo.readMemInfo();
            mVmem = memInfo.getTotalSize();
            mPageSize = Os.sysconf(OsConstants._SC_PAGE_SIZE);
            // If either system parameter is invalid, log an error but keep going.  Limit checks
            // will be disabled.
            if (mVmem <= 0 || mPageSize <= 0) {
                Slog.e(TAG, formatSimple("Invalid system config: vmem=%d pageSize=%d",
                                mVmem, mPageSize));
            }

            // Note that getConfiguration() accepts a null input.
            Configuration cfg = getConfiguration(mInjector.configFile());
            mMemoryVisible = memLimit(mVmem, mPageSize, cfg.visible);
            mMemoryNotVisible = memLimit(mVmem, mPageSize, cfg.notVisible);

            // Initialize the procState/limit map.
            for (int state = ActivityManager.MIN_PROCESS_STATE;
                    state <= ActivityManager.MAX_PROCESS_STATE;
                    state++) {
                mStateLimit[state] = initStateLimit(state);
            }
        }

        // A helper function that returns the correct memory limit given a total memory size and a
        // percentage.  If the percentage is 100, then MAX_MEMORY is returned.  A non-positive
        // percentage should not be seen but if it is, the function returns zero.  Return values
        // are truncated to the kernel page size.  If either system configuration parameter (total
        // and pageSize) is invalid, MAX_MEMORY is returned.
        private static long memLimit(long total, long pageSize, int percentage) {
            if (percentage >= 100) {
                return MAX_MEMORY;
            } else if (total <= 0 || pageSize <= 0) {
                return MAX_MEMORY;
            } else if (percentage <= 0) {
                return 0;
            } else {
                long limit = (percentage * total) / 100;
                limit = (limit / pageSize) * pageSize;
                return limit;
            }
        }

        // Compute the memory limit by state.
        private Long initStateLimit(@ProcessState int newState) {
            // Never try to configure a process that does not exist or is cached.
            if (newState == ActivityManager.PROCESS_STATE_UNKNOWN
                    || newState == ActivityManager.PROCESS_STATE_NONEXISTENT) {
                return null;
            } else if (ActivityManager.isProcStateCached(newState)) {
                return null;
            } else if (ActivityManager.isProcStateJankPerceptible(newState)) {
                return mMemoryVisible;
            } else {
                return mMemoryNotVisible;
            }
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        /**
         * Send a command.
         */
        private void sendCommand(int command, int pid, int uid, Object obj) {
            mQueue.sendMessage(mQueue.obtainMessage(command, pid, uid, obj));
        }

        @Override
        public void setPidUid(int pid, int uid) {
            sendCommand(MESSAGE_START, pid, uid, null);
        }

        @Override
        public void setLimit(int pid, int uid, Long limit) {
            sendCommand(MESSAGE_CONFIG, pid, uid, limit);
        }

        @Override
        public Long getStateLimit(@ProcessState int newState) {
            return mStateLimit[newState];
        }

        // Allow burst of up to MAX_TOKENS reports in any period.
        static final int MAX_TOKENS = 4;

        // A period is one hour.
        static final long TOKEN_PERIOD_MS = Duration.ofHours(1).toMillis();

        // A lock that ensures the token bucket is updated atomically.
        private final Object mBucketLock = new Object();

        // The last time the token bucket was updated.
        @GuardedBy("mBucketLock")
        private long mLastBucketUpdate = 0;

        // The tokens available.
        @GuardedBy("mBucketLock")
        private int mAvailableTokens = MAX_TOKENS;

        // The token bucket rate limiter with a supplied current-time, for testing.
        @VisibleForTesting
        final boolean shouldLogAtom(long now) {
            synchronized (mBucketLock) {
                // 1. Compute the number of available tokens, as of the last period.
                now = (now / TOKEN_PERIOD_MS) * TOKEN_PERIOD_MS;
                long accumulated = (now - mLastBucketUpdate) / TOKEN_PERIOD_MS;
                mLastBucketUpdate = now;
                // Just in case the clocks glitch, never accept a negative accumulated value.
                accumulated = Math.max(0, accumulated);
                mAvailableTokens += accumulated;
                mAvailableTokens = Math.min(mAvailableTokens, MAX_TOKENS);
                if (mAvailableTokens > 0) {
                    mAvailableTokens--;
                    return true;
                } else {
                    return false;
                }
            }
        }

        // The token bucket rate limiter that uses the system clock.
        final boolean shouldLogAtom() {
            return shouldLogAtom(System.currentTimeMillis());
        }

        @UsedByNative
        private void onLimitExceeded(int pid, int uid, int type, long limit) {
            final String pkg = mInjector.getPackageNameForPid(pid);

            Slog.i(TAG, formatSimple("onLimitExceeded: pid=%d uid=%d type=%s limit=%d pkg=%s",
                    pid, uid, limitTypeToString(type), limit, pkg));

            // If mVmem is zero, something is badly wrong with the system.  The code should never
            // reach this point, because an invalid mVmem should disable limits, but just in case
            // we do reach this point, log and exit immediately.
            if (mVmem <= 0) {
                Slog.e(TAG, formatSimple("onLimitExceeded: invalid mVmem=%d", mVmem));
                return;
            }

            // Convert the limit into a percentage of available memory.  The next two lines
            // safely generate the percentage between 0 and 100.
            final double ratio = (100.0D * limit) / mVmem;
            final int percent = (int) Math.round(Math.max(0.0, Math.min(100.0, ratio)));

            // statsd logging is throttled to at most 28 events per day.
            if (shouldLogAtom()) {
                FrameworkStatsLog.write(FrameworkStatsLog.MEMORY_LIMITER_OVER_LIMIT_EVENT,
                        uid, limitTypeToAtom(type), percent);
            }
            onLimitExceeded(pid, uid, type, limit, percent, pkg);
        }

        // This method can be overridden by test clients to capture the full over-limit event.
        public void onLimitExceeded(int pid, int uid, int type, long limit, int percent,
                String pkg) {
            if (pkg == null) {
                // A null package cannot be reported.
                return;
            }

            if (!android.os.profiling.Flags.systemTriggeredProfilingNew()
                    || !android.os.profiling.anomaly.flags.Flags.anomalyDetectorCore()) {
                // Profiling is disabled globally.
                return;
            }

            try {
                ProfilingServiceHelper helper = ProfilingServiceHelper.getInstance();
                helper.onProfilingTriggerOccurred(uid, pkg, ProfilingTrigger.TRIGGER_TYPE_ANOMALY);
            } catch (IllegalStateException e) {
                // Log the exception but otherwise discard it.  A failure to generate a profile is
                // not fatal.
                Slog.w(TAG, e.getMessage());
            }
        }

        @Override
        public void ignoreUid(int uid, boolean ignore) {
            sendCommand(MESSAGE_IGNORE, INVALID_PID, uid, Boolean.valueOf(ignore));
        }

        @Override
        public void setManualLimit(int pid, int uid, int limitPercent) {
            long limitBytes;
            if (limitPercent < 0) {
                limitBytes = MAX_MEMORY;
            } else {
                limitBytes = memLimit(mVmem, mPageSize, limitPercent);
            }
            setLimit(pid, uid, limitBytes);
        }

        @Override
        public void close() {
            sendCommand(MESSAGE_CLOSE, INVALID_PID, INVALID_UID, null);
        }

        // A simple function to string-ify the ignored UID.
        private String ignoredUid() {
            final int ignored = mIgnoredUid.get();
            return switch (ignored) {
                case INVALID_UID -> "none";
                case ALL_UIDS -> "all";
                default -> Integer.toString(ignored);
            };
        }

        // Return true if the input UID is being ignored.
        private boolean shouldIgnore(int uid) {
            final int ignored = mIgnoredUid.get();
            return switch (ignored) {
                case INVALID_UID -> false;
                case ALL_UIDS -> true;
                default -> uid == ignored;
            };
        }

        // Print a set of white-space-delimited fields in columns.
        private void dumpLine(PrintWriter pw, String line) {
            StringBuilder out = new StringBuilder();
            for (String field : line.trim().split("\\s+")) {
                if (out.length() > 0) {
                    // Force a space between columns, just in case one of the fields is longer
                    // than the column width.
                    out.append(" ");
                }
                // String.format() is used instead of formatSimple() because the latter does not
                // support negative field widths for left justification.
                out.append(String.format("%-24s", field));
            }
            pw.println(out.toString().stripTrailing());
        }

        @Override
        public void dump(PrintWriter pw) {
            final String stats;
            synchronized (mDumpLock) {
                final long service = mNative.get();
                stats = (service != 0) ? getStatistics(service) : "closed";
            }

            final long meg = 1024 * 1024;
            dumpLine(pw, formatSimple("enabled low=%dMB high=%dMB ignored=%s",
                            mMemoryNotVisible / meg, mMemoryVisible / meg, ignoredUid()));
            if (stats != null) {
                // Format the output.  Use the line splits provided by the native layer but put
                // the key/value pairs into columns.
                for (String line : stats.split("\n")) {
                    dumpLine(pw, line);
                }
            }
        }
    }

    /**
     * Fetch the configuration.  If the file is null or the file does not exist, return the
     * default.  Otherwise try to parse the file.  All errors are converted to an
     * IllegalArgumentException.
     */
    @VisibleForTesting
    static Configuration getConfiguration(@Nullable String file) {
        if (file == null) {
            return sDefaultConfig;
        }
        try (InputStream str = new BufferedInputStream(new FileInputStream(file))) {
            MemoryLimiterConfig cfg = XmlParser.read(str);
            // The following conditionals test that the required fields are present.  The parser
            // only verifies that the xml is well-formed, not that it conforms to the xsd.
            if (cfg == null) {
                throw new IllegalArgumentException("bad config: no MemoryLimiterConfig");
            } else if (cfg.getVersion() == null) {
                throw new IllegalArgumentException("bad config: no version attribute");
            } else if (cfg.getVisible() == null) {
                throw new IllegalArgumentException("bad config: no Visible attribute");
            } else if (cfg.getNotVisible() == null) {
                throw new IllegalArgumentException("bad config: no NotVisible attribute");
            }
            // Most values are checked when the Configuration is constructed.  As a special case,
            // though, the version must be positive if it comes from a configuration file.
            if (cfg.getVersion().intValue() <= 0) {
                throw new IllegalArgumentException("bad config: invalid version");
            }
            return new Configuration(file, cfg.getVersion().intValue(),
                    cfg.getVisible().intValue(), cfg.getNotVisible().intValue());
        } catch (FileNotFoundException e) {
            // It is not an error if the file does not exist.  Silently return the default.
            return sDefaultConfig;
        } catch (IOException e) {
            Slog.e(TAG, "I/O error: " + e);
            throw new IllegalArgumentException("bad config: " + file, e);
        } catch (XmlPullParserException | DatatypeConfigurationException e) {
            Slog.e(TAG, "XML error: " + e);
            throw new IllegalArgumentException("bad config: " + file, e);
        }
    }

    /**
     * The controller for this processes created from this limiter.
     */
    private final Controller mController;

    /**
     * Initialize the native layer and any maps.  This eventually makes a native call and
     * therefore cannot be invoked before the native libraries are loaded.  Unit tests call this
     * directly to supply specialized controllers.
     */
    @VisibleForTesting
    MemoryLimiter(@NonNull Controller controller) {
        mController = controller;
    }

    /**
     * Construct the default memory limiter.
     */
    private static Controller getDefaultController() {
        if (!Flags.memoryLimiterEnable()) {
            // The feature is disabled.
            return new ControllerDisabled();
        } else if (Process.myUid() != Process.SYSTEM_UID) {
            // The feature is not running in a system process, which means this is a test.  The
            // feature must be enabled explicitly by the test method using the constructor that
            // takes a Controller.
            return new ControllerDisabled();
        } else {
            // The feature is enabled and this is system_server.
            return new ControllerEnabled(new Injector());
        }
    }

    /**
     * Create the default MemoryLimiter, based on the feature flag and the enclosing process.
     */
    static MemoryLimiter getDefaultMemoryLimiter() {
        return new MemoryLimiter(getDefaultController());
    }

    // The object that tracks the state of an individual process.  It is not static.  Methods in
    // this class are not thread-safe.  Normally, these are called inside the AMS lock.
    class Limiter {

        // The pid that this instance controls.
        private int mPid = INVALID_PID;
        // The uid that this instance controls.
        private int mUid = INVALID_UID;
        // The last limit assigned to the process.
        private Long mLimit = null;

        /**
         * Return true if the process should be monitored and limited.
         */
        private boolean shouldMonitor() {
            return (mPid != INVALID_PID && mUid != INVALID_UID && mUid >= FIRST_APPLICATION_UID);
        }

        /**
         * Return true if the object is ready to manage a process.  The pid and uid must be valid
         * and the UID must belong to the application name space.  This method is called whenever
         * the pid or uid changes.
         */
        private void maybeStart() {
            if (!shouldMonitor()) return;
            mLimit = null;
            mController.setPidUid(mPid, mUid);
        }

        /**
         * Set the UID.  If this is change from the previous pid/uid combination then start the
         * process.
         */
        void setUid(int uid) {
            if (!mController.isEnabled()) return;
            if (uid == mUid) {
                return;
            }
            mUid = uid;
            maybeStart();
        }


        /**
         * Set the pid and uid of the instance.  The instance is created before the pid is known,
         * so both are set at this time, and the native layer is notified that the process has
         * started.  The pid and uid are saved for reuse when the process memory limits is
         * changed.  The package name is passed to the native layer and is not retained by this
         * class.
         *
         * This method should be called while holding the AMS lock.  The actual work happens on a
         * handler thread.
         */
        void setPid(int pid) {
            if (!mController.isEnabled()) return;

            if (pid == 0) {
                // The upper layers tend to use 0 as "invalid".  Convert the pid now.
                pid = INVALID_PID;
            }
            if (pid == mPid) {
                return;
            }
            mPid = pid;
            maybeStart();
        }

        /**
         * React to a new procstate.  If the new procstate requires a profile change, notify the
         * controller.
         *
         * This method should be called while holding the AMS lock.  The actual work happens on a
         * handler thread.
         */
        void onProcStateUpdated(@ProcessState int newState) {
            if (!mController.isEnabled()) return;

            // Do not assign limits if the process should not be monitored.
            if (!shouldMonitor()) return;

            final Long newLimit = mController.getStateLimit(newState);
            if (newLimit != null && !Objects.equals(mLimit, newLimit)) {
                mLimit = newLimit;
                mController.setLimit(mPid, mUid, mLimit);
            }
        }
    }

    /**
     * Return a new Process-helper object bound to this instance.
     */
    Limiter newLimiter() {
        return new Limiter();
    }

    /**
     * Close the instance.  This is idempotent.
     */
    @Override
    public void close() throws Exception {
        mController.close();
    }

    /**
     * Return the operational status of the controller.
     */
    boolean isEnabled() {
        return mController.isEnabled();
    }

    /**
     * Display the status of the limiter.
     */
    void dump(PrintWriter pw) {
        pw.println("Memory limiter");
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw).increaseIndent();
        mController.dump(ipw);
    }

    /**
     * Manually set a limit for a process (for testing).
     */
    void setManualLimit(int pid, int uid, int limitPercent) {
        mController.setManualLimit(pid, uid, limitPercent);
    }

    /**
     * Block or unblock a UID.  This is used in feature testing, when it is important that the
     * limits are controlled by the test process and not by system_server.
     */
    @VisibleForTesting
    void ignoreUid(int uid, boolean ignore) {
        mController.ignoreUid(uid, ignore);
    }

    /**
     * Native methods.
     */

    /**
     * Initialize the native layer and return a pointer to the native handler.  The controller
     * receives any over-limit events.  The method will throw an exception if initialization
     * fails.
     *
     * @param controller is the Controller that receives over-limit events.
     * @param monitor is true if limit monitoring is enabled.
     * @param testMode is true if running in test mode.
     * @return the native service.
     */
    private static native long initLimiter(Controller controller, boolean monitor,
            boolean testMode);

    /**
     * Release the native handler.
     */
    private static native void closeLimiter(long servicePtr);

    /**
     * Inform the native layer that a process has started.  No profile is assigned to the process
     * but the native service prepares to monitor the process, if monitoring was enabled when the
     * native service was initialized.
     */
    private static native void onProcessStarted(long servicePtr, int pid, int uid);

    /**
     * Request that a process's memory.high be configured to limit.  Negative values for the limit
     * mean "maximum memory".
     */
    private static native void configureLimit(long servicePtr, int pid, int uid, long limit);

    /**
     * Fetch the native statistics.  This returns an null pointer if the service pointer is
     * invalid.  The returned string is a list of key/value pairs with the format "key=value",
     * separated by whitespace.
     */
    private static native @Nullable String getStatistics(long servicePtr);
}
