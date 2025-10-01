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

package android.app;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.os.OutcomeReceiver;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * Encapsulates information about task properties on a specific display controlled by system
 * policies, such as those from the window manager service.
 *
 * <p>It can be used to check if tasks can be programmatically repositioned on a specific display
 * before attempting an operation like {@link ActivityManager.AppTask#moveTaskTo(TaskLocation,
 * Executor, OutcomeReceiver)}.
 */
@FlaggedApi(com.android.window.flags.Flags.FLAG_ENABLE_TASK_MOVE_ALLOWED_LISTENER_API)
public final class TaskDisplayPolicyState {

    /**
     * An indicator of an unknown state.
     *
     * @see ActivityManager.AppTask#moveTaskTo(TaskLocation, Executor, OutcomeReceiver)
     */
    public static final int TASK_MOVE_UNKNOWN = 0;

    /**
     * Moving tasks is allowed on a display.
     *
     * @see ActivityManager.AppTask#moveTaskTo(TaskLocation, Executor, OutcomeReceiver)
     */
    public static final int TASK_MOVE_ALLOWED = 1;

    /**
     * Moving tasks is not allowed on a display.
     *
     * @see ActivityManager.AppTask#moveTaskTo(TaskLocation, Executor, OutcomeReceiver)
     */
    public static final int TASK_MOVE_DISALLOWED = 2;

    /** @hide */
    @IntDef(
            prefix = {"TASK_MOVE_"},
            value = {TASK_MOVE_ALLOWED, TASK_MOVE_DISALLOWED, TASK_MOVE_UNKNOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TaskMoveState {}

    private final int mDisplayId;
    private final int mTaskMoveState;

    /** @hide */
    public TaskDisplayPolicyState(int displayId, @TaskMoveState int taskMoveState) {
        mDisplayId = displayId;
        mTaskMoveState = taskMoveState;
    }

    /**
     * @return display ID of the display whose policy this {@link TaskDisplayPolicyState} describes.
     * @see android.hardware.display.DisplayManager#getDisplay(int)
     */
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * Provides information whether the display allows moving tasks it hosts.
     */
    public @TaskMoveState int getTaskMoveState() {
        return mTaskMoveState;
    }
}
