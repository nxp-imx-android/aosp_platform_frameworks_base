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

import static android.view.Display.FLAG_PRESENTATION;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.window.flags.Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

import android.annotation.NonNull;
import android.graphics.Rect;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.DisplayInfo;
import android.view.IWindow;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run:
 * atest WmTests:PresentationControllerTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class PresentationControllerTests extends WindowTestsBase {

    TestTransitionPlayer mPlayer;

    @Before
    public void setUp() {
        mPlayer = registerTestTransitionPlayer();
    }

    @EnableFlags(FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testPresentationShowAndHide() {
        final DisplayContent dc = createPresentationDisplay();
        final ActivityRecord activity = createActivityRecord(createTask(dc));
        assertTrue(activity.isVisible());

        // Add a presentation window, which requests the activity to stop.
        final WindowState window = addPresentationWindow(100000, dc.mDisplayId);
        assertFalse(activity.isVisibleRequested());
        assertTrue(activity.isVisible());
        final Transition addTransition = window.mTransitionController.getCollectingTransition();
        assertEquals(TRANSIT_OPEN, addTransition.mType);
        assertTrue(addTransition.isInTransition(window));
        assertTrue(addTransition.isInTransition(activity));

        // Completing the transition makes the activity invisible.
        completeTransition(addTransition, /*abortSync=*/ true);
        assertFalse(activity.isVisible());

        // Remove a Presentation window, which requests the activity to be resumed back.
        window.removeIfPossible();
        final Transition removeTransition = window.mTransitionController.getCollectingTransition();
        assertEquals(TRANSIT_CLOSE, removeTransition.mType);
        assertTrue(removeTransition.isInTransition(window));
        assertTrue(removeTransition.isInTransition(activity));
        assertTrue(activity.isVisibleRequested());
        assertFalse(activity.isVisible());

        // Completing the transition makes the activity visible.
        completeTransition(removeTransition, /*abortSync=*/ false);
        assertTrue(activity.isVisible());
    }

    @DisableFlags(FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testPresentationShowAndHide_flagDisabled() {
        final DisplayContent dc = createPresentationDisplay();
        final ActivityRecord activity = createActivityRecord(createTask(dc));
        assertTrue(activity.isVisible());

        final WindowState window = addPresentationWindow(100000, dc.mDisplayId);
        assertFalse(window.mTransitionController.isCollecting());
        assertTrue(activity.isVisibleRequested());
        assertTrue(activity.isVisible());

        window.removeIfPossible();
        assertFalse(window.mTransitionController.isCollecting());
        assertTrue(activity.isVisibleRequested());
        assertTrue(activity.isVisible());
        assertFalse(window.isAttached());
    }

    private WindowState addPresentationWindow(int uid, int displayId) {
        final Session session = createTestSession(mAtm, 1234 /* pid */, uid);
        final int userId = UserHandle.getUserId(uid);
        doReturn(true).when(mWm.mUmInternal).isUserVisible(eq(userId), eq(displayId));
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_PRESENTATION);
        final IWindow clientWindow = new TestIWindow();
        final int res = mWm.addWindow(session, clientWindow, params, View.VISIBLE, displayId,
                userId, WindowInsets.Type.defaultVisible(), null, new InsetsState(),
                new InsetsSourceControl.Array(), new Rect(), new float[1]);
        assertTrue(res >= WindowManagerGlobal.ADD_OKAY);
        final WindowState window = mWm.windowForClientLocked(session, clientWindow, false);
        window.mHasSurface = true;
        return window;
    }

    private DisplayContent createPresentationDisplay() {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.copyFrom(mDisplayInfo);
        displayInfo.flags = FLAG_PRESENTATION;
        final DisplayContent dc = createNewDisplay(displayInfo);
        final int displayId = dc.getDisplayId();
        doReturn(dc).when(mWm.mRoot).getDisplayContentOrCreate(displayId);
        return dc;
    }

    private void completeTransition(@NonNull Transition transition, boolean abortSync) {
        final ActionChain chain = ActionChain.testFinish(transition);
        if (abortSync) {
            // Forcefully finishing the active sync for testing purpose.
            mWm.mSyncEngine.abort(transition.getSyncId());
        } else {
            transition.onTransactionReady(transition.getSyncId(), mTransaction);
        }
        transition.finishTransition(chain);
    }
}
