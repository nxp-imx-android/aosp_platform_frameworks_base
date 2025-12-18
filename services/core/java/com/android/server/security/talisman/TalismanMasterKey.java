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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Slog;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Represents the master key that's used to encrypt and attest per-Talisman keys.
 *
 * <p>The master key is stored in the hardware-backed keystore, which is used to encrypt and attest
 * the per-Talisman key. It provides methods to use the per-Talisman key. The plaintext of the
 * private part of the per-Talisman key created by this class is never revealed.
 */
final class TalismanMasterKey {
    private static final String TAG = "TalismanMasterKey";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    @NonNull private final KeyStoreHelper mHelper;
    @NonNull private final SecretKey mEncryptionKey;
    @NonNull private final PrivateKey mAttestationKey;
    @NonNull private final List<Certificate> mAttestationCertificateChain;

    @NonNull
    static TalismanMasterKey generateMasterKey(@NonNull String keyPrefix) {
        // Since AndroidKeyStore overwrites existing key with the same alias, we remove the key
        // manually to avoid any incompatibility error.
        try {
            removeFromKeyStore(keyPrefix);
        } catch (IllegalStateException e) {
            Slog.w(TAG, "Failed to delete previous master key: " + e.toString());
        }
        KeyStoreHelper.generateEncryptionKey(encryptionKeyAlias(keyPrefix));
        KeyStoreHelper.generateAttestationKey(attestationKeyAlias(keyPrefix));
        return fromKeyStore(keyPrefix);
    }

    @NonNull
    static TalismanMasterKey fromKeyStore(@NonNull String keyPrefix) {
        var helper = new KeyStoreHelper();
        var encryptionEntry =
                (KeyStore.SecretKeyEntry) helper.getEntry(encryptionKeyAlias(keyPrefix));
        var attestationEntry =
                (KeyStore.PrivateKeyEntry) helper.getEntry(attestationKeyAlias(keyPrefix));
        var certificates =
                new ArrayList<Certificate>(1 + attestationEntry.getCertificateChain().length);
        certificates.addAll(Arrays.asList(attestationEntry.getCertificateChain()));
        certificates.add(attestationEntry.getCertificate());
        return new TalismanMasterKey(
                helper,
                encryptionEntry.getSecretKey(),
                attestationEntry.getPrivateKey(),
                certificates);
    }

    static void removeFromKeyStore(@NonNull String keyPrefix) {
        var helper = new KeyStoreHelper();
        helper.deleteEntry(encryptionKeyAlias(keyPrefix));
        helper.deleteEntry(attestationKeyAlias(keyPrefix));
    }

    @NonNull
    TalismanKey generatePerTalismanKey() {
        KeyPair key = mHelper.generateKeyPair();
        byte[] publicKey = mHelper.encode(key.getPublic());
        // Use the public key as the AAD so that we can easily verify that the public key and the
        // private key are a pair.
        byte[] privateKey = mHelper.wrap(mEncryptionKey, key.getPrivate(), publicKey);
        return new TalismanKey(publicKey, privateKey);
    }

    @NonNull
    byte[] sign(@NonNull TalismanKey key, @NonNull byte[] message) {
        PrivateKey privateKey =
                mHelper.unwrap(
                        mEncryptionKey,
                        key.privateKey().toByteArray(),
                        key.publicKey().toByteArray());
        return mHelper.sign(privateKey, message);
    }

    @NonNull
    TalismanBatchAttestation attest(@NonNull List<TalismanKey> keys) {
        for (TalismanKey key : keys) {
            // Verify the keys are valid and owned by this master key.
            mHelper.unwrap(
                    mEncryptionKey, key.privateKey().toByteArray(), key.publicKey().toByteArray());
        }
        MessageDigest digest = mHelper.sha256Digest();
        for (TalismanKey key : keys) {
            digest.update(key.publicKey().asReadOnlyByteBuffer());
        }
        byte[] batchHash = digest.digest();
        return new TalismanBatchAttestation(
                batchHash, mHelper.sign(mAttestationKey, batchHash), mAttestationCertificateChain);
    }

