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

package com.android.server.multisensory;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.VibratorManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class MultisensoryServiceScopeTest {

    private MultisensoryServiceScope mUnderTest = new MultisensoryServiceScope();

    @Before
    public void setUp() {
        initializeScope();
    }

    @Test
    public void onInitializeLocked_scopeInitializes() {
        initializeScope();
        assertTrue(mUnderTest.isInitialized());
    }

    private void initializeScope() {
        Context testContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VibratorManager vibratorManager = testContext.getSystemService(VibratorManager.class);
        mUnderTest.initializeLocked(
                vibratorManager.getDefaultVibrator(), testContext.getContentResolver());
    }
}
