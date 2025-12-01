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

package android.service.personalcontext.renderer;

import static com.google.common.truth.Truth.assertThat;

import android.service.personalcontext.Token;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RendererFilterTest {

    @Test
    public void testRendererFilterRequireRenderToken() {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RendererFilter filter = RendererFilter.REQUIRE_RENDER_TOKEN;

        assertThat(filter.isInterestedInInsight(insight)).isFalse();
    }

    @Test
    public void testRendererFilterAll() {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RendererFilter filter = new RendererFilter.Builder().build();

        assertThat(filter.isInterestedInInsight(insight)).isTrue();
    }

    @Test
    public void testRendererFilterClassMatch() {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RendererFilter filter = new RendererFilter.Builder()
                .addAllowedInsightClass(BundleInsight.class)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isTrue();
    }

    @Test
    public void testRendererFilterClassNoMatch() {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RendererFilter filter = new RendererFilter.Builder()
                .addAllowedInsightClass(ActionableInsight.class)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isFalse();
    }

    @Test
    public void testRendererFilterAllowTagsMatch() {
        final Token tokenA = new Token();
        final ContextInsight insight = new BundleInsight.Builder().addToken(tokenA).build();
        final RendererFilter filter = new RendererFilter.Builder()
                .addAllowedInsightToken(tokenA)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isTrue();
    }

    @Test
    public void testRendererFilterAllowTagsNoMatch() {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final ContextInsight insight = new BundleInsight.Builder().addToken(tokenA).build();
        final RendererFilter filter = new RendererFilter.Builder()
                .addAllowedInsightToken(tokenB)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isFalse();
    }

    @Test
    public void testRendererFilterRequireTagsNoMatch() {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final ContextInsight insight = new BundleInsight.Builder().addToken(tokenA).build();
        final RendererFilter filter = new RendererFilter.Builder()
                .addRequiredInsightToken(tokenA)
                .addRequiredInsightToken(tokenB)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isFalse();
    }

    @Test
    public void testRendererFilterRequireTagsMatch() {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final ContextInsight insight = new BundleInsight.Builder()
                .addToken(tokenA)
                .addToken(tokenB)
                .build();
        final RendererFilter filter = new RendererFilter.Builder()
                .addRequiredInsightToken(tokenA)
                .addRequiredInsightToken(tokenB)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isTrue();
    }
}
