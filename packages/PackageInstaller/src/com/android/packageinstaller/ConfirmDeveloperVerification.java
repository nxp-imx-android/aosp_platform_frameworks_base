/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.packageinstaller;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ERROR;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN;
import static android.content.pm.PackageInstaller.EXTRA_SESSION_ID;
import static android.content.pm.PackageInstaller.SessionInfo;
import static android.content.pm.PackageInstaller.SessionInfo.INVALID_ID;

import static com.android.packageinstaller.PackageUtil.AppSnippet;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public class ConfirmDeveloperVerification extends Activity {

    private static final String TAG = ConfirmDeveloperVerification.class.getSimpleName();

    private final boolean mLocalLOGV = false;
    private PackageManager mPackageManager;
    private PackageInstaller mPackageInstaller;
    private AlertDialog mDialog;
    private AppSnippet mAppSnippet;
    private Button mPositiveBtn;
    private Button mNegativeBtn;
    private Button mNeutralBtn;

    @Override
    @SuppressLint("MissingPermission")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageManager = getPackageManager();
        mPackageInstaller = mPackageManager.getPackageInstaller();

        Intent intent = getIntent();
        int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, INVALID_ID);

        SessionInfo sessionInfo = mPackageInstaller.getSessionInfo(sessionId);
        String resolvedPath = sessionInfo != null ? sessionInfo.getResolvedBaseApkPath() : null;
        if (sessionInfo == null || !sessionInfo.isSealed() || resolvedPath == null) {
            Log.e(TAG, "Session " + sessionId + " in funky state; ignoring");
            mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                    DEVELOPER_VERIFICATION_USER_RESPONSE_ERROR);
            finish();
            return;
        }

        DeveloperVerificationUserConfirmationInfo verificationInfo =
                mPackageInstaller.getDeveloperVerificationUserConfirmationInfo(sessionId);
        if (verificationInfo == null) {
            Log.e(TAG, "Could not get VerificationInfo for sessionId " + sessionId);
            mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                    DEVELOPER_VERIFICATION_USER_RESPONSE_ERROR);
            finish();
            return;
        }

        mAppSnippet = generateAppSnippet(resolvedPath);
        if (mAppSnippet == null) {
            Log.e(TAG, "Could not generate AppSnippet for session " + sessionId);
            if (mLocalLOGV) {
                Log.d(TAG, "Failed to generate AppSnippet for path " + resolvedPath);
            }
            mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                    DEVELOPER_VERIFICATION_USER_RESPONSE_ERROR);
            finish();
            return;
        }

        boolean isBypassAllowed = isBypassAllowed(verificationInfo);
        int userActionNeededReason = verificationInfo.getUserActionNeededReason();
        int msgResId = getDialogMessageResourceId(userActionNeededReason);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setIcon(mAppSnippet.icon)
                .setTitle(mAppSnippet.label)
                .setMessage(msgResId);


        if (isBypassAllowed) {
            // allow bypass
            builder.setPositiveButton(R.string.dont_install, (dialog, which) -> {
                mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                        DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT);
                finish();
            }).setNegativeButton(R.string.install_anyway, (dialog, which) -> {
                mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                        DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY);
                finish();
            });
        } else {
            // allow only acknowledging the error
            builder.setPositiveButton(
                    (userActionNeededReason
                            == DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED)
                            ? R.string.close
                            : R.string.ok,
                    (dialog, which) -> {
                        mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                                PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT);
                        finish();
                    });
        }

        mDialog = builder.create();
        mPositiveBtn = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        mNegativeBtn = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        mNeutralBtn = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        mDialog.show();

        mDialog.setOnCancelListener(dialog -> {
            mPackageInstaller.setDeveloperVerificationUserResponse(sessionId,
                    DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT);
            finish();
        });

    }

    @Nullable
    private AppSnippet generateAppSnippet(@NonNull String resolvedPath) {
        File sourceFile = new File(resolvedPath);
        final PackageInfo packageInfo = PackageUtil.getPackageInfo(this, sourceFile,
                PackageManager.GET_PERMISSIONS);

        // Check for parse errors
        if (packageInfo == null) {
            Log.e(TAG, "Parse error when parsing manifest. Discontinuing installation");
            //show error to user?
            return null;
        }
        if (mLocalLOGV) {
            Log.i(TAG, "Creating snippet for local file " + sourceFile);
        }
        return PackageUtil.getAppSnippet(this, packageInfo.applicationInfo, sourceFile);
    }

    /**
     * Returns whether the user can choose to bypass the verification result and force installation,
     * based on the verification policy and the reason for user action.
     */
    public static boolean isBypassAllowed(
            PackageInstaller.DeveloperVerificationUserConfirmationInfo verificationInfo) {
        int userActionNeededReason = verificationInfo.getUserActionNeededReason();
        int verificationPolicy = verificationInfo.getVerificationPolicy();

        return switch (userActionNeededReason) {
            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED -> false;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE,
                 DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN,
                 DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION ->
                // Only disallow bypass if policy is closed.
                verificationPolicy != DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;

            default -> {
                Log.e(TAG, "Unknown user action needed reason: " + userActionNeededReason);
                yield false;
            }
        };
    }

    private int getDialogMessageResourceId(int userActionNeededReason) {
        return switch (userActionNeededReason) {
            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN ->
                    R.string.cannot_install_verification_unavailable_summary;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE ->
                    R.string.verification_incomplete_summary;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION ->
                    R.string.lite_verification_summary;

            default -> R.string.cannot_install_package_summary;
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Don't allow the buttons to be clicked as there might be overlays
        Button[] buttons = {mPositiveBtn, mNegativeBtn, mNeutralBtn};
        for (Button button : buttons) {
            if (button != null) {
                button.setEnabled(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-enable the buttons since they were disabled when activity was paused
        Button[] buttons = {mPositiveBtn, mNegativeBtn, mNeutralBtn};
        for (Button button : buttons) {
            if (button != null) {
                button.setEnabled(true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }
}
