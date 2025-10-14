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

import android.os.Bundle;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BundleHintTest {

    @Test
    public void testBundleHintParcelUnparcel() {
        final int inputValue = 1234;
        final String dataKey = "test-key";
        final Bundle data = new Bundle();
        data.putInt(dataKey, inputValue);

        final BundleHint hint = new BundleHint();
        hint.getDataBundle().putAll(data);

        final ContextHint outputHint = assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(BundleHint.class);
        final int outputValue = ((BundleHint) outputHint).getDataBundle().getInt(dataKey);

        assertThat(outputValue).isEqualTo(inputValue);
    }
}
