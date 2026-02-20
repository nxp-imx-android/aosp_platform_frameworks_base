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

package android.security.keystore2;

import android.annotation.NonNull;
import android.security.KeyStoreSecurityLevel;
import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyDescriptor;
import android.system.keystore2.KeyMetadata;

import java.security.ProviderException;

/**
 * {@link java.security.PublicKey} implementation for ML-DSA public keys backed by Android Keystore.
 * Only the ML-DSA-65 and ML-DSA-87 parameter sets are supported.
 *
 * <p>This class is not case-sensitive since Java Security Standard Algorithm Names are not
 * case-sensitive.
 *
 * <p>This class is partially compliant with <a href="https://openjdk.org/jeps/497">JEP 497</a>. The
 * only deviation is that it does <b>not</b> implement {@link
 * java.security.AsymmetricKey#getParams()}. This is because the {@link java.security.AsymmetricKey}
 * interface was introduced in JDK 22 and has not been imported into Android. The interface would
 * not only need to be imported, but wrapper classes would need to be created to enable Conscrypt to
 * continue to be backwards-compatible to JDK 8. However, callers can leverage other methods to
 * retrieve the same information:
 *
 * <ul>
 *   <li>The algorithm family ("ML-DSA") can be obtained via {@link
 *       java.security.Key#getAlgorithm()}.
 *   <li>The parameter set name (e.g. "ML-DSA-65") can be determined by calling {@link
 *       java.security.Key#getEncoded()} on an instance of this class. The parameter set is
 *       indicated by the OID in the AlgorithmIdentifier structure (which appears in the X.509
 *       certificate's preamble and in its SubjectPublicKeyInfo structure).
 * </ul>
 *
 * @hide
 */
public class AndroidKeyStoreMlDsaPublicKey extends AndroidKeyStorePublicKey
        implements AndroidKeyStoreMlDsaKey {
    // Java Security Standard Algorithm Name for the key's ML-DSA parameter set (e.g. "ML-DSA-65").
    private final String mMlDsaAlgorithm;

    /**
     * Constructs an {@link AndroidKeyStoreMlDsaPublicKey}.
     *
     * @param descriptor Key descriptor.
     * @param metadata Key metadata.
     * @param securityLevel Security level of the key.
     * @param x509EncodedForm X.509/SPKI encoded public key.
     * @param algorithm Java Security Standard Algorithm Name for the ML-DSA-65 or ML-DSA-87
     *        parameter set.
     */
    public AndroidKeyStoreMlDsaPublicKey(
            @NonNull KeyDescriptor descriptor,
            @NonNull KeyMetadata metadata,
            @NonNull KeyStoreSecurityLevel securityLevel,
            @NonNull byte[] x509EncodedForm,
            @NonNull String algorithm) {
        super(
                descriptor,
                metadata,
                x509EncodedForm,
                KeyProperties.KEY_ALGORITHM_ML_DSA,
                securityLevel);

        if (!algorithm.equals(KeyProperties.KEY_ALGORITHM_ML_DSA_65)
                && !algorithm.equals(KeyProperties.KEY_ALGORITHM_ML_DSA_87)) {
            throw new ProviderException("Unsupported algorithm: " + algorithm);
        }

        mMlDsaAlgorithm = algorithm;
    }

    @Override
    public AndroidKeyStorePrivateKey getPrivateKey() {
        return new AndroidKeyStoreMlDsaPrivateKey(
                getUserKeyDescriptor(),
                getKeyIdDescriptor().nspace,
                getAuthorizations(),
                getSecurityLevel(),
                mMlDsaAlgorithm);
    }

    @Override
    public String getMlDsaAlgorithm() {
        return mMlDsaAlgorithm;
    }
}
