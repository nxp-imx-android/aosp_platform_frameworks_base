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

import android.annotation.NonNull;

/**
 * Interface for tracking camera state, and notifying {@link AppCompatCameraStatePolicy} of changes.
 *
 * <p>{@link AppCompatCameraStateStrategy} implementations should track which apps hold the camera
 * access, and any ongoing camera state changes changes. 'track' methods should always be called
 * before appropriate 'maybeNotify' methods for the same task-cameraId pair, but the order of
 * open/close can vary, for example due to built-in delays from the caller.
 */
interface AppCompatCameraStateStrategy {
    /**
     * Allows saving cameraId, to be processed later on
     * {@link AppCompatCameraStateStrategy#notifyPolicyCameraOpenedIfNeeded} after a delay.
     *
     * <p>The {@link AppCompatCameraStateStrategy} should track which camera operations have been
     * started (delayed), as camera opened/closed operations often compete with each other, and due
     * to built-in delays can cause different order of these operations when they are finally
     * processed. Examples of quickly closing and opening the camera: activity relaunch due to
     * configuration change, switching front/back cameras, new app requesting camera and taking the
     * access rights away from the existing camera app.
     */
    void trackOnCameraOpened(@NonNull String cameraId);

    /**
     * Processes camera opened signal, and if the change is relevant for {@link
     * AppCompatCameraStatePolicy} calls {@link AppCompatCameraStatePolicy#onCameraOpened}.
     */
    void notifyPolicyCameraOpenedIfNeeded(@NonNull String cameraId, @NonNull String packageName,
            @NonNull AppCompatCameraStatePolicy policy);

    /**
     * Allows saving cameraId to be processed later on
     * {@link AppCompatCameraStateStrategy#notifyPolicyCameraClosedIfNeeded} after a delay.
     *
     * <p>The {@link AppCompatCameraStateStrategy} should track which camera operations have been
     * started (delayed), as camera opened/closed operations often compete with each other, and due
     * to built-in delays can cause different order of these operations when they are finally
     * processed. Examples of quickly closing and opening the camera: activity relaunch due to
     * configuration change, switching front/back cameras, new app requesting camera and taking the
     * access rights away from the existing camera app.
     */
    void trackOnCameraClosed(@NonNull String cameraId);


    /**
     * Processes camera closed signal, and if the change is relevant for {@link
     * AppCompatCameraStatePolicy} calls {@link AppCompatCameraStatePolicy#onCameraClosed}.
     *
     * @return true if policies were able to handle the camera closed event, or false if it needs to
     * be rescheduled.
     */
    boolean notifyPolicyCameraClosedIfNeeded(@NonNull String cameraId,
            @NonNull AppCompatCameraStatePolicy policy);

    /** Returns whether a given activity holds any camera opened. */
    boolean isCameraRunningForActivity(@NonNull ActivityRecord activity);

    /** Returns whether a given activity holds a specific camera opened. */
    // TODO(b/336474959): try to decouple `cameraId` from the listeners.
    boolean isCameraWithIdRunningForActivity(@NonNull ActivityRecord activity,
            @NonNull String cameraId);
}
