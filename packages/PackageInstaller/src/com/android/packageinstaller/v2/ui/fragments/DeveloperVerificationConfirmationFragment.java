/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.packageinstaller.v2.ui.fragments;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_RETRY;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN;

import static com.android.packageinstaller.ConfirmDeveloperVerification.FLAG_VERIFICATION_FAILED_MAY_BYPASS;
import static com.android.packageinstaller.ConfirmDeveloperVerification.FLAG_VERIFICATION_FAILED_MAY_RETRY;
import static com.android.packageinstaller.ConfirmDeveloperVerification.FLAG_VERIFICATION_FAILED_MAY_RETRY_MAY_BYPASS;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.ui.InstallActionListener;

public class DeveloperVerificationConfirmationFragment extends DialogFragment {

    public static final String LOG_TAG = "DeveloperVerificationConf";
    @NonNull
    private final InstallUserActionRequired mDialogData;
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private AlertDialog mDialog;
    private Button mPositiveBtn;
    private Button mNegativeBtn;
    private Button mNeutralBtn;

    public DeveloperVerificationConfirmationFragment(
            @NonNull InstallUserActionRequired dialogData) {
        mDialogData = dialogData;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        DeveloperVerificationUserConfirmationInfo verificationInfo =
                mDialogData.getVerificationInfo();
        assert verificationInfo != null;
        int dialogTypeFlag = getUserConfirmationDialogFlag(verificationInfo);
        int userActionNeededReasonReason = verificationInfo.getUserActionNeededReason();
        int msgResId = getDialogMessageResourceId(userActionNeededReasonReason);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                .setIcon(mDialogData.getAppIcon())
                .setTitle(mDialogData.getAppLabel())
                .setMessage(msgResId);

        if ((dialogTypeFlag & FLAG_VERIFICATION_FAILED_MAY_RETRY_MAY_BYPASS)
                == FLAG_VERIFICATION_FAILED_MAY_RETRY_MAY_BYPASS) {
            // allow retry and bypass
            builder.setPositiveButton(R.string.try_again,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_RETRY))
                    .setNegativeButton(R.string.dont_install,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT))
                    .setNeutralButton(R.string.install_anyway,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY));
        } else if ((dialogTypeFlag & FLAG_VERIFICATION_FAILED_MAY_RETRY) != 0) {
            // only allow retry
            builder.setPositiveButton(R.string.ok,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT))
                    .setNegativeButton(R.string.try_again,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_RETRY));
        } else if ((dialogTypeFlag & FLAG_VERIFICATION_FAILED_MAY_BYPASS) != 0) {
            // only allow bypass
            builder.setPositiveButton(R.string.dont_install,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT))
                    .setNegativeButton(R.string.install_anyway,
                            (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                                    DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY));
        } else {
            // allow only acknowledging the error
            builder.setPositiveButton(
                    (userActionNeededReasonReason
                            == DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED)
                            ? R.string.close : R.string.ok,
                    (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                            DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT));
        }
        builder.setOnCancelListener(dialog ->
                mInstallActionListener.setVerificationUserResponse(
                        DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT));

        mDialog = builder.create();
        return mDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        mPositiveBtn = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        mNegativeBtn = mDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        mNeutralBtn = mDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
    }

    @Override
    public void onPause() {
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
    public void onResume() {
        super.onResume();
        // Re-enable the buttons since they were disabled when activity was paused
        Button[] buttons = {mPositiveBtn, mNegativeBtn, mNeutralBtn};
        for (Button button : buttons) {
            if (button != null) {
                button.setEnabled(true);
            }
        }
    }

    /**
     * Returns the correct type of dialog based on the verification policy and the reason for user
     * action
     */
    private int getUserConfirmationDialogFlag(
            DeveloperVerificationUserConfirmationInfo verificationInfo) {
        int userActionNeededReason = verificationInfo.getUserActionNeededReason();
        int verificationPolicy = verificationInfo.getVerificationPolicy();

        return switch (userActionNeededReason) {
            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED -> 0;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE -> {
                int flag = FLAG_VERIFICATION_FAILED_MAY_RETRY;
                if (verificationPolicy == DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN
                        || verificationPolicy == DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN) {
                    flag |= FLAG_VERIFICATION_FAILED_MAY_BYPASS;
                }
                yield flag;
            }

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN -> {
                int flag = 0;
                if (verificationPolicy == DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN
                        || verificationPolicy == DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_WARN) {
                    flag |= FLAG_VERIFICATION_FAILED_MAY_BYPASS;
                }
                yield flag;
            }

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION ->
                    FLAG_VERIFICATION_FAILED_MAY_BYPASS;

            default -> {
                Log.e(LOG_TAG, "Unknown user action needed reason: " + userActionNeededReason);
                yield 0;
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
}
