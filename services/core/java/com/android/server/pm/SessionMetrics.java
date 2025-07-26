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

package com.android.server.pm;

import static android.content.pm.PackageManager.installStatusToPublicStatus;
import static android.os.Process.INVALID_UID;

import android.annotation.Nullable;
import android.content.pm.DataLoaderType;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.verify.developer.DeveloperVerificationSession;
import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.os.Handler;

import com.android.internal.util.FrameworkStatsLog;
import com.android.server.pm.verify.developer.DeveloperVerificationStatusInternal;

import java.util.Arrays;

final class SessionMetrics {
    private static final String TAG = "SessionMetrics";
    private final Handler mHandler;
    private final int mSessionId;
    private final int mUserId;
    private final int mInstallerUid;
    @Nullable
    private final int[] mChildSessionIds;
    private final int mParentSessionId;

    private final long mCreatedMillis;
    private long mCommittedMillis;
    private long mNativeLibExtractionStartedMillis;
    private long mNativeLibExtractionFinishedMillis;
    private long mVerificationStartedMillis;
    private long mVerificationFinishedMillis;
    private long mInternalInstallationStarted;
    private long mInternalInstallationFinished;
    private long mFinishedMillis;
    private int mStatusCode;
    private boolean mIsExpired = false;

    private final int mMode;
    private final int mRequireUserAction;
    private final int mInstallFlags;
    private final int mInstallLocation;
    private final int mInstallReason;
    private final int mInstallScenario;
    private final boolean mIsStaged;
    private final long mRequiredInstalledVersionCode;
    private final int mDataLoaderType;
    private final int mRollbackDataPolicy;
    private final long mRollbackLifetimeMillis;
    private final int mRollbackImpactLevel;
    private final boolean mForceQueryableOverride;
    private final boolean mApplicationEnabledSettingPersistent;
    private final boolean mIsMultiPackage;
    private boolean mIsPreapproval;
    private final boolean mIsUnarchive;
    private final boolean mIsAutoInstallDependenciesEnabled;
    private long mApksSizeBytes;
    private boolean mWasUserActionIntentSent;
    private boolean mWasUserResponseReceived;
    private final int mDefaultDeveloperVerificationPolicy;
    private long mDeveloperVerifierBindStartedMillis;
    private long mDeveloperVerifierConnectedMillis;
    private long mDeveloperVerifierRequestSentMillis;
    private long mDeveloperVerificationDurationMillis;

    private int mDeveloperVerifierUid = INVALID_UID;
    private int mIsDeveloperVerificationBypassedReason =
            DeveloperVerificationSession.DEVELOPER_VERIFICATION_BYPASSED_REASON_UNSPECIFIED;
    private boolean mIsDeveloperVerificationTimeoutExtensionRequested = false;
    private final boolean mHasDeveloperVerificationExtensionParams;
    private boolean mIsDeveloperVerificationPolicyOverridden = false;
    private DeveloperVerificationStatusInternal mDeveloperVerificationStatus =
            DeveloperVerificationStatusInternal.UNKNOWN;
    private @DeveloperVerificationStatus.AppMetadataVerificationStatus int mAslStatus;
    private @PackageInstaller.DeveloperVerificationPolicy int mDeveloperVerificationPolicyOverride;
    private boolean mWasDeveloperVerificationUserActionRequired = false;
    private boolean mWasDeveloperVerificationUserResponseReceived = false;
    private @PackageInstaller.DeveloperVerificationUserConfirmationInfo.UserActionNeededReason int
            mDeveloperVerificationUserActionRequiredReason;
    private @PackageInstaller.DeveloperVerificationUserResponse int
            mDeveloperVerificationUserResponse = -1;
    private @PackageInstaller.DeveloperVerificationFailedReason int
            mDeveloperVerificationFailureReason;
    @Nullable
    private String mPackageNameWhenDeveloperVerificationFailed = null;
    private boolean mDeveloperVerificationCancelled;

