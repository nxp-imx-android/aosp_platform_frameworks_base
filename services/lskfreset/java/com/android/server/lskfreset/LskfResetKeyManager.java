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

package com.android.server.lskfreset;

import static android.app.lskfreset.flags.Flags.enableLskfResetManager;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.security.keystore.KeyProperties;
import android.util.Slog;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;

public class LskfResetKeyManager {
    private static final String TAG = "LskfResetKeyManagerStub";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String EC_CURVE_P256 = "secp256r1";

    public LskfResetKeyManager(Context context) {
        Slog.d(TAG, "Initialized");
    }

    /**
     * Generates and stores an LSKF reset key.
     *
     * @param keyAlias The alias for the key.
     * @return The generated key as a byte array, or null if generation failed.
     */
    @Nullable
    public byte[] generateAndStoreLskfResetKey(@NonNull String keyAlias) {
        try {
            if (enableLskfResetManager()) {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                          KeyProperties.KEY_ALGORITHM_EC);
                ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE_P256);
                keyPairGenerator.initialize(ecSpec);
                KeyPair destinationShareKey = keyPairGenerator.generateKeyPair();
                KeyPair mediatorShareKey = null;
                ECPrivateKey ecDestinationPrivateKey = null, ecMediatorPrivateKey = null;
                BigInteger p = BigInteger.ZERO;
                // This do-while loop won't be running many times as the chance
                // of getting two keys that their addition is divisible by the
                // EC's prime number is extremely unlikely. The addition of these
                // two keys will be used as a separate key, as a result, we check
                // that the addition mod p is not zero as an extra layer of
                // security.
                do {
                    mediatorShareKey = keyPairGenerator.generateKeyPair();

                    ecDestinationPrivateKey = (ECPrivateKey) destinationShareKey.getPrivate();
                    ecMediatorPrivateKey = (ECPrivateKey) mediatorShareKey.getPrivate();

                    p = ((ECFieldFp) ecDestinationPrivateKey.getParams().getCurve()
                        .getField()).getP();
                } while (ecDestinationPrivateKey.getS()
                    .add(ecMediatorPrivateKey.getS())
                    .mod(p).equals(BigInteger.ZERO));
                return destinationShareKey.getPublic().getEncoded();
            } else {
                return null;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to generate key pair " + keyAlias, e);
            return null;
        }
    }

    /**
     * Deletes an LSKF reset key.
     *
     * @param keyAlias The alias of the key to delete.
     */
    public void deleteLskfResetKey(@NonNull String keyAlias) {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias);
                Slog.i(TAG, "Deleted key with alias: " + keyAlias);
            } else {
                Slog.w(TAG, "Key with alias " + keyAlias + " not found for deletion.");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to delete key " + keyAlias, e);
        }
    }

    /**
     * Wrap LskfReset data
     *
     * @param data The data to be encrypted.
     */
    public byte[] wrapData(byte[] data) {
        Slog.d(TAG, "wrapData called (STUB)");
        return null; // Stub
    }

    /**
     * Unwrap LskfReset data
     *
     * @param ivAndEncrypted The data to be unencrypted.
     */
    public byte[] unwrapData(byte[] ivAndEncrypted) {
        Slog.d(TAG, "unwrapData called (STUB)");
        return null; // Stub
    }
}
