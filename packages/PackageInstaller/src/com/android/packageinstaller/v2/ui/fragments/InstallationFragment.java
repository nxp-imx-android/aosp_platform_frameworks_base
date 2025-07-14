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

package com.android.packageinstaller.v2.ui.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallInstalling;
import com.android.packageinstaller.v2.model.InstallStage;
import com.android.packageinstaller.v2.model.InstallSuccess;
import com.android.packageinstaller.v2.model.InstallUserActionRequired;
import com.android.packageinstaller.v2.ui.InstallActionListener;
import com.android.packageinstaller.v2.ui.UiUtil;
import com.android.packageinstaller.v2.viewmodel.InstallViewModel;

import java.util.List;

/**
 * The dialogFragment to handle the installation.
 */
public class InstallationFragment extends DialogFragment {

    public static final String LOG_TAG = InstallationFragment.class.getSimpleName();
    @NonNull
    private InstallActionListener mInstallActionListener;
    @NonNull
    private Dialog mDialog;

    private ImageView mAppIcon = null;
    private TextView mAppLabelTextView = null;
    private View mAppSnippet = null;
    private TextView mCustomMessageTextView = null;
    private ProgressBar mProgressBar = null;
    private View mTitleTemplate = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final InstallStage installStage = getCurrentInstallStage();
        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + installStage);

        // There is no root view here. Ok to pass null view root
        @SuppressWarnings("InflateParams")
        View dialogView = getLayoutInflater().inflate(
                UiUtil.getInstallationLayoutResId(requireContext()), null);
        mAppSnippet = dialogView.requireViewById(R.id.app_snippet);
        mAppIcon = dialogView.requireViewById(R.id.app_icon);
        mAppLabelTextView = dialogView.requireViewById(R.id.app_label);
        mProgressBar = dialogView.requireViewById(R.id.progress_bar);
        mCustomMessageTextView = dialogView.requireViewById(R.id.custom_message);

        String title = getString(R.string.title_install_staging);
        mDialog = UiUtil.getAlertDialog(requireContext(), title, dialogView,
                R.string.button_install, R.string.button_cancel,
                /* positiveBtnListener= */ null,
                (dialog, which) -> {
                    mInstallActionListener.onNegativeResponse(installStage.getStageCode());
                });

        return mDialog;
    }

    private InstallStage getCurrentInstallStage() {
        return new ViewModelProvider(requireActivity()).get(InstallViewModel.class)
                .getCurrentInstallStage().getValue();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);

        mInstallActionListener.onNegativeResponse(getCurrentInstallStage().getStageCode());
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        // This prevents tapjacking since an overlay activity started in front of Pia will
        // cause Pia to be paused.
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(true);
        }
    }

    /**
     * Update the UI based on the current install stage
     */
    public void updateUI() {
        if (!isAdded()) {
            return;
        }

        // Get the current install stage
        final InstallStage installStage = getCurrentInstallStage();

        // show the title and adjust the paddings of the custom message textview
        if (mTitleTemplate != null) {
            mTitleTemplate.setVisibility(View.VISIBLE);
            mCustomMessageTextView.setPadding(0, 0, 0, 0);
        }
        switch (installStage.getStageCode()) {
            case InstallStage.STAGE_INSTALLING -> {
                updateInstallInstallingUI(mDialog, (InstallInstalling) installStage);
            }
            case InstallStage.STAGE_STAGING -> {
                updateInstallStagingUI(mDialog);
            }
            case InstallStage.STAGE_SUCCESS -> {
                updateInstallSuccessUI(mDialog, (InstallSuccess) installStage);
            }
            case InstallStage.STAGE_USER_ACTION_REQUIRED -> {
                updateUserActionRequiredUI(mDialog, (InstallUserActionRequired) installStage);
            }
        }
    }

    private void updateInstallInstallingUI(Dialog dialog, InstallInstalling installStage) {
        mProgressBar.setVisibility(View.VISIBLE);
        mAppSnippet.setVisibility(View.VISIBLE);
        mCustomMessageTextView.setVisibility(View.GONE);

        // Set the app icon, label and progress bar
        mAppIcon.setImageDrawable(installStage.getAppIcon());
        mAppLabelTextView.setText(installStage.getAppLabel());
        mProgressBar.setIndeterminate(true);

        // Set the title
        final int titleResId = installStage.isAppUpdating()
                ? R.string.title_updating : R.string.title_installing;
        dialog.setTitle(titleResId);

        // Hide the buttons
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            positiveButton.setVisibility(View.GONE);
        }
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setVisibility(View.GONE);
        }

        // Cancelable is false
        this.setCancelable(false);
    }

    private void updateInstallStagingUI(@NonNull Dialog dialog) {
        mProgressBar.setVisibility(View.VISIBLE);
        mAppSnippet.setVisibility(View.GONE);
        mCustomMessageTextView.setVisibility(View.GONE);

        // Set the title
        dialog.setTitle(R.string.title_install_staging);

        // Set the progress bar
        mProgressBar.setIndeterminate(false);
        mProgressBar.setMax(100);
        mProgressBar.setProgress(0);

        // Hide the positive button
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            positiveButton.setVisibility(View.GONE);
        }

        // Set the negative button
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setVisibility(View.VISIBLE);
            UiUtil.applyOutlinedButtonStyle(requireContext(), negativeButton);
            negativeButton.setText(R.string.button_cancel);
            negativeButton.setOnClickListener(view -> {
                mInstallActionListener.onNegativeResponse(InstallStage.STAGE_STAGING);
            });
        }

        this.setCancelable(false);
    }

    private void updateInstallSuccessUI(Dialog dialog, InstallSuccess installStage) {
        mProgressBar.setVisibility(View.GONE);
        mAppSnippet.setVisibility(View.VISIBLE);
        mCustomMessageTextView.setVisibility(View.GONE);

        // Set the app icon and label
        mAppIcon.setImageDrawable(installStage.getAppIcon());
        mAppLabelTextView.setText(installStage.getAppLabel());

        // Set the title
        final int titleResId = installStage.isAppUpdating()
                ? R.string.title_updated : R.string.title_installed;
        dialog.setTitle(titleResId);

        // If there is an activity entry, show the positive button
        final Intent resultIntent = installStage.getResultIntent();
        if (resultIntent != null) {
            final List<ResolveInfo> list =
                    requireContext().getPackageManager().queryIntentActivities(resultIntent, 0);
            if (!list.isEmpty()) {
                Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
                if (positiveButton != null) {
                    positiveButton.setVisibility(View.VISIBLE);
                    UiUtil.applyFilledButtonStyle(requireContext(), positiveButton);
                    positiveButton.setText(R.string.button_open);
                    positiveButton.setOnClickListener(view -> {
                        Log.i(LOG_TAG, "Finished installing and launching "
                                + installStage.getAppLabel());
                        mInstallActionListener.openInstalledApp(resultIntent);
                    });
                }
            }
        }

        // Show the Done button
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setVisibility(View.VISIBLE);
            UiUtil.applyOutlinedButtonStyle(requireContext(), negativeButton);
            negativeButton.setText(R.string.button_done);
            negativeButton.setOnClickListener(view -> {
                mInstallActionListener.onNegativeResponse(installStage.getStageCode());
            });
        }

        this.setCancelable(true);
    }

    private void updateUserActionRequiredUI(Dialog dialog, InstallUserActionRequired installStage) {
        switch (installStage.getActionReason()) {
            case InstallUserActionRequired.USER_ACTION_REASON_INSTALL_CONFIRMATION -> {
                updateInstallConfirmationUI(dialog, installStage);
            }
            case InstallUserActionRequired.USER_ACTION_REASON_UNKNOWN_SOURCE -> {
                updateUnknownSourceUI(dialog, installStage);
            }

            case InstallUserActionRequired.USER_ACTION_REASON_ANONYMOUS_SOURCE -> {
                updateAnonymousSourceUI(dialog, installStage);
            }
        }
    }

    private void updateUnknownSourceUI(Dialog dialog, InstallUserActionRequired installStage) {
        mAppSnippet.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        mCustomMessageTextView.setVisibility(View.VISIBLE);

        // Set the app icon and label
        mAppIcon.setImageDrawable(installStage.getAppIcon());
        mAppLabelTextView.setText(installStage.getAppLabel());

        // Set the title and the message
        dialog.setTitle(R.string.title_unknown_source_blocked);
        mCustomMessageTextView.setText(R.string.message_external_source_blocked);

        // Set the button be a text button and set the listener
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            positiveButton.setVisibility(View.VISIBLE);
            UiUtil.applyTextButtonStyle(requireContext(), positiveButton);
            positiveButton.setText(R.string.external_sources_settings);
            positiveButton.setFilterTouchesWhenObscured(true);
            positiveButton.setOnClickListener(view -> {
                mInstallActionListener.sendUnknownAppsIntent(
                        installStage.getUnknownSourcePackageName());
            });
        }

        // Set the button be a text button and set the listener
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setVisibility(View.VISIBLE);
            UiUtil.applyTextButtonStyle(requireContext(), negativeButton);
            negativeButton.setText(R.string.button_cancel);
            negativeButton.setOnClickListener(view -> {
                mInstallActionListener.onNegativeResponse(installStage.getStageCode());
            });
        }

        this.setCancelable(true);
    }

    private void updateAnonymousSourceUI(Dialog dialog, InstallUserActionRequired installStage) {
        mAppSnippet.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        mCustomMessageTextView.setVisibility(View.VISIBLE);

        // Hide the title and set the message
        mCustomMessageTextView.setText(R.string.message_anonymous_source_warning);
        dialog.setTitle(null);
        mTitleTemplate =
                dialog.findViewById(UiUtil.getAlertDialogTitleTemplateId(requireContext()));
        if (mTitleTemplate != null) {
            mTitleTemplate.setVisibility(View.GONE);
            final int expectedSpace =
                    getResources().getDimensionPixelOffset(R.dimen.alert_dialog_inner_padding);
            final int currentSpace =
                    getResources().getDimensionPixelOffset(R.dimen.dialog_inter_element_margin);
            mCustomMessageTextView.setPadding(0, expectedSpace - currentSpace, 0, 0);
        }

        // Set the button be a text button and set the listener
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            positiveButton.setVisibility(View.VISIBLE);
            UiUtil.applyTextButtonStyle(requireContext(), positiveButton);
            positiveButton.setText(R.string.button_continue);
            positiveButton.setFilterTouchesWhenObscured(true);
            positiveButton.setOnClickListener(view -> {
                mInstallActionListener.onPositiveResponse(
                        InstallUserActionRequired.USER_ACTION_REASON_ANONYMOUS_SOURCE);
            });
        }

        // Set the button be a text button and set the listener
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setVisibility(View.VISIBLE);
            UiUtil.applyTextButtonStyle(requireContext(), negativeButton);
            negativeButton.setText(R.string.button_cancel);
            negativeButton.setOnClickListener(view -> {
                mInstallActionListener.onNegativeResponse(installStage.getStageCode());
            });
        }

        this.setCancelable(true);
    }

    private void updateInstallConfirmationUI(Dialog dialog,
            InstallUserActionRequired installStage) {
        mAppSnippet.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        mCustomMessageTextView.setVisibility(View.GONE);

        // Set the app icon and label
        mAppIcon.setImageDrawable(installStage.getAppIcon());
        mAppLabelTextView.setText(installStage.getAppLabel());

        // Set the title and the message
        String title = null;
        int positiveBtnTextRes = 0;
        boolean isUpdateOwnerShip = false;
        if (installStage.isAppUpdating()) {
            if (installStage.getExistingUpdateOwnerLabel() != null
                    && installStage.getRequestedUpdateOwnerLabel() != null) {
                isUpdateOwnerShip = true;
                title = getString(R.string.title_update_ownership_change,
                        installStage.getRequestedUpdateOwnerLabel());
                positiveBtnTextRes = R.string.button_update_anyway;
                mCustomMessageTextView.setVisibility(View.VISIBLE);
                String updateOwnerString = getString(R.string.message_update_owner_change,
                        installStage.getExistingUpdateOwnerLabel());
                mCustomMessageTextView.setText(
                        Html.fromHtml(updateOwnerString, Html.FROM_HTML_MODE_LEGACY));
                mCustomMessageTextView.setMovementMethod(new ScrollingMovementMethod());
            } else {
                title = getString(R.string.title_update);
                positiveBtnTextRes = R.string.button_update;
            }
        } else {
            title = getString(R.string.title_install);
            positiveBtnTextRes = R.string.button_install;
        }
        dialog.setTitle(title);

        // Set the button and the listener
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            positiveButton.setVisibility(View.VISIBLE);
            if (isUpdateOwnerShip) {
                UiUtil.applyTextButtonStyle(requireContext(), positiveButton);
            } else {
                UiUtil.applyFilledButtonStyle(requireContext(), positiveButton);
            }
            positiveButton.setText(positiveBtnTextRes);
            positiveButton.setFilterTouchesWhenObscured(true);
            positiveButton.setOnClickListener(view -> {
                mInstallActionListener.onPositiveResponse(
                        InstallUserActionRequired.USER_ACTION_REASON_INSTALL_CONFIRMATION);
            });
        }

        // Set the button and the listener
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setVisibility(View.VISIBLE);
            if (isUpdateOwnerShip) {
                UiUtil.applyTextButtonStyle(requireContext(), negativeButton);
            } else {
                UiUtil.applyOutlinedButtonStyle(requireContext(), negativeButton);
            }
            negativeButton.setText(R.string.button_cancel);
            negativeButton.setOnClickListener(view -> {
                mInstallActionListener.onNegativeResponse(installStage.getStageCode());
            });
        }

        this.setCancelable(true);
    }

    /**
     * Set the progress of the progress bar
     */
    public void setProgress(int progress) {
        if (mProgressBar != null) {
            mProgressBar.setProgress(progress);
        }
    }
}