    SessionMetrics(Handler handler,
            int sessionId, int userId, int installerUid,
            PackageInstaller.SessionParams params, long createdMillis, long committedMillis,
            boolean committed, @Nullable int[] childSessionIds, int parentSessionId,
            int sessionStatusCode,
            @PackageInstaller.DeveloperVerificationPolicy int defaultDeveloperVerificationPolicy) {
        mHandler = handler;
        mSessionId = sessionId;
        mUserId = userId;
        mInstallerUid = installerUid;
        mChildSessionIds = childSessionIds == null
                ? null : Arrays.copyOf(childSessionIds, childSessionIds.length);
        mParentSessionId = parentSessionId;
        mCreatedMillis = createdMillis;
        mCommittedMillis = committed ? committedMillis : 0;
        mStatusCode = sessionStatusCode;

        mMode = params.mode;
        mRequireUserAction = params.requireUserAction;
        mInstallFlags = params.installFlags;
        mInstallLocation = params.installLocation;
        mInstallReason = params.installReason;
        mInstallScenario = params.installScenario;
        mIsStaged = params.isStaged;
        mRequiredInstalledVersionCode = params.requiredInstalledVersionCode;
        mDataLoaderType = params.dataLoaderParams == null
                ? DataLoaderType.NONE : params.dataLoaderParams.getType();
        mRollbackDataPolicy = params.rollbackDataPolicy;
        mRollbackLifetimeMillis = params.rollbackLifetimeMillis;
        mRollbackImpactLevel = params.rollbackImpactLevel;
        mForceQueryableOverride = params.forceQueryableOverride;
        mApplicationEnabledSettingPersistent = params.applicationEnabledSettingPersistent;
        mIsMultiPackage = params.isMultiPackage;
        mIsUnarchive = params.unarchiveId != PackageInstaller.SessionInfo.INVALID_ID;
        mIsAutoInstallDependenciesEnabled = params.isAutoInstallDependenciesEnabled;
        mDefaultDeveloperVerificationPolicy = defaultDeveloperVerificationPolicy;
        mHasDeveloperVerificationExtensionParams =
                params.extensionParams != null && !params.extensionParams.isEmpty();
    }

    public void onPreapprovalSet() {
        mIsPreapproval = true;
    }

    public void onUserActionIntentSent() {
        mWasUserActionIntentSent = true;
    }

    public void onUserResponseReceived() {
        mWasUserResponseReceived = true;
    }

    public void onSessionCommitted(long committedMillis) {
        mCommittedMillis = committedMillis;
    }

    public void onNativeLibExtractionStarted() {
        mNativeLibExtractionStartedMillis = System.currentTimeMillis();
    }

    public void onNativeLibExtractionFinished() {
        mNativeLibExtractionFinishedMillis = System.currentTimeMillis();
    }

    public void onSessionVerificationStarted() {
        mVerificationStartedMillis = System.currentTimeMillis();
    }

    public void onSessionVerificationFinished() {
        mVerificationFinishedMillis = System.currentTimeMillis();
    }

    public void onInternalInstallationStarted() {
        mInternalInstallationStarted = System.currentTimeMillis();
    }

    public void onInternalInstallationFinished() {
        mInternalInstallationFinished = System.currentTimeMillis();
    }

    public void onSessionFinished(int statusCode) {
        mStatusCode = statusCode;
        mFinishedMillis = System.currentTimeMillis();
        reportStats();
    }

    public void onSessionExpired() {
        mFinishedMillis = System.currentTimeMillis();
        mIsExpired = true;
        reportStats();
    }

    public void onDeveloperVerificationBindStarted(int verifierUid) {
        mDeveloperVerifierBindStartedMillis = System.currentTimeMillis();
        mDeveloperVerifierUid = verifierUid;
    }

    public void onDeveloperVerifierConnectionEstablished() {
        mDeveloperVerifierConnectedMillis = System.currentTimeMillis();
    }

    public void onDeveloperVerificationRequestSent() {
        mDeveloperVerifierRequestSentMillis = System.currentTimeMillis();
    }

    public void onDeveloperVerificationBypassed(int bypassReason) {
        mIsDeveloperVerificationBypassedReason = bypassReason;
    }

    public void onDeveloperVerificationTimeoutExtensionRequested() {
        mIsDeveloperVerificationTimeoutExtensionRequested = true;
    }

    public void onDeveloperVerificationPolicyOverridden(
            @PackageInstaller.DeveloperVerificationPolicy int defaultDeveloperVerificationPolicy) {
        mIsDeveloperVerificationPolicyOverridden = true;
        mDeveloperVerificationPolicyOverride = defaultDeveloperVerificationPolicy;
    }

