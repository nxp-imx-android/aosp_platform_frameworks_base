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
import java.util.concurrent.atomic.AtomicInteger;

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

    // NOTE: We could set it with USER_SYSTEM, but it's safer to explicitly set it on constructor
    // with the value returned by AM.
    private final @UserIdInt AtomicInteger mCurrentUserId = new AtomicInteger(UserHandle.USER_NULL);

    UserHelper(UserManagerInternal umi) {
        mIsHeadlessSystemUserMode = umi.isHeadlessSystemUserMode();
        mUmi = umi;
        // TODO(b/455582152): integrate with allowlist mode to support other 2 statuses
        mActivityLaunchIntegrationStatus = mIsHeadlessSystemUserMode
                ? ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED
                : ACTIVITY_LAUNCH_INTEGRATION_STATUS_DISABLED_NOT_HSUM;
        setCurrentUserId(ActivityManager.getCurrentUser());
    }

    void setCurrentUserId(@UserIdInt int userId) {
        int previous = mCurrentUserId.getAndSet(userId);
        if (previous != userId) {
            Slogf.i(TAG, "UserHelper.setCurrentUserId(): changed from %d to %d", previous , userId);
        }
    }

    /**
     * Checks if the request is valid.
     *
     * @return {@code START_SUCCESS} is valid, or specific error code if it isn't.
     */
    public int checkRequest(Request request) {
        if (mActivityLaunchIntegrationStatus != ACTIVITY_LAUNCH_INTEGRATION_STATUS_ENABLED) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "checkRequest(%s): skipping because status is %s", request,
                        activityLaunchIntegrationStatusToString(mActivityLaunchIntegrationStatus));
            }
            return START_SUCCESS;
        }

        // Currently, the goal of the allowlist is to avoid activities being shown when the current
        // user is the HSU, so it's explicitly checking for these conditions, i.e.:
        // - Device is HSUM
        // - Current user is the HSU (regardless of userId in the request)
        // - Allowlist for HSU is enabled
        //
        // In the future, if we want to extend this mechanism to all users, we should check if the
        // activity is allowed to any user visible in the display insteads.
        int currentUserId = getCurrentUserId();
        if (currentUserId != USER_SYSTEM) {
            if (DEBUG_USER_VISIBILITY) {
                Slogf.d(TAG, "checkRequest(%s): skipping because current user (id=%d) is not the "
                        + "Headless System User", request, currentUserId);
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

        var allowlist = mUmi.getActivitiesAllowlist(USER_SYSTEM);
        if (allowlist != null && !allowlist.isAllowed(compName)) {
            int userId = getUserId(aInfo);
            if (userId == USER_SYSTEM) {
                Slogf.w(TAG, "Activity %s not allowed for system user",
                        compName.flattenToShortString());
            } else {
                Slogf.w(TAG, "Activity %s is intended for user %d, but it's not allowlisted for "
                        + "USER_SYSTEM (which is the current user)",
                        compName.flattenToShortString(), userId);
            }
            // TODO(b/414326600): consolidate with the logActivityStarted() on
            // handleResult and/or log for all users (once the final API for logging is
            // defined)
            mUmi.logBlockedHsuActivity(compName);
            return START_NOT_ALLOWED_FOR_USER;
        }

        if (DEBUG_USER_VISIBILITY) {
            Slogf.d(TAG, "Activity %s (intended for user %d) is allowlisted for USER_SYSTEM (which "
                    + "is the current user)", compName.flattenToShortString(), getUserId(aInfo));
        }

        return START_SUCCESS;
    }

    @VisibleForTesting
    @UserIdInt int getCurrentUserId() {
        return mCurrentUserId.get();
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
        pw.printf("%smCurrentUserId=%s\n", prefix2, mCurrentUserId);
    }

    @Override
    public String toString() {
        return "UserHelper[activityLaunchIntegrationStatus="
                + activityLaunchIntegrationStatusToString(mActivityLaunchIntegrationStatus)
                + ", currentUserId=" + mCurrentUserId + "]";
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
