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

import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.ArraySet;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Set;

/** {@link AppCompatCameraStateStrategy} that tracks packageName-cameraId pairs. */
class AppCompatCameraStateStrategyForPackage implements AppCompatCameraStateStrategy {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "AppCompatCameraStateStrategyForPackage"
            : TAG_WM;
    @NonNull
    private final DisplayContent mDisplayContent;

    // Bi-directional map between package names and active camera IDs since we need to 1) get a
    // camera id by a package name when resizing the window; 2) get a package name by a camera id
    // when camera connection is closed and we need to clean up our records.
    private final CameraIdPackageNameBiMapping mCameraIdPackageBiMapping =
            new CameraIdPackageNameBiMapping();
    // TODO(b/380840084): Consider making this a set of CameraId/PackageName pairs. This is to
    // keep track of camera-closed signals when apps are switching camera access, so that the policy
    // can restore app configuration when an app closes camera (e.g. loses camera access due to
    // another app).
    private final Set<String> mScheduledToBeRemovedCameraIdSet = new ArraySet<>();

    private final Set<String> mScheduledCompatModeUpdateCameraIdSet = new ArraySet<>();

    AppCompatCameraStateStrategyForPackage(@NonNull DisplayContent displayContent) {
        mDisplayContent = displayContent;
    }

    @Override
    public void trackOnCameraOpened(@NonNull String cameraId) {
        mScheduledToBeRemovedCameraIdSet.remove(cameraId);
        mScheduledCompatModeUpdateCameraIdSet.add(cameraId);
    }

    @Override
    public void notifyPolicyCameraOpenedIfNeeded(@NonNull String cameraId,
            @NonNull String packageName, @NonNull AppCompatCameraStatePolicy policy) {
        if (!mScheduledCompatModeUpdateCameraIdSet.remove(cameraId)) {
            // Camera compat mode update has happened already or was cancelled
            // because camera was closed.
            return;
        }
        mCameraIdPackageBiMapping.put(packageName, cameraId);
        // If there are multiple activities of the same package name and none of
        // them are the top running activity, we do not apply treatment (rather than
        // guessing and applying it to the wrong activity).
        final ActivityRecord cameraActivity = findUniqueActivityWithPackageName(packageName);
        if (cameraActivity == null) {
            // If camera is active, activity, task and app process must exist. No need to notify
            // listeners or track the package otherwise.
            return;
        }
        policy.onCameraOpened(cameraActivity);
    }

    @Override
    public void trackOnCameraClosed(@NonNull String cameraId) {
        mScheduledToBeRemovedCameraIdSet.add(cameraId);
        // No need to update window size for this camera if it's already closed.
        mScheduledCompatModeUpdateCameraIdSet.remove(cameraId);
    }

    @Override
    public boolean notifyPolicyCameraClosedIfNeeded(@NonNull String cameraId,
            @NonNull AppCompatCameraStatePolicy policy) {
        if (!mScheduledToBeRemovedCameraIdSet.remove(cameraId)) {
            // Already reconnected to this camera, no need to clean up.
            return true;
        }
        final boolean canClose = policy.canCameraBeClosed(cameraId);
        if (canClose) {
            // Finish cleaning up.
            mCameraIdPackageBiMapping.removeCameraId(cameraId);
            policy.onCameraClosed();
            return true;
        } else {
            mScheduledToBeRemovedCameraIdSet.add(cameraId);
            // Not ready to process closure yet - the camera activity might be refreshing.
            // Try again later.
            return false;
        }
    }

    @Override
    public boolean isCameraRunningForActivity(@NonNull ActivityRecord activity) {
        return getCameraIdForActivity(activity) != null;
    }

    @Override
    public boolean isCameraWithIdRunningForActivity(@NonNull ActivityRecord activity,
            @NonNull String cameraId) {
        return cameraId.equals(getCameraIdForActivity(activity));
    }

    /** Returns the information about apps using camera, for logging purposes. */
    private String getCameraIdForActivity(@NonNull ActivityRecord activity) {
        return mCameraIdPackageBiMapping.getCameraId(activity.packageName);
    }

    // TODO(b/335165310): verify that this works in multi instance and permission dialogs.
    /**
     * Finds a visible activity with the given package name.
     *
     * <p>If there are multiple visible activities with a given package name, and none of them are
     * the `topRunningActivity`, returns null.
     */
    @Nullable
    private ActivityRecord findUniqueActivityWithPackageName(@NonNull String packageName) {
        final ActivityRecord topActivity = mDisplayContent.topRunningActivity(
                /* considerKeyguardState= */ true);
        if (topActivity != null && topActivity.packageName.equals(packageName)) {
            return topActivity;
        }

        final ArrayList<ActivityRecord> activitiesOfPackageWhichOpenedCamera = new ArrayList<>();
        mDisplayContent.forAllActivities(activityRecord -> {
            if (activityRecord.isVisibleRequested()
                    && activityRecord.packageName.equals(packageName)) {
                activitiesOfPackageWhichOpenedCamera.add(activityRecord);
            }
        });

        if (activitiesOfPackageWhichOpenedCamera.isEmpty()) {
            Slog.w(TAG, "Cannot find camera activity.");
            return null;
        }

        if (activitiesOfPackageWhichOpenedCamera.size() == 1) {
            return activitiesOfPackageWhichOpenedCamera.get(0);
        }

        // Return null if we cannot determine which activity opened camera. This is preferred to
        // applying treatment to the wrong activity.
        Slog.w(TAG, "Cannot determine which activity opened camera.");
        return null;
    }

    @Override
    public String toString() {
        return "CameraIdPackageNameBiMapping=" + mCameraIdPackageBiMapping.toString();
    }
}
