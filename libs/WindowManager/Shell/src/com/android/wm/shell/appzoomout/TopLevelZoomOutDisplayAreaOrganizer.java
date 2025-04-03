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

package com.android.wm.shell.appzoomout;

import android.content.Context;
import android.util.ArrayMap;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;

import com.android.wm.shell.common.DisplayLayout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Display area organizer that manages the top level zoom out UI and states. */
public class TopLevelZoomOutDisplayAreaOrganizer extends DisplayAreaOrganizer {
    private final DisplayLayout mDisplayLayout = new DisplayLayout();
    private final Map<WindowContainerToken, SurfaceControl> mDisplayAreaTokenMap =
            new ArrayMap<>();

    private float mScale = 1f;

    public TopLevelZoomOutDisplayAreaOrganizer(DisplayLayout displayLayout, Executor mainExecutor) {
        super(mainExecutor);
        setDisplayLayout(displayLayout);
    }

    @Override
    public void onDisplayAreaAppeared(DisplayAreaInfo displayAreaInfo, SurfaceControl leash) {
        leash.setUnreleasedWarningCallSite(
                "TopLevelZoomDisplayAreaOrganizer.onDisplayAreaAppeared");
        if (displayAreaInfo.displayId == Display.DEFAULT_DISPLAY) {
            mDisplayAreaTokenMap.put(displayAreaInfo.token, leash);
        }
    }

    @Override
    public void onDisplayAreaVanished(DisplayAreaInfo displayAreaInfo) {
        final SurfaceControl leash = mDisplayAreaTokenMap.get(displayAreaInfo.token);
        if (leash != null) {
            leash.release();
        }
        mDisplayAreaTokenMap.remove(displayAreaInfo.token);
    }

    /**
     * Registers the TopLevelZoomOutDisplayAreaOrganizer to manage the display area of
     * {@link DisplayAreaOrganizer#FEATURE_WINDOWED_MAGNIFICATION}.
     */
    void registerOrganizer() {
        final List<DisplayAreaAppearedInfo> displayAreaInfos = registerOrganizer(
                DisplayAreaOrganizer.FEATURE_WINDOWED_MAGNIFICATION);
        for (int i = 0; i < displayAreaInfos.size(); i++) {
            final DisplayAreaAppearedInfo info = displayAreaInfos.get(i);
            onDisplayAreaAppeared(info.getDisplayAreaInfo(), info.getLeash());
        }
    }

    @Override
    public void unregisterOrganizer() {
        super.unregisterOrganizer();
        reset();
    }

    void setScale(float scale) {
        if (mScale == scale) {
            return;
        }

        mScale = scale;
        apply();
    }

    private void apply() {
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mDisplayAreaTokenMap.forEach((token, leash) -> updateSurface(tx, leash, mScale));
        tx.apply();
    }

    private void reset() {
        setScale(1f);
    }

    private void updateSurface(SurfaceControl.Transaction tx, SurfaceControl leash, float scale) {
        tx
                .setScale(leash, scale, scale)
                .setPosition(leash, (1f - scale) * mDisplayLayout.width() * 0.5f,
                        (1f - scale) * mDisplayLayout.height() * 0.5f);
    }

    void setDisplayLayout(DisplayLayout displayLayout) {
        mDisplayLayout.set(displayLayout);
    }

    void onRotateDisplay(Context context, int toRotation) {
        if (mDisplayLayout.rotation() == toRotation) {
            return;
        }
        mDisplayLayout.rotateTo(context.getResources(), toRotation);
    }
}
