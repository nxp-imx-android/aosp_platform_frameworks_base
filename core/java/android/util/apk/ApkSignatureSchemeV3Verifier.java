/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.util.apk;

import static android.util.apk.ApkSigningBlockUtils.CONTENT_DIGEST_VERITY_CHUNKED_SHA256;
import static android.util.apk.ApkSigningBlockUtils.compareSignatureAlgorithm;
import static android.util.apk.ApkSigningBlockUtils.getContentDigestAlgorithmJcaDigestAlgorithm;
import static android.util.apk.ApkSigningBlockUtils.getLengthPrefixedSlice;
import static android.util.apk.ApkSigningBlockUtils.getSignatureAlgorithmContentDigestAlgorithm;
import static android.util.apk.ApkSigningBlockUtils.getSignatureAlgorithmJcaKeyAlgorithm;
import static android.util.apk.ApkSigningBlockUtils.getSignatureAlgorithmJcaSignatureAlgorithm;
import static android.util.apk.ApkSigningBlockUtils.isSupportedSignatureAlgorithm;
import static android.util.apk.ApkSigningBlockUtils.readLengthPrefixedByteArray;
import static android.util.apk.ApkSigningBlockUtils.verifyProofOfRotationStruct;

import android.os.Build;
import android.util.ArrayMap;
import android.util.Pair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * APK Signature Scheme v3 verifier.
 *
 * @hide for internal use only.
 */
public class ApkSignatureSchemeV3Verifier {

    /**
     * ID of this signature scheme as used in X-Android-APK-Signed header used in JAR signing.
     */
    public static final int SF_ATTRIBUTE_ANDROID_APK_SIGNED_ID = 3;

    static final int APK_SIGNATURE_SCHEME_V3_BLOCK_ID = 0xf05368c0;
    static final int APK_SIGNATURE_SCHEME_V31_BLOCK_ID = 0x1b93ad61;
    static final int APK_SIGNATURE_SCHEME_V32_BLOCK_ID = 0x70e1c89f;

    /**
     * Returns {@code true} if the provided APK contains an APK Signature Scheme V3 signature.
     *
     * <p><b>NOTE: This method does not verify the signature.</b>
     */
    public static boolean hasSignature(String apkFile) throws IOException {
        try (RandomAccessFile apk = new RandomAccessFile(apkFile, "r")) {
            findSignature(apk);
            return true;
        } catch (SignatureNotFoundException e) {
            return false;
        }
    }

    /**
     * Verifies APK Signature Scheme v3 signatures of the provided APK and returns the certificates
     * associated with each signer.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v3.
     * @throws SecurityException          if the APK Signature Scheme v3 signature of this APK does
     *                                    not
     *                                    verify.
     * @throws IOException                if an I/O error occurs while reading the APK file.
     */
    public static VerifiedSigner verify(String apkFile)
            throws SignatureNotFoundException, SecurityException, IOException {
        return verify(apkFile, true);
    }

    /**
     * Returns the certificates associated with each signer for the given APK without verification.
     * This method is dangerous and should not be used, unless the caller is absolutely certain the
     * APK is trusted.  Specifically, verification is only done for the APK Signature Scheme v3
     * Block while gathering signer information.  The APK contents are not verified.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v3.
     * @throws IOException                if an I/O error occurs while reading the APK file.
     */
    public static VerifiedSigner unsafeGetCertsWithoutVerification(String apkFile)
            throws SignatureNotFoundException, SecurityException, IOException {
        return verify(apkFile, false);
    }

    private static VerifiedSigner verify(String apkFile, boolean verifyIntegrity)
            throws SignatureNotFoundException, SecurityException, IOException {
        try (RandomAccessFile apk = new RandomAccessFile(apkFile, "r")) {
            return verify(apk, verifyIntegrity);
        }
    }

