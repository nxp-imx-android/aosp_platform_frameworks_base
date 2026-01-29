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

package android.service.personalcontext.hint;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.os.Parcel;
import android.service.personalcontext.RenderToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextHintWithSignatureTest {
    @Test
    public void testParcelAndUnparcel() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();
        final RenderToken renderToken1 = new RenderToken(UUID.randomUUID());
        final RenderToken renderToken2 = new RenderToken(UUID.randomUUID());

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, key)
                        .addRenderTokens(List.of(renderToken1, renderToken2))
                        .build();

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, key)
                        .addRenderTokens(List.of(renderToken1, renderToken2))
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(new ContextHintWithSignatureWrapper(
                new ContextHintWithSignature.Builder(hint, key)
                        .setOriginatingPackage(origin.getPackageName())
                        .addRenderTokens(List.of(renderToken1, renderToken2))
                        .addAttributionHint(signedAttributedHint1)
                        .addAttributionHint(signedAttributedHint2)
                        .build()), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken1, renderToken2);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(2);
        assertThat(signedHint.getAttributionHints().get(0).getOriginatingPackage()).isNull();
        assertThat(signedHint.getAttributionHints().get(0).getContextHint().getHintId())
                .isEqualTo(attributedHint1.getHintId());
        assertThat(signedHint.getAttributionHints().get(1).getOriginatingPackage()).isNull();
        assertThat(signedHint.getAttributionHints().get(1).getContextHint().getHintId())
                .isEqualTo(attributedHint2.getHintId());
    }

    @Test
    public void testParcelAndUnparcelWithoutOrigin() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID());

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, key)
                        .addRenderTokens(List.of(renderToken))
                        .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(new ContextHintWithSignature.Builder(hint, key)
                        .addRenderTokens(List.of(renderToken))
                        .addAttributionHint(signedAttributedHint1)
                        .addAttributionHint(signedAttributedHint2)
                        .build()), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isNull();
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(2);
        assertThat(signedHint.getAttributionHints().get(0).getOriginatingPackage()).isNull();
        assertThat(signedHint.getAttributionHints().get(0).getContextHint().getHintId())
                .isEqualTo(attributedHint1.getHintId());
        assertThat(signedHint.getAttributionHints().get(1).getOriginatingPackage()).isNull();
        assertThat(signedHint.getAttributionHints().get(1).getContextHint().getHintId())
                .isEqualTo(attributedHint2.getHintId());
    }

    @Test
    public void testParcelAndUnparcelWithoutRenderToken() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final BundleHint attributedHint1 = new BundleHint.Builder().build();
        final BundleHint attributedHint2 = new BundleHint.Builder().build();

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, key).build();

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, key).build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(new ContextHintWithSignature.Builder(hint, key)
                        .setOriginatingPackage(origin.getPackageName())
                        .addAttributionHints(List.of(signedAttributedHint1, signedAttributedHint2))
                        .build()), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).isEmpty();
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(2);
        assertThat(signedHint.getAttributionHints().get(0).getContextHint().getHintId())
                .isEqualTo(attributedHint1.getHintId());
        assertThat(signedHint.getAttributionHints().get(1).getContextHint().getHintId())
                .isEqualTo(attributedHint2.getHintId());
    }

    @Test
    public void testParcelAndUnparcelWithoutAttribution() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final ComponentName origin = new ComponentName("com.whatever", "com.whatever.Code");
        final BundleHint hint = new BundleHint.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID());

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(
                new ContextHintWithSignatureWrapper(new ContextHintWithSignature.Builder(hint, key)
                        .setOriginatingPackage(origin.getPackageName())
                        .addRenderTokens(List.of(renderToken))
                        .build()), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint =
                parcel.readParcelable(/* loader= */ null, ContextHintWithSignatureWrapper.class)
                        .getContextHintWithSignature();

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderTokens()).containsExactly(renderToken);
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(origin.getPackageName());
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(0);
    }

    @Test
    public void testSignatureWrongKey() throws GeneralSecurityException {
        final BundleHint hint = new BundleHint.Builder().build();

        final ContextHintWithSignature signedHint =
                new ContextHintWithSignature.Builder(
                        hint, ContextHintTestUtils.generateSignedHintKey()).build();

        assertThat(signedHint.isSignatureValid(ContextHintTestUtils.generateSignedHintKey()))
                .isFalse();
    }

    @Test
    public void testSignatureBadHash() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final BundleHint hint = new BundleHint.Builder().build();

        final Parcel parcel = Parcel.obtain();
        new ContextHintWithSignature.Builder(hint, key)
                .build()
                .writeToParcel(parcel, 0);

        // Modify hash bytes in parcel.
        parcel.setDataPosition(0);
        final byte[] hash = parcel.createByteArray();

        hash[0] ^= 1;

        parcel.setDataPosition(0);
        parcel.writeByteArray(hash);
        parcel.setDataPosition(0);

        // Re-read the parcel with a bad signature.
        final ContextHintWithSignature signedHint = new ContextHintWithSignature(parcel);

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isFalse();
    }
}
