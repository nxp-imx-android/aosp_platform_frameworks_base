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

package android.service.personalcontext.refiner;

import static com.google.common.truth.Truth.assertThat;

import android.service.personalcontext.Token;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.ContextHintWithSignature;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HintFilterTest {

    private static ContextHintWithSignature makeHint(
            Function<BundleHint.Builder, BundleHint.Builder> hintTuner)
            throws GeneralSecurityException {
        return new ContextHintWithSignature.Builder(
                hintTuner.apply(new BundleHint.Builder()).build(),
                ContextHintTestUtils.generateSignedHintKey())
            .build();
    }

    @Test
    public void testHintFilterRequireAll() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, true)
                        .addHintToken(tokenB, true)
                        .addHintToken(tokenC, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @Test
    public void testHintFilterRequireSome() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, true)
                        .addHintToken(tokenB, true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }

    @Test
    public void testHintFilterRequireMissingSome() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, true)
                        .addHintToken(tokenB, true)
                        .addHintToken(tokenC, true)
                        .addHintToken(new Token(), true)
                        .addHintToken(new Token(), true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @Test
    public void testHintFilterRequireNone() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(new Token(), true)
                        .addHintToken(new Token(), true)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).isEmpty();
    }

    @Test
    public void testHintFilterAllowOne() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, false)
                        .addHintToken(new Token(), false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA);
    }

    @Test
    public void testHintFilterAllowMany() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, false)
                        .addHintToken(tokenB, false)
                        .addHintToken(tokenC, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB, hintC);
    }

    @Test
    public void testHintFilterAllowSome() throws GeneralSecurityException {
        final Token tokenA = new Token();
        final Token tokenB = new Token();
        final Token tokenC = new Token();
        ContextHintWithSignature hintA = makeHint(b -> b.addToken(tokenA));
        ContextHintWithSignature hintB = makeHint(b -> b.addToken(tokenB));
        ContextHintWithSignature hintC = makeHint(b -> b.addToken(tokenC));

        final Set<ContextHintWithSignature> interestedHintSet =
                new HintFilter.Builder()
                        .addHintToken(tokenA, false)
                        .addHintToken(tokenB, false)
                        .build()
                        .getInterestedHintClusters(
                                Set.of(hintA, hintB, hintC), Collections.emptySet());

        assertThat(interestedHintSet).containsExactly(hintA, hintB);
    }
}
