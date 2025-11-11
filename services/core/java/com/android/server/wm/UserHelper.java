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

package com.android.server.wm;

import static android.app.ActivityManager.getCurrentUser;
import static android.app.ActivityManager.START_CLASS_NOT_FOUND;
import static android.app.ActivityManager.START_SUCCESS;
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_USER_VISIBILITY;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.TAG_ATM;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.TAG_WITH_CLASS_NAME;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.UserHandle;
import android.util.IntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.pm.UserManagerInternal;
import com.android.server.utils.Slogf;
import com.android.server.wm.ActivityStarter.Request;

import java.io.PrintWriter;

// NOTE: UserController would be be a better name, but there's one on c.a.server.am. already...
/**
 * Provides integration with user-related features like activities allowlist.
 */
final class UserHelper {

    private static final String TAG = TAG_WITH_CLASS_NAME
            ? UserHelper.class.getSimpleName()
            : TAG_ATM;

    private final boolean mIsHeadlessSystemUserMode;
    private final UserManagerInternal mUmi;

    UserHelper(UserManagerInternal umi) {
        mIsHeadlessSystemUserMode = umi.isHeadlessSystemUserMode();
        mUmi = umi;
    }

    /**
     * Checks if the request is valid.
     *
     * @return {@code START_SUCCESS} is valid, or specific error code if it isn't.
     */
    public int checkRequest(Request request, int displayId) {
        ActivityInfo aInfo = request.activityInfo;
        if (aInfo == null) {
            // The caller should have checked before, but it doesn't hurt to double check...
            Slogf.wtf(TAG, "checkRequest(%s): returning START_CLASS_NOT_FOUND because aInfo is "
                    + "null", request);
            return START_CLASS_NOT_FOUND;
        }

        Intent intent = request.intent;
        var compName = intent.getComponent();
        if (compName == null) {
            // Should not be happen, but better be safe than sorry....
            Slogf.wtf(TAG, "Could not check if %s is allowed for HSU because its intent (%s) "
                    + "doesn't have a Component Name", aInfo, intent);
            return START_SUCCESS;
        }

        int userId = getUserId(aInfo);
        boolean showForAllUsers = (aInfo.flags & ActivityInfo.FLAG_SHOW_FOR_ALL_USERS) != 0;
        if (showForAllUsers) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "Not checking if activity %s is allowlisted for user %d because "
                        + "its marked as 'showForAllUsers' (currentUserId=%d)",
                        intent.getComponent().flattenToShortString(), userId, getCurrentUser());
            }
            return START_SUCCESS;
        }

        // The goal of the allowlist is to avoid activities being shown when they shouldn't (for
        // example, in a login screen that's displayed when user 0 is the current user), but
        // there might be cases where the activity is being launched on a different user, which is
        // not visible (for example, when the current user is user 10 and this activity is being
        // launched by the SystemUI on user 0). Hence, what really matters is whether the activity
        // is allowed for at least one of the visible users, regardless of the activity's user.
        IntArray visibleUserIds = mUmi.getVisibleUsers(displayId);
        for (int i = 0; i < visibleUserIds.size(); i++) {
            int visibleUserId = visibleUserIds.get(i);
            var allowlist = mUmi.getActivitiesAllowlist(visibleUserId);
            if (allowlist == null || allowlist.isAllowed(compName)) {
                if (DEBUG_USER_VISIBILITY) {
                    Slogf.d(TAG, "Activity %s can be launched on user %d because it's allowlist for"
                            + "at least one user (id %d) that's visible in the display (id %d) it "
                            + "will be launched (currentUserId=%d, visibleUserIds=%s)",
                            compName.flattenToShortString(), userId, visibleUserId, displayId,
                            ActivityManager.getCurrentUser(), visibleUserIds);
                }
                return START_SUCCESS;
            }
        }
        Slogf.w(TAG, "Activity %s (from user %d) is not allowlisted for any user visible "
                + "in the display (id %d) it would be launched (currentUser=%d)",
                compName.flattenToShortString(), userId, displayId, getCurrentUser());
        // TODO(b/414326600): consolidate with the logActivityStarted() on
        // handleResult and/or log for all users (once the final API for logging is
        // defined)
        if (mIsHeadlessSystemUserMode && userId == USER_SYSTEM) {
            mUmi.logBlockedHsuActivity(compName);
        }
        return START_NOT_ALLOWED_FOR_USER;
    }

    /**
     * Logs whether the given activity was started.
     */
    public void logActivityStarted(ActivityRecord started, boolean isStarted) {
        if (!isStarted || !mIsHeadlessSystemUserMode) {
            return;
        }

        logActivityStarted(started.mUserId, started.mActivityComponent);
    }

    // NOTE: method below is only used by UserHelperTest, otherwise it would be a pain to create a
    // ActivityRecord to test the "real" method above...
    @VisibleForTesting
    void logActivityStarted(@UserIdInt int userId, ComponentName started) {
        if (userId != USER_SYSTEM) {
            return;
        }
        // TODO(b/414326600): for now we're just logging activities launched on HSU, but once the
        // allowlist metrics mechanism is fully implemented, we'll need to change this call to log a
        // successful launch, but also log when it's blocked earlier on (probably before the check
        // voice session on executeRequest(), as voice interaction is not supported on the HSU)
        mUmi.logLaunchedHsuActivity(started);
    }

    /**
     * Gets the id of the user associated with the application info, or {@link #USER_SYSTEM} when
     * it's not set.
     */
    static @UserIdInt int getUserId(@Nullable ActivityInfo aInfo) {
        return aInfo != null && aInfo.applicationInfo != null
                ? UserHandle.getUserId(aInfo.applicationInfo.uid)
                : USER_SYSTEM;
    }

    /**
     * Dumps its internal state.
     */
    void dump(PrintWriter pw, String prefix) {
        pw.printf("%sUserHelper:\n", prefix);
        String prefix2 = prefix + "  ";
        pw.printf("%sTAG=%s\n", prefix2, TAG);
        pw.printf("%smIsHeadlessSystemUserMode=%b\n", prefix2, mIsHeadlessSystemUserMode);
    }
}
