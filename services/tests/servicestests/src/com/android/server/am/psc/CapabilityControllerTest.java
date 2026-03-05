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

package com.android.server.am.psc;

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_IMPLICIT_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_TOP;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT_UI;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;

import static com.android.server.am.psc.Constants.FOREGROUND_APP_ADJ;
import static com.android.server.am.psc.OomAdjuster.ALL_CPU_TIME_CAPABILITIES;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_ALLOW_LIST;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_NONE;
import static com.android.server.am.psc.OomAdjuster.CPU_TIME_REASON_OTHER;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_LEGACY;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_NONE;
import static com.android.server.am.psc.OomAdjusterImpl.Connection.CPU_TIME_TRANSMISSION_NORMAL;

import static org.junit.Assert.assertEquals;

import android.app.ActivityManager.ProcessCapability;
import android.app.ActivityManager.ProcessState;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.server.am.psc.TestGraphElements.TestProcessNode;
import com.android.server.am.psc.TestGraphElements.TestProviderBindingEdge;
import com.android.server.am.psc.TestGraphElements.TestServiceBindingEdge;
import com.android.server.am.psc.TestGraphElements.TestServiceRecord;
import com.android.server.tests.assertutils.FlagAssert;

import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link CapabilityController}.
 * Build/Install/Run:
 * atest FrameworksServicesTests:CapabilityControllerTest
 * atest FrameworksServicesTestsRavenwood_ProcessStateController:CapabilityControllerTest
 */
