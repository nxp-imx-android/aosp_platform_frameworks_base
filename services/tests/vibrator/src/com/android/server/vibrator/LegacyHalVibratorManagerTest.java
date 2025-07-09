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

import android.hardware.vibrator.IVibrator;
import android.os.test.TestLooper;

import com.android.server.vibrator.VintfHalVibratorManager.LegacyHalVibratorManager;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test class for {@link LegacyHalVibratorManager}. */
public class LegacyHalVibratorManagerTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock HalVibratorManager.Callbacks mHalCallbackMock;
    @Mock HalVibrator.Callbacks mHalVibratorCallbackMock;

    private final TestLooper mTestLooper = new TestLooper();
    private final HalVibratorManagerHelper mHelper =
            new HalVibratorManagerHelper(mTestLooper.getLooper());

    @Test
    public void init_returnsAllFalseExceptVibratorIds() {
        mHelper.setVibratorIds(new int[] { 1, 2 });
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(manager.getVibratorIds()).asList().containsExactly(1, 2).inOrder();
        assertThat(manager.getCapabilities()).isEqualTo(0);
        assertThat(manager.prepareSynced(new int[] { 1 })).isFalse();
        assertThat(manager.triggerSynced(1)).isFalse();
        assertThat(manager.cancelSynced()).isFalse();
        assertThat(manager.startSession(1, new int[] { 2 })).isFalse();
        assertThat(manager.endSession(1, false)).isFalse();
    }

    @Test
    public void init_initializesVibrators() {
        mHelper.setVibratorIds(new int[] { 1, 2 });
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(mHelper.getVibratorHelper(1).isInitialized()).isTrue();
        assertThat(mHelper.getVibratorHelper(2).isInitialized()).isTrue();
    }

    @Test
    public void onSystemReady_triggersAllVibratorsOnSystemReady() {
        mHelper.setVibratorIds(new int[] {1, 2});
        mHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHelper.getVibratorHelper(2).setLoadInfoToFail();
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(manager.getVibrator(1).getInfo().getCapabilities())
                .isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);
        assertThat(manager.getVibrator(2).getInfo().getCapabilities())
                .isEqualTo(0);

        mHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        manager.onSystemReady();

        // Capabilities from vibrator 2 reloaded after failure.
        assertThat(manager.getVibrator(1).getInfo().getCapabilities())
                .isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);
        assertThat(manager.getVibrator(2).getInfo().getCapabilities())
                .isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);
    }

    @Test
    public void getVibrator_returnsVibratorOnlyForValidIds() {
        mHelper.setVibratorIds(new int[] { 1, 2 });
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();

        assertThat(manager.getVibrator(-1)).isNull();
        assertThat(manager.getVibrator(0)).isNull();
        assertThat(manager.getVibrator(1)).isNotNull();
        assertThat(manager.getVibrator(1).getInfo().getId()).isEqualTo(1);
        assertThat(manager.getVibrator(2)).isNotNull();
        assertThat(manager.getVibrator(2).getInfo().getId()).isEqualTo(2);
        assertThat(manager.getVibrator(3)).isNull();
    }
}
