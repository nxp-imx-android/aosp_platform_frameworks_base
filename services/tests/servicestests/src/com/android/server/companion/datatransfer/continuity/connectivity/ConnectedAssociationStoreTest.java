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
import static com.android.server.companion.datatransfer.continuity.TaskContinuityTestUtils.createAssociationInfo;

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
import java.util.Collection;
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

        mConnectedAssociationStore = new ConnectedAssociationStore(
            mCompanionDeviceManager,
            mMockContext.getMainExecutor());

        mConnectedAssociationStore.addObserver(mMockObserver);
        verify(mMockCompanionDeviceManagerService).addOnTransportsChangedListener(
                mListenerCaptor.capture());
    }

    @Test
    public void testOnTransportConnected_notifyObserver() throws RemoteException {
        // Simulate a new association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        notifyTransportsChanged(Arrays.asList(associationInfo));

        // Verify the observer is notified.
        verify(mMockObserver).onTransportConnected(associationInfo);
        verify(mMockObserver, never()).onTransportDisconnected(associationInfo.getId());
    }

    @Test
    public void testOnTransportDisconnected_notifyObserver() throws RemoteException {
        // Start with an association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        notifyTransportsChanged(Arrays.asList(associationInfo));

        // Simulate the association being disconnected.
        notifyTransportsChanged(Collections.emptyList());

        // Verify the observer is notified of the disconnection.
        verify(mMockObserver).onTransportDisconnected(associationInfo.getId());
    }

    @Test
    public void testOnTransportChanged_noChange_noNotification() throws RemoteException {
        // Start with an association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        notifyTransportsChanged(Arrays.asList(associationInfo));

        // Simulate the same association still connected.
        notifyTransportsChanged(Arrays.asList(associationInfo));

        // Verify the observer is only notified once for the initial connection.
        verify(mMockObserver, times(1)).onTransportConnected(associationInfo);
        verify(mMockObserver, never()).onTransportDisconnected(associationInfo.getId());
    }

    @Test
    public void testGetConnectedAssociations() throws RemoteException {
        // Connect two associations.
        AssociationInfo associationInfo1 = createAssociationInfo(1, "name");
        AssociationInfo associationInfo2 = createAssociationInfo(2, "name");
        notifyTransportsChanged(Arrays.asList(associationInfo1, associationInfo2));

        // Verify that getConnectedAssociations returns the correct set.
        Collection<AssociationInfo> connectedAssociations
            = mConnectedAssociationStore.getConnectedAssociations();
        assertThat(connectedAssociations)
            .containsExactly(associationInfo1, associationInfo2);

        AssociationInfo result
            = mConnectedAssociationStore.getConnectedAssociationById(1);
        assertThat(result).isEqualTo(associationInfo1);

        // Disconnect one association.
        notifyTransportsChanged(
                Arrays.asList(associationInfo1));

        // Verify that getConnectedAssociations returns the updated set.
        connectedAssociations = mConnectedAssociationStore.getConnectedAssociations();

        assertThat(connectedAssociations).containsExactly(associationInfo1);
    }

    @Test
    public void testAddAndRemoveObserver() throws RemoteException {
        ConnectedAssociationStore.Observer newMockObserver = mock(
            ConnectedAssociationStore.Observer.class);

        // Add a new observer
        mConnectedAssociationStore.addObserver(newMockObserver);

        // Simulate a new association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name");
        notifyTransportsChanged(
            Arrays.asList(associationInfo));

        // Verify the new observer is notified.
        verify(newMockObserver).onTransportConnected(associationInfo);

        // Remove the new observer
        mConnectedAssociationStore.removeObserver(newMockObserver);

        // Simulate the association being disconnected.
        notifyTransportsChanged(Collections.emptyList());

        // Verify the removed observer is not notified.
        verify(newMockObserver, never()).onTransportDisconnected(associationInfo.getId());
        // But the original observer is still notified
        verify(mMockObserver).onTransportDisconnected(associationInfo.getId());
    }

    private void notifyTransportsChanged(
        List<AssociationInfo> associationInfos) throws RemoteException {

        mListenerCaptor.getValue().onTransportsChanged(associationInfos);
        TestableLooper.get(this).processAllMessages();
    }
}