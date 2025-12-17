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

@RunWith(AndroidJUnit4.class)
public final class TalismanMasterKeyTest {
    static final String TAG = "TalismanMasterKeyTest";

    @Test
    public void generateMasterKey_success() throws Exception {
        TalismanMasterKey.generateMasterKey("talisman-");
    }

    @Test
    public void generatePerTalismanKey_keyShouldBeEncrypted() throws Exception {
        var masterKey = TalismanMasterKey.generateMasterKey("talisman-");
        TalismanKey talismanKey = masterKey.generatePerTalismanKey();
        // One should not be able to use the private key directly.
        var keyFactory = KeyFactory.getInstance("Ed25519");
        assertThrows(
                InvalidKeySpecException.class,
                () ->
                        keyFactory.generatePrivate(
                                new PKCS8EncodedKeySpec(talismanKey.privateKey().toByteArray())));
    }

    @Test
    public void sign_success() throws Exception {
        var masterKey = TalismanMasterKey.generateMasterKey("talisman-");
        TalismanKey talismanKey = masterKey.generatePerTalismanKey();
        byte[] message = "talisman".getBytes();
        byte[] signature = masterKey.sign(talismanKey, message);
        var keyFactory = KeyFactory.getInstance("Ed25519");
        var verifier = Signature.getInstance("Ed25519");
        var publicKey =
                keyFactory.generatePublic(
                        new X509EncodedKeySpec(talismanKey.publicKey().toByteArray()));
        verifier.initVerify(publicKey);
        verifier.update(message);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    public void sign_unmatched_key() throws Exception {
        var masterKey = TalismanMasterKey.generateMasterKey("talisman-");
        var talismanKey =
                new TalismanKey(
                        masterKey.generatePerTalismanKey().publicKey(),
                        masterKey.generatePerTalismanKey().privateKey());
        byte[] message = "talisman".getBytes();
        assertThrows(IllegalArgumentException.class, () -> masterKey.sign(talismanKey, message));
    }

    @Test
    public void sign_different_master_key() throws Exception {
        var masterKey1 = TalismanMasterKey.generateMasterKey("talisman1-");
        var masterKey2 = TalismanMasterKey.generateMasterKey("talisman2-");
        TalismanKey talismanKey = masterKey1.generatePerTalismanKey();
        byte[] message = "talisman".getBytes();
        assertThrows(IllegalArgumentException.class, () -> masterKey2.sign(talismanKey, message));
    }
}
