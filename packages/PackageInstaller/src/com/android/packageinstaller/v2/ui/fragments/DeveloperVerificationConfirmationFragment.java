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

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_DEVELOPER_BLOCKED;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE;
import static android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo.DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.ui.InstallActionListener;
import com.android.packageinstaller.v2.ui.UiUtil;

public class DeveloperVerificationConfirmationFragment extends DialogFragment {

    public static final String LOG_TAG = "DeveloperVerificationConf";
    @NonNull
    private final InstallUserActionRequired mDialogData;
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private Dialog mDialog;
    private boolean mIsBypassAllowed;
    private ImageView mAppIcon;
    private TextView mAppLabelTextView;
    private View mAppSnippet;
    private View mMoreDetailsClickableLayout;
    private View mMoreDetailsExpandedLayout;
    private TextView mInstallWithoutVerifyingTextView;
    private String mCustomMessage;
    private TextView mCustomMessageTextView;

    private boolean mIsExpanded;

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
        // There is no root view here. Ok to pass null view root
        @SuppressWarnings("InflateParams")
        View dialogView = getLayoutInflater().inflate(
                R.layout.verification_confirmation_fragment_layout, null);
        mAppSnippet = dialogView.requireViewById(R.id.app_snippet);
        mAppIcon = dialogView.requireViewById(R.id.app_icon);
        mAppLabelTextView = dialogView.requireViewById(R.id.app_label);
        mMoreDetailsClickableLayout = dialogView.requireViewById(
                R.id.more_details_clickable_layout);
        mMoreDetailsExpandedLayout = dialogView.requireViewById(
                R.id.more_details_expanded_layout);
        mInstallWithoutVerifyingTextView = dialogView.requireViewById(
                R.id.install_without_verifying_text);
        mCustomMessageTextView = dialogView.requireViewById(R.id.custom_message);

        DeveloperVerificationUserConfirmationInfo verificationInfo =
                mDialogData.getVerificationInfo();
        assert verificationInfo != null;
        mIsBypassAllowed = isBypassAllowed(verificationInfo);
        int userActionNeededReasonReason = verificationInfo.getUserActionNeededReason();
        int titleResId = getDialogTitleResourceId(userActionNeededReasonReason);
        int msgResId = getDialogMessageResourceId(userActionNeededReasonReason);
        mCustomMessage = getString(msgResId);
        mDialog = UiUtil.getAlertDialog(requireContext(), getString(titleResId),
                dialogView, R.string.ok, Resources.ID_NULL,
                (dialog, which) -> mInstallActionListener.setVerificationUserResponse(
                        DEVELOPER_VERIFICATION_USER_RESPONSE_ABORT),
                null);

        return mDialog;
    }

    private void updateUI() {
        if (!isAdded()) {
            return;
        }

        mCustomMessageTextView.setText(Html.fromHtml(mCustomMessage, Html.FROM_HTML_MODE_LEGACY));

        mAppSnippet.setVisibility(View.VISIBLE);
        mAppIcon.setImageDrawable(mDialogData.getAppIcon());
        mAppLabelTextView.setText(mDialogData.getAppLabel());

        if (mIsBypassAllowed) {
            if (!mIsExpanded) {
                mMoreDetailsClickableLayout.setVisibility(View.VISIBLE);
                mMoreDetailsExpandedLayout.setVisibility(View.GONE);
            }

            mMoreDetailsClickableLayout.setOnClickListener(v -> {
                mIsExpanded = true;
                mMoreDetailsClickableLayout.setVisibility(View.GONE);
                mMoreDetailsExpandedLayout.setVisibility(View.VISIBLE);
                mInstallWithoutVerifyingTextView.setTypeface(
                        mInstallWithoutVerifyingTextView.getTypeface(), Typeface.BOLD);
                mInstallWithoutVerifyingTextView.setOnClickListener(v1 -> {
                    mInstallActionListener.setVerificationUserResponse(
                            DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY);
                    mDialog.dismiss();
                });
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Don't allow the buttons to be clicked as there might be overlays
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-enable the buttons since they were disabled when activity was paused
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(true);
        }
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
                Log.e(LOG_TAG, "Unknown user action needed reason: " + userActionNeededReason);
                yield false;
            }
        };
    }

    private int getDialogTitleResourceId(int userActionNeededReason) {
        return switch (userActionNeededReason) {
            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN ->
                    R.string.cannot_install_verification_unavailable_title;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE ->
                    R.string.cannot_install_verification_no_internet_title;

            default -> R.string.cannot_install_app_blocked_title;
        };
    }

    private int getDialogMessageResourceId(int userActionNeededReason) {
        return switch (userActionNeededReason) {
            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN ->
                    R.string.cannot_install_verification_unavailable_summary;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_NETWORK_UNAVAILABLE ->
                    R.string.cannot_install_verification_no_internet_summary;

            case DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_LITE_VERIFICATION ->
                    R.string.lite_verification_summary;

            default -> R.string.cannot_install_app_blocked_summary;
        };
    }
}
