/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.companion.CompanionDeviceManager.MESSAGE_TASK_CONTINUITY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.content.ContextWrapper;
import android.companion.IOnMessageReceivedListener;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.AssociationInfo;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Arrays;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskReceiverTest {

    private Context mMockContext;

    @Mock
    private ICompanionDeviceManager mMockCompanionDeviceManagerService;

    private CompanionDeviceManager mCompanionDeviceManager;

    private TaskReceiver mTaskReceiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockContext =  Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));

        // Setup fake services.
        mCompanionDeviceManager
            = new CompanionDeviceManager(
                mMockCompanionDeviceManagerService,
                mMockContext);

        when(mMockContext.getSystemService(Context.COMPANION_DEVICE_SERVICE))
            .thenReturn(mCompanionDeviceManager);

        // Create TaskReceiver.
        mTaskReceiver = new TaskReceiver(mMockContext);
    }

    @Test
    public void testStopListening_doesNothingIfNotListening()
        throws Exception {

        mTaskReceiver.stopListening();
        Mockito.verifyNoInteractions(mMockCompanionDeviceManagerService);
    }

    @Test
    public void testStartAndStopListening_registersMessageListener()
        throws Exception {

        // Start listening, verifying a message listener is added.
        ArgumentCaptor<IOnMessageReceivedListener> listenerCaptor
            = ArgumentCaptor.forClass(IOnMessageReceivedListener.class);
        mTaskReceiver.startListening();
        verify(mMockCompanionDeviceManagerService, times(1))
            .addOnMessageReceivedListener(
                eq(MESSAGE_TASK_CONTINUITY),
                listenerCaptor.capture());
        IOnMessageReceivedListener listener = listenerCaptor.getValue();
        assertThat(listener).isNotNull();

        // Stop listening, verifying the message listener is removed.
        mTaskReceiver.stopListening();
        verify(mMockCompanionDeviceManagerService, times(1))
            .removeOnMessageReceivedListener(
                eq(MESSAGE_TASK_CONTINUITY),
                eq(listener));
    }
}