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

import android.content.Context;
import android.util.Dumpable;
import android.util.IndentingPrintWriter;

import com.android.internal.util.ArrayUtils;

import java.io.PrintWriter;

/**
 * Class responsible for managing allowlists associated with the HSU (Headless System User).
 *
 * <p>This class is thread safe.
 */
final class HsuAllowlistsMediator implements Dumpable {

    // List of activities that are permanently allowed (i.e., they survive reboots).
    // NOTE: for now it's an array as they're just read from config, but should change to Set
    // once it supports APIs to change it (for example, from DPM).
    private final String[] mPermanentActivitiesAllowlist;

    HsuAllowlistsMediator(Context context) {
        mPermanentActivitiesAllowlist = context.getResources()
                .getStringArray(com.android.internal.R.array.config_hsu_allowlist_activitivies);
    }

    /** Returns whether the given activity is allowed. */
    public boolean isActivityAllowed(String componentName) {
        return mPermanentActivitiesAllowlist.length == 0
                || ArrayUtils.contains(mPermanentActivitiesAllowlist, componentName);
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
}
