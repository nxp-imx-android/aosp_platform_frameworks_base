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

package com.android.server.companion.datatransfer.continuity.connectivity;

import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportsChangedListener;
import android.content.Context;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import com.android.server.companion.datatransfer.continuity.messages.Proto;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskContinuityMessengerTest {

    private static final int USER_ID = 1;

    private Context mMockContext;
    @Mock private ICompanionDeviceManager mMockCompanionDeviceManagerService;
    private CompanionDeviceManager mCompanionDeviceManager;
    @Mock private TaskContinuityMessenger.Listener mMockListener;
    @Mock private AssociationProfileManager mMockAssociationProfileManager;

    private final Executor mExecutor = Runnable::run;

    private TaskContinuityMessenger mTaskContinuityMessenger;

    private static final TaskContinuityMessage TEST_MESSAGE =
            new TaskContinuityMessage.Builder().build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Setup fake services.
        mCompanionDeviceManager =
                new CompanionDeviceManager(mMockCompanionDeviceManagerService, mMockContext);

        // Create TaskContinuityMessenger.
        mTaskContinuityMessenger =
                new TaskContinuityMessenger(
                        USER_ID,
                        mCompanionDeviceManager,
                        mExecutor,
                        mMockAssociationProfileManager);
    }

    @Test
    public void testAddAndRemoveListeners_flowsMessages() throws Exception {
        // Start listening, verifying a message listener is added.
        ArgumentCaptor<IOnMessageReceivedListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnMessageReceivedListener.class);
        mTaskContinuityMessenger.addListener(mMockListener);
        verify(mMockCompanionDeviceManagerService, times(1))
                .addOnMessageReceivedListener(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY), listenerCaptor.capture());
        IOnMessageReceivedListener listener = listenerCaptor.getValue();
        assertThat(listener).isNotNull();

        // Send a message to the listener.
        int expectedAssociationId = 1;
        connectAssociations(List.of(expectedAssociationId));
        listener.onMessageReceived(expectedAssociationId, Proto.toBytes(TEST_MESSAGE));
        TestableLooper.get(this).processAllMessages();
        verify(mMockListener, times(1))
                .onMessageReceived(eq(expectedAssociationId), eq(TEST_MESSAGE));

        // Stop listening, verifying the message listener is removed.
        mTaskContinuityMessenger.removeListener(mMockListener);
        verify(mMockCompanionDeviceManagerService, times(1))
                .removeOnMessageReceivedListener(eq(MESSAGE_ONEWAY_TASK_CONTINUITY), any());
    }

    @Test
    public void testAddAndRemoveListeners_multipleListeners_flowsMessages() throws Exception {
        // Start listening, verifying a message listener is added.
        ArgumentCaptor<IOnMessageReceivedListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnMessageReceivedListener.class);
        mTaskContinuityMessenger.addListener(mMockListener);
        TaskContinuityMessenger.Listener mockListener2 =
                Mockito.mock(TaskContinuityMessenger.Listener.class);
        mTaskContinuityMessenger.addListener(mockListener2);
        verify(mMockCompanionDeviceManagerService, times(1))
                .addOnMessageReceivedListener(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY), listenerCaptor.capture());
        IOnMessageReceivedListener listener = listenerCaptor.getValue();
        assertThat(listener).isNotNull();
        mTaskContinuityMessenger.removeListener(mMockListener);
        verify(mMockCompanionDeviceManagerService, never())
                .removeOnMessageReceivedListener(eq(MESSAGE_ONEWAY_TASK_CONTINUITY), any());
        mTaskContinuityMessenger.removeListener(mockListener2);
        verify(mMockCompanionDeviceManagerService, times(1))
                .removeOnMessageReceivedListener(eq(MESSAGE_ONEWAY_TASK_CONTINUITY), any());
    }

    @Test
    public void testSendMessage_sendsMessageToAssociation() throws RemoteException, IOException {
        int associationId = 1;

        mTaskContinuityMessenger.addListener(mMockListener);
        connectAssociations(List.of(associationId));
        TaskContinuityMessenger.SendMessageResult result =
                mTaskContinuityMessenger.sendMessage(associationId, TEST_MESSAGE);
        verify(mMockCompanionDeviceManagerService, times(1))
                .sendMessage(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                        eq(Proto.toBytes(TEST_MESSAGE)),
                        aryEq(new int[] {associationId}));
        assertThat(result).isEqualTo(TaskContinuityMessenger.SendMessageResult.SUCCESS);
    }

    @Test
    public void testSendMessage_associationNotFound_returnsFailure()
            throws RemoteException, IOException {

        int associationId = 1;
        TaskContinuityMessenger.SendMessageResult result =
                mTaskContinuityMessenger.sendMessage(associationId, TEST_MESSAGE);
        verify(mMockCompanionDeviceManagerService, never())
                .sendMessage(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                        eq(Proto.toBytes(TEST_MESSAGE)),
                        aryEq(new int[] {associationId}));
        assertThat(result)
                .isEqualTo(TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND);
    }

    private void connectAssociations(List<Integer> associationIds) {
        ArgumentCaptor<IOnTransportsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnTransportsChangedListener.class);
        try {
            verify(mMockCompanionDeviceManagerService, times(1))
                    .addOnTransportsChangedListener(listenerCaptor.capture());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        List<AssociationInfo> associationInfos =
                associationIds.stream()
                        .map(
                                id ->
                                        new AssociationInfo.Builder(id, USER_ID, "com.android.test")
                                                .setDisplayName("name")
                                                .build())
                        .collect(Collectors.toList());
        when(mMockAssociationProfileManager.isAssociationAvailableForUser(
                        eq(USER_ID), any(AssociationInfo.class)))
                .thenReturn(true);

        try {
            listenerCaptor.getValue().onTransportsChanged(associationInfos);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        TestableLooper.get(this).processAllMessages();
    }
}
