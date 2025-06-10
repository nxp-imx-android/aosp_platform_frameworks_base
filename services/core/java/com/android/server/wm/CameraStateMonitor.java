/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_STATES;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Class that listens to camera open/closed signals, keeps track of the current apps using camera,
 * and notifies listeners.
 */
class CameraStateMonitor {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "CameraStateMonitor" : TAG_WM;

    // Delay for updating letterbox after Camera connection is closed. Needed to avoid flickering
    // when an app is flipping between front and rear cameras or when size compat mode is restarted.
    // TODO(b/330148095): Investigate flickering without using delays, remove them if possible.
    private static final int CAMERA_CLOSED_LETTERBOX_UPDATE_DELAY_MS = 2000;
    // Delay for updating letterboxing after Camera connection is opened. This delay is selected to
    // be long enough to avoid conflicts with transitions on the app's side.
    // Using a delay < CAMERA_CLOSED_ROTATION_UPDATE_DELAY_MS to avoid flickering when an app
    // is flipping between front and rear cameras (in case requested orientation changes at
    // runtime at the same time) or when size compat mode is restarted.
    // TODO(b/330148095): Investigate flickering without using delays, remove them if possible.
    private static final int CAMERA_OPENED_LETTERBOX_UPDATE_DELAY_MS =
            CAMERA_CLOSED_LETTERBOX_UPDATE_DELAY_MS / 2;

    @NonNull
    private final DisplayContent mDisplayContent;
    @NonNull
    private final WindowManagerService mWmService;
    @Nullable
    private final CameraManager mCameraManager;
    @NonNull
    private final Handler mHandler;

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

    // TODO(b/336474959): should/can this go in the compat listeners?
    private final Set<String> mScheduledCompatModeUpdateCameraIdSet = new ArraySet<>();

    @VisibleForTesting
    final AppCompatCameraStatePolicy mAppCompatCameraStatePolicy;

    /**
     * Value toggled on {@link #startListeningToCameraState()} to {@code true} and on {@link
     * #stopListeningToCameraState()} to {@code false}.
     */
    private boolean mIsListeningToCameraState;

