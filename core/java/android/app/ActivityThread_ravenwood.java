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
package android.app;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.platform.test.ravenwood.RavenwoodAppDriver;

import java.util.concurrent.Executor;

/**
 * Inject Ravenwood methods to {@link ActivityThread}.
 *
 * TODO: Stop using Objensis to instantiate ActivityThread and start using more of the
 * real code, rather than handling all methods here with @RavenwoodRedirect.
 * At that point, the initialization logic should be moved from RavenwoodAppDriver to this class.
 */
public final class ActivityThread_ravenwood {
    private ActivityThread_ravenwood() {
    }

    /** Backdoor to set ActivityThread's package-private member field. */
    public static void setInstrumentation(ActivityThread at, Instrumentation inst) {
        at.mInstrumentation = inst;
    }

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final HandlerExecutor sHandlerExecutor = new HandlerExecutor(sHandler);

    /** Override the corresponding ActivityThread method. */
    public static Context currentSystemContext() {
        return RavenwoodAppDriver.getInstance().getAndroidAppBridge().getSystemContext();
    }

    /** Override the corresponding ActivityThread method. */
    public static Application currentApplication() {
        return RavenwoodAppDriver.getInstance().getApplication();
    }

    static Looper getLooper(ActivityThread at) {
        return Looper.getMainLooper();
    }

    static Handler getHandler(ActivityThread at) {
        return sHandler;
    }

    static Executor getExecutor(ActivityThread at) {
        return sHandlerExecutor;
    }
}
