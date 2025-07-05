/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallStage;
import com.android.packageinstaller.v2.ui.InstallActionListener;
import com.android.packageinstaller.v2.ui.UiUtil;

public class InstallStagingFragment extends DialogFragment {

    private static final String LOG_TAG = InstallStagingFragment.class.getSimpleName();
    private ProgressBar mProgressBar;
    private Dialog mDialog;
    @NonNull
    private InstallActionListener mInstallActionListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Log.i(LOG_TAG, "Creating " + LOG_TAG);

        // There is no root view here. Ok to pass null view root
        @SuppressWarnings("InflateParams")
        View dialogView = getLayoutInflater().inflate(
                UiUtil.getInstallationLayoutResId(requireContext()), null);
        mProgressBar = dialogView.requireViewById(R.id.progress_bar);
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setIndeterminate(false);
        mProgressBar.setMax(100);
        mProgressBar.setProgress(0);

        mDialog = UiUtil.getAlertDialog(requireContext(), getString(R.string.title_install_staging),
                dialogView, /* positiveBtnText= */ null, getString(R.string.button_cancel),
                /* positiveBtnListener= */ null,
                (dialog, which) ->
                        mInstallActionListener.onNegativeResponse(InstallStage.STAGE_STAGING));

        mDialog.setCanceledOnTouchOutside(false);
        this.setCancelable(false);
        return mDialog;
    }

    public void setProgress(int progress) {
        if (mProgressBar != null) {
            mProgressBar.setProgress(progress);
        }
    }
}
