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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_END_AND_DISMISS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_START_AND_DISMISS;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.util.DisplayMetrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.mechanics.spec.MotionSpec;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MagneticDividerUtilsTests {
    private MockitoSession mMockitoSession;

    private final List<SnapTarget> mTargets = List.of(
            new SnapTarget(0, SNAP_TO_START_AND_DISMISS),
            new SnapTarget(100, SNAP_TO_2_10_90),
            new SnapTarget(500, SNAP_TO_2_50_50),
            new SnapTarget(900, SNAP_TO_2_90_10),
            new SnapTarget(1000, SNAP_TO_END_AND_DISMISS)
    );

    @Mock Resources mResources;
    @Mock DisplayMetrics mDisplayMetrics;

    @Before
    public void setup() {
        mMockitoSession = mockitoSession()
                .initMocks(this)
                .mockStatic(PipUtils.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void generateMotionSpec_producesCorrectNumberOfBreakpointsAndMappings() {
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        when(PipUtils.dpToPx(anyFloat(), eq(mDisplayMetrics))).thenReturn(30);

        MotionSpec motionSpec = MagneticDividerUtils.generateMotionSpec(mTargets, mResources);

        // Expect 12 breakpoints: the "min" breakpoint, the "max" breakpoint, and 2 breakpoints for
        // each of the 5 snap points.
        assertEquals(12, motionSpec.getMaxDirection().getBreakpoints().size());
        // Expect 11 mappings, that go between the breakpoints.
        assertEquals(11, motionSpec.getMaxDirection().getMappings().size());
    }
}
