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

package com.android.server.companion.datatransfer.continuity.handoff;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.content.Context;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.platform.test.annotations.Presubmit;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.tasks.TaskSyncController;
import com.android.server.companion.datatransfer.continuity.handoff.HandoffController;

import java.util.List;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class HandoffControllerTest {

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;
    @Mock private TaskSyncController mMockTaskSyncController;
    @Mock private InboundHandoffRequestHandler mMockInboundHandoffRequestHandler;
    @Mock private OutboundHandoffRequestHandler mMockOutboundHandoffRequestHandler;

    private HandoffController mHandoffController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mHandoffController =
                new HandoffController(
                        mMockTaskContinuityMessenger,
                        mMockTaskSyncController,
                        mMockInboundHandoffRequestHandler,
                        mMockOutboundHandoffRequestHandler);
    }

    @Test
    public void testRequestHandoff_callsOutboundHandoffRequestHandler() {
        int associationId = 1;
        int remoteTaskId = 2;
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mHandoffController.requestHandoff(associationId, remoteTaskId, callback);
        verify(mMockOutboundHandoffRequestHandler)
                .requestHandoff(associationId, remoteTaskId, callback);
    }

    @Test
    public void testOnHandoffRequestMessageReceived_callsInboundHandoffRequestHandler() {
        int associationId = 1;
        HandoffRequestMessage handoffRequestMessage = new HandoffRequestMessage(1);
        mHandoffController.onMessageReceived(associationId, handoffRequestMessage);
        verify(mMockInboundHandoffRequestHandler)
                .onHandoffRequestMessageReceived(associationId, handoffRequestMessage);
    }

    @Test
    public void testOnHandoffRequestResultMessageReceived_callsOutboundHandoffRequestHandler() {
        int associationId = 1;
        HandoffRequestResultMessage handoffRequestResultMessage =
                new HandoffRequestResultMessage(1, 1, List.of());
        mHandoffController.onMessageReceived(associationId, handoffRequestResultMessage);
        verify(mMockOutboundHandoffRequestHandler)
                .onHandoffRequestResultMessageReceived(associationId, handoffRequestResultMessage);
    }

    @Test
    public void testOnDisabled_cancelsAllOutboundRequests() {
        mHandoffController.enable();
        mHandoffController.disable();
        verify(mMockOutboundHandoffRequestHandler).cancelAllOutboundRequests();
    }
}