    private TalismanMasterKey(
            @NonNull KeyStoreHelper helper,
            @NonNull SecretKey encryptionKey,
            @NonNull PrivateKey attestationKey,
            @NonNull List<Certificate> attestationCertificateChain) {
        mHelper = helper;
        mEncryptionKey = encryptionKey;
        mAttestationKey = attestationKey;
        mAttestationCertificateChain = List.copyOf(attestationCertificateChain);
    }

    @NonNull
    private static String encryptionKeyAlias(@NonNull String keyPrefix) {
        // WARNING: Modifying this invalidates all Talismans created before.
        return keyPrefix + "-encryption";
    }

    @NonNull
    private static String attestationKeyAlias(@NonNull String keyPrefix) {
        return keyPrefix + "-attestation";
    }

    private static class KeyStoreHelper {
        private static final String ED25519 = "Ed25519";

        @NonNull private final KeyStore mKeyStore;
        @NonNull private final KeyPairGenerator mPerTalismanKeyGenerator;
        @NonNull private final KeyFactory mKeyFactory;

        KeyStoreHelper() {
            try {
                mKeyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
                mKeyStore.load(null);
                mPerTalismanKeyGenerator = KeyPairGenerator.getInstance(ED25519);
                mPerTalismanKeyGenerator.initialize(255);
                mKeyFactory = KeyFactory.getInstance(ED25519);
            } catch (KeyStoreException
                    | CertificateException
                    | IOException
                    | NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to initialize KeyStore", e);
            }
        }

        @NonNull
        KeyStore.Entry getEntry(@NonNull String alias) {
            try {
                return mKeyStore.getEntry(alias, null);
            } catch (KeyStoreException | UnrecoverableEntryException e) {
                throw new IllegalStateException("cannot load key " + alias, e);
            } catch (NoSuchAlgorithmException impossible) {
                // CTS guarantees Ed25519 is supported.
                throw new AssertionError(impossible);
            }
        }

        void deleteEntry(@NonNull String alias) {
            try {
                mKeyStore.deleteEntry(alias);
            } catch (KeyStoreException e) {
                throw new IllegalStateException("cannot delete key " + alias, e);
            }
        }

        KeyPair generateKeyPair() {
            return mPerTalismanKeyGenerator.generateKeyPair();
        }

        byte[] encode(PublicKey key) {
            try {
                return mKeyFactory.getKeySpec(key, X509EncodedKeySpec.class).getEncoded();
            } catch (InvalidKeySpecException impossible) {
                // This method only encodes the public key, for which X.509 is appropriate.
                throw new AssertionError(impossible);
            }
        }

        @NonNull
        byte[] wrap(SecretKey masterKey, PrivateKey toWrap, byte[] aad) {
            // Use ENCRYPT_MODE instead of WRAP_MODE so that we can do AEAD.
            Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, masterKey, null);
            cipher.updateAAD(aad);
            byte[] wrapped;
            try {
                wrapped =
                        cipher.doFinal(
                                mKeyFactory
                                        .getKeySpec(toWrap, PKCS8EncodedKeySpec.class)
                                        .getEncoded());
            } catch (IllegalBlockSizeException
                    | InvalidKeySpecException
                    | BadPaddingException impossible) {
                // The block size, the key spec and the padding are known to be valid.
                throw new AssertionError(impossible);
            }
            try {
                return new EncryptedPrivateKeyInfo(cipher.getParameters(), wrapped).getEncoded();
            } catch (NoSuchAlgorithmException | IOException impossible) {
                // CTS guarantees AES is supported. The parameters are generated by the code
                // itself, so it should always be valid.
                throw new AssertionError(impossible);
            }
        }

