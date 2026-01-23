/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.personalcontext.hint;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HintFilterTest {
    private static final String HINT_CLASS_A =
            "android.service.personalcontext.hint.HintFilterTest.A";
    private static final String HINT_CLASS_B =
            "android.service.personalcontext.hint.HintFilterTest.B";
    private static final String HINT_CLASS_C =
            "android.service.personalcontext.hint.HintFilterTest.C";
    private static final String HINT_CLASS_D =
            "android.service.personalcontext.hint.HintFilterTest.D";
    private static final String HINT_CLASS_E =
            "android.service.personalcontext.hint.HintFilterTest.E";

    private static ContextHintWithSignature makeHint(String hintClass)
            throws GeneralSecurityException {
        return new ContextHintWithSignature.Builder(
                new BundleHint.Builder().setHintTypeName(hintClass).build(),
                ContextHintTestUtils.generateSignedHintKey())
            .build();
    }

    @Test
    public void testHintFilterRequireAll() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_A, true)
                        .addHintType(HINT_CLASS_B, true)
                        .addHintType(HINT_CLASS_C, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @Test
    public void testHintFilterRequireSome() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_A, true)
                        .addHintType(HINT_CLASS_B, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }

    @Test
    public void testHintFilterRequireMissingSome() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_A, true)
                        .addHintType(HINT_CLASS_B, true)
                        .addHintType(HINT_CLASS_C, true)
                        .addHintType(HINT_CLASS_D, true)
                        .addHintType(HINT_CLASS_E, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @Test
    public void testHintFilterRequireNone() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_D, true)
                        .addHintType(HINT_CLASS_E, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @Test
    public void testHintFilterAllowOne() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_A, false)
                        .addHintType(HINT_CLASS_D, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA);
    }

    @Test
    public void testHintFilterAllowMany() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_A, false)
                        .addHintType(HINT_CLASS_B, false)
                        .addHintType(HINT_CLASS_C, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @Test
    public void testHintFilterAllowSome() throws GeneralSecurityException {
        ContextHintWithSignature hintA = makeHint(HINT_CLASS_A);
        ContextHintWithSignature hintB = makeHint(HINT_CLASS_B);
        ContextHintWithSignature hintC = makeHint(HINT_CLASS_C);

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintType(HINT_CLASS_A, false)
                        .addHintType(HINT_CLASS_B, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }
}
