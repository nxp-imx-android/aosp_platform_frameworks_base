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

package com.android.server.security.talisman;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenMasterKeyTest {
    @Test
    public void generateMasterKey_success() throws Exception {
        TrustTokenMasterKey.generateMasterKey("trust_token-");
    }

    @Test
    public void generatePerTokenKey_keyShouldBeEncrypted() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        TrustTokenKey tokenKey = masterKey.generatePerTokenKey();
        // One should not be able to use the private key directly.
        var keyFactory = KeyFactory.getInstance("Ed25519");
        assertThrows(
                InvalidKeySpecException.class,
                () ->
                        keyFactory.generatePrivate(
                                new PKCS8EncodedKeySpec(tokenKey.getPrivateKey())));
    }

    @Test
    public void sign_success() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        TrustTokenKey tokenKey = masterKey.generatePerTokenKey();
        byte[] message = "trust_token".getBytes();
        byte[] signature = masterKey.sign(tokenKey, message);
        var keyFactory = KeyFactory.getInstance("Ed25519");
        var verifier = Signature.getInstance("Ed25519");
        var publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(tokenKey.getPublicKey()));
        verifier.initVerify(publicKey);
        verifier.update(message);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    public void sign_unmatched_key() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        var tokenKey =
                new TrustTokenKey(
                        masterKey.generatePerTokenKey().getPublicKey(),
                        masterKey.generatePerTokenKey().getPrivateKey());
        byte[] message = "trust_token".getBytes();
        assertThrows(IllegalArgumentException.class, () -> masterKey.sign(tokenKey, message));
    }

    @Test
    public void sign_different_master_key() throws Exception {
        var masterKey1 = TrustTokenMasterKey.generateMasterKey("trust_token1-");
        var masterKey2 = TrustTokenMasterKey.generateMasterKey("trust_token2-");
        TrustTokenKey tokenKey = masterKey1.generatePerTokenKey();
        byte[] message = "trust_token".getBytes();
        assertThrows(IllegalArgumentException.class, () -> masterKey2.sign(tokenKey, message));
    }

    @Test
    public void attest_success() throws Exception {
        var masterKey = TrustTokenMasterKey.generateMasterKey("trust_token-");
        TrustTokenKey tokenKey = masterKey.generatePerTokenKey();
        TrustTokenBatchAttestation attestation = masterKey.attest(Arrays.asList(tokenKey));
        assertThat(attestation.getBatchHash().length).isGreaterThan(0);
        assertThat(attestation.getSignature().length).isGreaterThan(0);
        assertThat(attestation.getCertificates().size()).isAtLeast(2);
        // TODO(b/468195017): Change to Ed25519 once the issue with loading Ed25519 keys is
        // resolved.
        var verifier = Signature.getInstance("SHA256WithECDSA");
        verifier.initVerify(
                attestation.getCertificates().get(attestation.getCertificates().size() - 1));
        verifier.update(attestation.getBatchHash());
        assertThat(verifier.verify(attestation.getSignature())).isTrue();
    }

    @Test
    public void attest_key_not_owned() throws Exception {
        var masterKey1 = TrustTokenMasterKey.generateMasterKey("trust_token1-");
        var masterKey2 = TrustTokenMasterKey.generateMasterKey("trust_token2-");
        TrustTokenKey tokenKey = masterKey1.generatePerTokenKey();
        assertThrows(
                IllegalArgumentException.class, () -> masterKey2.attest(Arrays.asList(tokenKey)));
    }
}