    private final CameraManager.AvailabilityCallback mAvailabilityCallback =
            new  CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraOpened(@NonNull String cameraId, @NonNull String packageId) {
                    synchronized (mWmService.mGlobalLock) {
                        notifyCameraOpenedWithDelay(cameraId, packageId);
                    }
                }
                @Override
                public void onCameraClosed(@NonNull String cameraId) {
                    synchronized (mWmService.mGlobalLock) {
                        notifyCameraClosedWithDelay(cameraId);
                    }
                }
            };

    CameraStateMonitor(@NonNull DisplayContent displayContent, @NonNull Handler handler,
            @NonNull AppCompatCameraStatePolicy appCompatCameraStatePolicy) {
        // This constructor is called from DisplayContent constructor. Don't use any fields in
        // DisplayContent here since they aren't guaranteed to be set.
        mHandler = handler;
        mDisplayContent = displayContent;
        mAppCompatCameraStatePolicy = appCompatCameraStatePolicy;
        mWmService = displayContent.mWmService;
        mCameraManager = mWmService.mContext.getSystemService(CameraManager.class);
    }

    /** Starts listening to camera opened/closed signals. */
    void startListeningToCameraState() {
        if (mCameraManager != null) {
            mCameraManager.registerAvailabilityCallback(
                    mWmService.mContext.getMainExecutor(), mAvailabilityCallback);
        }
        mIsListeningToCameraState = true;
    }

    /** Stops listening to camera opened/closed signals. */
    public void stopListeningToCameraState() {
        if (mCameraManager != null) {
            mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
        }
        mIsListeningToCameraState = false;
    }

    /**
     * Returns whether {@link CameraStateMonitor} is listening to camera opened/closed
     * signals.
     */
    @VisibleForTesting
    boolean isListeningToCameraState() {
        return mIsListeningToCameraState;
    }

    private void notifyCameraOpenedWithDelay(@NonNull String cameraId,
            @NonNull String packageName) {
        // If an activity is restarting or camera is flipping, the camera connection can be
        // quickly closed and reopened.
        mScheduledToBeRemovedCameraIdSet.remove(cameraId);
        ProtoLog.v(WM_DEBUG_STATES,
                "Display id=%d is notified that Camera %s is open for package %s",
                mDisplayContent.mDisplayId, cameraId, packageName);
        // Some apps can’t handle configuration changes coming at the same time with Camera setup so
        // delaying orientation update to accommodate for that.
        mScheduledCompatModeUpdateCameraIdSet.add(cameraId);
        mHandler.postDelayed(() -> notifyCameraOpenedInternal(cameraId, packageName),
                CAMERA_OPENED_LETTERBOX_UPDATE_DELAY_MS);
    }

    private void notifyCameraOpenedInternal(@NonNull String cameraId, @NonNull String packageName) {
        synchronized (mWmService.mGlobalLock) {
            if (!mScheduledCompatModeUpdateCameraIdSet.remove(cameraId)) {
                // Camera compat mode update has happened already or was cancelled
                // because camera was closed.
                return;
            }
            mCameraIdPackageBiMapping.put(packageName, cameraId);
            // If there are multiple activities of the same package name and none of
            // them are the top running activity, we do not apply treatment (rather than
            // guessing and applying it to the wrong activity).
            final ActivityRecord cameraActivity =
                    findUniqueActivityWithPackageName(packageName);
            if (cameraActivity == null || cameraActivity.getTask() == null) {
                return;
            }
            mAppCompatCameraStatePolicy.onCameraOpened(cameraActivity);
        }
    }

    /**
     * Processes camera closed, and schedules notifying listeners.
     *
     * <p>The delay is introduced to avoid flickering when switching between front and back camera,
     * and when an activity is refreshed due to camera compat treatment.
     */
    private void notifyCameraClosedWithDelay(@NonNull String cameraId) {
        ProtoLog.v(WM_DEBUG_STATES,
                "Display id=%d is notified that Camera %s is closed.",
                mDisplayContent.mDisplayId, cameraId);
        mScheduledToBeRemovedCameraIdSet.add(cameraId);
        // No need to update window size for this camera if it's already closed.
        mScheduledCompatModeUpdateCameraIdSet.remove(cameraId);
        scheduleRemoveCameraId(cameraId);
    }

    boolean isCameraRunningForActivity(@NonNull ActivityRecord activity) {
        return getCameraIdForActivity(activity) != null;
    }

    // TODO(b/336474959): try to decouple `cameraId` from the listeners.
    boolean isCameraWithIdRunningForActivity(@NonNull ActivityRecord activity, String cameraId) {
        return cameraId.equals(getCameraIdForActivity(activity));
    }

    void rescheduleRemoveCameraActivity(@NonNull String cameraId) {
        mScheduledToBeRemovedCameraIdSet.add(cameraId);
        scheduleRemoveCameraId(cameraId);
    }

    @Nullable
    private String getCameraIdForActivity(@NonNull ActivityRecord activity) {
        return mCameraIdPackageBiMapping.getCameraId(activity.packageName);
    }

    // Delay is needed to avoid rotation flickering when an app is flipping between front and
    // rear cameras, when size compat mode is restarted or activity is being refreshed.
    private void scheduleRemoveCameraId(@NonNull String cameraId) {
        mHandler.postDelayed(
                () -> removeCameraId(cameraId),
                CAMERA_CLOSED_LETTERBOX_UPDATE_DELAY_MS);
    }

    private void removeCameraId(@NonNull String cameraId) {
        synchronized (mWmService.mGlobalLock) {
            if (!mScheduledToBeRemovedCameraIdSet.remove(cameraId)) {
                // Already reconnected to this camera, no need to clean up.
                return;
            }
            final boolean canClose = mAppCompatCameraStatePolicy.canCameraBeClosed(cameraId);
            if (canClose) {
                // Finish cleaning up.
                mCameraIdPackageBiMapping.removeCameraId(cameraId);
                mAppCompatCameraStatePolicy.onCameraClosed();
            } else {
                // Not ready to process closure yet - the camera activity might be refreshing.
                // Try again later.
                rescheduleRemoveCameraActivity(cameraId);
            }
        }
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

        final List<ActivityRecord> activitiesOfPackageWhichOpenedCamera = new ArrayList<>();
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
            return activitiesOfPackageWhichOpenedCamera.getFirst();
        }

        // Return null if we cannot determine which activity opened camera. This is preferred to
        // applying treatment to the wrong activity.
        Slog.w(TAG, "Cannot determine which activity opened camera.");
        return null;
    }

    String getSummary() {
        return " CameraIdPackageNameBiMapping="
                + mCameraIdPackageBiMapping
                .getSummaryForDisplayRotationHistoryRecord();
    }
}
