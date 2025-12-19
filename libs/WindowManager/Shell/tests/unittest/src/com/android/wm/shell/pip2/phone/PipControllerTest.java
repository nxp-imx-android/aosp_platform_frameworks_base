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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.times;
import static org.mockito.kotlin.VerificationKt.verify;

import android.platform.test.annotations.EnableFlags;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TabletopModeController;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PhonePipKeepClearAlgorithm;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipController}
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(AndroidTestingRunner.class)
public class PipControllerTest extends ShellTestCase {
    private PipController mPipController;
    @Mock private ShellInit mShellInit;
    @Mock private ShellCommandHandler mMockShellCommandHandler;
    @Mock private ShellController mShellController;
    @Mock private DisplayController mMockDisplayController;
    @Mock private DisplayInsetsController mMockDisplayInsetsController;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private PipBoundsAlgorithm mMockPipBoundsAlgorithm;
    @Mock private PipDisplayLayoutState mMockPipDisplayLayoutState;
    @Mock private PipScheduler mMockPipScheduler;
    @Mock private TaskStackListenerImpl mMockTaskStackListener;
    @Mock private ShellTaskOrganizer mMockShellTaskOrganizer;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipTouchHandler mMockPipTouchHandler;
    @Mock private PipAppOpsListener mMockPipAppOpsListener;
    @Mock private PhonePipMenuController mMockPhonePipMenuController;
    @Mock private PipUiEventLogger mMockPipUiEventLogger;
    @Mock private PipMediaController mMockPipMediaController;
    @Mock private TabletopModeController mMockTabletopModeController;
    @Mock private PhonePipKeepClearAlgorithm mMockPipKeepClearAlgorithm;
    @Mock private PipSurfaceTransactionHelper mMockPipSurfaceTransactionHelper;
    @Mock private ShellExecutor mMockMainExecutor;
    private static final int DISPLAY_ID_MAIN = 1;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockPipDisplayLayoutState.getDisplayId()).thenReturn(DISPLAY_ID_MAIN);

        mPipController = PipController.create(mContext, mShellInit, mMockShellCommandHandler,
                mShellController, mMockDisplayController, mMockDisplayInsetsController,
                mMockPipBoundsState, mMockPipBoundsAlgorithm, mMockPipDisplayLayoutState,
                mMockPipScheduler, mMockTaskStackListener, mMockShellTaskOrganizer,
                mMockPipTransitionState, mMockPipTouchHandler, mMockPipAppOpsListener,
                mMockPhonePipMenuController, mMockPipUiEventLogger, mMockPipMediaController,
                mMockTabletopModeController, mMockPipKeepClearAlgorithm,
                mMockPipSurfaceTransactionHelper, mMockMainExecutor);
        // Directly init mPipController instead of using ShellInit
        mPipController.onInit();
    }

    @Test
    public void instantiatePipController_addGlobalInsetsChangedListener() {
        verify(mMockDisplayInsetsController, times(1)).addGlobalInsetsChangedListener(any());
    }

    @Test
    public void testInsetsListenerPerDisplay_addedOnInsetsChange_thenRemovedOnDisplayRemove() {
        // Capture the global insets listener
        ArgumentCaptor<DisplayInsetsController.OnInsetsChangedListener> captor =
                ArgumentCaptor.forClass(
                        DisplayInsetsController.OnInsetsChangedListener.class);
        verify(mMockDisplayInsetsController).addGlobalInsetsChangedListener(captor.capture());
        DisplayInsetsController.OnInsetsChangedListener globalListener = captor.getValue();

        // Simulate insets change on a new display
        int newDisplayId = DISPLAY_ID_MAIN + 1;
        android.view.InsetsState insetsState = new android.view.InsetsState();
        globalListener.insetsChanged(newDisplayId, insetsState);

        // Verify listener creation
        assertNotNull(mPipController.mListenersPerDisplay.get(newDisplayId));
        // Verify that both Ime and nav bar listeners were added in a list
        assertEquals(2, mPipController.mListenersPerDisplay.get(newDisplayId).size());

        // Simulate display removal
        mPipController.onDisplayRemoved(newDisplayId);

        // Verify listener removal
        assertNull(mPipController.mListenersPerDisplay.get(newDisplayId));
    }
}
