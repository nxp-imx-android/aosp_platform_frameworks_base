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

package com.android.server.display;

import static com.android.server.display.DisplayDeviceConfig.DEFAULT_LOW_REFRESH_RATE;

import android.annotation.SuppressLint;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.server.display.LocalDisplayAdapter.DisplayModeRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DisplayModeFactory {

    /**
     * Used to generate globally unique display mode ids.
     */
    private static final AtomicInteger NEXT_DISPLAY_MODE_ID = new AtomicInteger(1);  // 0 = no mode.


    private static final float FLOAT_TOLERANCE = 0.01f;
    private static final float SYNTHETIC_MODE_REFRESH_RATE = DEFAULT_LOW_REFRESH_RATE;
    private static final float SYNTHETIC_MODE_HIGH_BOUNDARY =
            SYNTHETIC_MODE_REFRESH_RATE + FLOAT_TOLERANCE;


    static Display.Mode createMode(int width, int height, float refreshRate) {
        return new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                Display.Mode.INVALID_MODE_ID, 0,
                width, height, refreshRate, refreshRate,
                new float[0], new int[0]
        );
    }

    @SuppressLint("WrongConstant")
    static Display.Mode createMode(SurfaceControl.DisplayMode mode, float[] alternativeRefreshRates,
            boolean hasArrSupport, boolean syntheticModesV2Enabled) {
        int flags = 0;
        if (syntheticModesV2Enabled
                && hasArrSupport && mode.peakRefreshRate <= SYNTHETIC_MODE_HIGH_BOUNDARY) {
            flags |= Display.Mode.FLAG_ARR_RENDER_RATE;
        }

        return new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                Display.Mode.INVALID_MODE_ID, flags,
                mode.width, mode.height, mode.peakRefreshRate, mode.vsyncRate,
                alternativeRefreshRates, mode.supportedHdrTypes
        );
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    static List<DisplayModeRecord> createArrSyntheticModes(List<DisplayModeRecord> records,
            boolean hasArrSupport, boolean syntheticModesV2Enabled) {
        if (!syntheticModesV2Enabled) {
            return Collections.emptyList();
        }

        if (!hasArrSupport) {
            return Collections.emptyList();
        }

        List<Display.Mode> modesToSkipForArrSyntheticMode = new ArrayList<>();
        for (DisplayModeRecord record: records) {
            // already have < 60Hz mode, don't need to add synthetic
            if ((record.mMode.getFlags() & Display.Mode.FLAG_ARR_RENDER_RATE) != 0) {
                modesToSkipForArrSyntheticMode.add(record.mMode);
            }
        }

        List<Display.Mode> modesForArrSyntheticMode = new ArrayList<>();
        for (DisplayModeRecord record: records) {
            if (!is60HzAchievable(record.mMode)) {
                continue;
            }
            // already have < 60Hz mode, don't need to add synthetic
            if ((record.mMode.getFlags() & Display.Mode.FLAG_ARR_RENDER_RATE) != 0) {
                continue;
            }
            // already added OR should be skipped
            boolean skipAdding = hasMatichingForArr(modesForArrSyntheticMode, record.mMode)
                    || hasMatichingForArr(modesToSkipForArrSyntheticMode, record.mMode);

            if (!skipAdding) {
                modesForArrSyntheticMode.add(record.mMode);
            }
        }

        List<DisplayModeRecord> syntheticModes = new ArrayList<>();
        for (Display.Mode mode : modesForArrSyntheticMode) {
            syntheticModes.add(new DisplayModeRecord(
                    new Display.Mode(NEXT_DISPLAY_MODE_ID.getAndIncrement(),
                            mode.getModeId(), Display.Mode.FLAG_ARR_RENDER_RATE,
                            mode.getPhysicalWidth(), mode.getPhysicalHeight(),
                            SYNTHETIC_MODE_REFRESH_RATE, SYNTHETIC_MODE_REFRESH_RATE,
                            new float[0], mode.getSupportedHdrTypes()
                    )));
        }

        return syntheticModes;
    }

    private static boolean hasMatichingForArr(List<Display.Mode> modes, Display.Mode modeToMatch) {
        for (Display.Mode mode : modes) {
            if (matchingForSyntheticArr(modeToMatch, mode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean is60HzAchievable(Display.Mode mode) {
        float divisor = mode.getVsyncRate() / SYNTHETIC_MODE_REFRESH_RATE;
        return Math.abs(divisor - Math.round(divisor)) < FLOAT_TOLERANCE;
    }

    private static  boolean matchingForSyntheticArr(Display.Mode mode1, Display.Mode mode2) {
        return mode1.getPhysicalWidth() == mode2.getPhysicalWidth()
                && mode1.getPhysicalHeight() == mode2.getPhysicalHeight()
                && Arrays.equals(mode1.getSupportedHdrTypes(), mode2.getSupportedHdrTypes());
    }
}
