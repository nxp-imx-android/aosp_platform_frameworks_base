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

import android.annotation.Nullable;
import android.content.ComponentName;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.NamedLock;
import com.android.server.utils.Slogf;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class responsible for managing allowlists associated with a {@code UserType}.
 *
 * <p>This class is thread safe.
 */
final class UserActivitiesAllowlist {

    private static final String TAG = UserActivitiesAllowlist.class.getSimpleName();

    @VisibleForTesting
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = NamedLock.create(UserActivitiesAllowlist.class.getSimpleName());

    // List of activities that are permanently allowed (i.e., they survive reboots).
    // If empty, allowlist is disabled (and all activities are allowed).
    // NOTE: for now it's an array as they're just read from config, but should change to Set
    // once it supports APIs to change it (for example, from DPM).
    private final String[] mPermanentAllowlist;

    // List of activities that are temporarily allowed (i.e., until reboot or set back to null)
    // When set (i.e., not null), it will override the value of mPermanentActivitiesAllowlist.
    // If empty, allowlist is disabled (and all activities are allowed).
    // NOTE: using LinkedHashSet to preserve order in test; this list is small enough (and not used
    // on critical path, so we don't need to optimize and use a ArraySet)
    @Nullable
    @GuardedBy("mLock")
    private String[] mTemporaryAllowlist;

    UserActivitiesAllowlist(String[] permanentActivities) {
        mPermanentAllowlist = getValidComponentNames(permanentActivities);
    }

    // NOTE: only called by 'cmd user' (which needs to "build" the temporary allowlist based on
    // incremental actions, like add or remove an activity) and unit tests, so we don't have to
    // worry about performance (like caching the result or not using streams)
    List<ComponentName> getEffectiveAllowlist() {
        synchronized (mLock) {
            return Arrays
                    .stream(mTemporaryAllowlist != null ? mTemporaryAllowlist : mPermanentAllowlist)
                    .map(ComponentName::unflattenFromString)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /**
    * Returns whether the given activity is allowed.
    *
    * <p>It will check the temporary list first (if set), then the permanent one. If the checked
    * list is empty, then allowlisting is disabled and all activities (except {@code null}) are
    * allowed.
    */
    public boolean isAllowed(ComponentName activity) {
        Objects.requireNonNull(activity, "activity cannot be null");
        String normalizedName = activity.flattenToShortString();

        // Checks the temporary list first...
        synchronized (mLock) {
            if (mTemporaryAllowlist != null) {
                if (mTemporaryAllowlist.length == 0) {
                    if (DEBUG) {
                        Slogf.d(TAG, "isActivityAllowed(%s): returning true because temporary "
                                + "allowlist overrides permanent allowlist and is empty, so any "
                                + "activity is allowed", normalizedName);
                    }
                    return true;
                }
                if (DEBUG) {
                    Slogf.d(TAG, "isActivityAllowed(%s): checking temporary list (%s)",
                            normalizedName, Arrays.toString(mTemporaryAllowlist));
                }
                return ArrayUtils.contains(mTemporaryAllowlist, normalizedName);
            }
        }

        // ...then the permanent one.
        if (mPermanentAllowlist.length == 0) {
            if (DEBUG) {
                Slogf.d(TAG, "isActivityAllowed(%s): returning true because permanent allowlist"
                        + "is empty, so any activity is allowed", normalizedName);
            }
            return true;
        }
        if (DEBUG) {
            Slogf.d(TAG, "isActivityAllowed(%s): checking permanent list (%s)", normalizedName,
                    Arrays.toString(mPermanentAllowlist));
        }
        return ArrayUtils.contains(mPermanentAllowlist, normalizedName);
    }

    /** Sets the temporary allowlist (or resets it when passed with {@code null}. */
    public void setTemporaryAllowlist(@Nullable Collection<ComponentName> componentNames) {
        if (DEBUG) {
            Slogf.d(TAG, "setTemporaryAllowList(%s)", componentNames);
        }
        synchronized (mLock) {
            if (componentNames == null) {
                mTemporaryAllowlist = null;
                return;
            }
            mTemporaryAllowlist = new String[componentNames.size()];
            int i = 0;
            for (var component : componentNames) {
                mTemporaryAllowlist[i++] = component.flattenToShortString();
            }
            if (DEBUG) {
                Slogf.d(TAG, "setTemporaryAllowList(): set as %s",
                        Arrays.toString(mTemporaryAllowlist));
            }
        }
    }

    void dump(PrintWriter writer, String prefix, String header) {
        dump(new IndentingPrintWriter(writer, /* singleIndent=*/ "  ", prefix), header);
    }

    private void dump(IndentingPrintWriter writer, String header) {
        writer.printf("%s:\n", header);
        writer.increaseIndent();

        writer.printf("DEBUG: %b\n", DEBUG);

        dumpAllowlistStatus(writer);

        dumpAllowlist(writer, "permanent", mPermanentAllowlist);
        synchronized (mLock) {
            dumpAllowlist(writer, "temporary", mTemporaryAllowlist);
        }

        writer.decreaseIndent();
    }

    private void dumpAllowlistStatus(IndentingPrintWriter writer) {
        writer.print("activities allowlist status: ");
        synchronized (mLock) {
            if (mTemporaryAllowlist != null) {
                if (mTemporaryAllowlist.length == 0) {
                    writer.println("allowlisting disabled");
                } else {
                    writer.println("using temporary allowlist");
                }
                return;
            }
        }
        if (mPermanentAllowlist.length == 0) {
            writer.println("allowlisting disabled");
        } else {
            writer.println("using permanent allowlist");
        }
    }

    private void dumpAllowlist(IndentingPrintWriter writer, String name, @Nullable String[] list) {
        if (list == null) {
            writer.printf("%s activities allowlist is not set.\n", name);
            return;
        }
        // Header / number of activities
        int size = list.length;
        if (size == 0) {
            writer.printf("%s activities allowlist is empty.\n", name);
            return;
        }
        String suffix = size > 1 ? "ies" : "y";
        writer.printf("%s activities allowlist has %d activit%s:\n", name, size, suffix);

        // Body / activity components
        writer.increaseIndent();
        for (int i = 0; i < list.length; i++) {
            writer.println(list[i]);
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
                Slogf.w(TAG, "Invalid activity from config: %s", componentName);
                continue;
            }
            // Must "normalize" the component into the flattened format, as the class part could
            // have been expressed as FQCN (Fully-Qualified Class Name).
            String normalizedName = validComponentName.flattenToShortString();
            if (set.contains(normalizedName)) {
                Slogf.w(TAG, "Activity %s already added (as %s)", componentName, normalizedName);
            }
            set.add(normalizedName);
        }
        String[] valid = new String[set.size()];
        set.toArray(valid);
        if (DEBUG) {
            Slogf.d(TAG, "Valid activities from config: %s", Arrays.toString(valid));
        }
        return valid;
    }
}
