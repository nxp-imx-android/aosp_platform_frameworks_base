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

package android.view.autofill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.app.assist.AssistStructure;
import android.content.ComponentName;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class AutofillNoiseInjectorTest {

    private static final String MASTER_SEED1 = "testMasterSeed123";
    private static final String MASTER_SEED2 = "anotherMasterSeed456";
    private static final ComponentName TEST_COMPONENT =
            new ComponentName("com.example", "com.example.TestActivity");
    private static final int FIXED_LENGTH_BYTES = 32;

    private static final int BITS_TO_RETAIN = 2;

    @Mock private AssistStructure.ViewNode mMockViewNode;
    private AutofillId mTestAutofillId;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestAutofillId = new AutofillId(123);
        when(mMockViewNode.getAutofillId()).thenReturn(mTestAutofillId);
    }

    @Test
    public void testInjectNoise_nullText() {
        when(mMockViewNode.getText()).thenReturn(null);
        AutofillNoiseInjector injector = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result = injector.injectNoise(mMockViewNode);
        assertNull(result);
    }

    @Test
    public void testInjectNoise_emptyText() {
        when(mMockViewNode.getText()).thenReturn(null);
        AutofillNoiseInjector injector = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result = injector.injectNoise(mMockViewNode);
        assertNull(result);
    }

    @Test
    public void testInjectNoise_padding() {
        when(mMockViewNode.getText()).thenReturn("Test"); // Short string
        AutofillNoiseInjector injector = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result = injector.injectNoise(mMockViewNode);
        assertNotNull(result);
        assertEquals(FIXED_LENGTH_BYTES, result.getNoiseInjectedPayload().length);
    }

    @Test
    public void testInjectNoise_truncation() {
        String longString = "ThisIsAVeryLongStringThatExceedsTheThirtyTwoByteLimit";
        when(mMockViewNode.getText()).thenReturn(longString);
        // UTF-16BE bytes
        assertTrue(longString.getBytes(StandardCharsets.UTF_16BE).length > FIXED_LENGTH_BYTES);

        AutofillNoiseInjector injector = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result = injector.injectNoise(mMockViewNode);
        assertNotNull(result);
        assertEquals(FIXED_LENGTH_BYTES, result.getNoiseInjectedPayload().length);
    }

    @Test
    public void testInjectNoise_deterministicDifferentInjectorInstances() {
        when(mMockViewNode.getText()).thenReturn("SameText");
        AutofillNoiseInjector injector1 = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result1 = injector1.injectNoise(mMockViewNode);

        AutofillNoiseInjector injector2 = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result2 = injector2.injectNoise(mMockViewNode);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getRetainedBitMask(), result2.getRetainedBitMask());
        assertTrue(
                Arrays.equals(
                        result1.getNoiseInjectedPayload(), result2.getNoiseInjectedPayload()));
    }

    @Test
    public void testInjectNoise_deterministicSameInjectorInstance() {
        when(mMockViewNode.getText()).thenReturn("SameText");
        AutofillNoiseInjector injector = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result1 = injector.injectNoise(mMockViewNode);
        // Inject the noise again with the same injector instance on the same ViewNode
        AutofillNoiseInjectedData result2 = injector.injectNoise(mMockViewNode);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getRetainedBitMask(), result2.getRetainedBitMask());
        assertTrue(
                Arrays.equals(
                        result1.getNoiseInjectedPayload(), result2.getNoiseInjectedPayload()));
    }

    private boolean isBitSet(byte b, int bit) {
        return ((b >> bit) & 1) == 1;
    }

    @Test
    public void testBitRemoval() {
        when(mMockViewNode.getText()).thenReturn("ABCDEF");
        AutofillNoiseInjector injector = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result = injector.injectNoise(mMockViewNode);
        byte[] payload = result.getNoiseInjectedPayload();
        int retainedBitMask = result.getRetainedBitMask();

        assertEquals(BITS_TO_RETAIN, Integer.bitCount(retainedBitMask));
        for (byte b : payload) {
            for (int i = 0; i < 8; i++) {
                if ((retainedBitMask & (1 << i)) == 0) {
                    // This bit should be zero.
                    assertFalse(isBitSet(b, i));
                }
            }
        }
    }

    @Test
    public void testInjectNoise_differentSeedDifferentResult() {
        when(mMockViewNode.getText()).thenReturn("SameText");
        AutofillNoiseInjector injector1 = new AutofillNoiseInjector(MASTER_SEED1, TEST_COMPONENT);
        AutofillNoiseInjectedData result1 = injector1.injectNoise(mMockViewNode);

        AutofillNoiseInjector injector2 = new AutofillNoiseInjector(MASTER_SEED2, TEST_COMPONENT);
        AutofillNoiseInjectedData result2 = injector2.injectNoise(mMockViewNode);

        assertNotNull(result1);
        assertNotNull(result2);

        // Highly likely to be different due to different seeds for noise
        assertFalse(
                Arrays.equals(
                        result1.getNoiseInjectedPayload(), result2.getNoiseInjectedPayload()));
    }
}
