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
package android.platform.test.ravenwood;

import android.annotation.NonNull;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.ravenwood.RavenwoodVmState;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A singleton class that prepares and manages various app/process lifecycle related states.
 *
 * It's also responsible for initializing {@link RavenwoodVmState}.
 *
 * TODO: Move more of app/process lifecycle related initialization to this class. For example,
 * resource loading should probably move here, and have Context use it to get resources, just like
 * how we handle the app data directories.
 */
public class RavenwoodEnvironment {
    public static final String TAG = "RavenwoodEnvironment";

    private static final AtomicReference<RavenwoodEnvironment> sInstance = new AtomicReference<>();

    /**
     * Returns the singleton instance.
     */
    public static RavenwoodEnvironment getInstance() {
        return Objects.requireNonNull(sInstance.get(), "Instance not set!");
    }

    private final Object mLock = new Object();

    private final int mUid;
    private final int mPid;
    private final int mTargetSdkLevel;

    @NonNull
    private final String mTargetPackageName;

    @NonNull
    private final String mInstPackageName;

    @NonNull
    private final String mInstrumentationClass;

    /** Represents the filesystem root. */
    private final File mRootDir;

    @NonNull
    private final HandlerThread mMainThread;

    @NonNull
    private final Thread mTestThread;

    @GuardedBy("mLock")
    private final Map<String, File> mAppDataDirs = new HashMap<>();

    public RavenwoodEnvironment(
            int uid,
            int pid,
            int targetSdkLevel,
            @NonNull String targetPackageName,
            @NonNull String instPackageName,
            @NonNull String instrumentationClass,
            @NonNull Thread testThread,
            @NonNull HandlerThread mainThread
    ) throws IOException {
        mUid = uid;
        mPid = pid;
        mTargetSdkLevel = targetSdkLevel;
        mTargetPackageName = Objects.requireNonNull(targetPackageName);
        mInstPackageName = Objects.requireNonNull(instPackageName);
        mInstrumentationClass = Objects.requireNonNull(instrumentationClass);
        mTestThread = testThread;
        mMainThread = mainThread;

        mRootDir = Files.createTempDirectory("ravenwood-root-dir-").toFile();
        mRootDir.mkdirs();

        mainThread.start();
        Looper.setMainLooperForTest(mainThread.getLooper());

        Log.i(TAG, "TargetPackageName=" + mTargetPackageName);
        Log.i(TAG, "TestPackageName=" + mInstPackageName);
        Log.i(TAG, "TargetSdkLevel=" + mTargetSdkLevel);
    }

    /**
     * Create and initialize the singleton instance. Also initializes {@link RavenwoodVmState}.
     */
    public static RavenwoodEnvironment init(
            int uid,
            int pid,
            int targetSdkLevel,
            @NonNull String targetPackageName,
            @NonNull String instPackageName,
            @NonNull String instrumentationClass,
            @NonNull Thread testThread,
            @NonNull HandlerThread mainThread
    ) throws IOException {
        final var instance = new RavenwoodEnvironment(
                uid,
                pid,
                targetSdkLevel,
                targetPackageName,
                instPackageName,
                instrumentationClass,
                testThread,
                mainThread);
        if (!sInstance.compareAndSet(null, instance)) {
            throw new RuntimeException("RavenwoodEnvironment already initialized!");
        }
        RavenwoodVmState.init(instance.getUid(), instance.getPid(), instance.getTargetSdkLevel());
        return instance;
    }

    /**
     * If the test is self-instrumenting. i.e. if the test package name and the target package
     * name are the same.
     */
    public boolean isSelfInstrumenting() {
        return mTargetPackageName.equals(mInstPackageName);
    }

    public int getUid() {
        return mUid;
    }

    public int getPid() {
        return mPid;
    }

    public int getTargetSdkLevel() {
        return mTargetSdkLevel;
    }

    @NonNull
    public String getTargetPackageName() {
        return mTargetPackageName;
    }

    @NonNull
    public String getInstPackageName() {
        return mInstPackageName;
    }

    @NonNull
    public String getInstrumentationClass() {
        return mInstrumentationClass;
    }

    /**
     * @return the directory that we use as the "root" directory.
     */
    @NonNull
    public File getRootDir() {
        return mRootDir;
    }

    @NonNull
    public Thread getTestThread() {
        return mTestThread;
    }

    @NonNull
    public HandlerThread getMainThread() {
        return mMainThread;
    }

    public long getDefaultCallingIdentity() {
        return RavenwoodDriver.packBinderIdentityToken(false, mUid, mPid);
    }

    /**
     * Returns a data dir for a given package. The package must be "known" to the environment.
     * (Note "android" doesn't work because in reality too, the system server doesn't have
     * an app data directory.)
     */
    @NonNull
    public File getAppDataDir(@NonNull String packageName) {
        synchronized (mLock) {
            var cached = mAppDataDirs.get(packageName);
            if (cached != null) {
                return cached;
            }

            // Create a directory, but only if it's a known package.
            if (!packageName.equals(mInstPackageName)
                    && !packageName.equals(mTargetPackageName)) {
                throw new RuntimeException("Unknown package " + packageName);
            }

            var dir = new File(mRootDir, "data/app/" + packageName + "-appdatadir/");
            dir.mkdirs();

            mAppDataDirs.put(packageName, dir);
            return dir;
        }
    }
}
