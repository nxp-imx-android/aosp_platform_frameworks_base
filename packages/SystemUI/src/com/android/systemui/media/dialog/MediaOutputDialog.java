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

import static com.android.media.flags.Flags.enableOutputSwitcherPersonalAudioSharing;
import static com.android.media.flags.Flags.enableOutputSwitcherRedesign;
import static com.android.settingslib.flags.Flags.legacyLeAudioSharing;

import android.annotation.Nullable;
import android.content.Context;
import android.media.RoutingSessionInfo;
import android.os.Bundle;
import android.util.FeatureFlagUtils;
import android.view.View;
import android.view.WindowManager;

import androidx.core.graphics.drawable.IconCompat;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.res.R;

import java.util.concurrent.Executor;

/**
 * Dialog for media output transferring.
 */
@SysUISingleton
public class MediaOutputDialog extends MediaOutputBaseDialog {
    private final DialogTransitionAnimator mDialogTransitionAnimator;
    private final UiEventLogger mUiEventLogger;

    MediaOutputDialog(
            Context context,
            boolean aboveStatusbar,
            BroadcastSender broadcastSender,
            MediaSwitchingController mediaSwitchingController,
            DialogTransitionAnimator dialogTransitionAnimator,
            UiEventLogger uiEventLogger,
            Executor mainExecutor,
            Executor backgroundExecutor,
            boolean includePlaybackAndAppMetadata) {
        super(context, broadcastSender, mediaSwitchingController, includePlaybackAndAppMetadata);
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mUiEventLogger = uiEventLogger;
        mAdapter = enableOutputSwitcherRedesign()
                ? new MediaOutputAdapter(mMediaSwitchingController)
                : new MediaOutputAdapterLegacy(mMediaSwitchingController, mainExecutor,
                        backgroundExecutor);
        if (!aboveStatusbar) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUiEventLogger.log(MediaOutputEvent.MEDIA_OUTPUT_DIALOG_SHOW);
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
    int getStopButtonVisibility() {
        boolean isActiveRemoteDevice = false;
        if (mMediaSwitchingController.getCurrentConnectedMediaDevice() != null) {
            isActiveRemoteDevice =
                    mMediaSwitchingController.isActiveRemoteDevice(
                            mMediaSwitchingController.getCurrentConnectedMediaDevice());
        }
        // TODO(b/333324985): Remove these code (code under flag legacy_le_audio_sharing) once flag
        //  enable_le_audio_sharing has reached nextfood.
        // isBroadcastSupported always returns false when flag legacy_le_audio_sharing is disabled
        boolean showBroadcastButton =
                isBroadcastSupported() && mMediaSwitchingController.isPlaying();

        boolean inBroadcast =
                enableOutputSwitcherPersonalAudioSharing()
                        && mMediaSwitchingController.getSessionReleaseType()
                                == RoutingSessionInfo.RELEASE_TYPE_SHARING;

        return (isActiveRemoteDevice || showBroadcastButton || inBroadcast)
                ? View.VISIBLE
                : View.GONE;
    }

    @Override
    public boolean isBroadcastSupported() {
        if (!legacyLeAudioSharing()) return false;
        boolean isBluetoothLeDevice = false;
        boolean isBroadcastEnabled = false;
        if (FeatureFlagUtils.isEnabled(mContext,
                FeatureFlagUtils.SETTINGS_NEED_CONNECTED_BLE_DEVICE_FOR_BROADCAST)) {
            if (mMediaSwitchingController.getCurrentConnectedMediaDevice() != null) {
                isBluetoothLeDevice =
                        mMediaSwitchingController.isBluetoothLeDevice(
                                mMediaSwitchingController.getCurrentConnectedMediaDevice());
                // if broadcast is active, broadcast should be considered as supported
                // there could be a valid case that broadcast is ongoing
                // without active LEA device connected
                isBroadcastEnabled = mMediaSwitchingController.isBluetoothLeBroadcastEnabled();
            }
        } else {
            // To decouple LE Audio Broadcast and Unicast, it always displays the button when there
            // is no LE Audio device connected to the phone
            isBluetoothLeDevice = true;
        }

        return mMediaSwitchingController.isBroadcastSupported()
                && (isBluetoothLeDevice || isBroadcastEnabled);
    }

    @Override
    public CharSequence getStopButtonText() {
        int resId = R.string.media_output_dialog_button_stop_casting;
        if (isBroadcastSupported()
                && mMediaSwitchingController.isPlaying()
                && !mMediaSwitchingController.isBluetoothLeBroadcastEnabled()) {
            // TODO(b/333324985): Remove these code (code under flag legacy_le_audio_sharing)
            //  once flag enable_le_audio_sharing has reached nextfood.
            // isBroadcastSupported always returns false when flag legacy_le_audio_sharing is
            // disabled.
            resId = R.string.media_output_broadcast;
        } else if (enableOutputSwitcherPersonalAudioSharing()) {
            CharSequence stopButtonText = getTextForSessionReleaseType();
            if (stopButtonText != null) {
                return stopButtonText;
            }
        }
        return mContext.getText(resId);
    }

    @Override
    public void onStopButtonClick() {
        if (isBroadcastSupported() && mMediaSwitchingController.isPlaying()) {
            // TODO(b/333324985): Remove these code (code under flag legacy_le_audio_sharing)
            //  once flag enable_le_audio_sharing has reached nextfood.
            // isBroadcastSupported always returns false when flag legacy_le_audio_sharing is
            // disabled.
            if (!mMediaSwitchingController.isBluetoothLeBroadcastEnabled()) {
                if (startLeBroadcastDialogForFirstTime()) {
                    return;
                }
                startLeBroadcast();
            } else {
                stopLeBroadcast();
            }
        } else {
            mMediaSwitchingController.releaseSession();
            mDialogTransitionAnimator.disableAllCurrentDialogsExitAnimations();
            dismiss();
        }
    }

    @Override
    public int getBroadcastIconVisibility() {
        return (isBroadcastSupported() && mMediaSwitchingController.isBluetoothLeBroadcastEnabled())
                ? View.VISIBLE
                : View.GONE;
    }

    @Override
    public void onBroadcastIconClick() {
        startLeBroadcastDialog();
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

    @Nullable
    private CharSequence getTextForSessionReleaseType() {
        return switch (mMediaSwitchingController.getSessionReleaseType()) {
            case RoutingSessionInfo.RELEASE_TYPE_CASTING ->
                    mContext.getText(R.string.media_output_dialog_button_stop_casting);
            case RoutingSessionInfo.RELEASE_TYPE_SHARING ->
                    mContext.getText(R.string.media_output_dialog_button_stop_sharing);
            default -> null;
        };
    }
}
