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

package com.android.systemui.media.dialog;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;

/**
 * Confirmation dialog for releasing media session
 */

public class MediaSessionReleaseDialog extends SystemUIDialog {

    private View mDialogView;

    private final Context mContext;
    private final View.OnClickListener mPositiveButtonListener;
    @NonNull private final MediaOutputColorScheme mColorScheme;

    public MediaSessionReleaseDialog(Context context, Runnable runnable,
            @NonNull MediaOutputColorScheme colorScheme) {
        super(context, R.style.Theme_SystemUI_Dialog_Media);
        mContext = getContext();
        mPositiveButtonListener = (v) -> {
            runnable.run();
            dismiss();
        };
        mColorScheme = colorScheme;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialogView = LayoutInflater.from(mContext).inflate(R.layout.media_session_end_dialog,
                null);
        final Window window = getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL);
        window.setContentView(mDialogView);

        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.CENTER;
        lp.width = (int) (mContext.getResources().getDisplayMetrics().widthPixels * 0.90);

        TextView endSessionDialogTitle = mDialogView.requireViewById(R.id.end_session_dialog_title);
        endSessionDialogTitle.setTextColor(mColorScheme.getOnSurface());

        ImageView headerIcon = mDialogView.requireViewById(R.id.end_icon);
        headerIcon.setImageDrawable(mContext.getDrawable(R.drawable.media_output_status_failed));
        headerIcon.setImageTintList(ColorStateList.valueOf(mColorScheme.getSecondary()));

        Button stopButton = mDialogView.requireViewById(R.id.stop_button);
        stopButton.setOnClickListener(mPositiveButtonListener);
        stopButton.setTextColor(mColorScheme.getOnPrimary());
        stopButton.getBackground().setTint(mColorScheme.getPrimary());

        Button cancelButton = mDialogView.requireViewById(R.id.cancel_button);
        cancelButton.setOnClickListener((v) -> dismiss());
        cancelButton.setTextColor(mColorScheme.getPrimary());
        cancelButton.getBackground().setTint(mColorScheme.getOutlineVariant());
    }
}
