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
import android.annotation.SpecialUsers.CanBeALL;
import android.annotation.UserIdInt;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Map;

/**
 * Class used to report events that indicate the potential existence of non-multiuser-compliant
 * features, such as the API calls to check for "main user" (deprecated) and the activities and
 * notifications on headless system user.
 */
final class MultiuserNonComplianceLogger {

    private static final String PROP_ENABLE_IT = "fw.user.log_non_compliance";

    /**
     * When this property is set to {@code true}, the title of the notifications shown in the HSU
     * (Headless System User) will be logged.
     *
     * <p>This is fine because it's unlikely that the title will contain PII:
     *   <ul>
     *     <li>If the target user is USER_SYSTEM, it wouldn't have PII because the HSU is not
     *         associated with a “human” user (for example, it cannot have accounts or apps manually
     *         installed from the app store).
     *     <li>If it's USER_ALL, the same title would be shown to all users – hence, if a
     *         system app / service is adding PII about a particular user in the title of a
     *         notification sent to USER_ALL, it’s leaking that PII to the other users.
     *   </ul>
     */
    @VisibleForTesting
    static final String PROP_SHOW_HSU_NOTIFICATION_TITLE = "fw.user.log_hsu_notification_title";

    private static final int PROP_ENABLED = 1;
    private static final int PROP_DEFAULT = -1;

    private final Context mContext;

    private final Handler mHandler;

    // TODO(b/414326600): merge collections below and/or use the proper proto / structure. For
    // example, instead of having 2 sets for mLaunchedHsuActivities and mBlockedHsuActivities,
    // we should have just one for the activity and an @IntDef with the result.

    /** Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count. */
    @Nullable
    private final SparseIntArray mGetMainUserCalls;

    /** Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count. */
    @Nullable
    private final SparseIntArray mIsMainUserCalls;

    /** Activities launched while the current user is the headless system user. */
    @Nullable
    private final ArraySet<ComponentName> mLaunchedHsuActivities;

    /** Activities blocked while the current user is the headless system user. */
    @Nullable
    private final ArraySet<ComponentName> mBlockedHsuActivities;

    /** Notifications shown while the current user is the headless system user. */
    @Nullable
    private final Map<HsuNotification, Integer> mShownHsuNotifications;

    private record HsuNotification(
            String pkg,
            @Nullable String tag,
            int id,
            @CanBeALL @UserIdInt int targetUserId,
            int visibility,
            @Nullable CharSequence title,
            boolean redacted,
            @Nullable String category,
            @Nullable String channel) {

        HsuNotification {
            if (title == null) {
                redacted = false;
            } else {
                // TODO(b/414326600): current notifications are only logged for the HSU, but if /
                // once the mechanism becomes more generic, it should check that the actual user is
                // the HSU (notice that's different than the target user, which could be USER_ALL).
                redacted = !SystemProperties.getBoolean(PROP_SHOW_HSU_NOTIFICATION_TITLE, false);
                if (redacted) {
                    title = title.length() + "_chars";
                }
            }
        }

        void dump(IndentingPrintWriter pw) {
            pw.print("[pkg="); pw.print(pkg);
            pw.print(", tag="); pw.print(tag);
            pw.print(", id="); pw.print(id);
            pw.print(", targetUserId="); pw.print(targetUserId);
            pw.print(", title=");
            if (title == null) {
                pw.print("null");
            } else if (redacted) {
                // Don't need to wrap with "" (i.e. "title") as it will be X_chars
                pw.printf("%s (redacted)", title);
            } else {
                pw.printf("\"%s\"", title);
            }
            pw.print(", vis="); pw.print(Notification.visibilityToString(visibility));
            pw.print(", category="); pw.print(category);
            pw.print(", channel="); pw.print(channel);
            pw.print("]");
        }
    }

    MultiuserNonComplianceLogger(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        if (Build.isDebuggable()
                || SystemProperties.getInt(PROP_ENABLE_IT, PROP_DEFAULT) == PROP_ENABLED) {
            mGetMainUserCalls = new SparseIntArray();
            mIsMainUserCalls = new SparseIntArray();
            mLaunchedHsuActivities = new ArraySet<>();
            mBlockedHsuActivities = new ArraySet<>();
            mShownHsuNotifications = new ArrayMap<>();
        } else {
            mGetMainUserCalls = null;
            mIsMainUserCalls = null;
            mLaunchedHsuActivities = null;
            mBlockedHsuActivities = null;
            mShownHsuNotifications = null;
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

    void logShownHsuNotification(StatusBarNotification sbn) {
        if (mShownHsuNotifications == null) {
            return;
        }
        Notification notification = sbn.getNotification();
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        // NOTE: title could have PII, so it must be redacted, but that's done in the
        // HsuNotification constructor.
        HsuNotification notif = new HsuNotification(
                sbn.getPackageName(), sbn.getTag(), sbn.getId(), sbn.getUser().getIdentifier(),
                notification.visibility, title, /* redacted= */ true, notification.category,
                notification.getChannelId());
        mHandler.post(() -> {
            Integer currentCount = mShownHsuNotifications.get(notif);
            if (currentCount == null) {
                mShownHsuNotifications.put(notif, 1);
            } else {
                mShownHsuNotifications.put(notif, currentCount + 1);
            }
        });
    }

    void dump(IndentingPrintWriter pw) {
        dumpDeprecatedCalls(pw, "getMainUser", mGetMainUserCalls);
        pw.println();
        dumpDeprecatedCalls(pw, "isMainUser", mIsMainUserCalls);
        pw.println();
        dumpHsuActivities(pw, mBlockedHsuActivities, "blocked");
        pw.println();
        dumpHsuActivities(pw, mLaunchedHsuActivities, "launched");
        pw.println();
        dumpShownHsuNotifications(pw);
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

    private void dumpShownHsuNotifications(IndentingPrintWriter pw) {
        if (mShownHsuNotifications == null) {
            pw.println("Not logging shown HSU notifications");
            return;
        }

        int size = mShownHsuNotifications.size();
        pw.printf("%d notifications shown on HSU\n", size);
        pw.increaseIndent();
        for (Map.Entry<HsuNotification, Integer> entry : mShownHsuNotifications.entrySet()) {
            entry.getKey().dump(pw);
            pw.printf(": %d times\n", entry.getValue());
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
        if (mShownHsuNotifications != null) {
            mShownHsuNotifications.clear();
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
