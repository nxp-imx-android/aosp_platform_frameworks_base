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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.window.flags.Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

import android.graphics.Rect;
import android.os.UserHandle;
import android.os.UserManager;
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

    @EnableFlags(FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testPresentationHidesActivitiesBehind() {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.copyFrom(mDisplayInfo);
        displayInfo.flags = FLAG_PRESENTATION;
        final DisplayContent dc = createNewDisplay(displayInfo);
        final int displayId = dc.getDisplayId();
        doReturn(dc).when(mWm.mRoot).getDisplayContentOrCreate(displayId);
        final ActivityRecord activity = createActivityRecord(createTask(dc));
        assertTrue(activity.isVisible());

        doReturn(true).when(() -> UserManager.isVisibleBackgroundUsersEnabled());
        final int uid = 100000; // uid for non-system user
        final Session session = createTestSession(mAtm, 1234 /* pid */, uid);
        final int userId = UserHandle.getUserId(uid);
        doReturn(false).when(mWm.mUmInternal).isUserVisible(eq(userId), eq(displayId));
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_PRESENTATION);

        final IWindow clientWindow = new TestIWindow();
        final int result = mWm.addWindow(session, clientWindow, params, View.VISIBLE, displayId,
                userId, WindowInsets.Type.defaultVisible(), null, new InsetsState(),
                new InsetsSourceControl.Array(), new Rect(), new float[1]);
        assertTrue(result >= WindowManagerGlobal.ADD_OKAY);
        assertFalse(activity.isVisible());

        final WindowState window = mWm.windowForClientLocked(session, clientWindow, false);
        window.removeImmediately();
        assertTrue(activity.isVisible());
    }
}
