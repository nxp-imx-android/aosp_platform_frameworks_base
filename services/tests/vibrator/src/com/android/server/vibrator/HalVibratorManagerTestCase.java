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

package com.android.server.vibrator;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.vibrator.IVibratorManager;
import android.os.test.TestLooper;
import android.os.vibrator.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Base test class for {@link HalVibratorManager} implementations. */
public abstract class HalVibratorManagerTestCase {
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock HalVibratorManager.Callbacks mHalCallbackMock;

    final TestLooper mTestLooper = new TestLooper();
    final HalVibratorManagerHelper mHelper = new HalVibratorManagerHelper(mTestLooper.getLooper());

    abstract HalVibratorManager newVibratorManager();

    HalVibratorManager newInitializedVibratorManager() {
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock);
        manager.onSystemReady();
        return manager;
    }

    @Test
    public void init_initializesCapabilitiesAndVibratorIds() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock);

        assertThat(mHelper.getConnectCount()).isEqualTo(1);
        assertThat(manager.getCapabilities()).isEqualTo(IVibratorManager.CAP_SYNC);
        assertThat(manager.getVibratorIds()).isEqualTo(new int[] {1, 2});
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void init_initializesHalAndClearSyncedAndSessions() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock);

        assertThat(mHelper.getConnectCount()).isEqualTo(1);
        assertThat(mHelper.getCancelSyncedCount()).isEqualTo(1);
        assertThat(mHelper.getClearSessionsCount()).isEqualTo(1);
    }

    @Test
    public void init_withNullVibratorIds_returnsEmptyArray() {
        mHelper.setVibratorIds(null);
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock);
        assertThat(manager.getVibratorIds()).isEmpty();
    }

    @Test
    public void hasCapability_checksAllFlagBits() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC | IVibratorManager.CAP_START_SESSIONS);
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.hasCapability(IVibratorManager.CAP_SYNC)).isTrue();
        assertThat(manager.hasCapability(
                IVibratorManager.CAP_SYNC | IVibratorManager.CAP_START_SESSIONS)).isTrue();
        assertThat(manager.hasCapability(
                IVibratorManager.CAP_SYNC | IVibratorManager.CAP_PREPARE_ON)).isFalse();
        assertThat(manager.hasCapability(IVibratorManager.CAP_TRIGGER_CALLBACK)).isFalse();
    }

    @Test
    public void prepareSynced_withCapabilityAndValidVibrators_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[] {1})).isTrue();
        assertThat(manager.prepareSynced(new int[] {1, 3})).isTrue();
        assertThat(manager.prepareSynced(new int[] {1, 2, 3})).isTrue();
        assertThat(mHelper.getPrepareSyncedCount()).isEqualTo(3);
    }

    @Test
    public void prepareSynced_withCapabilityAndBadVibrators_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[0])).isFalse();
        assertThat(manager.prepareSynced(new int[] {4})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 4})).isFalse();
    }

    @Test
    public void prepareSynced_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[] {1})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 3})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void prepareSynced_failure_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        mHelper.setPrepareSyncedToFail();
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[] {1})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 3})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void triggerSynced_withCapability_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.triggerSynced(/* vibrationId= */ 1)).isTrue();
        assertThat(mHelper.getTriggerSyncedCount()).isEqualTo(1);
    }

    @Test
    public void triggerSynced_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.triggerSynced(/* vibrationId= */ 1)).isFalse();
    }

    @Test
    public void triggerSynced_failure_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        mHelper.setTriggerSyncedToFail();
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.triggerSynced(/* vibrationId= */ 1)).isFalse();
    }

    @Test
    public void cancelSynced_withCapability_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.cancelSynced()).isTrue();
    }

    @Test
    public void cancelSynced_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.cancelSynced()).isFalse();
    }

    @Test
    public void startSession_withCapabilityAndValidVibrators_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {1})).isTrue();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 3})).isTrue();
        assertThat(manager.startSession(/* sessionId= */ 3, new int[] {1, 2, 3})).isTrue();
        assertThat(mHelper.getStartSessionCount()).isEqualTo(3);
    }

    @Test
    public void startSession_withCapabilityAndBadVibrators_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {4})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 5})).isFalse();
    }

    @Test
    public void startSession_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {1})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 3})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 3, new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void startSession_failure_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        mHelper.setStartSessionToFail();
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {1})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 3})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 3, new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void endSession_withCapability_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.endSession(/* sessionId= */ 1, /* shouldAbort= */ true)).isTrue();
        assertThat(manager.endSession(/* sessionId= */ 2, /* shouldAbort= */ false)).isTrue();
    }

    @Test
    public void endSession_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.endSession(/* sessionId= */ 1, /* shouldAbort= */ true)).isFalse();
        assertThat(manager.endSession(/* sessionId= */ 2, /* shouldAbort= */ false)).isFalse();
    }
}
