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

import static com.android.window.flags.Flags.enablePresentationForConnectedDisplays;

import android.annotation.NonNull;
import android.util.IntArray;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.protolog.WmProtoLogGroups;

/**
 * Manages presentation windows.
 */
class PresentationController {

    // TODO(b/395475549): Add support for display add/remove, and activity move across displays.
    private final IntArray mPresentingDisplayIds = new IntArray();

    PresentationController() {}

    private boolean isPresenting(int displayId) {
        return mPresentingDisplayIds.contains(displayId);
    }

    boolean shouldOccludeActivities(int displayId) {
        // All activities on the presenting display must be hidden so that malicious apps can't do
        // tap jacking (b/391466268).
        // For now, this should only be applied to external displays because presentations can only
        // be shown on them.
        // TODO(b/390481621): Disallow a presentation from covering its controlling activity so that
        // the presentation won't stop its controlling activity.
        return enablePresentationForConnectedDisplays() && isPresenting(displayId);
    }

    void onPresentationAdded(@NonNull WindowState win) {
        final int displayId = win.getDisplayId();
        if (isPresenting(displayId)) {
            return;
        }
        ProtoLog.v(WmProtoLogGroups.WM_DEBUG_PRESENTATION, "Presentation added to display %d: %s",
                win.getDisplayId(), win);
        mPresentingDisplayIds.add(win.getDisplayId());
        win.mWmService.mDisplayManagerInternal.onPresentation(displayId, /*isShown=*/ true);
    }

    void onPresentationRemoved(@NonNull WindowState win) {
        final int displayId = win.getDisplayId();
        if (!isPresenting(displayId)) {
            return;
        }
        ProtoLog.v(WmProtoLogGroups.WM_DEBUG_PRESENTATION,
                "Presentation removed from display %d: %s", win.getDisplayId(), win);
        // TODO(b/393945496): Make sure that there's one presentation at most per display.
        final int displayIdIndex = mPresentingDisplayIds.indexOf(displayId);
        if (displayIdIndex != -1) {
            mPresentingDisplayIds.remove(displayIdIndex);
        }
        win.mWmService.mDisplayManagerInternal.onPresentation(displayId, /*isShown=*/ false);
    }
}