    /**
     * Verifies APK Signature Scheme v3 signatures of the provided APK and returns the certificates
     * associated with each signer.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v3.
     * @throws SecurityException          if an APK Signature Scheme v3 signature of this APK does
     *                                    not
     *                                    verify.
     * @throws IOException                if an I/O error occurs while reading the APK file.
     */
    private static VerifiedSigner verify(RandomAccessFile apk, boolean verifyIntegrity)
            throws SignatureNotFoundException, SecurityException, IOException {
        ApkSignatureSchemeV3Verifier verifier = new ApkSignatureSchemeV3Verifier(apk,
                verifyIntegrity);
        if (android.security.Flags.apkPqcHybridSigning()) {
            try {
                SignatureInfo signatureInfo = findSignature(apk, APK_SIGNATURE_SCHEME_V32_BLOCK_ID);
                return verifier.verify(signatureInfo, APK_SIGNATURE_SCHEME_V32_BLOCK_ID);
            } catch (SignatureNotFoundException ignored) {
                // This is expected if the APK is not signed with a v3.2 hybrid block.
            } catch (PlatformNotSupportedException ignored) {
                // This is expected if the v3.2 hybrid block is targeting a platform version later
                // than that of the current device.
            }
        }
        try {
            SignatureInfo signatureInfo = findSignature(apk, APK_SIGNATURE_SCHEME_V31_BLOCK_ID);
            return verifier.verify(signatureInfo, APK_SIGNATURE_SCHEME_V31_BLOCK_ID);
        } catch (SignatureNotFoundException ignored) {
            // This is expected if the APK is not using v3.1 to target rotation.
        } catch (PlatformNotSupportedException ignored) {
            // This is expected if the APK is targeting a platform version later than that of the
            // device for rotation.
        }
        try {
            SignatureInfo signatureInfo = findSignature(apk, APK_SIGNATURE_SCHEME_V3_BLOCK_ID);
            return verifier.verify(signatureInfo, APK_SIGNATURE_SCHEME_V3_BLOCK_ID);
        } catch (PlatformNotSupportedException e) {
            throw new SecurityException(e);
        }
    }

    /**
     * Returns the APK Signature Scheme v3 block contained in the provided APK file and the
     * additional information relevant for verifying the block against the file.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v3.
     * @throws IOException                if an I/O error occurs while reading the APK file.
     */
    public static SignatureInfo findSignature(RandomAccessFile apk)
            throws IOException, SignatureNotFoundException {
        return findSignature(apk, APK_SIGNATURE_SCHEME_V3_BLOCK_ID);
    }

    /*
     * Returns the APK Signature Scheme v3 block in the provided {@code apk} file with the specified
     * {@code blockId} and the additional information relevant for verifying the block against the
     * file.
     *
     * @throws SignatureNotFoundException if the APK is not signed using APK Signature Scheme v3
     * @throws IOException                if an I/O error occurs while reading the APK file
     **/
    private static SignatureInfo findSignature(RandomAccessFile apk, int blockId)
            throws IOException, SignatureNotFoundException {
        return ApkSigningBlockUtils.findSignature(apk, blockId);
    }

    private final RandomAccessFile mApk;
    private final boolean mVerifyIntegrity;
    private OptionalInt mOptionalRotationMinSdkVersion = OptionalInt.empty();
    private OptionalInt mOptionalHybridMinSdkVersion = OptionalInt.empty();
    private int mSignerMinSdkVersion;
    private int mBlockId;

    private ApkSignatureSchemeV3Verifier(RandomAccessFile apk, boolean verifyIntegrity) {
        mApk = apk;
        mVerifyIntegrity = verifyIntegrity;
    }

