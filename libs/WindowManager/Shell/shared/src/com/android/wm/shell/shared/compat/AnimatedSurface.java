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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.WindowManager.LayoutParams.INVALID_WINDOW_TYPE;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import java.util.Arrays;

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

    /**
     * The {@link SurfaceControl} for the starting state if this transition's mode is Mode.OTHER
     * (MODE_CHANGING) in {@link RemoteAnimationTarget}), {@code null)} otherwise. This is relative
     * to the app window.
     */
    public final SurfaceControl startLeash;

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
    public final ActivityManager.RunningTaskInfo taskInfo;

    /** Whether this Surface is opening, closing, or doing something else during the animation. */
    public final Mode mode;

    /** Bounds of the surface relative to the screen. */
    public final Rect screenSpaceBounds;

    /** Bounds of the surface relative to its parent */
    public final Rect localBounds;

    /** The starting bounds of the source container in screen space coordinates. */
    public final Rect startBounds;

    /** The insets of the main app window. */
    public final Rect contentInsets;

    /** The source position of the app, in screen spaces coordinates. */
    public final Point position;

    /** Difference in surface's start and end rotation. */
    public final int rotationChange;

    /** The window configuration of the surface. */
    public final WindowConfiguration windowConfiguration;

    /** The id of the task this app belongs to. */
    public final int taskId;

    /** The {@link android.view.WindowManager.LayoutParams.WindowType} of this window. */
    public final int windowType;

    /** Whether the activity is going to show IME on the target window after the app transition. */
    public final boolean willShowImeOnTarget;

    /** Whether the task is not presented in Recents UI. */
    public final boolean isNotInRecents;

    /** {@code true} if picture-in-picture permission is granted in
     * {@link android.app.AppOpsManager}
     */
    public final boolean allowEnterPip;

    public AnimatedSurface(
            SurfaceControl leash, @Nullable WindowAnimationState startState,
            WindowAnimationState endState, int backgroundColor, boolean isTranslucent,
            @Nullable ActivityManager.RunningTaskInfo taskInfo, Mode mode, Rect screenSpaceBounds,
            Rect localBounds, Point position, int rotationChange,
            WindowConfiguration windowConfiguration, int taskId, int windowType,
            SurfaceControl startLeash, Rect startBounds, Rect contentInsets,
            boolean willShowImeOnTarget, boolean isNotInRecents, boolean allowEnterPip) {
        this.leash = leash;
        this.startState = startState;
        this.endState = endState;
        this.backgroundColor = backgroundColor;
        this.isTranslucent = isTranslucent;
        this.taskInfo = taskInfo;
        this.mode = mode;
        this.screenSpaceBounds = screenSpaceBounds;
        this.localBounds = localBounds;
        this.position = position;
        this.rotationChange = rotationChange;
        this.windowConfiguration = windowConfiguration;
        this.taskId = taskId;
        this.windowType = windowType;
        this.startLeash = startLeash;
        this.startBounds = startBounds;
        this.contentInsets = contentInsets;
        this.willShowImeOnTarget = willShowImeOnTarget;
        this.isNotInRecents = isNotInRecents;
        this.allowEnterPip = allowEnterPip;
    }

    /** See {@link AnimatedSurface#from(RemoteAnimationTarget, WindowAnimationState)}. */
    public static AnimatedSurface from(RemoteAnimationTarget target) {
        return from(target, null /* startState */);
    }

    /** Utility function to convert RemoteAnimationTarget array to AnimatedSurface array. */
    public static AnimatedSurface[] mapFromTargets(RemoteAnimationTarget[] targets) {
        return Arrays.stream(targets)
                .map(AnimatedSurface::from)
                .toArray(AnimatedSurface[]::new);
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
                /* leash = */ target.leash,
                /* startState = */ startState,
                /* endState = */ endState,
                /* backgroundColor = */ target.backgroundColor,
                /* isTranslucent = */ target.isTranslucent,
                /* taskInfo = */ target.taskInfo,
                /* mode = */ mode,
                /* screenSpaceBounds = */ target.screenSpaceBounds,
                /* localBounds = */ target.localBounds,
                /* position = */ target.position,
                /* rotationChange = */ target.rotationChange,
                /* windowConfiguration = */ target.windowConfiguration,
                /* taskId = */ target.taskId,
                /* windowType = */ target.windowType,
                /* startLeash = */ target.startLeash,
                /* startBounds = */ target.startBounds,
                /* contentInsets = */ target.contentInsets,
                /* willShowImeOnTarget = */ target.willShowImeOnTarget,
                /* isNotInRecents = */ target.isNotInRecents,
                /* allowEnterPip = */ target.allowEnterPip);
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

        Rect localBounds = new Rect(change.getEndAbsBounds());
        localBounds.offsetTo(change.getEndRelOffset().x, change.getEndRelOffset().y);

        int rotationChange = change.getEndRotation() - change.getStartRotation();

        ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        WindowConfiguration windowConfiguration;
        int taskId;
        boolean isNotInRecents;

        if (taskInfo != null) {
            taskId = taskInfo.taskId;
            windowConfiguration = taskInfo.configuration.windowConfiguration;
            isNotInRecents = !taskInfo.isRunning;
        } else {
            taskId = INVALID_TASK_ID;
            windowConfiguration = new WindowConfiguration();
            isNotInRecents = true;
        }

        boolean willShowImeOnTarget = (change.getFlags() & TransitionInfo.FLAG_WILL_IME_SHOWN) != 0;

        return new AnimatedSurface(
                /* leash = */ change.getLeash(),
                /* startState = */ startState,
                /* endState = */ endState,
                /* backgroundColor = */ change.getBackgroundColor(),
                /* isTranslucent = */ change.hasFlags(TransitionInfo.FLAG_TRANSLUCENT),
                /* taskInfo = */ taskInfo,
                /* mode = */ mode,
                /* screenSpaceBounds = */ new Rect(change.getEndAbsBounds()),
                /* localBounds = */ localBounds,
                /* position = */ null,
                /* rotationChange = */ rotationChange,
                /* windowConfiguration = */ windowConfiguration,
                /* taskId = */ taskId,
                /* windowType = */ INVALID_WINDOW_TYPE,
                /* startLeash = */ null,
                /* startBounds = */ new Rect(change.getStartAbsBounds()),
                /* contentInsets = */ new Rect(0, 0, 0, 0),
                /* willShowImeOnTarget = */ willShowImeOnTarget,
                /* isNotInRecents = */ isNotInRecents,
                /* allowEnterPip = */ change.isAllowEnterPip());
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
