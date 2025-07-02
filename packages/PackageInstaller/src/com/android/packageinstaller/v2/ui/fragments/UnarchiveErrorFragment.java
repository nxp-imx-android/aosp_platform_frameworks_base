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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_INSTALLER_LABEL;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_INSTALLER_PACKAGE;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_PENDING_INTENT;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_REQUIRED_BYTES;
import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_UNARCHIVAL_STATUS;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInstaller;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Html;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.UnarchiveError;
import com.android.packageinstaller.v2.ui.UiUtil;
import com.android.packageinstaller.v2.ui.UnarchiveActionListener;

public class UnarchiveErrorFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

    private static final String LOG_TAG = UnarchiveErrorFragment.class.getSimpleName();
    private UnarchiveError mDialogData;
    private UnarchiveActionListener mUnarchiveActionListener;

    public UnarchiveErrorFragment() {
        // Required for DialogFragment
    }

    /**
     * Creates a new instance of this fragment with necessary data set as fragment arguments
     *
     * @param dialogData {@link UnarchiveError} object containing data to display
     *                   in the dialog
     * @return an instance of the fragment
     */
    public static UnarchiveErrorFragment newInstance(UnarchiveError dialogData) {
        Bundle args = new Bundle();
        args.putInt(ARGS_UNARCHIVAL_STATUS, dialogData.getUnarchivalStatus());
        args.putLong(ARGS_REQUIRED_BYTES, dialogData.getRequiredBytes());
        args.putString(ARGS_INSTALLER_LABEL, dialogData.getInstallerAppTitle());
        args.putString(ARGS_INSTALLER_PACKAGE, dialogData.getInstallerPackageName());
        args.putParcelable(ARGS_PENDING_INTENT, dialogData.getPendingIntent());

        UnarchiveErrorFragment dialog = new UnarchiveErrorFragment();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mUnarchiveActionListener = (UnarchiveActionListener) context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setDialogData(requireArguments());
        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + mDialogData);

        // There is no root view here. Ok to pass null view root
        @SuppressWarnings("InflateParams")
        View dialogView = getLayoutInflater().inflate(R.layout.uninstall_fragment_layout, null);

        return getDialog(dialogView);
    }

    private void setDialogData(Bundle args) {
        int status = args.getInt(ARGS_UNARCHIVAL_STATUS, -1);
        PendingIntent pendingIntent = args.getParcelable(ARGS_PENDING_INTENT, PendingIntent.class);
        long requiredBytes = args.getLong(ARGS_REQUIRED_BYTES);
        String installerPkgName = args.getString(ARGS_INSTALLER_PACKAGE);
        String installerAppTitle = args.getString(ARGS_INSTALLER_LABEL);

        mDialogData = new UnarchiveError(
                status, installerPkgName, installerAppTitle, requiredBytes, pendingIntent
        );
    }

    private Dialog getDialog(@NonNull View dialogView) {
        final int status = mDialogData.getUnarchivalStatus();
        final String installerAppTitle = mDialogData.getInstallerAppTitle();
        final long requiredBytes = mDialogData.getRequiredBytes();

        TextView customMessage = dialogView.requireViewById(R.id.custom_message);
        customMessage.setVisibility(View.VISIBLE);

        String title = null;
        int positiveBtnTextResId = Resources.ID_NULL;
        int negativeBtnTextResId = R.string.button_close;
        DialogInterface.OnClickListener positiveButtonListener = null;
        switch (status) {
            case PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED -> {
                title = getString(R.string.title_restore_error_user_action_needed);
                positiveBtnTextResId = R.string.button_continue;
                positiveButtonListener = this;
                negativeBtnTextResId = R.string.button_cancel;
                customMessage.setText(
                        getString(R.string.message_restore_error_user_action_needed,
                            installerAppTitle));
            }

            case PackageInstaller.UNARCHIVAL_ERROR_INSUFFICIENT_STORAGE -> {
                title = getString(R.string.title_restore_error_less_storage);
                positiveBtnTextResId = R.string.button_manage_apps;
                positiveButtonListener = this;
                negativeBtnTextResId = R.string.button_cancel;

                String message = String.format(
                        getString(R.string.message_restore_error_less_storage),
                            Formatter.formatShortFileSize(requireContext(), requiredBytes));
                customMessage.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
            }

            case PackageInstaller.UNARCHIVAL_ERROR_NO_CONNECTIVITY -> {
                title = getString(R.string.title_restore_error_offline);
                customMessage.setText(getString(R.string.message_restore_error_offline));
            }

            case PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_DISABLED -> {
                title = String.format(getString(R.string.title_restore_error_installer_disabled),
                        installerAppTitle);
                positiveBtnTextResId = R.string.button_settings;
                positiveButtonListener = this;
                negativeBtnTextResId = R.string.button_cancel;
                customMessage.setText(String.format(
                        getString(R.string.message_restore_error_installer_disabled),
                            installerAppTitle));
            }

            case PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_UNINSTALLED -> {
                title = String.format(getString(R.string.title_restore_error_installer_absent),
                        installerAppTitle);
                customMessage.setText(String.format(
                        getString(R.string.message_restore_error_installer_absent),
                            installerAppTitle));
            }

            case PackageInstaller.UNARCHIVAL_GENERIC_ERROR -> {
                title = getString(R.string.title_restore_error_generic);
                customMessage.setText(getString(R.string.message_restore_error_generic));
            }

            default ->
                // This should never happen through normal API usage.
                throw new IllegalArgumentException("Invalid unarchive status " + status);
        }
        return UiUtil.getAlertDialog(requireContext(), title, dialogView, positiveBtnTextResId,
                negativeBtnTextResId, positiveButtonListener, this);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which != Dialog.BUTTON_POSITIVE) {
            return;
        }
        mUnarchiveActionListener.handleUnarchiveErrorAction(
                mDialogData.getUnarchivalStatus(), mDialogData.getInstallerPackageName(),
                mDialogData.getPendingIntent());
    }


    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isAdded()) {
            getActivity().finish();
        }
    }
}
