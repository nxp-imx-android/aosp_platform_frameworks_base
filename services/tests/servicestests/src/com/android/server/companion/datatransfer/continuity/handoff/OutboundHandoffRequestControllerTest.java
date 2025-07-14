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

import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createAssociationInfo;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createMockContext;
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createMockCompanionDeviceManager;
import static org.mockito.AdditionalMatchers.aryEq;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;

import com.android.server.companion.datatransfer.continuity.connectivity.ConnectedAssociationStore;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageSerializer;

import android.app.ActivityManager;
import android.app.HandoffActivityData;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.os.PersistableBundle;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class OutboundHandoffRequestControllerTest {

    private Context mContext;
    private ICompanionDeviceManager mMockCompanionDeviceManagerService;

    @Mock private ConnectedAssociationStore mMockConnectedAssociationStore;
    @Mock private PackageManager mMockPackageManager;

    private OutboundHandoffRequestController mOutboundHandoffRequestController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = createMockContext();
        mMockCompanionDeviceManagerService = createMockCompanionDeviceManager(mContext);
        doReturn(mMockPackageManager).when(mContext).getPackageManager();

        mOutboundHandoffRequestController = new OutboundHandoffRequestController(
            mContext,
            mMockConnectedAssociationStore);
    }

    @Test
    public void testRequestHandoff_success() throws Exception {
        int associationId = 1;
        int taskId = 1;
        FakeHandoffRequestCallback callbackHolder = new FakeHandoffRequestCallback();
        AssociationInfo mockAssociationInfo = createAssociationInfo(associationId, "device");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(associationId))
            .thenReturn(mockAssociationInfo);

        // Request a handoff to a device.
        mOutboundHandoffRequestController.requestHandoff(
            associationId,
            taskId,
            callbackHolder);

        // Verify HandoffRequestMessage was sent.
        HandoffRequestMessage expectedHandoffRequestMessage = new HandoffRequestMessage(taskId);
        verify(mMockCompanionDeviceManagerService).sendMessage(
            eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
            eq(TaskContinuityMessageSerializer.serialize(expectedHandoffRequestMessage)),
            aryEq(new int[] {associationId}));

        // Simulate a response message.
        ComponentName expectedComponentName = new ComponentName(
            "com.example.app",
            "com.example.app.Activity");
        PersistableBundle expectedExtras = new PersistableBundle();
        expectedExtras.putString("key", "value");
        HandoffActivityData handoffActivityData
            = new HandoffActivityData.Builder(expectedComponentName)
                .setExtras(expectedExtras)
                .build();
        when(mMockPackageManager.getActivityInfo(
            eq(expectedComponentName), eq(PackageManager.MATCH_DEFAULT_ONLY)))
            .thenReturn(new ActivityInfo());
        doReturn(ActivityManager.START_SUCCESS)
            .when(mContext).startActivitiesAsUser(any(), any(), any());

        HandoffRequestResultMessage handoffRequestResultMessage = new HandoffRequestResultMessage(
            taskId,
            TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS,
            List.of(handoffActivityData));
        mOutboundHandoffRequestController.onHandoffRequestResultMessageReceived(
            associationId,
            handoffRequestResultMessage);

        // Verify the intent was launched.
        ArgumentCaptor<Intent[]> intentCaptor = ArgumentCaptor.forClass(Intent[].class);
        verify(mContext, times(1)).startActivitiesAsUser(intentCaptor.capture(), any(), any());
        Intent actualIntent = intentCaptor.getValue()[0];
        assertThat(actualIntent.getComponent()).isEqualTo(expectedComponentName);
        assertThat(actualIntent.getExtras().size()).isEqualTo(1);
        for (String key : actualIntent.getExtras().keySet()) {
            assertThat(actualIntent.getExtras().getString(key))
                .isEqualTo(expectedExtras.getString(key));
        }

        // Verify the callback was invoked.
        callbackHolder.verifyInvoked(
            associationId,
            taskId,
            TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS);
    }

    @Test
    public void testRequestHandoff_associationNotConnected_returnsFailure() {
        int associationId = 1;
        int taskId = 1;
        FakeHandoffRequestCallback callbackHolder = new FakeHandoffRequestCallback();
        when(mMockConnectedAssociationStore.getConnectedAssociationById(associationId))
            .thenReturn(null);
        mOutboundHandoffRequestController.requestHandoff(
            associationId,
            taskId,
            callbackHolder);

        // Verify the callback was invoked.
        callbackHolder.verifyInvoked(
            associationId,
            taskId,
            TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND);
    }

    @Test
    public void testRequestHandoff_multipleTimes_onlySendsOneMessage() throws Exception {
        int associationId = 1;
        int taskId = 1;
        AssociationInfo mockAssociationInfo = createAssociationInfo(associationId, "device");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(associationId))
            .thenReturn(mockAssociationInfo);

        // Request handoff multiple times.
        FakeHandoffRequestCallback firstCallback = new FakeHandoffRequestCallback();
        FakeHandoffRequestCallback secondCallback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestController.requestHandoff(
            associationId,
            taskId,
            firstCallback);
        mOutboundHandoffRequestController.requestHandoff(
            associationId,
            taskId,
            secondCallback);

        HandoffRequestMessage expectedHandoffRequestMessage = new HandoffRequestMessage(taskId);
        verify(mMockCompanionDeviceManagerService, times(1)).sendMessage(
            eq(CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY),
            eq(TaskContinuityMessageSerializer.serialize(expectedHandoffRequestMessage)),
            aryEq(new int[] {associationId}));
    }

    @Test
    public void testRequestHandoff_failureStatusCode_returnsFailure() {
        // Request a handoff
        int associationId = 1;
        int taskId = 1;
        AssociationInfo mockAssociationInfo = createAssociationInfo(associationId, "device");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(associationId))
            .thenReturn(mockAssociationInfo);
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestController.requestHandoff(
            associationId,
            taskId,
            callback);

        // Simulate a message failure
        int failureStatusCode =
            TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
        mOutboundHandoffRequestController.onHandoffRequestResultMessageReceived(
            associationId,
            new HandoffRequestResultMessage(taskId, failureStatusCode, List.of()));

        // Verify the callback was invoked.
        callback.verifyInvoked(
            associationId,
            taskId,
            failureStatusCode);

        // Verify no intent was launched.
        verify(mContext, never()).startActivitiesAsUser(any(), any(), any());
    }

    @Test
    public void testRequestHandoff_noActivities_returnsFailure() {
        // Request a handoff
        int associationId = 1;
        int taskId = 1;
        AssociationInfo mockAssociationInfo = createAssociationInfo(associationId, "device");
        when(mMockConnectedAssociationStore.getConnectedAssociationById(associationId))
            .thenReturn(mockAssociationInfo);
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestController.requestHandoff(
            associationId,
            taskId,
            callback);

        // Return no data for this request.
        mOutboundHandoffRequestController.onHandoffRequestResultMessageReceived(
            associationId,
            new HandoffRequestResultMessage(
                taskId,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS,
                List.of()));

        // Verify the callback was invoked.
        callback.verifyInvoked(
            associationId,
            taskId,
            TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK);

        // Verify no intent was launched.
        verify(mContext, never()).startActivitiesAsUser(any(), any(), any());
    }
}