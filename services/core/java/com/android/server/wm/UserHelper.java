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

import static android.app.ActivityManager.START_CLASS_NOT_FOUND;
import static android.app.ActivityManager.START_SUCCESS;
import static android.app.ActivityManager.START_NOT_ALLOWED_FOR_USER;
import static android.os.UserHandle.USER_SYSTEM;

import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_USER_VISIBILITY;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.TAG_ATM;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.TAG_WITH_CLASS_NAME;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.UserHandle;

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

    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED = 1;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM = -1;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST = -2;
    private static final int ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_LOG_ONLY = -3;

    @IntDef(prefix = { "ACTIVITY_LAUNCH_INTEGRATION_STATUS_" }, value = {
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST,
            ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_LOG_ONLY,
    })
    private @interface ActivityLaunchIntegrationStatus {}

    private final boolean mIsHeadlessSystemUserMode;
    private final UserManagerInternal mUmi;
    private final @ActivityLaunchIntegrationStatus int mActivityLaunchIntegrationStatus;

    UserHelper(UserManagerInternal umi) {
        mIsHeadlessSystemUserMode = umi.isHeadlessSystemUserMode();
        mUmi = umi;
        //TODO(b/455582152): integrate with allowlist mode to support other 2 statuses
        mActivityLaunchIntegrationStatus = mIsHeadlessSystemUserMode
                ? ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED
                : ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM;
    }

    /**
     * Checks if the request is valid.
     *
     * @return {@code START_SUCCESS} is valid, or specific error code if it isn't.
     */
    public int checkRequest(Request request, int displayId) {
        if (mActivityLaunchIntegrationStatus != ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "checkRequest(%s): skipping because status is %s", request,
                        activityLaunchIntegrationStatusToString(mActivityLaunchIntegrationStatus));
            }
            return START_SUCCESS;
        }
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

        // TODO(b/412177078): remove this use case once it's checking if the current user is HSU
        int userId = getUserId(aInfo);
        boolean showForAllUsers = (aInfo.flags & ActivityInfo.FLAG_SHOW_FOR_ALL_USERS) != 0;
        if (showForAllUsers) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "Not checking if activity %s is allowlisted for user %d because "
                        + "its marked as 'showForAllUsers' (currentUserId=%d)",
                        intent.getComponent().flattenToShortString(), userId,
                        getCurrentUserId());
            }
            return START_SUCCESS;
        }

        // TODO(b/456300837): check if the current user is the HSU instead (and remove displayId
        // from the method signature) and update comment
        // The goal of the allowlist is to avoid activities being shown when they shouldn't (for
        // example, in a login screen that's displayed when user 0 is the current user), but
        // there might be cases where the activity is being launched on a different user, which is
        // not visible (for example, when the current user is user 10 and this activity is being
        // launched by the SystemUI on user 0). Hence, this mechanism should be ignored when the
        // user is not visible (in the target display)
        // TODO(b/456300837): rather than checking if the user is visible, we should instead get all
        // users visible in the display and check that the activity is allowlisted on all of them.
        // But that will be done in a future CL, for 2 reasons:
        // - There is no UMI.getVisibleUsers(displayId) yet
        // - Such logic is more complicated and will require more unit tests
        if (mUmi.isUserVisible(userId, displayId)) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "Not checking if activity %s is allowlisted for user %d because "
                        + "it's visible on display %d (currentUserId=%d)",
                        intent.getComponent().flattenToShortString(), userId, displayId,
                        getCurrentUserId());
            }
            return START_SUCCESS;
        }

        var allowlist = mUmi.getActivitiesAllowlist(userId);
        if (allowlist != null && !allowlist.isAllowed(compName)) {
            Slogf.w(TAG, "Activity %s is not allowlisted for user %d",
                    compName.flattenToShortString(), userId);
            // TODO(b/414326600): consolidate with the logActivityStarted() on
            // handleResult and/or log for all users (once the final API for logging is
            // defined)
            if (mIsHeadlessSystemUserMode && userId == USER_SYSTEM) {
                mUmi.logBlockedHsuActivity(compName);
            }
            return START_NOT_ALLOWED_FOR_USER;
        }

        if (DEBUG_USER_VISIBILITY) {
            Slogf.d(TAG, "Activity %s is allowlisted for user %d (currentUser=%d)",
                    compName.flattenToShortString(), userId, getCurrentUserId());
        }

        return START_SUCCESS;
    }

    // TODO(b/456300837): use callback to set it locally (when user switches) and inline it instead
    private @UserIdInt int getCurrentUserId() {
        return ActivityManager.getCurrentUser();
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
        pw.printf("%smActivityLaunchIntegrationStatus=%d (%s)\n", prefix2,
                mActivityLaunchIntegrationStatus,
                activityLaunchIntegrationStatusToString(mActivityLaunchIntegrationStatus));
    }

    private static String activityLaunchIntegrationStatusToString(@ActivityLaunchIntegrationStatus
            int status) {
        // Cannot use DebugUtils.constantToString because status constants are private
        return switch (status) {
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED -> "ENABLED";
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM -> "DISABLED_NOT_HSUM";
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NO_ALLOWLIST-> "DISABLED_NO_ALLOWLIST";
            case ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_LOG_ONLY-> "DISABLED_LOG_ONLY";
            default -> "UNKNOWN_" + status;
        };
    }
}
