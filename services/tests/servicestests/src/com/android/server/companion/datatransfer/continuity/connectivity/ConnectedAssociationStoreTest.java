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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.IOnTransportsChangedListener;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class ConnectedAssociationStoreTest {

    private Context mMockContext;
    @Mock private ICompanionDeviceManager mMockCompanionDeviceManagerService;
    @Mock private Executor mMockExecutor;
    @Mock private ConnectedAssociationStore.Observer mMockObserver;

    @Captor
    private ArgumentCaptor<IOnTransportsChangedListener> mListenerCaptor;

    private ConnectedAssociationStore mConnectedAssociationStore;

    private CompanionDeviceManager mCompanionDeviceManager;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mMockContext =  Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));

        mCompanionDeviceManager = new CompanionDeviceManager(
                mMockCompanionDeviceManagerService,
                mMockContext);

        when(mMockContext.getSystemService(Context.COMPANION_DEVICE_SERVICE))
            .thenReturn(mCompanionDeviceManager);

        mConnectedAssociationStore = new ConnectedAssociationStore(mMockContext);
        mConnectedAssociationStore.addObserver(mMockObserver);
        verify(mMockCompanionDeviceManagerService).addOnTransportsChangedListener(
                mListenerCaptor.capture());
    }

    @Test
    public void testOnTransportConnected_notifyObserver() throws RemoteException {
        // Simulate a new association connected.
        int associationId = 1;
        notifyTransportsChanged(
                Arrays.asList(createAssociationInfo(associationId)));

        // Verify the observer is notified.
        verify(mMockObserver).onTransportConnected(associationId);
        verify(mMockObserver, never()).onTransportDisconnected(associationId);
    }

    @Test
    public void testOnTransportDisconnected_notifyObserver() throws RemoteException {
        // Start with an association connected.
        int associationId = 1;
        notifyTransportsChanged(
                Arrays.asList(createAssociationInfo(associationId)));

        // Simulate the association being disconnected.
        notifyTransportsChanged(Collections.emptyList());

        // Verify the observer is notified of the disconnection.
        verify(mMockObserver).onTransportDisconnected(associationId);
    }

    @Test
    public void testOnTransportChanged_noChange_noNotification() throws RemoteException {
        // Start with an association connected.
        int associationId = 1;
        notifyTransportsChanged(
                Arrays.asList(createAssociationInfo(associationId)));

        // Simulate the same association still connected.
        notifyTransportsChanged(
                Arrays.asList(createAssociationInfo(associationId)));

        // Verify the observer is only notified once for the initial connection.
        verify(mMockObserver, times(1)).onTransportConnected(associationId);
        verify(mMockObserver, never()).onTransportDisconnected(associationId);
    }

    @Test
    public void testGetConnectedAssociations() throws RemoteException {
        // Connect two associations.
        int associationId1 = 1;
        int associationId2 = 2;
        notifyTransportsChanged(
                Arrays.asList(
                        createAssociationInfo(associationId1),
                        createAssociationInfo(associationId2)));

        // Verify that getConnectedAssociations returns the correct set.
        Set<Integer> connectedAssociations
            = mConnectedAssociationStore.getConnectedAssociations();
        assertThat(connectedAssociations)
            .containsExactly(associationId1, associationId2);

        // Disconnect one association.
        notifyTransportsChanged(
                Arrays.asList(createAssociationInfo(associationId1)));

        // Verify that getConnectedAssociations returns the updated set.
        connectedAssociations
            = mConnectedAssociationStore.getConnectedAssociations();

        assertThat(connectedAssociations).containsExactly(associationId1);
    }

    @Test
    public void testAddAndRemoveObserver() throws RemoteException {
        ConnectedAssociationStore.Observer newMockObserver = mock(
            ConnectedAssociationStore.Observer.class);

        // Add a new observer
        mConnectedAssociationStore.addObserver(newMockObserver);

        // Simulate a new association connected.
        int associationId = 1;
        notifyTransportsChanged(
            Arrays.asList(createAssociationInfo(associationId)));

        // Verify the new observer is notified.
        verify(newMockObserver).onTransportConnected(associationId);

        // Remove the new observer
        mConnectedAssociationStore.removeObserver(newMockObserver);

        // Simulate the association being disconnected.
        notifyTransportsChanged(Collections.emptyList());

        // Verify the removed observer is not notified.
        verify(newMockObserver, never()).onTransportDisconnected(associationId);
        // But the original observer is still notified
        verify(mMockObserver).onTransportDisconnected(associationId);
    }

    private void notifyTransportsChanged(
        List<AssociationInfo> associationInfos) throws RemoteException {

        mListenerCaptor.getValue().onTransportsChanged(associationInfos);
        TestableLooper.get(this).processAllMessages();
    }

    private AssociationInfo createAssociationInfo(int associationId) {
        return new AssociationInfo.Builder(associationId, 0, "test_device_mac_address")
                .setDisplayName("test_device_name")
                .build();
    }
}