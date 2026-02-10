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

package androidx.window.extensions.embedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class for {@link AppCompatEmbeddingRuleController}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:AppCompatEmbeddingRuleControllerTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppCompatEmbeddingRuleControllerTest {
    @Test
    public void testCreateVirtualGamepadOverrideRule() {
        final EmbeddingRule embeddingRule =
                AppCompatEmbeddingRuleController.createVirtualGamepadOverrideRule(
                        "package", "activity", 100, "self-package", 1000);
        assertNotNull(embeddingRule);
        assertTrue(embeddingRule instanceof SplitPlaceholderRule);

        final SplitPlaceholderRule placeholderRule = (SplitPlaceholderRule) embeddingRule;
        final ComponentName componentName = placeholderRule.getPlaceholderIntent().getComponent();
        assertNotNull(componentName);
        assertEquals("package", componentName.getPackageName());
        assertEquals("activity", componentName.getClassName());
    }

    @Test
    public void testCreateVirtualGamepadOverrideRule_emptyConfig() {
        final EmbeddingRule embeddingRule =
                AppCompatEmbeddingRuleController.createVirtualGamepadOverrideRule(
                        "" /* packageName */, "" /* activityName */, 100, "self-package", 1000);
        assertNull(embeddingRule);
    }
}
