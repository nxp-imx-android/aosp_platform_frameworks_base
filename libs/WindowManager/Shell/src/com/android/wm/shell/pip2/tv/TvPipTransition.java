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

package com.android.wm.shell.pip2.tv;

import static android.view.WindowManager.TRANSIT_PIP;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.annotation.NonNull;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip.tv.TvPipMenuController;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

/**
 * A skeleton placeholder for the TV PiP2 Transition handler.
 * It does not handle any incoming requests.
 */
public class TvPipTransition extends PipTransitionController {
    private static final String TAG = "TvPip2Transition";

    @Nullable
    private IBinder mEnterPipTransition;

    public TvPipTransition(
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            TvPipBoundsState tvPipBoundsState,
            TvPipMenuController tvPipMenuController,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm) {
        super(shellInit, shellTaskOrganizer, transitions, tvPipBoundsState, tvPipMenuController,
                tvPipBoundsAlgorithm);
    }

    @Override
    protected void onInit() {
        if (PipFlags.isPip2ExperimentEnabled()) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: V2 Flag is ON, registering TvPip2Transition handler.", TAG);
            mTransitions.addHandler(this);
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        // Only look for a direct pip enter request
        if (requestHasPipEnter(request)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: handle PiP enter request", TAG);
            // Cache the token so we can identify this transition in startAnimation().
            mEnterPipTransition = transition;
        }
        return null;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // Check if this is the enter transition we handled in handleRequest().
        if (transition == mEnterPipTransition || info.getType() == TRANSIT_PIP) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: starting PiP enter animation", TAG);
        }
        return false;
    }
}
