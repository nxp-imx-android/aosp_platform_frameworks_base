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

package com.android.server.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.DisplayInfo;

import androidx.test.filters.SmallTest;

import com.android.graphics.surfaceflinger.flags.Flags;
import com.android.server.display.ModeRequestManager.RequestStatus;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SmallTest
@RunWith(JUnit4.class)
@EnableFlags(Flags.FLAG_MODESET_MULTI_DISPLAY)
public class ModeRequestManagerTest {
    private ModeRequestManager mManager;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        mManager = new ModeRequestManager();
    }

    @Test
    public void testOnUserPreferredModesRequestedLocked() {
        int displayId1 = 1;
        Display.Mode mode1 = new Display.Mode(1, 1920, 1080, 60f);
        ModeRequestManager.UserPreferredModeRequest request1 =
                new ModeRequestManager.UserPreferredModeRequest(displayId1, mode1, true);

        int displayId2 = 2;
        Display.Mode mode2 = new Display.Mode(2, 3840, 2160, 120f);
        ModeRequestManager.UserPreferredModeRequest request2 =
                new ModeRequestManager.UserPreferredModeRequest(displayId2, mode2, false);

        assertTrue(mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{request1, request2}));

        assertEquals(RequestStatus.IDLE, mManager.getStatus(displayId1));
        assertEquals(RequestStatus.IDLE, mManager.getStatus(displayId2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnUserPreferredModesRequestedLocked_InvalidDisplay() {
        ModeRequestManager.UserPreferredModeRequest request =
                new ModeRequestManager.UserPreferredModeRequest(Display.INVALID_DISPLAY, null,
                        true);
        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{request});
    }

    @Test
    public void testOnUserPreferredModesRequestedLocked_InProgress() {
        int displayId = 1;
        Display.Mode mode = new Display.Mode(1, 1920, 1080, 60f);
        ModeRequestManager.UserPreferredModeRequest request =
                new ModeRequestManager.UserPreferredModeRequest(displayId, mode, true);

        assertTrue(mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{request}));
        assertEquals(RequestStatus.IDLE, mManager.getStatus(displayId));
    }

    @Test
    public void testClearRequests() {
        int displayId = 1;
        Display.Mode mode = new Display.Mode(1, 1920, 1080, 60f);
        ModeRequestManager.UserPreferredModeRequest request =
                new ModeRequestManager.UserPreferredModeRequest(displayId, mode, true);

        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{request});

        mManager.clearRequests();
        assertFalse(mManager.isRequestInProgress(displayId));
        assertEquals(RequestStatus.IDLE, mManager.getStatus(displayId));
    }

    @Test
    public void testUpdateStatus() {
        int displayId = 1;
        // initialize
        mManager.updateStatus(displayId, RequestStatus.IDLE);

        // update
        mManager.updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);
        assertEquals(RequestStatus.WAITING_FOR_DEVICE_INFO, mManager.getStatus(displayId));

        // Should not downgrade
        mManager.updateStatus(displayId, RequestStatus.IDLE);
        assertEquals(RequestStatus.WAITING_FOR_DEVICE_INFO, mManager.getStatus(displayId));

        // should not skip levels
        mManager.updateStatus(displayId, RequestStatus.MODE_SPECS_SET);
        assertEquals(RequestStatus.WAITING_FOR_DEVICE_INFO, mManager.getStatus(displayId));
    }

    @Test
    public void testInfoUpdated() {
        int displayId = 1;
        Display.Mode mode = new Display.Mode(1, 1920, 1080, 60f);
        ModeRequestManager.UserPreferredModeRequest request =
                new ModeRequestManager.UserPreferredModeRequest(displayId, mode, true);

        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{request});
        mManager.updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);

        DisplayInfo info = new DisplayInfo();
        info.userPreferredModeId = 1;

        mManager.infoUpdated(displayId, info);
        assertEquals(RequestStatus.DEVICE_INFO_CREATED, mManager.getStatus(displayId));
    }

    @Test
    public void testInfoUpdated_Mismatch_DoesNotProgress() {
        int displayId = 1;
        Display.Mode requestedMode = new Display.Mode(1, 1920, 1080, 60f);
        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{
                        new ModeRequestManager.UserPreferredModeRequest(
                                displayId, requestedMode, true)});
        mManager.waitingForDeviceInfo(displayId);

        // Simulate a mismatch: requested mode 1, but device inforeports mode 2
        DisplayInfo info = new DisplayInfo();
        info.userPreferredModeId = 2;
        mManager.infoUpdated(displayId, info);

        // Status should still be WAITING_FOR_DEVICE_INFO, not DEVICE_INFO_CREATED
        assertEquals(RequestStatus.WAITING_FOR_DEVICE_INFO, mManager.getStatus(displayId));
    }

    @Test
    public void testInfoUpdated_NullRequestedMode_MatchesInvalidId() {
        int displayId = 1;
        // Requesting null (reset)
        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{
                        new  ModeRequestManager.UserPreferredModeRequest(
                                displayId, null, true)});
        mManager.updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);

        DisplayInfo info = new DisplayInfo();
        info.userPreferredModeId = Display.Mode.INVALID_MODE_ID;
        mManager.infoUpdated(displayId, info);

        // Should progress because null matches INVALID_MODE_ID (0)
        assertEquals(RequestStatus.DEVICE_INFO_CREATED, mManager.getStatus(displayId));
    }

    @Test
    public void testVotesReady() {
        int displayId = 1;
        Display.Mode mode = new Display.Mode(1, 1920, 1080, 60f);
        ModeRequestManager.UserPreferredModeRequest request =
                new ModeRequestManager.UserPreferredModeRequest(displayId, mode, true);

        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{request});
        mManager.updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);
        mManager.updateStatus(displayId, RequestStatus.DEVICE_INFO_CREATED);

        mManager.votesReady(displayId, 1);
        assertEquals(RequestStatus.WAITING_FOR_MODE_SPECS, mManager.getStatus(displayId));
    }

    @Test
    public void testVotesReady_Mismatch_DoesNotProgress() {
        int displayId = 1;
        Display.Mode requestedMode = new Display.Mode(1, 1920, 1080, 60f);
        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{
                        new ModeRequestManager.UserPreferredModeRequest(
                                displayId, requestedMode, true)});
        mManager.updateStatus(displayId, RequestStatus.DEVICE_INFO_CREATED);

        // Mismatch: requested mode 1, but director reports mode 2
        mManager.votesReady(displayId, 2);
        // In this case, we should return early and clear the requests in progress
        assertFalse(mManager.isRequestInProgress(displayId));
    }

    @Test
    public void testVotesReady_NullRequestedMode_MatchesInvalidId() {
        int displayId = 1;
        // Requesting null (reset)
        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{
                        new  ModeRequestManager.UserPreferredModeRequest(
                                displayId, null, true)});
        mManager.updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);
        mManager.updateStatus(displayId, RequestStatus.DEVICE_INFO_CREATED);

        mManager.votesReady(displayId, /* userPreferredModeId */ Display.Mode.INVALID_MODE_ID);

        // Should progress because null matches INVALID_MODE_ID (0)
        assertEquals(RequestStatus.WAITING_FOR_MODE_SPECS, mManager.getStatus(displayId));
    }

    @Test
    public void testAllDisplaysReachedStatus() {
        int displayId1 = 1;
        int displayId2 = 2;
        Display.Mode mode = new Display.Mode(1, 1920, 1080, 60f);
        ModeRequestManager.UserPreferredModeRequest r1 =
                new ModeRequestManager.UserPreferredModeRequest(displayId1, mode, true);
        ModeRequestManager.UserPreferredModeRequest r2 =
                new ModeRequestManager.UserPreferredModeRequest(displayId2, mode, true);

        mManager.onUserPreferredModesRequestedLocked(
                new ModeRequestManager.UserPreferredModeRequest[]{r1, r2});

        assertFalse(mManager.allDisplaysReachedStatus(RequestStatus.MODE_SPECS_SET));

        // the states must be advanced in order
        mManager.updateStatus(displayId1, RequestStatus.WAITING_FOR_DEVICE_INFO);
        mManager.updateStatus(displayId2, RequestStatus.WAITING_FOR_DEVICE_INFO);
        mManager.updateStatus(displayId1, RequestStatus.DEVICE_INFO_CREATED);
        mManager.updateStatus(displayId2, RequestStatus.DEVICE_INFO_CREATED);
        mManager.updateStatus(displayId1, RequestStatus.WAITING_FOR_MODE_SPECS);
        mManager.updateStatus(displayId2, RequestStatus.WAITING_FOR_MODE_SPECS);

        // start by setting only one
        mManager.updateStatus(displayId1, RequestStatus.MODE_SPECS_SET);
        assertFalse(mManager.allDisplaysReachedStatus(RequestStatus.MODE_SPECS_SET));

        mManager.updateStatus(displayId2, RequestStatus.MODE_SPECS_SET);
        assertTrue(mManager.allDisplaysReachedStatus(RequestStatus.MODE_SPECS_SET));
    }
}