    public void onDeveloperVerificationFinished(DeveloperVerificationStatusInternal status) {
        mDeveloperVerificationStatus = status;
        final long responseReceivedMillis = System.currentTimeMillis();
        if (mDeveloperVerifierRequestSentMillis != 0) {
            mDeveloperVerificationDurationMillis =
                    responseReceivedMillis - mDeveloperVerifierRequestSentMillis;
        }
    }

    public void onDeveloperVerificationUserActionRequired(
            @PackageInstaller.DeveloperVerificationUserConfirmationInfo.UserActionNeededReason int
                    reason) {
        mWasDeveloperVerificationUserActionRequired = true;
        mDeveloperVerificationUserActionRequiredReason = reason;
    }

    public void onDeveloperVerificationUserResponseReceived(
            @PackageInstaller.DeveloperVerificationUserResponse int response) {
        mDeveloperVerificationUserResponse = response;
        mWasDeveloperVerificationUserResponseReceived = true;
    }

    public void onDeveloperVerificationFailed(
            @PackageInstaller.DeveloperVerificationFailedReason int reason, String packageName) {
        mDeveloperVerificationFailureReason = reason;
        mPackageNameWhenDeveloperVerificationFailed = packageName;
    }

    public void onDeveloperVerificationCancelled() {
        mDeveloperVerificationCancelled = true;
    }

    private void reportStats() {
        final long sessionIdleDurationMillis = mCommittedMillis - mCreatedMillis;
        final long sessionCommitDurationMillis = mFinishedMillis - mCommittedMillis;
        final long nativeLibExtractionDurationMillis =
                mNativeLibExtractionFinishedMillis - mNativeLibExtractionStartedMillis;
        final long packageVerificationDurationMillis =
                mVerificationFinishedMillis - mVerificationStartedMillis;
        final long internalInstallationDurationMillis =
                mInternalInstallationFinished - mInternalInstallationStarted;
        final long sessionLifetimeMillis = mFinishedMillis - mCreatedMillis;
        final long developerVerifierConnectionDurationMillis =
                mDeveloperVerifierConnectedMillis == 0
                        ? 0 // Binding was already established before ths installation
                        : mDeveloperVerifierConnectedMillis - mDeveloperVerifierBindStartedMillis;
        final long developerVerificationPrepDurationMillis =
                        mDeveloperVerifierRequestSentMillis - mDeveloperVerifierBindStartedMillis;
        // Do this on a handler so that we don't block anything critical
        mHandler.post(() ->
                FrameworkStatsLog.write(
                        FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED,
                        mSessionId,
                        mUserId,
                        mInstallerUid,
                        mChildSessionIds,
                        mParentSessionId,
                        getTranslatedModeForStats(mMode),
                        mRequireUserAction,
                        mInstallFlags,
                        mInstallLocation,
                        mInstallReason,
                        mInstallScenario,
                        mIsStaged,
                        mRequiredInstalledVersionCode,
                        mDataLoaderType,
                        getTranslatedRollbackDataPolicyForStats(mRollbackDataPolicy),
                        mRollbackLifetimeMillis,
                        getTranslatedRollbackImpactLevelForStats(mRollbackImpactLevel),
                        mForceQueryableOverride,
                        mApplicationEnabledSettingPersistent,
                        mIsMultiPackage,
                        mIsPreapproval,
                        mIsUnarchive,
                        mIsAutoInstallDependenciesEnabled,
                        mApksSizeBytes, // TODO(b/418283971): compute apks size bytes
                        getTranslatedStatusCodeForStats(installStatusToPublicStatus(mStatusCode)),
                        mWasUserActionIntentSent,
                        mIsExpired,
                        sessionIdleDurationMillis,
                        sessionCommitDurationMillis,
                        nativeLibExtractionDurationMillis,
                        packageVerificationDurationMillis,
                        internalInstallationDurationMillis,
                        sessionLifetimeMillis,
                        getTranslatedPolicyCodeForStats(mDefaultDeveloperVerificationPolicy),
                        mDeveloperVerifierUid,
                        mIsDeveloperVerificationBypassedReason,
                        mIsDeveloperVerificationTimeoutExtensionRequested,
                        mHasDeveloperVerificationExtensionParams,
                        mIsDeveloperVerificationPolicyOverridden,
                        getTranslatedPolicyCodeForStats(mDeveloperVerificationPolicyOverride),
                        getTranslatedResponseCodeForStats(
                                mDeveloperVerificationStatus.getInternalStatus()),
                        getTranslatedAppMetadataVerificationStatusForStats(
                                mDeveloperVerificationStatus.getAppMetadataVerificationStatus()),
                        mWasDeveloperVerificationUserActionRequired,
                        getTranslatedDeveloperVerificationUserActionReasonForStats(
                                mDeveloperVerificationUserActionRequiredReason),
                        getTranslatedDeveloperVerificationUserResponseForStats(
                                mDeveloperVerificationUserResponse),
                        /* retryCount= */ 0,
                        mDeveloperVerificationStatus.isLiteVerification(),
                        getTranslatedDeveloperVerificationFailedReasonForStats(
                                mDeveloperVerificationFailureReason),
                        mPackageNameWhenDeveloperVerificationFailed,
                        mDeveloperVerificationCancelled,
                        mDeveloperVerificationDurationMillis,
                        developerVerificationPrepDurationMillis,
                        /* retryDuration= */ 0L,
                        developerVerifierConnectionDurationMillis,
                        mWasUserResponseReceived,
                        mWasDeveloperVerificationUserResponseReceived
                        )
        );
    }