@Presubmit
public class CapabilityControllerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testDefaultNode_GrantsNoCapabilities() {
        TestProcessNode node = new TestProcessNode.Builder().build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_NONE, edge.evaluateCapabilityFilter());
        assertEquals(CPU_TIME_REASON_NONE, edge.getCpuTimeReasons());
    }

    @Test
    public void testEvaluateProcessEdgeFilter_NonRunningProcess_GrantsNoCapabilities() {
        // A non-running process should never have any capabilities, even if other conditions for
        // granting capabilities are met.
        TestProcessNode node = new TestProcessNode.Builder()
                .withProcessRunning(false)
                .withMaxAdj(FOREGROUND_APP_ADJ) // This would normally grant all capabilities.
                .withHasActiveInstrumentation(true) // This would normally grant BFSL.
                .build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_NONE, edge.evaluateCapabilityFilter());
    }

    @Test
    public void testEvaluateMaxAdjPolicy_MaxAdjForeground_GrantsAllCapabilities() {
        TestProcessNode node = new TestProcessNode.Builder().withMaxAdj(FOREGROUND_APP_ADJ).build();
        ProcessEdge edge = new ProcessEdge(node);

        assertEquals(PROCESS_CAPABILITY_ALL, edge.evaluateCapabilityFilter());
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_RegularFgs_GrantsBfsl() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .withHasNonShortForegroundServices(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_ShortFgs_DoesNotGrantBfsl() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .withHasNonShortForegroundServices(false)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasNotSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    @RequiresFlagsEnabled(android.media.audio.Flags.FLAG_RO_FOREGROUND_AUDIO_CONTROL)
    public void testEvaluateForegroundServicePolicy_Fgs_GrantsAudioControl() {
        final TestServiceRecord service = new TestServiceRecord.Builder().build();
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_FgsWithLocation_GrantsLocation() {
        final TestServiceRecord service = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_LOCATION)
                .build();
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_LOCATION);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_FgsWithCameraMic_GrantsCameraMic() {
        final TestServiceRecord service = new TestServiceRecord.Builder()
                .withForegroundServiceType(
                        FOREGROUND_SERVICE_TYPE_CAMERA | FOREGROUND_SERVICE_TYPE_MICROPHONE)
                .build();
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .withCachedCompatChangeCameraMicrophoneCapability(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_CAMERA | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE);
    }

    @Test
    public void testEvaluateForegroundServicePolicy_FgsWithCompatDisabled_GrantsCameraMic() {
        // No specific FGS type, but compat change is off.
        final TestServiceRecord service = new TestServiceRecord.Builder().build();
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .addService(service)
                .withCachedCompatChangeCameraMicrophoneCapability(false)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_CAMERA | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE);
    }

    @Test
    @RequiresFlagsEnabled(android.media.audio.Flags.FLAG_RO_FOREGROUND_AUDIO_CONTROL)
    public void testEvaluateForegroundServicePolicy_MultipleServices_GrantsCombinedCapabilities() {
        // Service 1: Location
        final TestServiceRecord service1 = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_LOCATION)
                .build();

        // Service 2: Camera
        final TestServiceRecord service2 = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_CAMERA)
                .build();

        // Service 3: Microphone
        final TestServiceRecord service3 = new TestServiceRecord.Builder()
                .withForegroundServiceType(FOREGROUND_SERVICE_TYPE_MICROPHONE)
                .build();

        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .addService(service1)
                .addService(service2)
                .addService(service3)
                .withCachedCompatChangeCameraMicrophoneCapability(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_FOREGROUND_LOCATION
                        | PROCESS_CAPABILITY_FOREGROUND_CAMERA
                        | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE
                        | PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL);
    }

    @Test
    public void testEvaluateProcStatePolicy_HasActiveInstrumentation_GrantsBfSl() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasActiveInstrumentation(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    public void testEvaluateProcStatePolicy_TopOrBetter_GrantsAll() {
        // States that are better than or equal to TOP.
        final @ProcessState int[] states = {
                PROCESS_STATE_PERSISTENT,
                PROCESS_STATE_PERSISTENT_UI,
                PROCESS_STATE_TOP,
        };
        for (final @ProcessState int state : states) {
            final TestProcessNode node = new TestProcessNode.Builder().withProcState(state).build();
            final ProcessEdge edge = new ProcessEdge(node);

            assertEquals(PROCESS_CAPABILITY_ALL, edge.evaluateCapabilityFilter());
            // No CPU time reason is granted in this case.
            assertEquals(CPU_TIME_REASON_NONE, edge.getCpuTimeReasons());
        }
    }

    @Test
    public void testEvaluateProcStatePolicy_BtopNoInstrumentation_GrantsBfsl() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withProcState(PROCESS_STATE_BOUND_TOP)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_BFSL);
    }

    @Test
    public void testEvaluateProcStatePolicy_BtopWithInstrumentation_GrantsInstrDefaults() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withProcState(PROCESS_STATE_BOUND_TOP)
                .withHasActiveInstrumentation(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter())
                .hasSet(PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS);
    }

    @Test
    public void testEvaluateProcStatePolicy_FgsWithInstrumentation_GrantsInstrDefaults() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withProcState(PROCESS_STATE_FOREGROUND_SERVICE)
                .withHasActiveInstrumentation(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter())
                .hasSet(PROCESS_CAPABILITY_INSTRUMENTATION_DEFAULTS);
    }

    @Test
    public void testEvaluateProcStatePolicy_BfgsOrBetter_GrantsNetworkCapabilities() {
        final @ProcessCapability int networkCapabilities =
                PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK
                        | PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;

        // States that are better than or equal to BFGS.
        final @ProcessState int[] states = {
                PROCESS_STATE_PERSISTENT,
                PROCESS_STATE_PERSISTENT_UI,
                PROCESS_STATE_TOP,
                PROCESS_STATE_BOUND_TOP,
                PROCESS_STATE_BOUND_FOREGROUND_SERVICE,
        };
        for (final @ProcessState int state : states) {
            final TestProcessNode node = new TestProcessNode.Builder().withProcState(state).build();
            final ProcessEdge edge = new ProcessEdge(node);

            FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(networkCapabilities);
        }

        // Network capabilities should not be granted for other states.
        final TestProcessNode node = new TestProcessNode.Builder()
                .withProcState(PROCESS_STATE_IMPORTANT_FOREGROUND)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasNotSet(networkCapabilities);
    }

    @Test
    public void testEvaluateCpuTimePolicy_AllowListed_GrantsCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder().withCurAllowListed(true).build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_ALLOW_LIST);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasForegroundActivities_GrantsCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundActivities(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasExecutingServices_GrantsCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasExecutingServices(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasForegroundServices_GrantsCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasForegroundServices(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_IsReceivingBroadcast_GrantsCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withReceivingBroadcast(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_HasActiveInstrumentation_GrantsCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasActiveInstrumentation(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(PROCESS_CAPABILITY_CPU_TIME);
        FlagAssert.assertThat(edge.getCpuTimeReasons()).hasSet(CPU_TIME_REASON_OTHER);
    }

    @Test
    public void testEvaluateCpuTimePolicy_PolicyNotSatisfied_DoesNotGrantCpuTime() {
        final TestServiceRecord service = new TestServiceRecord.Builder()
                .withForegroundService(false)
                .build();
        final TestProcessNode node = new TestProcessNode.Builder()
                .addService(service)
                .withHasForegroundServices(false)
                .withProcState(PROCESS_STATE_SERVICE)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasNotSet(
                PROCESS_CAPABILITY_CPU_TIME);
        assertEquals(CPU_TIME_REASON_NONE, edge.getCpuTimeReasons());
    }

    @Test
    public void testEvaluateImplicitCpuTimePolicy_IntrinsicImplicitCpuTime_GrantsImplicitCpuTime() {
        final TestProcessNode node = new TestProcessNode.Builder()
                .withHasIntrinsicImplicitCpuTime(true)
                .build();
        final ProcessEdge edge = new ProcessEdge(node);

        FlagAssert.assertThat(edge.evaluateCapabilityFilter()).hasSet(
                PROCESS_CAPABILITY_IMPLICIT_CPU_TIME);
    }

    @Test
    public void testDefaultProviderBindingEdge_GrantsOnlyBfslAndCpuTime() {
        final TestProviderBindingEdge edge = new TestProviderBindingEdge();
        assertEquals(PROCESS_CAPABILITY_BFSL | ALL_CPU_TIME_CAPABILITIES,
                edge.evaluateCapabilityFilter());
    }

    @Test
    public void testDefaultServiceBindingEdge_GrantsOnlyBfslAndAudioControl() {
        final TestServiceBindingEdge edge = new TestServiceBindingEdge.Builder().build();
        assertEquals(PROCESS_CAPABILITY_BFSL | PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL,
                edge.evaluateCapabilityFilter());
    }

    @Test
    public void testEvaluateServiceBindingCpuTimePolicy_TransmissionTypes() {
        final TestServiceBindingEdge edge1 = new TestServiceBindingEdge.Builder()
                .withCpuTimeTransmissionType(CPU_TIME_TRANSMISSION_NORMAL)
                .build();
        FlagAssert.assertThat(edge1.evaluateCapabilityFilter()).hasSet(ALL_CPU_TIME_CAPABILITIES);

        final TestServiceBindingEdge edge2 = new TestServiceBindingEdge.Builder()
                .withCpuTimeTransmissionType(CPU_TIME_TRANSMISSION_LEGACY)
                .build();
        FlagAssert.assertThat(edge2.evaluateCapabilityFilter()).hasSet(ALL_CPU_TIME_CAPABILITIES);

        final TestServiceBindingEdge edge3 = new TestServiceBindingEdge.Builder()
                .withCpuTimeTransmissionType(CPU_TIME_TRANSMISSION_NONE)
                .build();
        FlagAssert.assertThat(edge3.evaluateCapabilityFilter()).hasNotSet(
                ALL_CPU_TIME_CAPABILITIES);
    }
}
