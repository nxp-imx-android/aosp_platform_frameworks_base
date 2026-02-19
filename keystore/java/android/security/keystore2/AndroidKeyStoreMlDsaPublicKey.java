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
import java.security.cert.X509Certificate;

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
    // OID value for ML-DSA-65. See RFC 9881 section 2.
    private static final String ML_DSA_65_OID = "2.16.840.1.101.3.4.3.18";

    // OID value for ML-DSA-87. See RFC 9881 section 2.
    private static final String ML_DSA_87_OID = "2.16.840.1.101.3.4.3.19";

    // Java Security Standard Algorithm Name for the key's ML-DSA parameter set (e.g. "ML-DSA-65").
    private final String mMlDsaAlgorithm;

    /**
     * Constructs an {@link AndroidKeyStoreMlDsaPublicKey}.
     *
     * @param descriptor Key descriptor.
     * @param metadata Key metadata.
     * @param securityLevel Security level of the key.
     * @param x509Certificate X.509 certificate for the public key.
     */
    // Implementation note: The X.509 certificate is passed as a parameter instead of the algorithm
    // name as a String to prevent callers from passing the incorrect value (e.g. the family name
    // "ML-DSA") and therefore avoiding the need for input parameter validation.
    public AndroidKeyStoreMlDsaPublicKey(
            @NonNull KeyDescriptor descriptor,
            @NonNull KeyMetadata metadata,
            @NonNull KeyStoreSecurityLevel securityLevel,
            @NonNull X509Certificate x509Certificate) {
        super(
                descriptor,
                metadata,
                x509Certificate.getPublicKey().getEncoded(),
                KeyProperties.KEY_ALGORITHM_ML_DSA,
                securityLevel);

        // Get the algorithm name that the OID maps to, or the OID if no mapping exists.
        String mlDsaAlgorithm = x509Certificate.getSigAlgName();

        // If the OID is returned, it means Conscrypt's mapping is incomplete. We explicitly
        // override this fallback and do the mapping ourselves since
        // {@link android.security.keystore2.AndroidKeyStoreMlDsaKey#getMlDsaAlgorithm()} must
        // return the Java Security Standard Algorithm Name for the ML-DSA parameter set. See
        // RFC 9881 section 2.
        if (mlDsaAlgorithm.equals(ML_DSA_65_OID)) {
            mlDsaAlgorithm = KeyProperties.KEY_ALGORITHM_ML_DSA_65;
        } else if (mlDsaAlgorithm.equals(ML_DSA_87_OID)) {
            mlDsaAlgorithm = KeyProperties.KEY_ALGORITHM_ML_DSA_87;
        } else if (!mlDsaAlgorithm.equalsIgnoreCase(KeyProperties.KEY_ALGORITHM_ML_DSA_65)
                && !mlDsaAlgorithm.equalsIgnoreCase(KeyProperties.KEY_ALGORITHM_ML_DSA_87)) {
            throw new ProviderException("Unsupported algorithm: " + mlDsaAlgorithm);
        }

        mMlDsaAlgorithm = mlDsaAlgorithm;
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