    public int getTranslatedModeForStats(int mode) {
        return switch (mode) {
            case PackageInstaller.SessionParams.MODE_INVALID ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__MODE__MODE_INVALID;
            case PackageInstaller.SessionParams.MODE_FULL_INSTALL ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__MODE__MODE_FULL_INSTALL;
            case PackageInstaller.SessionParams.MODE_INHERIT_EXISTING ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__MODE__MODE_INHERIT_EXISTING;
            default -> FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__MODE__MODE_UNSPECIFIED;
        };
    }

    public int getTranslatedRollbackDataPolicyForStats(int rollbackDataPolicy) {
        return switch (rollbackDataPolicy) {
            case PackageManager.ROLLBACK_DATA_POLICY_RESTORE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_DATA_POLICY__ROLLBACK_DATA_POLICY_RESTORE;
            case PackageManager.ROLLBACK_DATA_POLICY_WIPE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_DATA_POLICY__ROLLBACK_DATA_POLICY_WIPE;
            case PackageManager.ROLLBACK_DATA_POLICY_RETAIN ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_DATA_POLICY__ROLLBACK_DATA_POLICY_RETAIN;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_DATA_POLICY__ROLLBACK_DATA_POLICY_UNSPECIFIED;
        };
    }

    public int getTranslatedRollbackImpactLevelForStats(int rollbackImpactLevel) {
        return switch (rollbackImpactLevel) {
            case PackageManager.ROLLBACK_USER_IMPACT_LOW ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_IMPACT_LEVEL__ROLLBACK_USER_IMPACT_LOW;
            case PackageManager.ROLLBACK_USER_IMPACT_HIGH ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_IMPACT_LEVEL__ROLLBACK_USER_IMPACT_HIGH;
            case PackageManager.ROLLBACK_USER_IMPACT_ONLY_MANUAL ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_IMPACT_LEVEL__ROLLBACK_USER_IMPACT_ONLY_MANUAL;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ROLLBACK_IMPACT_LEVEL__ROLLBACK_USER_IMPACT_UNSPECIFIED;
        };
    }

    private static int getTranslatedStatusCodeForStats(int statusCode) {
        return switch (statusCode) {
            case PackageInstaller.STATUS_PENDING_STREAMING ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_PENDING_STREAMING;
            case PackageInstaller.STATUS_PENDING_USER_ACTION ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_PENDING_USER_ACTION;
            case PackageInstaller.STATUS_SUCCESS ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_SUCCESS;
            case PackageInstaller.STATUS_FAILURE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE;
            case PackageInstaller.STATUS_FAILURE_BLOCKED ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_BLOCKED;
            case PackageInstaller.STATUS_FAILURE_ABORTED ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_ABORTED;
            case PackageInstaller.STATUS_FAILURE_INVALID ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_INVALID;
            case PackageInstaller.STATUS_FAILURE_CONFLICT ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_CONFLICT;
            case PackageInstaller.STATUS_FAILURE_STORAGE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_STORAGE;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_INCOMPATIBLE;
            case PackageInstaller.STATUS_FAILURE_TIMEOUT ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_FAILURE_TIMEOUT;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__STATUS_CODE__STATUS_UNSPECIFIED;
        };
    }

