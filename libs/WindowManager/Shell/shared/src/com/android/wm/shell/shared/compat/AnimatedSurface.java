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

package com.android.wm.shell.shared.compat;

import android.annotation.Nullable;
import android.app.TaskInfo;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

/**
 * Abstraction layer for surfaces that can be animated as part of a remote animation or remote
 * transition.
 *
 * This class is meant to be a drop-in replacement for usages of RemoteAnimationTarget as an
 * intermediate step of migrating to the Shell APIs. Once support for the old APIs is removed, this
 * abstraction can be swapped out for TransitionInfo.Change.
 */
public class AnimatedSurface {
    /** Used to animate this window. */
    public final SurfaceControl leash;

    /** The state of this window at the beginning of the animation, if any. */
    @Nullable
    public final WindowAnimationState startState;

    /** The state of this window at the end of the animation. */
    public final WindowAnimationState endState;

    /** The background color of the task populating this window. */
    public final int backgroundColor;

    /** Whether this window's background should be ignored and considered transparent. */
    public final boolean isTranslucent;

    /** The [TaskInfo] representing the content of this window. */
    @Nullable
    public final TaskInfo taskInfo;

    /** Whether this Surface is opening, closing, or doing something else during the animation. */
    public final Mode mode;

    public AnimatedSurface(
            SurfaceControl leash, @Nullable WindowAnimationState startState,
            WindowAnimationState endState, int backgroundColor, boolean isTranslucent,
            @Nullable TaskInfo taskInfo, Mode mode) {
        this.leash = leash;
        this.startState = startState;
        this.endState = endState;
        this.backgroundColor = backgroundColor;
        this.isTranslucent = isTranslucent;
        this.taskInfo = taskInfo;
        this.mode = mode;
    }

    /** See {@link AnimatedSurface#from(RemoteAnimationTarget, WindowAnimationState)}. */
    public static AnimatedSurface from(RemoteAnimationTarget target) {
        return from(target, null /* startState */);
    }

    /** Factory for compat use with the pre-Shell APIs. */
    public static AnimatedSurface from(
            RemoteAnimationTarget target, @Nullable WindowAnimationState startState) {
        WindowAnimationState endState = new WindowAnimationState();
        endState.bounds = new RectF(target.screenSpaceBounds);

        Mode mode = switch (target.mode) {
            case RemoteAnimationTarget.MODE_CLOSING -> Mode.CLOSING;
            case RemoteAnimationTarget.MODE_OPENING -> Mode.OPENING;
            default -> Mode.OTHER;
        };

        return new AnimatedSurface(
                target.leash, startState, endState, target.backgroundColor, target.isTranslucent,
                target.taskInfo, mode);
    }

    /** See {@link AnimatedSurface#from(TransitionInfo.Change, WindowAnimationState)}. */
    public static AnimatedSurface from(TransitionInfo.Change change) {
        return from(change, null /* startState */);
    }

    /** Factory for direct use with the Shell APIs. */
    public static AnimatedSurface from(
            TransitionInfo.Change change, @Nullable WindowAnimationState startState) {
        WindowAnimationState endState = new WindowAnimationState();
        endState.bounds = new RectF(change.getEndAbsBounds());

        Mode mode = switch (change.getMode()) {
            case WindowManager.TRANSIT_CLOSE, WindowManager.TRANSIT_TO_BACK -> Mode.CLOSING;
            case WindowManager.TRANSIT_OPEN, WindowManager.TRANSIT_TO_FRONT -> Mode.OPENING;
            default -> Mode.OTHER;
        };

        return new AnimatedSurface(
                change.getLeash(), startState, endState, change.getBackgroundColor(),
                change.hasFlags(TransitionInfo.FLAG_TRANSLUCENT), change.getTaskInfo(), mode);
    }

    /** Checks whether this surface is closing based on its mode. */
    public boolean isClosing() {
        return mode == Mode.CLOSING;
    }

    /** Checks whether this surface is opening based on its mode. */
    public boolean isOpening() {
        return mode == Mode.OPENING;
    }

    public enum Mode {
        CLOSING,
        OPENING,
        OTHER
    }
}
