/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.annotation.IntDef;
import android.security.apksigverify.ApkSigVerifyProtoEnums;

import com.android.internal.util.FrameworkStatsLog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Logs metrics for APK signature verification.
 *
 * @hide
 */
public class ApkSignatureVerifierMetrics {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"VERIFICATION_"}, value = {
            VerificationResult.VERIFICATION_SUCCESS,
            VerificationResult.VERIFICATION_MALFORMED_BLOCK,
            VerificationResult.VERIFICATION_MALFORMED_SIGNER,
            VerificationResult.VERIFICATION_TOO_MANY_SIGNERS,
            VerificationResult.VERIFICATION_NO_CONTENT_DIGESTS,
            VerificationResult.VERIFICATION_INTEGRITY_MISMATCH,
            VerificationResult.VERIFICATION_NO_SIGNATURES,
            VerificationResult.VERIFICATION_NO_SUPPORTED_SIGNATURES,
            VerificationResult.VERIFICATION_MISSING_SIGNATURE_ALGORITHM,
            VerificationResult.VERIFICATION_MALFORMED_SIGNATURE,
            VerificationResult.VERIFICATION_SIGNATURE_VERIFICATION_FAILED,
            VerificationResult.VERIFICATION_SIGNATURE_ALGORITHM_MISMATCH,
            VerificationResult.VERIFICATION_INCONSISTENT_DIGESTS,
            VerificationResult.VERIFICATION_MALFORMED_CERTIFICATE,
            VerificationResult.VERIFICATION_NO_CERTIFICATES,
            VerificationResult.VERIFICATION_PUBLIC_KEY_MISMATCH,
            VerificationResult.VERIFICATION_SIGNER_SDK_MISMATCH,
            VerificationResult.VERIFICATION_MALFORMED_ATTRIBUTES,
            VerificationResult.VERIFICATION_DUPLICATE_POR,
            VerificationResult.VERIFICATION_MALFORMED_POR,
            VerificationResult.VERIFICATION_POR_INTEGRITY_FAILURE,
            VerificationResult.VERIFICATION_POR_CERT_MISMATCH,
            VerificationResult.VERIFICATION_PQC_SINGLE_SIGNER,
            VerificationResult.VERIFICATION_V32_INVALID_SIGNER_COUNT,
            VerificationResult.VERIFICATION_V32_ALGO_TYPE_COLLISION,
            VerificationResult.VERIFICATION_V32_POR_MISSING,
            VerificationResult.VERIFICATION_V32_POR_MISMATCH,
            VerificationResult.VERIFICATION_V32_POR_CAPABILITY_MISMATCH,
            VerificationResult.VERIFICATION_V32_SDK_RANGE_MISMATCH,
            VerificationResult.VERIFICATION_V32_BLOCK_STRIPPED,
            VerificationResult.VERIFICATION_V32_SDK_ATTR_MISMATCH
    })
    public @interface VerificationResult {
        int VERIFICATION_SUCCESS = ApkSigVerifyProtoEnums.VERIFICATION_SUCCESS;
        int VERIFICATION_MALFORMED_BLOCK = ApkSigVerifyProtoEnums.VERIFICATION_MALFORMED_BLOCK;
        int VERIFICATION_MALFORMED_SIGNER = ApkSigVerifyProtoEnums.VERIFICATION_MALFORMED_SIGNER;
        int VERIFICATION_TOO_MANY_SIGNERS = ApkSigVerifyProtoEnums.VERIFICATION_TOO_MANY_SIGNERS;
        int VERIFICATION_NO_CONTENT_DIGESTS =
                ApkSigVerifyProtoEnums.VERIFICATION_NO_CONTENT_DIGESTS;
        int VERIFICATION_INTEGRITY_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_INTEGRITY_MISMATCH;
        int VERIFICATION_NO_SIGNATURES = ApkSigVerifyProtoEnums.VERIFICATION_NO_SIGNATURES;
        int VERIFICATION_NO_SUPPORTED_SIGNATURES =
                ApkSigVerifyProtoEnums.VERIFICATION_NO_SUPPORTED_SIGNATURES;
        int VERIFICATION_MISSING_SIGNATURE_ALGORITHM =
                ApkSigVerifyProtoEnums.VERIFICATION_MISSING_SIGNATURE_ALGORITHM;
        int VERIFICATION_MALFORMED_SIGNATURE =
                ApkSigVerifyProtoEnums.VERIFICATION_MALFORMED_SIGNATURE;
        int VERIFICATION_SIGNATURE_VERIFICATION_FAILED =
                ApkSigVerifyProtoEnums.VERIFICATION_SIGNATURE_VERIFICATION_FAILED;
        int VERIFICATION_SIGNATURE_ALGORITHM_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_SIGNATURE_ALGORITHM_MISMATCH;
        int VERIFICATION_INCONSISTENT_DIGESTS =
                ApkSigVerifyProtoEnums.VERIFICATION_INCONSISTENT_DIGESTS;
        int VERIFICATION_MALFORMED_CERTIFICATE =
                ApkSigVerifyProtoEnums.VERIFICATION_MALFORMED_CERTIFICATE;
        int VERIFICATION_NO_CERTIFICATES = ApkSigVerifyProtoEnums.VERIFICATION_NO_CERTIFICATES;
        int VERIFICATION_PUBLIC_KEY_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_PUBLIC_KEY_MISMATCH;
        int VERIFICATION_SIGNER_SDK_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_SIGNER_SDK_MISMATCH;
        int VERIFICATION_MALFORMED_ATTRIBUTES =
                ApkSigVerifyProtoEnums.VERIFICATION_MALFORMED_ATTRIBUTES;
        int VERIFICATION_DUPLICATE_POR = ApkSigVerifyProtoEnums.VERIFICATION_DUPLICATE_POR;
        int VERIFICATION_MALFORMED_POR = ApkSigVerifyProtoEnums.VERIFICATION_MALFORMED_POR;
        int VERIFICATION_POR_INTEGRITY_FAILURE =
                ApkSigVerifyProtoEnums.VERIFICATION_POR_INTEGRITY_FAILURE;
        int VERIFICATION_POR_CERT_MISMATCH = ApkSigVerifyProtoEnums.VERIFICATION_POR_CERT_MISMATCH;
        int VERIFICATION_PQC_SINGLE_SIGNER = ApkSigVerifyProtoEnums.VERIFICATION_PQC_SINGLE_SIGNER;
        int VERIFICATION_V32_INVALID_SIGNER_COUNT =
                ApkSigVerifyProtoEnums.VERIFICATION_V32_INVALID_SIGNER_COUNT;
        int VERIFICATION_V32_ALGO_TYPE_COLLISION =
                ApkSigVerifyProtoEnums.VERIFICATION_V32_ALGO_TYPE_COLLISION;
        int VERIFICATION_V32_POR_MISSING = ApkSigVerifyProtoEnums.VERIFICATION_V32_POR_MISSING;
        int VERIFICATION_V32_POR_MISMATCH = ApkSigVerifyProtoEnums.VERIFICATION_V32_POR_MISMATCH;
        int VERIFICATION_V32_POR_CAPABILITY_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_V32_POR_CAPABILITY_MISMATCH;
        int VERIFICATION_V32_SDK_RANGE_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_V32_SDK_RANGE_MISMATCH;
        int VERIFICATION_V32_BLOCK_STRIPPED =
                ApkSigVerifyProtoEnums.VERIFICATION_V32_BLOCK_STRIPPED;
        int VERIFICATION_V32_SDK_ATTR_MISMATCH =
                ApkSigVerifyProtoEnums.VERIFICATION_V32_SDK_ATTR_MISMATCH;
    }

    /**
     * Logs a successful APK signature verification.
     *
     * @param blockId            the ID of the block that was successfully verified
     * @param blockMinSdkVersion the minimum SDK version targeted by the block
     * @param blockMaxSdkVersion the maximum SDK version targeted by the block
     * @param sigAlgorithmId     the signature algorithm ID used to sign the block
     */
    public static void logSignatureVerificationSuccess(int blockId, int blockMinSdkVersion,
            int blockMaxSdkVersion, int sigAlgorithmId) {
        logApkSignatureVerificationReported(blockId, blockMinSdkVersion, blockMaxSdkVersion,
                sigAlgorithmId, false,
                false, ApkSigVerifyProtoEnums.VERIFICATION_SUCCESS);
    }

    /**
     * Logs a failure verifying the APK signature at the block level.
     *
     * <p>This method should be used when the failure prevents the verifier from parsing the
     * signer(s) within the block.
     *
     * @param blockId            the ID of the block that failed verification
     * @param verificationResult the {@link VerificationResult} expressing the cause of the failure
     */
    public static void logSignatureVerificationBlockFailure(int blockId,
            @VerificationResult int verificationResult) {
        logApkSignatureVerificationReported(blockId, 0, 0, 0, false, false, verificationResult);
    }

    /**
     * Logs a failure verifying the APK signature at the signer level.
     *
     * <p>This method should be used when the failure occurs after at least a portion of the signer
     * within the signature block was parsed.
     *
     * @param blockId            the ID of the block that failed verification
     * @param blockMinSdkVersion the minimum SDK version targeted by the signer, or 0 if it could
     *                           not be determined
     * @param blockMaxSdkVersion the maximum SDK version targeted by the signer, or 0 if it could
     *                           not be determined
     * @param sigAlgorithmId     the signature algorithm ID used by the signer, or 0 if it could not
     *                           be determined
     * @param verificationResult the {@link VerificationResult} expressing the cause of the failure
     */
    public static void logSignatureVerificationSignerFailure(int blockId, int blockMinSdkVersion,
            int blockMaxSdkVersion, int sigAlgorithmId,
            @VerificationResult int verificationResult) {
        logApkSignatureVerificationReported(blockId, blockMinSdkVersion, blockMaxSdkVersion,
                sigAlgorithmId, false, false, verificationResult);
    }

    /**
     * Logs a new {@code ApkSignatureVerificationReported} metric with the {@link
     * FrameworkStatsLog}.
     *
     * @param blockId            the ID of the block to log
     * @param blockMinSdkVersion the minimum SDK version targeted by the block, or 0 if it could not
     *                           be determined
     * @param blockMaxSdkVersion the maximum SDK version targeted by the block, or 0 if it could not
     *                           be determined
     * @param sigAlgorithmId     the signature algorithm ID used by th signer for this block, or 0
     *                           if it could not be determined
     * @param isRollback         whether the current event reflects a rollback of the APK's signing
     *                           key
     * @param isRotation         whether the current event reflects a rotation of the APK's signing
     *                           key
     * @param verificationResult the {@link VerificationResult} expressing the result of the event
     */
    private static void logApkSignatureVerificationReported(int blockId, int blockMinSdkVersion,
            int blockMaxSdkVersion, int sigAlgorithmId, boolean isRollback, boolean isRotation,
            @VerificationResult int verificationResult) {
        FrameworkStatsLog.write(FrameworkStatsLog.APK_SIGNATURE_VERIFICATION_REPORTED,
                blockId, blockMinSdkVersion, blockMaxSdkVersion, sigAlgorithmId, isRollback,
                isRotation, verificationResult);
    }
}
