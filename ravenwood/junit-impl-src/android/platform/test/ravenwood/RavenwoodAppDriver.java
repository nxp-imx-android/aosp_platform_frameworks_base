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

import static com.android.ravenwood.common.RavenwoodInternalUtils.ANDROID_PACKAGE_NAME;

import android.annotation.NonNull;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Application_ravenwood;
import android.app.IUiAutomationConnection;
import android.app.Instrumentation;
import android.app.LoadedApk;
import android.app.RavenwoodAndroidAppBridge;
import android.app.UiAutomation;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ravenwood.common.SneakyThrow;

import org.objenesis.ObjenesisStd;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for initializing and handling the app / process lifeclyce related
 * stuff. See also {@link RavenwoodEnvironment}.
 */
public final class RavenwoodAppDriver {
    /** Singleton instance. */
    private static final AtomicReference<RavenwoodAppDriver> sInstance = new AtomicReference<>();

    /**
     * Returns the singleton instance.
     */
    public static RavenwoodAppDriver getInstance() {
        return Objects.requireNonNull(sInstance.get(), "RavenwoodAppDriver not initialized");
    }

    /**
     * Initialize the singleton instance.
     */
    public static void init() {
        if (!sInstance.compareAndSet(null, new RavenwoodAppDriver())) {
            throw new RuntimeException("RavenwoodAppDriver already initialized!");
        }
    }

    private final ObjenesisStd mObjenesis;

    /** This is an empty instance created by Objenesis. None of its fields are initialized. */
    private final ActivityThread mActivityThread;

    private final LoadedApk mTargetLoadedApk;

    private final RavenwoodContext mInstContext;
    private final RavenwoodContext mTargetContext;
    private final Application mApplication;

    // It's instantiated on the main thread, so not final.s
    private volatile Instrumentation mInstrumentation;

    private final RavenwoodAndroidAppBridge mAppBridge;

    /**
     * Constructor. It essentially simulates the start of an app lifecycle.
     */
    private RavenwoodAppDriver() {
        var env = RavenwoodEnvironment.getInstance();
        mObjenesis = new ObjenesisStd();

        mActivityThread = mObjenesis.newInstance(ActivityThread.class);
        mTargetLoadedApk = makeLoadedApk(mActivityThread, env.getTargetPackageName());

        mInstContext = new RavenwoodContext(
                env.getInstPackageName(), env.getMainThread());
        mTargetContext = new RavenwoodContext(
                env.getTargetPackageName(), env.getMainThread());

        // Set up app context. App context is always created for the target app.
        var application = new Application();
        Application_ravenwood.attach(application, mTargetContext);
        if (env.isSelfInstrumenting()) {
            mInstContext.attachApplicationContext(application);
            mTargetContext.attachApplicationContext(application);
        } else {
            // When instrumenting into another APK, the test context doesn't have an app context.
            mTargetContext.attachApplicationContext(application);
        }
        mApplication = application;

        var systemServerContext = new RavenwoodContext(
                ANDROID_PACKAGE_NAME, env.getMainThread());

        var uiAutomation = new UiAutomation(
                mInstContext, new IUiAutomationConnection.Default());

        var instArgs = Bundle.EMPTY;
        RavenwoodUtils.runOnMainThreadSync(() -> {
            try {
                var clazz = Class.forName(env.getInstrumentationClass());
                mInstrumentation = (Instrumentation) clazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                SneakyThrow.sneakyThrow(e);
            }

            mInstrumentation.basicInit(mInstContext, mTargetContext, uiAutomation);
            mInstrumentation.onCreate(instArgs);
        });
        InstrumentationRegistry.registerInstance(mInstrumentation, instArgs);

        RavenwoodSystemServer.init(systemServerContext);

        // This constructor accesses this class's getters, so it must be at the end.
        mAppBridge = new RavenwoodAndroidAppBridge(this);
    }

    /**
     * Crate an {@link ApplicationInfo} for a package.
     *
     * The package must be "known" to Ravenwood; for now, that means it must be an instrumentation
     * or target package name. "android" isn't supported.
     *
     * User-id is assumed to be 0.
     */
    private static ApplicationInfo makeApplicationInfo(@NonNull String packageName) {
        var env = RavenwoodEnvironment.getInstance();
        if (!packageName.equals(env.getTargetPackageName())
                && !packageName.equals(env.getInstPackageName())) {
            throw env.makeUnknownPackageException(packageName);
        }

        ApplicationInfo ai = new ApplicationInfo();
        ai.uid = env.getUid();
        ai.targetSdkVersion = env.getTargetSdkLevel();
        ai.packageName = packageName;

        return ai;
    }

    private static LoadedApk makeLoadedApk(
            ActivityThread activityThread,
            String packageName
    ) {
        ApplicationInfo ai = makeApplicationInfo(packageName);

        var loadedApk = new LoadedApk(
                /* activityThread= */ activityThread,
                /* aInfo= */ ai,
                /* compatInfo= */ null,
                /* baseLoader= */ RavenwoodAppDriver.class.getClassLoader(),
                /* securityViolation= */ false,
                /* includeCode= */ true,
                /* registerPackage= */ false
        );
        return loadedApk;
    }

    /**
     * Return the helper to access {@code android.app} package-private stuff.
     */
    public RavenwoodAndroidAppBridge getAndroidAppBridge() {
        return mAppBridge;
    }

    public ActivityThread getActivityThread() {
        return mActivityThread;
    }

    public LoadedApk getTargetLoadedApk() {
        return mTargetLoadedApk;
    }

    public RavenwoodContext getInstContext() {
        return mInstContext;
    }

    public RavenwoodContext getTargetContext() {
        return mTargetContext;
    }

    public Application getApplication() {
        return mApplication;
    }

    public Instrumentation getInstrumentation() {
        return mInstrumentation;
    }
}
