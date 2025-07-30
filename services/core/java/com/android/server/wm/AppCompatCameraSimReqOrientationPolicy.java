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

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_NONE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_UNSPECIFIED;
import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOW_CONFIG_APP_BOUNDS;
import static android.app.WindowConfiguration.WINDOW_CONFIG_DISPLAY_ROTATION;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_UNDEFINED;

import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.CameraCompatTaskInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.util.Slog;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.WmProtoLogGroups;
import com.android.window.flags.Flags;

/**
 * Policy for camera compatibility simulate requested orientation treatment.
 *
 * <p>This treatment can be applied to a fixed-orientation activity while camera is open.
 * The treatment letterboxes or pillarboxes the activity to the expected orientation and provides
 * changes to the camera and display orientation signals to match those expected on a portrait
 * device in that orientation (for example, on a standard phone).
 */
final class AppCompatCameraSimReqOrientationPolicy implements AppCompatCameraStatePolicy,
        ActivityRefresher.Evaluator {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "AppCompatCameraSROPolicy" : TAG_WM;

    @NonNull
    private final DisplayContent mDisplayContent;
    @NonNull
    private final ActivityRefresher mActivityRefresher;
    @NonNull
    private final AppCompatCameraStateSource mCameraStateNotifier;
    @NonNull
    private final CameraStateMonitor mCameraStateMonitor;
    @NonNull
    private final AppCompatCameraRotationState mCameraDisplayRotationProvider;

    // TODO(b/380840084): Clean up after flag is launched.
    @Nullable
    private Task mCameraTask;

    /**
     * Value toggled on {@link #start()} to {@code true} and on {@link #dispose()} to {@code false}.
     */
    private boolean mIsRunning;

    AppCompatCameraSimReqOrientationPolicy(@NonNull DisplayContent displayContent,
            @NonNull CameraStateMonitor cameraStateMonitor,
            @NonNull AppCompatCameraStateSource cameraStateNotifier,
            @NonNull ActivityRefresher activityRefresher) {
        mDisplayContent = displayContent;
        mCameraStateMonitor = cameraStateMonitor;
        mCameraStateNotifier = cameraStateNotifier;
        mActivityRefresher = activityRefresher;
        mCameraDisplayRotationProvider = new AppCompatCameraRotationState(displayContent);
    }

    void start() {
        mCameraStateNotifier.addCameraStatePolicy(this);
        mActivityRefresher.addEvaluator(this);
        mCameraDisplayRotationProvider.start();
        mIsRunning = true;
    }

    /** Releases camera callback listener. */
    void dispose() {
        mCameraStateNotifier.removeCameraStatePolicy(this);
        mActivityRefresher.removeEvaluator(this);
        mCameraDisplayRotationProvider.dispose();
        mIsRunning = false;
    }

    @VisibleForTesting
    boolean isRunning() {
        return mIsRunning;
    }

    @Surface.Rotation
    int getCameraDeviceRotation() {
        return mCameraDisplayRotationProvider.getCameraDeviceRotation();
    }

    // Refreshing only when configuration changes after applying camera compat treatment.
    @Override
    public boolean shouldRefreshActivity(@NonNull ActivityRecord activity,
            @NonNull Configuration newConfig,
            @NonNull Configuration lastReportedConfig) {
        return isTreatmentEnabledForActivity(activity, /* shouldCheckOrientation= */ true)
                && haveCameraCompatAttributesChanged(newConfig, lastReportedConfig);
    }

    private boolean haveCameraCompatAttributesChanged(@NonNull Configuration newConfig,
            @NonNull Configuration lastReportedConfig) {
        // Camera compat treatment changes the following:
        // - Letterboxes app bounds to camera compat aspect ratio in app's requested orientation,
        // - Changes display rotation so it matches what the app expects in its chosen orientation,
        // - Rotate-and-crop camera feed to match that orientation (this changes iff the display
        //     rotation changes, so no need to check).
        final long diff = newConfig.windowConfiguration.diff(lastReportedConfig.windowConfiguration,
                /* compareUndefined= */ true);
        final boolean appBoundsChanged = (diff & WINDOW_CONFIG_APP_BOUNDS) != 0;
        final boolean displayRotationChanged = (diff & WINDOW_CONFIG_DISPLAY_ROTATION) != 0;
        return appBoundsChanged || displayRotationChanged;
    }

    @Override
    public void onCameraOpened(@NonNull WindowProcessController appProcess, @NonNull Task task) {
        final ActivityRecord cameraActivity = getTopActivityFromCameraTask(task);
        // Do not check orientation outside of the config recompute, as the app's orientation intent
        // might be obscured by a fullscreen override. Especially for apps which have a camera
        // functionality which is not the main focus of the app: while most of the app might work
        // well in fullscreen, often the camera setup still assumes it will run on a portrait device
        // in its natural orientation and comes out stretched or sideways.
        // Config recalculation will later check the original orientation to avoid applying
        // treatment to apps optimized for large screens.
        if (cameraActivity == null || !isTreatmentEnabledForActivity(cameraActivity,
                /* shouldCheckOrientation= */ false)) {
            return;
        }

        if (!Flags.enableCameraCompatTrackTaskAndAppBugfix()) {
            mCameraTask = cameraActivity.getTask();
        }
        updateAndDispatchCameraConfiguration(appProcess, task);
    }

    @Override
    public boolean canCameraBeClosed(@NonNull String cameraId, @NonNull Task task) {
        // Top activity in the same task as the camera activity, or `null` if the task is
        // closed.
        final ActivityRecord topActivity = getTopActivityFromCameraTask(task);
        if (topActivity == null) {
            return true;
        }

        if (isActivityForCameraIdRefreshing(topActivity, cameraId)) {
            ProtoLog.v(WmProtoLogGroups.WM_DEBUG_STATES,
                    "Display id=%d is notified that Camera %s is closed but activity is"
                            + " still refreshing. Rescheduling an update.",
                    mDisplayContent.mDisplayId, cameraId);
            return false;
        }
        return true;
    }

    @Override
    public void onCameraClosed(@Nullable WindowProcessController appProcess, @Nullable Task task) {
        if (Flags.enableCameraCompatTrackTaskAndAppBugfix()) {
            // With the refactoring for `enableCameraCompatTrackTaskAndAppBugfix`, `onCameraClosed`
            // is only received when camera is actually closed, and not on activity refresh or when
            // switching cameras. Proceed to revert camera compat mode.
            updateAndDispatchCameraConfiguration(appProcess, task);
        } else {
            // Top activity in the same task as the camera activity, or `null` if the task is
            // closed.
            final ActivityRecord topActivity = getTopActivityFromCameraTask(mCameraTask);
            // Only clean up if the camera is not running - this close signal could be from
            // switching cameras (e.g. back to front camera, and vice versa).
            if (topActivity == null || !mCameraStateMonitor.isCameraRunningForActivity(
                    topActivity)) {
                updateAndDispatchCameraConfiguration(appProcess, task);
                mCameraTask = null;
            }
        }
    }

    private void updateAndDispatchCameraConfiguration(@Nullable WindowProcessController app,
            @Nullable Task task) {
        // Without `enableCameraCompatTrackTaskAndAppBugfix` refactoring, `CameraStateMonitor` might
        // not be able to fetch the correct task.
        // TODO(b/380840084): Clean up after `enableCameraCompatTrackTaskAndAppBugfix` flag launch.
        if (!Flags.enableCameraCompatTrackTaskAndAppBugfix()) {
            if (mCameraTask == null) {
                return;
            }
            task = mCameraTask;
        }

        final ActivityRecord activity = getTopActivityFromCameraTask(task);
        if (activity != null) {
            activity.recomputeConfiguration();
        }
        if (task != null) {
            task.dispatchTaskInfoChangedIfNeeded(/* force= */ true);
        }
        if (app != null) {
            updateCompatibilityInfo(app, activity);
        }
        if (activity != null) {
            activity.ensureActivityConfiguration(/* ignoreVisibility= */ true);
        }
    }

    private void updateCompatibilityInfo(@NonNull WindowProcessController app,
            @Nullable ActivityRecord activityRecord) {
        if (app.getThread() == null || app.mInfo == null) {
            Slog.w(TAG, "Insufficient app information. Cannot revert display rotation sandboxing.");
            return;
        }
        final CompatibilityInfo compatibilityInfo = mDisplayContent.mAtmService
                .compatibilityInfoForPackageLocked(app.mInfo);
        // CompatibilityInfo fields are static, so even if task or activity has been closed, this
        // state should be updated.
        final int displayRotation = activityRecord == null
                ? ROTATION_UNDEFINED
                : CameraCompatTaskInfo.getDisplayRotationFromCameraCompatMode(
                        getCameraCompatMode(activityRecord));
        compatibilityInfo.applicationDisplayRotation = displayRotation;
        if (Flags.enableCameraCompatCompatibilityInfoRotateAndCropBugfix()) {
            compatibilityInfo.applicationCameraRotation =
                    getCameraRotationFromSandboxedDisplayRotation(displayRotation);
        }
        try {
            // TODO(b/380840084): Consider using a ClientTransaction for this update.
            app.getThread().updatePackageCompatibilityInfo(app.mInfo.packageName,
                    compatibilityInfo);
        } catch (RemoteException e) {
            ProtoLog.w(WmProtoLogGroups.WM_DEBUG_STATES,
                    "Unable to update CompatibilityInfo for app %s", app);
        }
    }

    /**
     * Calculates the angle for camera feed rotate-and-crop.
     *
     * <p>Camera apps most commonly calculate the preview rotation with the formula (simplified):
     * {code rotation = cameraSensorRotation - displayRotation}. When display rotation is sandboxed,
     * camera preview needs to be rotated by the same amount to keep the preview upright.
     */
    private int getCameraRotationFromSandboxedDisplayRotation(@Surface.Rotation int
            displayRotation) {
        if (displayRotation == ROTATION_UNDEFINED) {
            return ROTATION_UNDEFINED;
        }
        int realCameraRotation = mCameraDisplayRotationProvider.getCameraDeviceRotation();
        if (displayRotation == realCameraRotation) {
            // No need to rotate and crop, display rotation is unchanged.
            return ROTATION_UNDEFINED;
        }

        final int displayRotationInDegrees = getRotationToDegrees(displayRotation);
        final int realCameraRotationInDegrees = getRotationToDegrees(realCameraRotation);
        // Feed needs to be rotated by the same amount as the display sandboxing difference, in
        // order to keep the preview upright.
        return getRotationDegreesToEnum((displayRotationInDegrees - realCameraRotationInDegrees
                + 360) % 360);
    }

    private static int getRotationToDegrees(@Surface.Rotation int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0 -> {
                return 0;
            }
            case Surface.ROTATION_90 -> {
                return 90;
            }
            case Surface.ROTATION_180 -> {
                return 180;
            }
            case Surface.ROTATION_270 -> {
                return 270;
            }
            default -> {
                return ROTATION_UNDEFINED;
            }
        }
    }

    @Surface.Rotation
    private static int getRotationDegreesToEnum(int rotationDegrees) {
        switch (rotationDegrees) {
            case 0 -> {
                return Surface.ROTATION_0;
            }
            case 90 -> {
                return Surface.ROTATION_90;
            }
            case 180 -> {
                return Surface.ROTATION_180;
            }
            case 270 -> {
                return Surface.ROTATION_270;
            }
            default -> {
                return ROTATION_UNDEFINED;
            }
        }
    }

    /**
     * Returns true if letterboxing should be allowed for camera apps, even if otherwise it isn't.
     *
     * <p>Camera compat is currently the only use-case of letterboxing for desktop windowing.
     */
    boolean isFreeformLetterboxingForCameraAllowed(@NonNull ActivityRecord activity) {
        // Letterboxing is normally not allowed in desktop windowing.
        return isCameraRunningAndWindowingModeEligible(activity);
    }

    boolean shouldCameraCompatControlOrientation(@NonNull ActivityRecord activity) {
        return isCameraRunningAndWindowingModeEligible(activity);
    }

    boolean isCameraRunningAndWindowingModeEligible(@NonNull ActivityRecord activity) {
        return  activity.mAppCompatController.getCameraOverrides()
                .shouldApplyCameraCompatSimReqOrientationTreatment()
                && isWindowingModeEligible(activity)
                && mCameraStateMonitor.isCameraRunningForActivity(activity);
    }

    private boolean isWindowingModeEligible(@NonNull ActivityRecord activity) {
        return activity.inFreeformWindowingMode()
                || (Flags.cameraCompatUnifyCameraPolicies() && activity.inMultiWindowMode());
    }

    boolean shouldCameraCompatControlAspectRatio(@NonNull ActivityRecord activity) {
        // Camera compat should direct aspect ratio when in camera compat mode, unless an app has a
        // different camera compat aspect ratio set: this allows per-app camera compat override
        // aspect ratio to be smaller than the default.
        return isInCameraCompatMode(activity)
                && !activity.mAppCompatController.getCameraOverrides()
                        .isOverrideMinAspectRatioForCameraEnabled();
    }

    boolean isInCameraCompatMode(@NonNull ActivityRecord activity) {
        return getCameraCompatMode(activity) != CAMERA_COMPAT_UNSPECIFIED
                && getCameraCompatMode(activity) != CAMERA_COMPAT_NONE;
    }

    float getCameraCompatAspectRatio(@NonNull ActivityRecord activityRecord) {
        if (shouldCameraCompatControlAspectRatio(activityRecord)) {
            return activityRecord.mWmService.mAppCompatConfiguration.getCameraCompatAspectRatio();
        }

        return MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
    }

    @CameraCompatTaskInfo.CameraCompatMode
    int getCameraCompatMode(@NonNull ActivityRecord topActivity) {
        if (!isTreatmentEnabledForActivity(topActivity, /* shouldCheckOrientation= */ true)) {
            return CAMERA_COMPAT_NONE;
        }

        // This treatment targets only devices with portrait natural orientation, which most tablets
        // have.
        if (!mCameraDisplayRotationProvider.isCameraDeviceNaturalOrientationPortrait()) {
            // TODO(b/365725400): handle landscape natural orientation.
            return CAMERA_COMPAT_NONE;
        }

        final int appOrientation = topActivity.getRequestedConfigurationOrientation();
        final boolean isDisplayRotationPortrait = mCameraDisplayRotationProvider
                .isCameraDeviceOrientationPortrait();
        if (appOrientation == ORIENTATION_PORTRAIT) {
            if (isDisplayRotationPortrait) {
                return CAMERA_COMPAT_PORTRAIT_DEVICE_IN_PORTRAIT;
            } else {
                return CAMERA_COMPAT_PORTRAIT_DEVICE_IN_LANDSCAPE;
            }
        } else if (appOrientation == ORIENTATION_LANDSCAPE) {
            if (isDisplayRotationPortrait) {
                return CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_PORTRAIT;
            } else {
                return CAMERA_COMPAT_LANDSCAPE_DEVICE_IN_LANDSCAPE;
            }
        }

        return CAMERA_COMPAT_NONE;
    }

    /**
     * Whether camera compat treatment is applicable for the given activity, ignoring its windowing
     * mode.
     *
     * <p>Conditions that need to be met:
     * <ul>
     *     <li>Treatment is enabled.
     *     <li>Camera is active for the package.
     *     <li>The app has a fixed orientation if {@code checkOrientation} is true.
     * </ul>
     *
     * @param checkOrientation Whether to take apps orientation into account for this check. Only
     *                         fixed-orientation apps should be targeted, but this might be
     *                         obscured by OEMs via fullscreen override and the app's original
     *                         intent inaccessible when the camera opens. Thus, policy would pass
     *                         {@code false} here when considering whether to trigger config
     *                         recalculation, and later pass {@code true} during recalculation.
     */
    @VisibleForTesting
    boolean isTreatmentEnabledForActivity(@NonNull ActivityRecord activity,
            boolean checkOrientation) {
        int orientation = activity.getRequestedConfigurationOrientation();
        return activity.mAppCompatController.getCameraOverrides()
                .shouldApplyCameraCompatSimReqOrientationTreatment()
                && mCameraStateMonitor.isCameraRunningForActivity(activity)
                && (!checkOrientation || orientation != ORIENTATION_UNDEFINED)
                && activity.inFreeformWindowingMode()
                // "locked" and "nosensor" values are often used by camera apps that can't
                // handle dynamic changes so we shouldn't force-letterbox them.
                && activity.getRequestedOrientation() != SCREEN_ORIENTATION_NOSENSOR
                && activity.getRequestedOrientation() != SCREEN_ORIENTATION_LOCKED
                // TODO(b/332665280): investigate whether we can support activity embedding.
                && !activity.isEmbedded();
    }

    @Nullable
    private ActivityRecord getTopActivityFromCameraTask(@Nullable Task task) {
        return task != null
                ? task.getTopActivity(/* isFinishing */ false, /* includeOverlays */ false)
                : null;
    }

    private boolean isActivityForCameraIdRefreshing(@NonNull ActivityRecord topActivity,
            @NonNull String cameraId) {
        if (!isTreatmentEnabledForActivity(topActivity, /* checkOrientation= */ true)
                || !mCameraStateMonitor.isCameraWithIdRunningForActivity(topActivity, cameraId)) {
            return false;
        }
        return topActivity.mAppCompatController.getCameraOverrides().isRefreshRequested();
    }
}
