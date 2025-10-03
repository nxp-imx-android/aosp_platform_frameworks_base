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
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.SparseIntArray;

/**
 * Class used to report events that indicate the potential existence of non-multiuser-compliant
 * features, such as the API calls to check for "main user" (deprecated) and the activities and
 * notifications on headless system user.
 */
final class MultiuserNonComplianceLogger {

    private static final String PROP_ENABLE_IT = "fw.user.log_non_compliance";
    private static final int PROP_ENABLED = 1;
    private static final int PROP_DEFAULT = -1;

    private final Context mContext;

    private final Handler mHandler;

    // TODO(b/414326600): merge collections below and/or use the proper proto / structure. For
    // example, instead of having 2 sets for mLaunchedHsuActivities and mBlockedHsuActivities,
    // we should have just one for the activity and an @IntDef with the result.

    // Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count.
    @Nullable
    private final SparseIntArray mGetMainUserCalls;

    // Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count.
    @Nullable
    private final SparseIntArray mIsMainUserCalls;

    // Activities launched while the current user is the headless system user.
    @Nullable
    private final ArraySet<ComponentName> mLaunchedHsuActivities;

    // Activities blocked while the current user is the headless system user.
    @Nullable
    private final ArraySet<ComponentName> mBlockedHsuActivities;

    MultiuserNonComplianceLogger(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        if (Build.isDebuggable()
                || SystemProperties.getInt(PROP_ENABLE_IT, PROP_DEFAULT) == PROP_ENABLED) {
            mGetMainUserCalls = new SparseIntArray();
            mIsMainUserCalls = new SparseIntArray();
            mLaunchedHsuActivities = new ArraySet<>();
            mBlockedHsuActivities = new ArraySet<>();
        } else {
            mGetMainUserCalls = null;
            mIsMainUserCalls = null;
            mLaunchedHsuActivities = null;
            mBlockedHsuActivities = null;
        }
    }

    void logGetMainUserCall(int callingUid) {
        logMainUserCall(mGetMainUserCalls, callingUid);
    }

    void logIsMainUserCall(int callingUid) {
        logMainUserCall(mIsMainUserCalls, callingUid);
    }

    private void logMainUserCall(@Nullable SparseIntArray calls, int callingUid) {
        if (calls == null) {
            return;
        }

        mHandler.post(() -> {
            int canonicalUid = UserHandle.getAppId(callingUid);
            int newCount = calls.get(canonicalUid, 0) + 1;
            calls.put(canonicalUid, newCount);
        });
    }

    void logLaunchedHsuActivity(ComponentName activity) {
        logHsuActivity(mLaunchedHsuActivities, activity);
    }

    void logBlockedHsuActivity(ComponentName activity) {
        logHsuActivity(mBlockedHsuActivities, activity);
    }

    private void logHsuActivity(@Nullable ArraySet<ComponentName> activities,
            ComponentName activity) {
        if (activities == null) {
            return;
        }
        mHandler.post(() -> activities.add(activity));
    }

    void dump(IndentingPrintWriter pw) {
        dumpDeprecatedCalls(pw, "getMainUser", mGetMainUserCalls);
        pw.println();
        dumpDeprecatedCalls(pw, "isMainUser", mIsMainUserCalls);
        pw.println();
        dumpHsuActivities(pw, mBlockedHsuActivities, "blocked");
        pw.println();
        dumpHsuActivities(pw, mLaunchedHsuActivities, "launched");
    }

    private void dumpDeprecatedCalls(
            IndentingPrintWriter pw, String method, @Nullable SparseIntArray calls) {
        if (calls == null) {
            pw.printf("Not logging %s() calls\n", method);
            return;
        }

        // TODO(b/414326600): should dump in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done

        // TODO(b/414326600): should also dump on proto, but we need to wait until the format is
        // properly defined (for example, we might want to log a generic "user violation" that would
        // include other metrics such as stuff that shouldn't be called when the current user is the
        // headless system user)
        int size = calls.size();
        pw.printf("%d apps called %s()\n", size, method);
        pw.increaseIndent();
        for (int i = 0; i < size; i++) {
            int canonicalUid = calls.keyAt(i);
            int count = calls.valueAt(i);
            String pkgName = getPackageNameForLoggingPurposes(canonicalUid);
            // uid is the canonical UID, but including "canonical" would add extra churn / bytes
            pw.printf("UID %d (%s): %d calls\n", canonicalUid, pkgName, count);
        }
        pw.decreaseIndent();
    }

    private void dumpHsuActivities(IndentingPrintWriter pw, ArraySet<ComponentName> activities,
            String what) {
        if (activities == null) {
            pw.printf("Not logging %s HSU activities\n", what);
            return;
        }
        // TODO(b/414326600): should dump in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done
        int size = activities.size();

        pw.printf("%d activities %s on HSU\n", size, what);
        pw.increaseIndent();
        for (int i = 0; i < size; i++) {
            pw.println(activities.valueAt(i).flattenToShortString());
        }
        pw.decreaseIndent();
    }

    void reset() {
        // TODO(b/414326600): should reset in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done

        if (mGetMainUserCalls != null) {
            mGetMainUserCalls.clear();
        }
        if (mIsMainUserCalls != null) {
            mIsMainUserCalls.clear();
        }
        if (mLaunchedHsuActivities != null) {
            mLaunchedHsuActivities.clear();
        }
        if (mBlockedHsuActivities != null) {
            mBlockedHsuActivities.clear();
        }
    }

    private String getPackageNameForLoggingPurposes(int uid) {
        // pkgs is either null or an array with one or more elements.
        String[] pkgs = mContext.getPackageManager().getPackagesForUid(uid);
        if (pkgs == null) {
            // The caller package might have been uninstsalled or not installed for user 0.
            return "unknown";
        }
        if (pkgs.length > 1) {
            // The UID is shared by multiple packages with sharedUserId.
            return "shared";
        }
        return pkgs[0];
    }
}
