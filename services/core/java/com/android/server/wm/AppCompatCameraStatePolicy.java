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
 * Interface that camera compat policies to implement to be notified of camera open/close signals.
 */
interface AppCompatCameraStatePolicy {
    /**
     * Notifies the compat listener that a task has opened camera.
     */
    void onCameraOpened(@NonNull ActivityRecord cameraActivity);

    /**
     * Checks whether a listener is ready to do a cleanup when camera is closed.
     *
     * <p>The notifier might try again if false is returned.
     */
    // TODO(b/336474959): try to decouple `cameraId` from the listeners, as the treatment does not
    //  change based on the cameraId - CameraStateMonitor should keep track of this.
    //  This method actually checks "did an activity only temporarily close the camera", because a
    //  refresh for compatibility is triggered.
    boolean canCameraBeClosed(@NonNull String cameraId);

    /**
     * Notifies the compat listener that camera is closed.
     */
    void onCameraClosed();
}
