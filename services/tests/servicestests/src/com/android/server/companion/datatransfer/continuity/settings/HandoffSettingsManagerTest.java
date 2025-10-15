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

package com.android.server.companion.datatransfer.continuity.settings;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Mock;
import org.junit.Before;
import org.junit.Test;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffSettingsManagerTest {
    private static final int USER_ID = 1;

    @Mock private HandoffPreferenceStore mHandoffPreferenceStore;

    private HandoffSettingsManager mHandoffSettingsManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mHandoffSettingsManager = new HandoffSettingsManager(mHandoffPreferenceStore);
    }

    @Test
    public void isHandoffActiveForUser_returnsFromPreferenceStore() {
        when(mHandoffPreferenceStore.isHandoffEnabledForUser(USER_ID)).thenReturn(true);
        assertThat(mHandoffSettingsManager.isHandoffActiveForUser(USER_ID)).isTrue();
        verify(mHandoffPreferenceStore).isHandoffEnabledForUser(USER_ID);
    }

    @Test
    public void setHandoffEnabledForUser_callsPreferenceStore() {
        mHandoffSettingsManager.setHandoffEnabledForUser(USER_ID, false);
        verify(mHandoffPreferenceStore).setHandoffEnabledForUser(USER_ID, false);
    }
}
