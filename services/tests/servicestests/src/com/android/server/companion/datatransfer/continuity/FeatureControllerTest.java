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

package com.android.server.companion.datatransfer.continuity;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class FeatureControllerTest {

    public static final int USER_ID = 1;

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;
    FakeFeatureController mFakeFeatureController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFakeFeatureController = new FakeFeatureController(USER_ID, mMockTaskContinuityMessenger);
    }

    @Test
    public void testEnable_callsOnEnabled() {
        mFakeFeatureController.enable();
        assertThat(mFakeFeatureController.mOnEnabledCallCount).isEqualTo(1);
    }

    @Test
    public void testEnable_addsListenerToTaskContinuityMessenger() {
        mFakeFeatureController.enable();
        verify(mMockTaskContinuityMessenger).addListener(mFakeFeatureController);
    }

    @Test
    public void testEnable_alreadyEnabled_doesNotCallOnEnabled() {
        mFakeFeatureController.enable();
        mFakeFeatureController.enable();
        assertThat(mFakeFeatureController.mOnEnabledCallCount).isEqualTo(1);
    }

    @Test
    public void testDisable_callsOnDisabled() {
        mFakeFeatureController.enable();
        mFakeFeatureController.disable();
        assertThat(mFakeFeatureController.mOnDisabledCallCount).isEqualTo(1);
    }

    @Test
    public void testDisable_removesListenerFromTaskContinuityMessenger() {
        assertFalse(mFakeFeatureController.isEnabled());
        mFakeFeatureController.enable();
        assertTrue(mFakeFeatureController.isEnabled());
        mFakeFeatureController.disable();
        assertFalse(mFakeFeatureController.isEnabled());
        verify(mMockTaskContinuityMessenger).removeListener(mFakeFeatureController);
    }

    @Test
    public void testDisable_alreadyDisabled_doesNotCallOnDisabled() {
        mFakeFeatureController.enable();
        mFakeFeatureController.disable();
        mFakeFeatureController.disable();
        assertThat(mFakeFeatureController.mOnDisabledCallCount).isEqualTo(1);
    }

    @Test
    public void
            testOnMessageReceived_continuityDeviceConnected_callsOnContinuityDeviceConnectedMessageReceived() {
        mFakeFeatureController.onMessageReceived(
                /* associationId= */ 1, new ContinuityDeviceConnected(Collections.emptyList()));
        assertThat(mFakeFeatureController.mContinuityDeviceConnectedMessages).hasSize(1);
    }

    @Test
    public void testOnMessageReceived_remoteTaskAdded_callsOnRemoteTaskAddedMessageReceived() {
        mFakeFeatureController.onMessageReceived(
                /* associationId= */ 1,
                new RemoteTaskAddedMessage(
                        new RemoteTaskInfo(1, "label", 1000, new byte[0], true)));
        assertThat(mFakeFeatureController.mRemoteTaskAddedMessages).hasSize(1);
    }

    @Test
    public void testOnMessageReceived_remoteTaskRemoved_callsOnRemoteTaskRemovedMessageReceived() {
        mFakeFeatureController.onMessageReceived(
                /* associationId= */ 1, new RemoteTaskRemovedMessage(0));
        assertThat(mFakeFeatureController.mRemoteTaskRemovedMessages).hasSize(1);
    }

    @Test
    public void testOnMessageReceived_remoteTaskUpdated_callsOnRemoteTaskUpdatedMessageReceived() {
        mFakeFeatureController.onMessageReceived(
                /* associationId= */ 1,
                new RemoteTaskUpdatedMessage(
                        new RemoteTaskInfo(1, "label", 1000, new byte[0], true)));
        assertThat(mFakeFeatureController.mRemoteTaskUpdatedMessages).hasSize(1);
    }

    @Test
    public void testOnMessageReceived_handoffRequest_callsOnHandoffRequestMessageReceived() {
        mFakeFeatureController.onMessageReceived(
                /* associationId= */ 1, new HandoffRequestMessage(0));
        assertThat(mFakeFeatureController.mHandoffRequestMessages).hasSize(1);
    }

    @Test
    public void
            testOnMessageReceived_handoffRequestResult_callsOnHandoffRequestResultMessageReceived() {
        mFakeFeatureController.onMessageReceived(
                /* associationId= */ 1,
                new HandoffRequestResultMessage(0, 0, Collections.emptyList()));
        assertThat(mFakeFeatureController.mHandoffRequestResultMessages).hasSize(1);
    }
}
