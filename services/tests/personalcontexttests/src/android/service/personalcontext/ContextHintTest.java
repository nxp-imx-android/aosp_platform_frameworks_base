/*
 * Copyright 2025 The Android Open Source Project
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

import static android.service.personalcontext.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextHintTest {

    // Tests parceling and unparceling fields on the base ContextHint.
    @Test
    public void testContextHintParcelUnparcel() {
        final BundleHint hint = new BundleHint();
        RenderToken renderToken =
                new RenderToken.RenderTokenBuilder()
                        .setRendererComponentId(UUID.randomUUID())
                        .build();
        hint.setRenderToken(renderToken);
        hint.setAttributionHints(new ArrayList<>(List.of(new BundleHint())));

        final ContextHint outputHint = assertParcelUnparcel(hint);

        assertThat(hint.getHintType()).isEqualTo(outputHint.getHintType());
        assertThat(hint.getHintId()).isEqualTo(outputHint.getHintId());
        assertThat(hint.getAttributionHints().size())
                .isEqualTo(outputHint.getAttributionHints().size());

        RenderToken out = outputHint.getRenderToken();
        assertThat(out.getTokenId()).isEqualTo(renderToken.getTokenId());
        assertThat(out.getRendererComponentId()).isEqualTo(renderToken.getRendererComponentId());
    }
}
