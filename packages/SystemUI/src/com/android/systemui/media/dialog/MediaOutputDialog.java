/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.Nullable;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.graphics.drawable.IconCompat;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.dagger.SysUISingleton;

import java.util.concurrent.Executor;

/**
 * Dialog for media output transferring.
 */
@SysUISingleton
public class MediaOutputDialog extends MediaOutputBaseDialog {
    private final DialogTransitionAnimator mDialogTransitionAnimator;
    private final UiEventLogger mUiEventLogger;
    @Nullable private final OnDialogEventListener mOnDialogEventListener;

    MediaOutputDialog(
            Context context,
            boolean aboveStatusbar,
            BroadcastSender broadcastSender,
            MediaSwitchingController mediaSwitchingController,
            DialogTransitionAnimator dialogTransitionAnimator,
            UiEventLogger uiEventLogger,
            Executor mainExecutor,
            Executor backgroundExecutor,
            boolean includePlaybackAndAppMetadata,
            @Nullable OnDialogEventListener onDialogEventListener) {
        super(context, broadcastSender, mediaSwitchingController, includePlaybackAndAppMetadata);
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mUiEventLogger = uiEventLogger;
        mAdapter = new MediaOutputAdapter(mMediaSwitchingController);
        mOnDialogEventListener = onDialogEventListener;
        if (!aboveStatusbar) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUiEventLogger.log(MediaOutputEvent.MEDIA_OUTPUT_DIALOG_SHOW);

        if (mOnDialogEventListener != null) {
            mOnDialogEventListener.onCreate(this);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (mOnDialogEventListener != null) {
            mOnDialogEventListener.onConfigurationChanged(this, configuration);
        }
    }

    @Override
    int getHeaderIconRes() {
        return 0;
    }

    @Override
    IconCompat getHeaderIcon() {
        return mMediaSwitchingController.getHeaderIcon();
    }

    @Override
    CharSequence getHeaderText() {
        return mMediaSwitchingController.getHeaderTitle();
    }

    @Override
    CharSequence getHeaderSubtitle() {
        return mMediaSwitchingController.getHeaderSubTitle();
    }

    @Override
    IconCompat getAppSourceIcon() {
        return mMediaSwitchingController.getNotificationSmallIcon();
    }

    @Override
    public void onStopButtonClick() {
        mMediaSwitchingController.releaseSession();
        mDialogTransitionAnimator.disableAllCurrentDialogsExitAnimations();
        dismiss();
    }

    @VisibleForTesting
    public enum MediaOutputEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "The MediaOutput dialog became visible on the screen.")
        MEDIA_OUTPUT_DIALOG_SHOW(655);

        private final int mId;

        MediaOutputEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }

    /** Callback for configuration changes . */
    public interface OnDialogEventListener{
        /** Will be called inside onConfigurationChanged. */
        void onConfigurationChanged(Dialog dialog, Configuration newConfig);

        /** Will be called when the dialog is created. */
        void onCreate(Dialog dialog);
    }
}
