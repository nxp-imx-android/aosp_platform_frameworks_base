/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.audio;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.media.MediaMetrics;
import android.telephony.SubscriptionManager;
import android.util.Slog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Locale;

/**
 * Helper class for camera sound forced metrics.
 */
final class CameraMetricsHelper {
    private static final String TAG = "AS.CameraMetricsHelper";

    // For camera sound forced metrics
    // Camera sound forced status is unknown.
    private static final int CAM_SND_STATUS_UNKNOWN = 0;
    // Camera sound can be forced by the system locale.
    private static final int CAM_SND_WITH_FORCED_LOCALE = 1;
    // Camera sound cannot be forced by the system locale.
    private static final int CAM_SND_WITHOUT_FORCED_LOCALE = 2;

    // There is no active SIM in the device.
    private static final int CAM_SND_WITHOUT_ACTIVE_SIM = 1;
    // Camera sound can be forced by the SIM.
    private static final int CAM_SND_WITH_FORCED_SIM = 2;
    // Camera sound cannot be forced by the SIM.
    private static final int CAM_SND_WITHOUT_FORCED_SIM = 3;

    // Camera sound locale satus IntDef
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        CAM_SND_STATUS_UNKNOWN,
        CAM_SND_WITH_FORCED_LOCALE,
        CAM_SND_WITHOUT_FORCED_LOCALE
    })
    public @interface CameraSoundLocaleStatus {}

    // Camera sound SIM status IntDef
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        CAM_SND_STATUS_UNKNOWN,
        CAM_SND_WITHOUT_ACTIVE_SIM,
        CAM_SND_WITH_FORCED_SIM,
        CAM_SND_WITHOUT_FORCED_SIM
    })
    public @interface CameraSoundSimStatus {}

    // Relax forced camera sound due to locale change
    private static final String CAM_SND_RELAX_CAUSE_LOCALE_CHANGE = "locale_change";
    // Relax forced camera sound due to SIM removal
    private static final String CAM_SND_RELAX_CAUSE_SIM_REMOVAL = "sim_removal";

    private final Context mContext;

    @CameraSoundLocaleStatus
    private int mCurrentLocaleStatus = CAM_SND_STATUS_UNKNOWN;
    @CameraSoundSimStatus
    private int mCurrentSimStatus = CAM_SND_STATUS_UNKNOWN;

    CameraMetricsHelper(@NonNull Context context) {
        mContext = context;
    }

    void updateCameraSoundForcedStatus() {
        // 1. Locale status
        final String language = Locale.getDefault().getLanguage();
        final String[] languageList = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_cameraSoundForcedLanguage);
        final boolean localeForced = Arrays.asList(languageList).contains(language);
        @CameraSoundLocaleStatus int newLocaleStatus =
                localeForced ? CAM_SND_WITH_FORCED_LOCALE : CAM_SND_WITHOUT_FORCED_LOCALE;

        // 2. SIM status
        final SubscriptionManager subscriptionManager =
                mContext.getSystemService(SubscriptionManager.class);
        @CameraSoundSimStatus int newSimStatus = CAM_SND_STATUS_UNKNOWN;
        if (subscriptionManager == null) {
            Slog.e(TAG, "SubscriptionManager not found.");
            return;
        }

        final int[] subscriptionIds = subscriptionManager.getActiveSubscriptionIdList(false);
        if (subscriptionIds == null || subscriptionIds.length == 0) {
            newSimStatus = CAM_SND_WITHOUT_ACTIVE_SIM;
        } else {
            boolean hasForcedSim = false;
            for (int subId : subscriptionIds) {
                if (SubscriptionManager.getResourcesForSubId(mContext, subId).getBoolean(
                        com.android.internal.R.bool.config_camera_sound_forced)) {
                    hasForcedSim = true;
                    break;
                }
            }
            newSimStatus = hasForcedSim ? CAM_SND_WITH_FORCED_SIM : CAM_SND_WITHOUT_FORCED_SIM;
        }

        if (mCurrentLocaleStatus == newLocaleStatus
                && mCurrentSimStatus == newSimStatus) {
            return;
        }

        // Encode locale and SIM status into a single integer.
        // Hundreds digit: Locale status (1=FORCED, 2=NOT_FORCED)
        // Units digit: SIM status (1=WITHOUT_ACTIVE, 2=FORCED, 3=NOT_FORCED)
        final int combination = newLocaleStatus * 100 + newSimStatus;
        Slog.i(TAG, "Camera sound forced status : " + combination);
        new MediaMetrics.Item(MediaMetrics.Name.AUDIO_CAMERA)
                .set(MediaMetrics.Property.STATUS, combination)
                .record();

        if (newLocaleStatus == CAM_SND_WITHOUT_FORCED_LOCALE
                && newSimStatus == CAM_SND_WITHOUT_ACTIVE_SIM) {
            if (mCurrentLocaleStatus == CAM_SND_WITH_FORCED_LOCALE
                    && mCurrentSimStatus == CAM_SND_WITHOUT_ACTIVE_SIM) {
                // transition from being unable to mute to being able to mute due to locale change
                new MediaMetrics.Item(MediaMetrics.Name.AUDIO_CAMERA_RELAX_CAUSE)
                        .set(MediaMetrics.Property.EVENT, CAM_SND_RELAX_CAUSE_LOCALE_CHANGE)
                        .record();
            } else if (mCurrentLocaleStatus == CAM_SND_WITHOUT_FORCED_LOCALE
                    && mCurrentSimStatus == CAM_SND_WITH_FORCED_SIM) {
                // transition from being unable to mute to being able to mute due to sim removal
                new MediaMetrics.Item(MediaMetrics.Name.AUDIO_CAMERA_RELAX_CAUSE)
                        .set(MediaMetrics.Property.EVENT, CAM_SND_RELAX_CAUSE_SIM_REMOVAL)
                        .record();
            }
        } else if (newSimStatus == CAM_SND_WITHOUT_FORCED_SIM) {
            if (mCurrentSimStatus == CAM_SND_WITH_FORCED_SIM) {
                // transition from being unable to mute to being able to mute due to sim removal
                new MediaMetrics.Item(MediaMetrics.Name.AUDIO_CAMERA_RELAX_CAUSE)
                        .set(MediaMetrics.Property.EVENT, CAM_SND_RELAX_CAUSE_SIM_REMOVAL)
                        .record();
            }
        }
        mCurrentLocaleStatus = newLocaleStatus;
        mCurrentSimStatus = newSimStatus;
    }
}