    /**
     * Verifies the contents of the provided APK file against the provided APK Signature Scheme v3
     * Block.
     *
     * @param signatureInfo APK Signature Scheme v3 Block and information relevant for verifying it
     *                      against the APK file.
     */
    private VerifiedSigner verify(SignatureInfo signatureInfo, int blockId)
            throws SecurityException, IOException, PlatformNotSupportedException {
        mBlockId = blockId;
        List<VerifiedSigner> verifiedSigners = new ArrayList<>();
        CertificateFactory certFactory;
        try {
            certFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException("Failed to obtain X.509 CertificateFactory", e);
        }
        ByteBuffer signers;
        try {
            signers = getLengthPrefixedSlice(signatureInfo.signatureBlock);
        } catch (IOException e) {
            throw new SecurityException("Failed to read list of signers", e);
        }
        while (signers.hasRemaining()) {
            try {
                ByteBuffer signer = getLengthPrefixedSlice(signers);
                verifiedSigners.add(verifySigner(signer, certFactory));
                // No V3 signature scheme supports more than 2 signers.
                if (verifiedSigners.size() > 2) {
                    throw new SecurityException(
                            "APK Signature Scheme V3 found at least 3 signers targeting this "
                                    + "release");
                }
            } catch (PlatformNotSupportedException e) {
                // this signer is for a different platform, ignore it.
                continue;
            } catch (IOException | BufferUnderflowException | SecurityException e) {
                throw new SecurityException(
                        "Failed to parse/verify signer #" + verifiedSigners.size() + " block",
                        e);
            }
        }

        if (verifiedSigners.isEmpty()) {
            // There must always be a valid signer targeting the device SDK version for a v3.0
            // signature.
            if (blockId == APK_SIGNATURE_SCHEME_V3_BLOCK_ID) {
                throw new SecurityException("No signers found");
            }
            throw new PlatformNotSupportedException(
                    "None of the signers support the current platform version");
        }

        VerifiedSigner result;
        if (android.security.Flags.apkPqcHybridSigning()) {
            if (blockId == APK_SIGNATURE_SCHEME_V32_BLOCK_ID) {
                result = verifyV32Signers(verifiedSigners);
            } else if (blockId == APK_SIGNATURE_SCHEME_V31_BLOCK_ID) {
                result = verifyV31Signers(verifiedSigners);
            } else {
                // The v3.0 scheme only supports a single signer
                if (verifiedSigners.size() != 1) {
                    throw new SecurityException("APK Signature Scheme V3 only supports one signer: "
                            + "multiple signers found.");
                }
                result = verifiedSigners.get(0);
                if (ApkSigningBlockUtils.isCertificatePqc(result.certs[0])) {
                    throw new SecurityException(
                            "The platform does not currently support single PQC signers in the v3 "
                                    + "signature blocks");
                }
            }
        } else {
            if (verifiedSigners.size() != 1) {
                throw new SecurityException("APK Signature Scheme V3 only supports one signer: "
                        + "multiple signers found.");
            }
            result = verifiedSigners.get(0);
        }

        if (result.contentDigests.isEmpty()) {
            throw new SecurityException("No content digests found");
        }

        if (mVerifyIntegrity) {
            ApkSigningBlockUtils.verifyIntegrity(result.contentDigests, mApk, signatureInfo);
        }

        byte[] verityRootHash = null;
        if (result.contentDigests.containsKey(CONTENT_DIGEST_VERITY_CHUNKED_SHA256)) {
            byte[] verityDigest = result.contentDigests.get(CONTENT_DIGEST_VERITY_CHUNKED_SHA256);
            verityRootHash = ApkSigningBlockUtils.parseVerityDigestAndVerifySourceLength(
                    verityDigest, mApk.getChannel().size(), signatureInfo);
        }

        result.verityRootHash = verityRootHash;
        result.blockId = blockId;
        return result;
    }