    private static int getTranslatedPolicyCodeForStats(
            @PackageInstaller.DeveloperVerificationPolicy int policy) {
        return switch (policy) {
            case PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_POLICY__POLICY_NONE;
            case PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_POLICY__POLICY_FAIL_CLOSED;
            case PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_POLICY__POLICY_FAIL_OPEN;
            case PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_POLICY__POLICY_FAIL_WARN;
            default ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_POLICY__POLICY_UNSPECIFIED;
        };
    }

    private static int getTranslatedResponseCodeForStats(
            @DeveloperVerificationStatusInternal.Status int internalStatus) {
        return switch (internalStatus) {
            case DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_PASS ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_COMPLETE_WITH_PASS;
            case DeveloperVerificationStatusInternal.STATUS_COMPLETED_WITH_REJECT ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_COMPLETE_WITH_REJECT;
            case DeveloperVerificationStatusInternal.STATUS_INCOMPLETE_UNKNOWN ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_INCOMPLETE_UNKNOWN;
            case DeveloperVerificationStatusInternal.STATUS_INCOMPLETE_NETWORK_UNAVAILABLE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_INCOMPLETE_NETWORK_UNAVAILABLE;
            case DeveloperVerificationStatusInternal.STATUS_TIMEOUT ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_TIMEOUT;
            case DeveloperVerificationStatusInternal.STATUS_DISCONNECTED ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_MODULE_DISCONNECTED;
            case DeveloperVerificationStatusInternal.STATUS_INFEASIBLE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_OTHER;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFIER_RESPONSE__RESPONSE_UNSPECIFIED;
        };
    }

    private static int getTranslatedAppMetadataVerificationStatusForStats(
            @DeveloperVerificationStatus.AppMetadataVerificationStatus int aslStatus) {
        return switch (aslStatus) {
            case DeveloperVerificationStatus.APP_METADATA_VERIFICATION_STATUS_GOOD ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ASL_STATUS__ASL_STATUS_GOOD;
            case DeveloperVerificationStatus.APP_METADATA_VERIFICATION_STATUS_BAD ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ASL_STATUS__ASL_STATUS_BAD;
            default ->
                FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ASL_STATUS__ASL_STATUS_UNSPECIFIED;
        };
    }

    private static int getTranslatedDeveloperVerificationUserActionReasonForStats(
            @PackageInstaller.DeveloperVerificationUserConfirmationInfo.UserActionNeededReason int
                    reason) {
        return switch (reason) {
            case PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_ACTION_REQUIRED_REASON__USER_ACTION_REQUIRED_REASON_NETWORK_UNAVAILABLE;
            case PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_ACTION_REQUIRED_REASON__USER_ACTION_REQUIRED_REASON_DEVELOPER_BLOCKED;
            case PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_ACTION_REQUIRED_REASON__USER_ACTION_REQUIRED_REASON_LITE_VERIFICATION;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_ACTION_REQUIRED_REASON__USER_ACTION_REQUIRED_REASON_UNSPECIFIED;
        };
    }

    private static int getTranslatedDeveloperVerificationUserResponseForStats(
            @PackageInstaller.DeveloperVerificationUserResponse int response) {
        return switch (response) {
            case PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ERROR ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_RESPONSE__USER_RESPONSE_ERROR;
            case PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_RESPONSE__USER_RESPONSE_ABORT;
            case PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_RETRY ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_RESPONSE__USER_RESPONSE_RETRY;
            case PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_RESPONSE__USER_RESPONSE_INSTALL_ANYWAY;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_USER_RESPONSE__USER_RESPONSE_UNSPECIFIED;
        };
    }

    private static int getTranslatedDeveloperVerificationFailedReasonForStats(
            @PackageInstaller.DeveloperVerificationFailedReason int reason) {
        return switch (reason) {
            case PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_FAILED_REASON__DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE;
            case PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_FAILED_REASON__DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED;
            default ->
                    FrameworkStatsLog.PACKAGE_INSTALLER_SESSION_REPORTED__ADI_VERIFICATION_FAILED_REASON__DEVELOPER_VERIFICATION_FAILED_REASON_UNSPECIFIED;
        };
    }
}