        @NonNull
        PrivateKey unwrap(SecretKey masterKey, byte[] wrapped, byte[] aad) {
            EncryptedPrivateKeyInfo encryptedKey;
            try {
                encryptedKey = new EncryptedPrivateKeyInfo(wrapped);
            } catch (IOException e) {
                throw new IllegalArgumentException("invalid key", e);
            }
            // The decoded params use the OID parameter name, but AndroidKeyStore expects "GCM", so
            // we convert it to GCMParameterSpec explicitly.
            AlgorithmParameterSpec params;
            try {
                params = encryptedKey.getAlgParameters().getParameterSpec(GCMParameterSpec.class);
            } catch (InvalidParameterSpecException e) {
                throw new IllegalArgumentException("invalid key", e);
            }
            Cipher cipher = getCipher(Cipher.DECRYPT_MODE, masterKey, params);
            cipher.updateAAD(aad);
            try {
                return mKeyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(cipher.doFinal(encryptedKey.getEncryptedData())));
            } catch (InvalidKeySpecException | BadPaddingException | IllegalBlockSizeException e) {
                throw new IllegalArgumentException("invalid key", e);
            }
        }

        @NonNull
        byte[] sign(@NonNull PrivateKey key, @NonNull byte[] message) {
            try {
                Signature signature;
                // TODO(b/468195017): Change to only handle Ed25519 once the issue with loading
                // Ed25519 keys is resolved.
                if (key.getAlgorithm().equals(KeyProperties.KEY_ALGORITHM_EC)) {
                    signature = Signature.getInstance("SHA256withECDSA");
                } else {
                    signature = Signature.getInstance(key.getAlgorithm());
                }
                signature.initSign(key);
                signature.update(message);
                return signature.sign();
            } catch (InvalidKeyException e) {
                throw new IllegalArgumentException("invalid key", e);
            } catch (NoSuchAlgorithmException | SignatureException impossible) {
                // CTS guarantees Ed25519 is supported.
                throw new AssertionError(impossible);
            }
        }

        @NonNull
        MessageDigest sha256Digest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }

        @NonNull
        static SecretKey generateEncryptionKey(@NonNull String alias) {
            try {
                var keyGenerator =
                        KeyGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(
                                        alias,
                                        KeyProperties.PURPOSE_ENCRYPT
                                                | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setKeySize(256)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build());
                return keyGenerator.generateKey();
            } catch (NoSuchAlgorithmException
                    | NoSuchProviderException
                    | InvalidAlgorithmParameterException impossible) {
                // CTS guarantees AES is supported and that AndroidKeyStore is there.
                throw new AssertionError(impossible);
            }
        }

        @NonNull
        static KeyPair generateAttestationKey(@NonNull String alias) {
            // TODO(b/468195017): Change to Ed25519 once the issue with loading Ed25519 keys is
            // resolved.
            try {
                var keyPairGenerator =
                        KeyPairGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE);
                keyPairGenerator.initialize(
                        new KeyGenParameterSpec.Builder(
                                        alias,
                                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                                .setKeySize(256)
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .setAttestationChallenge(new byte[] {})
                                .build());
                return keyPairGenerator.generateKeyPair();
            } catch (NoSuchAlgorithmException
                    | NoSuchProviderException
                    | InvalidAlgorithmParameterException impossible) {
                throw new AssertionError(impossible);
            }
        }

        @NonNull
        private static Cipher getCipher(
                int mode, @NonNull SecretKey key, @Nullable AlgorithmParameterSpec params) {
            Cipher cipher;
            try {
                cipher = Cipher.getInstance("AES/GCM/NoPadding");
            } catch (NoSuchAlgorithmException | NoSuchPaddingException impossible) {
                // CTS guarantees AES/GCM/Nopadding is supported.
                throw new AssertionError(impossible);
            }
            try {
                cipher.init(mode, key, params);
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                throw new IllegalStateException("invalid key", e);
            }
            return cipher;
        }
    }
}