    private VerifiedSigner verifySigner(
                ByteBuffer signerBlock,
                CertificateFactory certFactory)
            throws SecurityException, IOException, PlatformNotSupportedException {
        ByteBuffer signedData = getLengthPrefixedSlice(signerBlock);
        int minSdkVersion = signerBlock.getInt();
        int maxSdkVersion = signerBlock.getInt();

        if (Build.VERSION.SDK_INT < minSdkVersion || Build.VERSION.SDK_INT > maxSdkVersion) {
            // if this is a v3.1 block then save the minimum SDK version for rotation for comparison
            // against the v3.0 additional attribute.
            if (mBlockId == APK_SIGNATURE_SCHEME_V31_BLOCK_ID) {
                if (!mOptionalRotationMinSdkVersion.isPresent()
                        || mOptionalRotationMinSdkVersion.getAsInt() > minSdkVersion) {
                    mOptionalRotationMinSdkVersion = OptionalInt.of(minSdkVersion);
                }
            } else if (android.security.Flags.apkPqcHybridSigning()
                    && mBlockId == APK_SIGNATURE_SCHEME_V32_BLOCK_ID) {
                // Stripping protection is also available for the v3.2 hybrid block; save the hybrid
                // block's minSdkVersion to ensure it matches what's written in the v3.0/v3.1 blocks
                if (mOptionalHybridMinSdkVersion.isPresent()) {
                    // The hybrid block should contain exactly two signers targeting the same SDK
                    // range.
                    int firstSignerMinSdkVersion = mOptionalHybridMinSdkVersion.getAsInt();
                    if (firstSignerMinSdkVersion != minSdkVersion) {
                        throw new SecurityException(
                                "Hybrid signer minimum SDK versions do not match; signer1: "
                                        + firstSignerMinSdkVersion + ", signer2: " + minSdkVersion);
                    }
                } else {
                    mOptionalHybridMinSdkVersion = OptionalInt.of(minSdkVersion);
                }
            }
            // this signature isn't meant to be used with this platform, skip it.
            throw new PlatformNotSupportedException(
                    "Signer not supported by this platform "
                            + "version. This platform: " + Build.VERSION.SDK_INT
                            + ", signer minSdkVersion: " + minSdkVersion
                            + ", maxSdkVersion: " + maxSdkVersion);
        }

        ByteBuffer signatures = getLengthPrefixedSlice(signerBlock);
        byte[] publicKeyBytes = readLengthPrefixedByteArray(signerBlock);

        int signatureCount = 0;
        int bestSigAlgorithm = -1;
        byte[] bestSigAlgorithmSignatureBytes = null;
        List<Integer> signaturesSigAlgorithms = new ArrayList<>();
        while (signatures.hasRemaining()) {
            signatureCount++;
            try {
                ByteBuffer signature = getLengthPrefixedSlice(signatures);
                if (signature.remaining() < 8) {
                    throw new SecurityException("Signature record too short");
                }
                int sigAlgorithm = signature.getInt();
                signaturesSigAlgorithms.add(sigAlgorithm);
                if (!isSupportedSignatureAlgorithm(sigAlgorithm)) {
                    continue;
                }
                if ((bestSigAlgorithm == -1)
                        || (compareSignatureAlgorithm(sigAlgorithm, bestSigAlgorithm) > 0)) {
                    bestSigAlgorithm = sigAlgorithm;
                    bestSigAlgorithmSignatureBytes = readLengthPrefixedByteArray(signature);
                }
            } catch (IOException | BufferUnderflowException e) {
                throw new SecurityException(
                        "Failed to parse signature record #" + signatureCount,
                        e);
            }
        }
        if (bestSigAlgorithm == -1) {
            if (signatureCount == 0) {
                throw new SecurityException("No signatures found");
            } else {
                throw new SecurityException("No supported signatures found");
            }
        }

        String keyAlgorithm = getSignatureAlgorithmJcaKeyAlgorithm(bestSigAlgorithm);
        Pair<String, ? extends AlgorithmParameterSpec> signatureAlgorithmParams =
                getSignatureAlgorithmJcaSignatureAlgorithm(bestSigAlgorithm);
        String jcaSignatureAlgorithm = signatureAlgorithmParams.first;
        AlgorithmParameterSpec jcaSignatureAlgorithmParams = signatureAlgorithmParams.second;
        boolean sigVerified;
        try {
            PublicKey publicKey =
                    KeyFactory.getInstance(keyAlgorithm)
                            .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature sig = Signature.getInstance(jcaSignatureAlgorithm);
            sig.initVerify(publicKey);
            if (jcaSignatureAlgorithmParams != null) {
                sig.setParameter(jcaSignatureAlgorithmParams);
            }
            sig.update(signedData);
            sigVerified = sig.verify(bestSigAlgorithmSignatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException
                | InvalidAlgorithmParameterException | SignatureException e) {
            throw new SecurityException(
                    "Failed to verify " + jcaSignatureAlgorithm + " signature", e);
        }
        if (!sigVerified) {
            throw new SecurityException(jcaSignatureAlgorithm + " signature did not verify");
        }

        // Signature over signedData has verified.
        Map<Integer, byte[]> contentDigests = new ArrayMap<>();
        byte[] contentDigest = null;
        signedData.clear();
        ByteBuffer digests = getLengthPrefixedSlice(signedData);
        List<Integer> digestsSigAlgorithms = new ArrayList<>();
        int digestCount = 0;
        while (digests.hasRemaining()) {
            digestCount++;
            try {
                ByteBuffer digest = getLengthPrefixedSlice(digests);
                if (digest.remaining() < 8) {
                    throw new IOException("Record too short");
                }
                int sigAlgorithm = digest.getInt();
                digestsSigAlgorithms.add(sigAlgorithm);
                if (sigAlgorithm == bestSigAlgorithm) {
                    contentDigest = readLengthPrefixedByteArray(digest);
                }
            } catch (IOException | BufferUnderflowException e) {
                throw new IOException("Failed to parse digest record #" + digestCount, e);
            }
        }

        if (!signaturesSigAlgorithms.equals(digestsSigAlgorithms)) {
            throw new SecurityException(
                    "Signature algorithms don't match between digests and signatures records");
        }
        int digestAlgorithm = getSignatureAlgorithmContentDigestAlgorithm(bestSigAlgorithm);
        byte[] previousSignerDigest = contentDigests.put(digestAlgorithm, contentDigest);
        if ((previousSignerDigest != null)
                && (!MessageDigest.isEqual(previousSignerDigest, contentDigest))) {
            throw new SecurityException(
                    getContentDigestAlgorithmJcaDigestAlgorithm(digestAlgorithm)
                            + " contents digest does not match the digest specified by a "
                            + "preceding signer");
        }

        ByteBuffer certificates = getLengthPrefixedSlice(signedData);
        List<X509Certificate> certs = new ArrayList<>();
        int certificateCount = 0;
        while (certificates.hasRemaining()) {
            certificateCount++;
            byte[] encodedCert = readLengthPrefixedByteArray(certificates);
            X509Certificate certificate;
            try {
                certificate = (X509Certificate)
                        certFactory.generateCertificate(new ByteArrayInputStream(encodedCert));
            } catch (CertificateException e) {
                throw new SecurityException("Failed to decode certificate #" + certificateCount, e);
            }
            certificate = new VerbatimX509Certificate(certificate, encodedCert);
            certs.add(certificate);
        }

        if (certs.isEmpty()) {
            throw new SecurityException("No certificates listed");
        }
        X509Certificate mainCertificate = certs.get(0);
        byte[] certificatePublicKeyBytes = mainCertificate.getPublicKey().getEncoded();
        if (!Arrays.equals(publicKeyBytes, certificatePublicKeyBytes)) {
            throw new SecurityException(
                    "Public key mismatch between certificate and signature record");
        }

        int signedMinSDK = signedData.getInt();
        if (signedMinSDK != minSdkVersion) {
            throw new SecurityException(
                    "minSdkVersion mismatch between signed and unsigned in v3 signer block.");
        }
        mSignerMinSdkVersion = signedMinSDK;

        int signedMaxSDK = signedData.getInt();
        if (signedMaxSDK != maxSdkVersion) {
            throw new SecurityException(
                    "maxSdkVersion mismatch between signed and unsigned in v3 signer block.");
        }

        ByteBuffer additionalAttrs = getLengthPrefixedSlice(signedData);
        VerifiedSigner verifiedSigner = verifyAdditionalAttributes(additionalAttrs, certs,
                certFactory, contentDigests);
        verifiedSigner.minSdkVersion = signedMinSDK;
        verifiedSigner.maxSdkVersion = signedMaxSDK;
        return verifiedSigner;
    }

    private static final int PROOF_OF_ROTATION_ATTR_ID = 0x3ba06f8c;
    private static final int ROTATION_MIN_SDK_VERSION_ATTR_ID = 0x559f8b02;
    private static final int SIGNER_TARGETS_DEV_RELEASE_ATTR_ID = 0xc2a6b3ba;
    private static final int HYBRID_MIN_SDK_VERSION_ATTR_ID = 0xbf940529;

    private VerifiedSigner verifyAdditionalAttributes(ByteBuffer attrs, List<X509Certificate> certs,
            CertificateFactory certFactory, Map<Integer, byte[]> contentDigests)
            throws IOException, PlatformNotSupportedException {
        X509Certificate[] certChain = certs.toArray(new X509Certificate[certs.size()]);
        ApkSigningBlockUtils.VerifiedProofOfRotation por = null;
        boolean isDevTarget = false;

        while (attrs.hasRemaining()) {
            ByteBuffer attr = getLengthPrefixedSlice(attrs);
            if (attr.remaining() < 4) {
                throw new IOException("Remaining buffer too short to contain additional attribute "
                        + "ID. Remaining: " + attr.remaining());
            }
            int id = attr.getInt();
            switch (id) {
                case PROOF_OF_ROTATION_ATTR_ID:
                    if (por != null) {
                        throw new SecurityException("Encountered multiple Proof-of-rotation records"
                                + " when verifying APK Signature Scheme v3 signature");
                    }
                    por = verifyProofOfRotationStruct(attr, certFactory);
                    // make sure that the last certificate in the Proof-of-rotation record matches
                    // the one used to sign this APK.
                    try {
                        if (por.certs.size() > 0
                                && !Arrays.equals(por.certs.get(por.certs.size() - 1).getEncoded(),
                                certChain[0].getEncoded())) {
                            throw new SecurityException("Terminal certificate in Proof-of-rotation"
                                    + " record does not match APK signing certificate");
                        }
                    } catch (CertificateEncodingException e) {
                        throw new SecurityException("Failed to encode certificate when comparing"
                                + " Proof-of-rotation record and signing certificate", e);
                    }

                    break;
                case ROTATION_MIN_SDK_VERSION_ATTR_ID:
                    if (attr.remaining() < 4) {
                        throw new IOException(
                                "Remaining buffer too short to contain rotation minSdkVersion "
                                        + "value. Remaining: "
                                        + attr.remaining());
                    }
                    int attrRotationMinSdkVersion = attr.getInt();
                    if (!mOptionalRotationMinSdkVersion.isPresent()) {
                        throw new SecurityException(
                                "Expected a v3.1 signing block targeting SDK version "
                                        + attrRotationMinSdkVersion
                                        + ", but a v3.1 block was not found");
                    }
                    int rotationMinSdkVersion = mOptionalRotationMinSdkVersion.getAsInt();
                    if (rotationMinSdkVersion != attrRotationMinSdkVersion) {
                        throw new SecurityException(
                                "Expected a v3.1 signing block targeting SDK version "
                                        + attrRotationMinSdkVersion
                                        + ", but the v3.1 block was targeting "
                                        + rotationMinSdkVersion);
                    }
                    break;
                case HYBRID_MIN_SDK_VERSION_ATTR_ID:
                    if (android.security.Flags.apkPqcHybridSigning()) {
                        if (attr.remaining() < 4) {
                            throw new IOException(
                                    "Remining buffer too short to contain the hybrid minSdkVersion "
                                            + "value. Remaining: "
                                            + attr.remaining());
                        }
                        int attrHybridMinSdkVersion = attr.getInt();
                        if (!mOptionalHybridMinSdkVersion.isPresent()) {
                            throw new SecurityException(
                                    "Expected a v3.2 signing block targeting SDK version "
                                            + attrHybridMinSdkVersion
                                            + ", but a v3.2 block was not found");
                        }
                        int hybridMinSdkVersion = mOptionalHybridMinSdkVersion.getAsInt();
                        if (hybridMinSdkVersion != attrHybridMinSdkVersion) {
                            throw new SecurityException(
                                    "Expected a v3.2 signing block targeting SDK version "
                                            + attrHybridMinSdkVersion
                                            + ", but the v3.2 block was targeting "
                                            + hybridMinSdkVersion);
                        }
                    }
                    break;
                case SIGNER_TARGETS_DEV_RELEASE_ATTR_ID:
                    // This attribute can be used by both a v3.1 signer and v3.2 signers as it
                    // allows these signature schemes to target a platform under development.
                    if (mBlockId == APK_SIGNATURE_SCHEME_V31_BLOCK_ID
                            || (android.security.Flags.apkPqcHybridSigning()
                                    && mBlockId == APK_SIGNATURE_SCHEME_V32_BLOCK_ID)) {
                        // A platform under development uses the same SDK version as the most
                        // recently released platform; if this platform's SDK version is the same as
                        // the minimum of the signer, then only allow this signer to be used if this
                        // is not a "REL" platform.
                        if (Build.VERSION.SDK_INT == mSignerMinSdkVersion
                                && "REL".equals(Build.VERSION.CODENAME)) {
                            // Set the rotation-min-sdk-version to be verified against the stripping
                            // attribute in the v3.0 block.
                            mOptionalRotationMinSdkVersion = OptionalInt.of(mSignerMinSdkVersion);
                            throw new PlatformNotSupportedException(
                                    "The device is running a release version of "
                                            + mSignerMinSdkVersion
                                            + ", but the signer is targeting a dev release");
                        }
                        // Note, this is set without checking the device SDK version because it's
                        // possible the SDK has since been finalized and no longer matches the
                        // minSdkVersion set by the signer; this signer should still be used in that
                        // case since it was intended to target the new platform release.
                        isDevTarget = true;
                    }
                    break;
                default:
                    // not the droid we're looking for, move along, move along.
                    break;
            }
        }
        return new VerifiedSigner(certChain, por, contentDigests, isDevTarget);
    }

    /**
     * Returns the signer from the provided {@code verifiedSigners} that should be used for
     * verifying this APK's signature while ensuring that the single signer is not a PQC signer
     * since PQC signing is only supported in the hybrid block for now.
     *
     * <p>The only time when multiple signers are supported for the V3.1 scheme is if one is
     * targeting the latest platform release and the other is targeting the current development
     * platform release which is still using the same SDK version as the previous release. In this
     * case, the signer targeting the development release should be returned since it is intended
     * to target the latest release.
     */
    private VerifiedSigner verifyV31Signers(List<VerifiedSigner> verifiedSigners) {
        VerifiedSigner result;
        if (verifiedSigners.size() == 2) {
            VerifiedSigner signer1 = verifiedSigners.get(0);
            VerifiedSigner signer2 = verifiedSigners.get(1);
            // If both signers have the same value for dev targeting then this is a multiple signer
            // case which is not supported.
            if (signer1.isDevTarget == signer2.isDevTarget) {
                throw new SecurityException(
                        "APK Signature Scheme V3 only supports one signer: multiple signers found"
                                + ".");
            }
            result = signer1.isDevTarget ? signer1 : signer2;
        } else {
            result = verifiedSigners.get(0);
        }
        if (ApkSigningBlockUtils.isCertificatePqc(result.certs[0])) {
            throw new SecurityException(
                    "The platform does not currently support single PQC signers in the v3 "
                            + "signature blocks");
        }
        return result;
    }

    /**
     * Returns the signer from the provided {@code verifiedSigners} that should be used as the
     * current signer of the APK; the hybrid block requires both a classical and a PQC signer, and
     * the PQC signer is treated as the current signer.
     *
     * <p>This method will also verify that the hybrid block meets all other requirements such as
     * both signers targeting the same SDK range and having the same signing lineage.
     */
    public VerifiedSigner verifyV32Signers(List<VerifiedSigner> verifiedSigners) {
        // Verify that there are exactly two signers in the provided list.
        if (verifiedSigners.size() != 2) {
            throw new SecurityException("APK Signature Scheme V3.2 supports exactly two signers; "
                    + verifiedSigners.size() + " found");
        }
        VerifiedSigner signer1 = verifiedSigners.get(0);
        VerifiedSigner signer2 = verifiedSigners.get(1);

        // Verify that there is one classical and one PQC signer; note, the first cert in the
        // array is always treated as the signing certificate.
        boolean signer1IsPqc = ApkSigningBlockUtils.isCertificatePqc(signer1.certs[0]);
        boolean signer2IsPqc = ApkSigningBlockUtils.isCertificatePqc(signer2.certs[0]);
        if (signer1IsPqc == signer2IsPqc) {
            String algoType = signer1IsPqc ? "PQC" : "classical";
            throw new SecurityException(
                    "APK Signature Scheme V3.2 requires one classical and one PQC signer; both are "
                            + algoType);
        }
        VerifiedSigner classicalSigner = signer1IsPqc ? signer2 : signer1;
        VerifiedSigner pqcSigner = signer1IsPqc ? signer1 : signer2;

        // Verify both signers target the exact same SDK range.
        if (classicalSigner.minSdkVersion != pqcSigner.minSdkVersion
                || classicalSigner.maxSdkVersion != pqcSigner.maxSdkVersion) {
            throw new SecurityException(
                    "APK Signature Scheme V3.2 requires both signers target the same SDK range; "
                            + "classical: "
                            + classicalSigner.minSdkVersion + "-" + classicalSigner.maxSdkVersion
                            + ", PQC: " + pqcSigner.minSdkVersion + "-" + pqcSigner.maxSdkVersion);
        }

        // Verify both signers have the exact same signing history attesting to their current key.
        ApkSigningBlockUtils.VerifiedProofOfRotation pqcPor;
        if (classicalSigner.por != null || pqcSigner.por != null) {
            if (classicalSigner.por == null || pqcSigner.por == null) {
                throw new SecurityException(
                        "APK Signature Scheme V3.2 requires both signers have the same signing "
                                + "history, but one of the signers is missing its lineage");
            }
            // Note, during creation the certs are initialized as an empty array list, so there's no
            // concern about the certs being null here.
            int classicalLineageSize = classicalSigner.por.certs.size();
            int pqcLineageSize = pqcSigner.por.certs.size();
            if (classicalLineageSize != pqcLineageSize) {
                throw new SecurityException(
                        "APK Signature Scheme V3.2 requires both signers have the same signing "
                                + "history, but classical has "
                                + classicalLineageSize + ", PQC has " + pqcLineageSize);
            }
            // The last entry in the lineage is the current signer, verify all of the certs before
            // the current signer are the same.
            for (int i = 0; i < classicalLineageSize - 1; i++) {
                try {
                    byte[] classicalCertBytes = classicalSigner.por.certs.get(i).getEncoded();
                    byte[] pqcCertBytes = pqcSigner.por.certs.get(i).getEncoded();
                    if (!Arrays.equals(classicalCertBytes, pqcCertBytes)) {
                        throw new SecurityException(
                                "APK Signature Scheme V3.2 requires both signers have the same "
                                        + "signing history, but the history diverges at index "
                                        + i);
                    }
                    if (!classicalSigner.por.flagsList.get(i).equals(
                            pqcSigner.por.flagsList.get(i))) {
                        throw new SecurityException(
                                "APK Signature Scheme V3.2 requires both signers have the same "
                                        + "capabilities granted to the signing history, but the "
                                        + "capabilities diverge at index "
                                        + i);
                    }
                } catch (CertificateEncodingException e) {
                    throw new SecurityException(
                            "Failed to encode certificate when comparing signing history between "
                                    + "V3.2 hybrid signers at index "
                                    + i, e);
                }
            }
            // Once the certificates are verified, add all of the certificates from the classical
            // lineage to the PQC signer's lineage since the platform treats this as an implicit
            // rotation from the classical signer to the PQC signer.
            List<X509Certificate> pqcLineage = new ArrayList<>();
            pqcLineage.addAll(classicalSigner.por.certs);
            // The last entry in the lineage is always the current signing certificate, and the PQC
            // signer is treated as the current signer.
            pqcLineage.add(pqcSigner.certs[0]);
            // The lineage also contains a corresponding flags list which has the capabilities
            // granted to previous signers in the lineage, so add all of the classical flags along
            // with the flags assigned to the PQC signer.
            List<Integer> pqcFlags = new ArrayList<>();
            pqcFlags.addAll(classicalSigner.por.flagsList);
            pqcFlags.add(pqcFlags.get(pqcFlags.size() - 1));
            pqcPor = new ApkSigningBlockUtils.VerifiedProofOfRotation(pqcLineage, pqcFlags);
        } else {
            // This is a new install with only the hybrid block, build the implicit lineage with
            // just the classical and PQC signers using the default flags.
            List<X509Certificate> pqcLineage = new ArrayList<>();
            List<Integer> pqcFlags = new ArrayList<>();
            pqcLineage.add(classicalSigner.certs[0]);
            pqcFlags.add(ApkSigningBlockUtils.VerifiedProofOfRotation.DEFAULT_FLAGS);
            pqcLineage.add(pqcSigner.certs[0]);
            pqcFlags.add(ApkSigningBlockUtils.VerifiedProofOfRotation.DEFAULT_FLAGS);
            pqcPor = new ApkSigningBlockUtils.VerifiedProofOfRotation(pqcLineage, pqcFlags);
        }

        VerifiedSigner result = new VerifiedSigner(pqcSigner.certs, pqcPor,
                pqcSigner.contentDigests, pqcSigner.isDevTarget);
        return result;
    }

    static byte[] getVerityRootHash(String apkPath)
            throws IOException, SignatureNotFoundException, SecurityException {
        try (RandomAccessFile apk = new RandomAccessFile(apkPath, "r")) {
            SignatureInfo signatureInfo = findSignature(apk);
            VerifiedSigner vSigner = verify(apk, false);
            return vSigner.verityRootHash;
        }
    }

    static byte[] generateApkVerity(String apkPath, ByteBufferFactory bufferFactory)
            throws IOException, SignatureNotFoundException, SecurityException, DigestException,
            NoSuchAlgorithmException {
        try (RandomAccessFile apk = new RandomAccessFile(apkPath, "r")) {
            SignatureInfo signatureInfo = findSignature(apk);
            return VerityBuilder.generateApkVerity(apkPath, bufferFactory, signatureInfo);
        }
    }

    /**
     * Verified APK Signature Scheme v3 signer, including the proof of rotation structure.
     *
     * @hide for internal use only.
     */
    public static class VerifiedSigner {
        public final X509Certificate[] certs;
        public final ApkSigningBlockUtils.VerifiedProofOfRotation por;
        // Algorithm -> digest map of signed digests in the signature.
        // All these are verified if requested.
        public final Map<Integer, byte[]> contentDigests;

        public byte[] verityRootHash;

        // ID of the signature block used to verify.
        public int blockId;
        public int minSdkVersion;
        public int maxSdkVersion;
        public boolean isDevTarget;

        public VerifiedSigner(X509Certificate[] certs,
                ApkSigningBlockUtils.VerifiedProofOfRotation por,
                Map<Integer, byte[]> contentDigests, boolean isDevTarget) {
            this.certs = certs;
            this.por = por;
            this.contentDigests = contentDigests;
            this.isDevTarget = isDevTarget;
        }

        public VerifiedSigner(X509Certificate[] certs,
                ApkSigningBlockUtils.VerifiedProofOfRotation por,
                byte[] verityRootHash, Map<Integer, byte[]> contentDigests,
                int blockId) {
            this.certs = certs;
            this.por = por;
            this.verityRootHash = verityRootHash;
            this.contentDigests = contentDigests;
            this.blockId = blockId;
        }
    }

    private static class PlatformNotSupportedException extends Exception {

        PlatformNotSupportedException(String s) {
            super(s);
        }
    }
}
