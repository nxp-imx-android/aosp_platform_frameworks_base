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

package com.android.wm.shell.common.split;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;

import android.content.res.Resources;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.mechanics.spec.MotionSpec;
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MagneticDividerUtilsTests {
    Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getInstrumentation().getContext().getResources();
    }

    @Test
    public void generateMotionSpec_worksOnThisDeviceWithoutCrashing() {
        int longEdge = Math.max(
                mResources.getDisplayMetrics().heightPixels,
                mResources.getDisplayMetrics().widthPixels
        );

        List<SnapTarget> mTargets = List.of(
                new SnapTarget(0, SNAP_TO_START_AND_DISMISS),
                new SnapTarget(longEdge / 10, SNAP_TO_2_10_90),
                new SnapTarget(longEdge / 2, SNAP_TO_2_50_50),
                new SnapTarget(longEdge - (longEdge / 10), SNAP_TO_2_90_10),
                new SnapTarget(longEdge, SNAP_TO_END_AND_DISMISS)
        );

        // Check that a MotionSpec gets created without crashing. A crash can happen if the dp
        // values set MagneticDividerUtils are large enough that the snap zones overlap on smaller
        // screens.
        MotionSpec motionSpec = MagneticDividerUtils.generateMotionSpec(mTargets, mResources);
    }
}
