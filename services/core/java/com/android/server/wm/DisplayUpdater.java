/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_FLAG_DISPLAY_LEVEL_TRANSITION;

import android.annotation.Nullable;
import android.hardware.display.DisplayManager;
import android.util.Slog;
import android.util.SparseArray;
import android.view.DisplayInfo;
import android.window.TransitionRequestInfo.DisplayChange;

import java.util.ArrayList;
import java.util.List;

/**
 * Class responsible for receiving updates about changes in displays from DisplayManager
 * and applying these changes to WindowManager hierarchy
 */
class DisplayUpdater {

    // TODO: b/448471638 - disable by default after rolling out to nextfood
    // Verbose logging is currently enabled to debug potential issues during development
    private static final boolean DEBUG = true;

    private static final String TAG = "DisplayUpdater";

    private final RootWindowContainer mRootWindowContainer;

    DisplayUpdater(RootWindowContainer rootWindowContainer) {
        mRootWindowContainer = rootWindowContainer;
        mRootWindowContainer.mDisplayManager.registerDisplayListener(new DisplayUpdatesListener(),
                mRootWindowContainer.mService.mUiHandler);
    }

    /**
     * Requests an update of all displays, it will schedule retrieval of the latest displays state
     * whenever transition queue is idle, and apply the latest DisplayManager state to WindowManager
     * using a Shell transition
     *
     * @param onChangesApplied a callback that will be invoked after the changes are applied to
     *                         WM Core hierarchy (DisplayContents are updated)
     */
    void updateDisplays(@Nullable Runnable onChangesApplied) {
        final Transition transition = new Transition(TRANSIT_CHANGE,
                /* flags= */ TRANSIT_FLAG_DISPLAY_LEVEL_TRANSITION,
                mRootWindowContainer.mTransitionController,
                mRootWindowContainer.mTransitionController.mSyncEngine);

        mRootWindowContainer.mTransitionController.startCollectOrQueue(transition, deferred -> {
            final DisplayInfos displays = getNonOverrideDisplayInfos();

            if (DEBUG) {
                Slog.d(TAG, "updateDisplays: started collecting, received display infos = "
                        + displays.displayInfos());
            }

            mRootWindowContainer.mWmService.mAtmService.mChainTracker.start("dispChg", transition);
            mRootWindowContainer.mWmService.mAtmService.deferWindowLayout();

            try {
                final List<DisplayChange> displayChanges = applyDisplayInfos(transition,
                        displays.displayInfos());
                if (onChangesApplied != null) onChangesApplied.run();
                final boolean shouldStartTransition = !transition.mParticipants.isEmpty();

                if (shouldStartTransition) {
                    mRootWindowContainer.mTransitionController.requestStartTransition(transition,
                            /* startTask= */ null, /* remoteTransition= */ null, displayChanges);
                } else {
                    if (DEBUG) {
                        Slog.d(TAG, "Transition aborted: no participants");
                    }
                    transition.abort();
                }
            } finally {
                mRootWindowContainer.mWmService.mAtmService.continueWindowLayout();
                mRootWindowContainer.mWmService.mAtmService.mChainTracker.end();
            }
        });
    }

    private List<DisplayChange> applyDisplayInfos(Transition transition,
            SparseArray<DisplayInfo> newDisplayInfos) {
        final List<DisplayChange> displayChanges = new ArrayList<>();
        for (int i = 0; i < newDisplayInfos.size(); i++) {
            final int displayId = newDisplayInfos.keyAt(i);
            final DisplayContent displayContent = mRootWindowContainer.getDisplayContent(displayId);

            if (displayContent == null) {
                // Display is connected
                // TODO: b/448471638 - handle display creation
            } else {
                // Display is changed
                // TODO: b/448471638 - handle display change
            }
        }

        // Displays are removed
        mRootWindowContainer.forAllDisplays(displayContent -> {
            if (!newDisplayInfos.contains(displayContent.getDisplayId())) {
                // TODO: b/448471638 - handle display removals
            }
        });

        return displayChanges;
    }

    // TODO: b/448471638 - this is just a placeholder implementation of DisplayManager future API,
    //  replace with the actual calls when DisplayManager is ready
    record DisplayInfos(SparseArray<DisplayInfo> displayInfos) {}

    private DisplayInfos getNonOverrideDisplayInfos() {
        final SparseArray<DisplayInfo> displayInfos = new SparseArray<>();
        final int[] displayIds = mRootWindowContainer.mDisplayManagerInternal
                        .getDisplayIds(/* includeDisabled= */ false);
        for (int i = 0; i < displayIds.length; i++) {
            final int displayId = displayIds[i];
            final DisplayInfo displayInfo = new DisplayInfo();
            mRootWindowContainer.mDisplayManagerInternal.getNonOverrideDisplayInfo(displayId,
                    displayInfo);
            displayInfos.put(displayId, displayInfo);
        }
        return new DisplayInfos(displayInfos);
    }

    private class DisplayUpdatesListener implements DisplayManager.DisplayListener {
        @Override
        public void onDisplayAdded(int displayId) {
            synchronized (mRootWindowContainer.mService.mGlobalLock) {
                updateDisplays(null);
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            synchronized (mRootWindowContainer.mService.mGlobalLock) {
                updateDisplays(null);
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {
            synchronized (mRootWindowContainer.mService.mGlobalLock) {
                updateDisplays(null);
            }
        }
    }
}
