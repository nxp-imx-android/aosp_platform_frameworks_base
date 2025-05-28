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

import java.util.Objects;

/**
 * Inject Ravenwood methods to {@link ActivityThread}.
 */
public class ActivityThread_ravenwood {
    private ActivityThread_ravenwood() {
    }

    /**
     * Equivalent to {@link ActivityThread#mInitialApplication}.
     */
    private static volatile Application sApplication;

    /**
     * Equivalent to {@link ActivityThread#getSystemContext}.
     */
    private static volatile Context sSystemContext;

    /** Initializer called by Ravenwood. */
    public static void init(Application application, Context systemContext) {
        sApplication = Objects.requireNonNull(application);
        sSystemContext = Objects.requireNonNull(application);
    }

    private static <T> T ensureInitialized(T object) {
        return Objects.requireNonNull(object, "ActivityThread_ravenwood not initialized");
    }

    /** Override the corresponding ActivityThread method. */
    public static Context currentSystemContext() {
        return ensureInitialized(sSystemContext);
    }

    /** Override the corresponding ActivityThread method. */
    public static Application currentApplication() {
        return ensureInitialized(sApplication);
    }
}
