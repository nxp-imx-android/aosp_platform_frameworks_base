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
package com.android.wm.shell.pip2.phone;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControl;

import androidx.annotation.VisibleForTesting;

/**
 * Handler for moving PiP window to another display when the device is connected to external
 * display(s) in extended mode.
 */
public class PipDisplayTransferHandler implements
        PipTransitionState.PipTransitionStateChangedListener {

    private static final String TAG = "PipDisplayTransferHandler";
    static final String ORIGIN_DISPLAY_ID_KEY = "origin_display_id";
    static final String TARGET_DISPLAY_ID_KEY = "target_display_id";

    @NonNull private final PipTransitionState mPipTransitionState;
    @NonNull private final PipScheduler mPipScheduler;
    @VisibleForTesting boolean mWaitingForDisplayTransfer;

    public PipDisplayTransferHandler(PipTransitionState pipTransitionState,
            PipScheduler pipScheduler) {
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this::onPipTransitionStateChanged);
        mPipScheduler = pipScheduler;
    }

    void scheduleMovePipToDisplay(int originDisplayId, int targetDisplayId) {
        Bundle extra = new Bundle();
        extra.putInt(ORIGIN_DISPLAY_ID_KEY, originDisplayId);
        extra.putInt(TARGET_DISPLAY_ID_KEY, targetDisplayId);

        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                if (extra == null || !extra.containsKey(ORIGIN_DISPLAY_ID_KEY)
                        || !extra.containsKey(TARGET_DISPLAY_ID_KEY)) {
                    break;
                }
                mWaitingForDisplayTransfer = true;

                mPipScheduler.scheduleMoveToDisplay(extra.getInt(ORIGIN_DISPLAY_ID_KEY),
                        extra.getInt(TARGET_DISPLAY_ID_KEY));
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                if (extra == null || !mWaitingForDisplayTransfer) {
                    break;
                }

                final SurfaceControl.Transaction startTx = extra.getParcelable(
                        PipTransition.PIP_START_TX, SurfaceControl.Transaction.class);
                final Rect destinationBounds = extra.getParcelable(
                        PipTransition.PIP_DESTINATION_BOUNDS, Rect.class);

                startMoveToDisplayAnimation(startTx, destinationBounds);
        }
    }

    private void startMoveToDisplayAnimation(SurfaceControl.Transaction startTx,
            Rect destinationBounds) {
        if (startTx == null) return;

        startTx.apply();
        mPipScheduler.scheduleFinishResizePip(destinationBounds);
    }
}
