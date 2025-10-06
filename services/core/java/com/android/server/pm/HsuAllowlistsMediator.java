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

package com.android.server.pm;

import android.content.ComponentName;
import android.content.Context;
import android.util.Dumpable;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.server.utils.Slogf;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * Class responsible for managing allowlists associated with the HSU (Headless System User).
 *
 * <p>This class is thread safe.
 */
final class HsuAllowlistsMediator implements Dumpable {

    private static final String TAG = HsuAllowlistsMediator.class.getSimpleName();

    @VisibleForTesting
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // List of activities that are permanently allowed (i.e., they survive reboots).
    // NOTE: for now it's an array as they're just read from config, but should change to Set
    // once it supports APIs to change it (for example, from DPM).
    private final String[] mPermanentActivitiesAllowlist;

    HsuAllowlistsMediator(Context context) {
        mPermanentActivitiesAllowlist = getValidComponentNames(context.getResources()
                .getStringArray(com.android.internal.R.array.config_hsu_allowlist_activitivies));
    }

    /** Returns whether the given activity is allowed. */
    public boolean isActivityAllowed(ComponentName activity) {
        Objects.requireNonNull(activity, "activity cannot be null");
        return mPermanentActivitiesAllowlist.length == 0 || ArrayUtils
                .contains(mPermanentActivitiesAllowlist, activity.flattenToShortString());
    }

    @Override
    public void dump(PrintWriter writer, String[] args) {
        if (writer instanceof IndentingPrintWriter) {
            dump((IndentingPrintWriter) writer);
            return;
        }
        dump(new IndentingPrintWriter(writer));
    }

    private void dump(IndentingPrintWriter writer) {
        writer.println("HsuAllowlistsMediator (HAM)");
        writer.increaseIndent();

        writer.printf("DEBUG: %b\n", DEBUG);

        dumpActivitiesAllowlist(writer);

        writer.decreaseIndent();
    }

    private void dumpActivitiesAllowlist(IndentingPrintWriter writer) {
        // TODO(b/412177078): split into permanent and temporary

        // Header / number of activities
        int size = mPermanentActivitiesAllowlist.length;
        writer.printf("%d permanently allowlisted activities", size);
        if (size == 0) {
            writer.println();
            return;
        }
        writer.println(":");

        // Body / activity components
        writer.increaseIndent();
        for (int i = 0; i < size; i++) {
            writer.println(mPermanentActivitiesAllowlist[i]);
        }
        writer.decreaseIndent();
    }

    private static String[] getValidComponentNames(String[] componentNames) {
        // NOTE: must use LinkedHashSet to preserve order, otherwise test case would fail and dump()
        // wouldn't show the same order as the config.xml
        LinkedHashSet<String> set = new LinkedHashSet<>(componentNames.length);
        for (String componentName : componentNames) {
            var validComponentName = ComponentName.unflattenFromString(componentName);
            if (validComponentName == null) {
                Slogf.w(TAG, "Invalid component from config: %s", componentName);
                continue;
            }
            // Must "normalize" the component into the flattened format, as the class part could
            // have been expressed as FQCN (Fully-Qualified Class Name).
            set.add(validComponentName.flattenToShortString());
        }
        String[] valid = new String[set.size()];
        set.toArray(valid);
        if (DEBUG) {
            Slogf.d(TAG, "valid activity component names from config: %s", Arrays.toString(valid));
        }
        return valid;
    }
}
