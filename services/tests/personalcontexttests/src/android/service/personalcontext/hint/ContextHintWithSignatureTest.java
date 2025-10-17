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

import android.os.Parcel;
import android.service.personalcontext.RenderToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextHintWithSignatureTest {
    private static SecretKeySpec generateKey() {
        byte[] key = new byte[64];
        new Random().nextBytes(key);
        return new SecretKeySpec(key, ContextHintWithSignature.HMAC_ALGORITHM);
    }

    @Test
    public void testParcelAndUnparcel() throws GeneralSecurityException {
        final SecretKeySpec key = generateKey();
        final String packageName = "com.google.packageName";
        final BundleHint hint = new BundleHint();
        final BundleHint attributedHint1 = new BundleHint();
        final BundleHint attributedHint2 = new BundleHint();
        final RenderToken renderToken = new RenderToken.RenderTokenBuilder()
                .setRendererComponentId(UUID.randomUUID())
                .build();

        final ContextHintWithSignature signedAttributedHint1 =
                new ContextHintWithSignature.Builder(attributedHint1, packageName)
                        .setRenderToken(renderToken)
                        .buildAndSign(key);

        final ContextHintWithSignature signedAttributedHint2 =
                new ContextHintWithSignature.Builder(attributedHint2, packageName)
                        .setRenderToken(renderToken)
                        .buildAndSign(key);

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(new ContextHintWithSignature.Builder(hint, packageName)
                .setRenderToken(renderToken)
                .addAttributionHint(signedAttributedHint1)
                .addAttributionHint(signedAttributedHint2)
                .buildAndSign(key), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint = parcel.readParcelable(
                /* loader= */ null, ContextHintWithSignature.class);

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderToken().getRendererComponentId())
                .isEqualTo(renderToken.getRendererComponentId());
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(packageName);
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(2);
        assertThat(signedHint.getAttributionHints().get(0).getContextHint().getHintId())
                .isEqualTo(attributedHint1.getHintId());
        assertThat(signedHint.getAttributionHints().get(1).getContextHint().getHintId())
                .isEqualTo(attributedHint2.getHintId());
    }

    @Test
    public void testParcelAndUnparcelWithoutRenderToken() throws GeneralSecurityException {
        final SecretKeySpec key = generateKey();
        final String packageName = "com.google.packageName";
        final BundleHint hint = new BundleHint();
        final BundleHint attributedHint1 = new BundleHint();
        final BundleHint attributedHint2 = new BundleHint();

        final ContextHintWithSignature signedAttributedHint1 = new ContextHintWithSignature.Builder(
                attributedHint1, packageName).buildAndSign(key);

        final ContextHintWithSignature signedAttributedHint2 = new ContextHintWithSignature.Builder(
                attributedHint2, packageName).buildAndSign(key);

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(new ContextHintWithSignature.Builder(
                hint, packageName)
                .addAttributionHints(List.of(signedAttributedHint1, signedAttributedHint2))
                .buildAndSign(key), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint = parcel.readParcelable(
                /* loader= */ null, ContextHintWithSignature.class);

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderToken()).isNull();
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(packageName);
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(2);
        assertThat(signedHint.getAttributionHints().get(0).getContextHint().getHintId())
                .isEqualTo(attributedHint1.getHintId());
        assertThat(signedHint.getAttributionHints().get(1).getContextHint().getHintId())
                .isEqualTo(attributedHint2.getHintId());
    }

    @Test
    public void testParcelAndUnparcelWithoutAttribution() throws GeneralSecurityException {
        final SecretKeySpec key = generateKey();
        final String packageName = "com.google.packageName";
        final BundleHint hint = new BundleHint();
        final RenderToken renderToken = new RenderToken.RenderTokenBuilder()
                .setRendererComponentId(UUID.randomUUID())
                .build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(new ContextHintWithSignature.Builder(
                hint, packageName)
                .setRenderToken(renderToken)
                .buildAndSign(key), 0);

        parcel.setDataPosition(0);

        final ContextHintWithSignature signedHint = parcel.readParcelable(
                /* loader= */ null, ContextHintWithSignature.class);

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(key)).isTrue();
        assertThat(signedHint.getContextHint().getHintId()).isEqualTo(hint.getHintId());
        assertThat(signedHint.getRenderToken().getRendererComponentId())
                .isEqualTo(renderToken.getRendererComponentId());
        assertThat(signedHint.getOriginatingPackage()).isEqualTo(packageName);
        assertThat(signedHint.getAttributionHints().size()).isEqualTo(0);
    }

    @Test
    public void testSignatureBadSalt() throws GeneralSecurityException {
        final String packageName = "com.google.packageName";
        final BundleHint hint = new BundleHint();

        final ContextHintWithSignature signedHint = (new ContextHintWithSignature.Builder(
                hint, packageName)
                .buildAndSign(generateKey()));

        assertThat(signedHint.isSignatureValid(generateKey())).isFalse();
    }

    @Test
    public void testSignatureBadHash() throws GeneralSecurityException {
        final SecretKeySpec key = generateKey();
        final String packageName = "com.google.packageName";
        final BundleHint hint = new BundleHint();

        final Parcel parcel = Parcel.obtain();
        new ContextHintWithSignature.Builder(
                hint, packageName)
                .buildAndSign(key)
                .writeToParcel(parcel, 0);

        // Modify hash bytes in parcel.
        parcel.setDataPosition(0);
        final byte[] data = parcel.createByteArray();
        final byte[] hash = parcel.createByteArray();

        hash[0] ^= 1;

        parcel.writeByteArray(data);
        parcel.writeByteArray(hash);
        parcel.setDataPosition(0);

        // Re-read the parcel with a bad signature.
        final ContextHintWithSignature signedHint =
                ContextHintWithSignature.CREATOR.createFromParcel(parcel);

        parcel.recycle();

        assertThat(signedHint.isSignatureValid(generateKey())).isFalse();
    }
}
