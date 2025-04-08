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

package com.android.server.companion.virtual;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.virtual.ViewConfigurationParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.om.OverlayConstraint;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@EnableFlags({Flags.FLAG_VIEWCONFIGURATION_APIS,
        android.content.res.Flags.FLAG_DIMENSION_FRRO})
public class ViewConfigurationControllerTest {

    private static final int DEVICE_ID = 5;

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private ViewConfigurationController mViewConfigurationController;
    @Mock
    private OverlayManager mOverlayManagerMock;
    @Captor
    private ArgumentCaptor<OverlayManagerTransaction> mTransactionArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = Mockito.spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        when(context.getSystemService(OverlayManager.class)).thenReturn(mOverlayManagerMock);
        mViewConfigurationController = new ViewConfigurationController(context);
    }

    @Test
    public void applyViewConfigurationParams_enablesOverlay() throws Exception {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, createParams());

        verify(mOverlayManagerMock).commit(mTransactionArgumentCaptor.capture());
        OverlayManagerTransaction transaction = mTransactionArgumentCaptor.getValue();
        List<OverlayManagerTransaction.Request> requests = new ArrayList<>();
        transaction.getRequests().forEachRemaining(requests::add);
        assertThat(requests).hasSize(2);
        OverlayManagerTransaction.Request request0 = requests.get(0);
        assertEquals(OverlayManagerTransaction.Request.TYPE_REGISTER_FABRICATED, request0.type);
        List<OverlayConstraint> constraints0 = request0.constraints;
        assertThat(constraints0).hasSize(0);
        OverlayManagerTransaction.Request request1 = requests.get(1);
        List<OverlayConstraint> constraints1 = request1.constraints;
        assertEquals(OverlayManagerTransaction.Request.TYPE_SET_ENABLED, request1.type);
        assertThat(constraints1).hasSize(1);
        OverlayConstraint constraint = constraints1.get(0);
        assertEquals(OverlayConstraint.TYPE_DEVICE_ID, constraint.getType());
        assertEquals(DEVICE_ID, constraint.getValue());
    }

    @Test
    public void close_disablesOverlay() throws Exception {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, createParams());
        clearInvocations(mOverlayManagerMock);

        mViewConfigurationController.close();

        verify(mOverlayManagerMock).commit(mTransactionArgumentCaptor.capture());
        OverlayManagerTransaction transaction = mTransactionArgumentCaptor.getValue();
        List<OverlayManagerTransaction.Request> requests = new ArrayList<>();
        transaction.getRequests().forEachRemaining(requests::add);
        assertThat(requests).hasSize(1);
        OverlayManagerTransaction.Request request = requests.get(0);
        assertEquals(OverlayManagerTransaction.Request.TYPE_UNREGISTER_FABRICATED, request.type);
        List<OverlayConstraint> constraints = request.constraints;
        assertThat(constraints).hasSize(0);
    }

    private static ViewConfigurationParams createParams() {
        return new ViewConfigurationParams.Builder()
                .setTapTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapMinTimeDuration(Duration.ofMillis(10L))
                .setScrollFriction(10f)
                .setMinimumFlingVelocityDpPerSecond(10f)
                .setMaximumFlingVelocityDpPerSecond(10f)
                .setTouchSlopDp(10f)
                .build();
    }
}
