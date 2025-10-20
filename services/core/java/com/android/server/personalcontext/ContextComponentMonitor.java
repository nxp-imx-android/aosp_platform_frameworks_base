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

package com.android.server.personalcontext;

import android.util.Log;
import android.util.Slog;

import com.android.internal.content.PackageMonitor;

/**
 * Monitors packages that are installed, uninstalled, and modified for re-registering components.
 * @hide
 */
final class ContextComponentMonitor extends PackageMonitor {
    private static final String TAG = "PersonalContext";
    private final ContextComponentManager mComponentManager;

    ContextComponentMonitor(ContextComponentManager componentManager) {
        mComponentManager = componentManager;
    }

    @Override
    public boolean onPackageChanged(String packageName, int uid, String[] components) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " changed, reregistering components");
        }
        mComponentManager.unregisterComponentsForPackage(packageName);
        mComponentManager.registerComponentsForPackage(packageName);
        return false;
    }

    @Override
    public void onPackageUpdateFinished(String packageName, int uid) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " updated, reregistering components");
        }
        mComponentManager.unregisterComponentsForPackage(packageName);
        mComponentManager.registerComponentsForPackage(packageName);
    }

    @Override
    public void onPackageAdded(String packageName, int uid) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " added, registering components");
        }
        mComponentManager.registerComponentsForPackage(packageName);
    }

    @Override
    public void onPackageRemoved(String packageName, int uid) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " removed, unregistering components");
        }
        mComponentManager.unregisterComponentsForPackage(packageName);
    }
}
