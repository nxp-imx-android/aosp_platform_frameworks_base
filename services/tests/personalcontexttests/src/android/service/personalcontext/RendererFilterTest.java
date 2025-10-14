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

package android.service.personalcontext;

import static com.google.common.truth.Truth.assertThat;

import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.renderer.RendererFilter;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RendererFilterTest {

    @Test
    public void testRendererFilterDefaults() {
        final RendererFilter filter = new RendererFilter.Builder().build();
        assertThat(filter.shouldIncludeRenderedInsights()).isFalse();
        assertThat(filter.getValidInsightTypes()).isEmpty();
        assertThat(filter.getValidHintTypes()).isEmpty();
    }


    @Test
    public void testRendererFilterNonDefaults() {
        final RendererFilter filter =
                new RendererFilter.Builder()
                        .setShouldIncludeRenderedInsights(true)
                        .addValidInsightType(ContextInsight.INSIGHT_TYPE_BUNDLE)
                        .addValidHintType(ContextHint.HINT_TYPE_BUNDLE)
                        .build();

        assertThat(filter.shouldIncludeRenderedInsights()).isTrue();
        assertThat(filter.getValidInsightTypes()
                .contains(ContextInsight.INSIGHT_TYPE_BUNDLE)).isTrue();
        assertThat(filter.getValidHintTypes().contains(ContextHint.HINT_TYPE_BUNDLE)).isTrue();
    }
}
