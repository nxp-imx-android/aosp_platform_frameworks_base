/*
 * Copyright 2023 The Android Open Source Project
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

import static android.Manifest.permission.USE_EXACT_ALARM;
import static android.Manifest.permission.SCHEDULE_EXACT_ALARM;
import static android.media.audio.Flags.autoPublicVolumeApiHardening;
import static com.android.media.audio.Flags.hardeningPartial;
import static com.android.media.audio.Flags.hardeningPartialVolume;
import static com.android.media.audio.Flags.hardeningStrict;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_FOCUS;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_RINGER;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_VOLUME;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_ALARM;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ALARM;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ANNOUNCEMENT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_SONIFICATION;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_CALL_ASSISTANT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_EMERGENCY;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_GAME;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_MEDIA;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_EVENT;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_SAFETY;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VEHICLE_STATUS;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VIRTUAL_SOURCE;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION;
import static com.android.media.audio.metrics.AudioAtomsLog.AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.PermissionChecker;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.IAudioPolicyService.HardeningOverride;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;

import com.android.media.audio.metrics.AudioAtomsLog;
import com.android.modules.expresslog.Counter;
import com.android.server.utils.EventLogger;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to encapsulate all audio API hardening operations
 */
public class HardeningEnforcer {

    private static final String TAG = "AS.HardeningEnforcer";
    private static final boolean DEBUG = false;
    private static final int LOG_NB_EVENTS = 20;

    final Context mContext;
    final AppOpsManager mAppOps;
    final AtomicInteger mHardeningOverride;
    final boolean mIsAutomotive;

    final ActivityManager mActivityManager;
    final PackageManager mPackageManager;

    final EventLogger mEventLogger;

    // capacity = 4 for each of the focus request types
    static final SparseArray<String> METRIC_COUNTERS_FOCUS_DENIAL = new SparseArray<>(4);
    static final SparseArray<String> METRIC_COUNTERS_FOCUS_GRANT = new SparseArray<>(4);

    static {
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN,
                "media_audio.value_audio_focus_gain_granted");
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                "media_audio.value_audio_focus_gain_transient_granted");
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                "media_audio.value_audio_focus_gain_transient_duck_granted");
        METRIC_COUNTERS_FOCUS_GRANT.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                "media_audio.value_audio_focus_gain_transient_excl_granted");

        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN,
                "media_audio.value_audio_focus_gain_appops_denial");
        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                "media_audio.value_audio_focus_gain_transient_appops_denial");
        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                "media_audio.value_audio_focus_gain_transient_duck_appops_denial");
        METRIC_COUNTERS_FOCUS_DENIAL.put(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                "media_audio.value_audio_focus_gain_transient_excl_appops_denial");
    }

    /**
     * Matches calls from {@link AudioManager#setStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_SET_STREAM_VOLUME = 100;
    /**
     * Matches calls from {@link AudioManager#adjustVolume(int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_VOLUME = 101;
    /**
     * Matches calls from {@link AudioManager#adjustSuggestedStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_SUGGESTED_STREAM_VOLUME = 102;
    /**
     * Matches calls from {@link AudioManager#adjustStreamVolume(int, int, int)}
     */
    public static final int METHOD_AUDIO_MANAGER_ADJUST_STREAM_VOLUME = 103;
    /**
     * Matches calls from {@link AudioManager#setRingerMode(int)}
     */
    public static final int METHOD_AUDIO_MANAGER_SET_RINGER_MODE = 200;
    /**
     * Matches calls from {@link AudioManager#requestAudioFocus(AudioFocusRequest)}
     * and legacy variants
     */
    public static final int METHOD_AUDIO_MANAGER_REQUEST_AUDIO_FOCUS = 300;

    private static final int ALLOWED = 0;
    private static final int DENIED_IF_PARTIAL = 1;
    private static final int DENIED_IF_FULL = 2;

    public HardeningEnforcer(Context ctxt, boolean isAutomotive,
            AtomicInteger hardeningOverride, AppOpsManager appOps, PackageManager pm,
            EventLogger logger) {
        mContext = ctxt;
        mIsAutomotive = isAutomotive;
        mHardeningOverride = hardeningOverride;
        mAppOps = appOps;
        mActivityManager = ctxt.getSystemService(ActivityManager.class);
        mPackageManager = pm;
        mEventLogger = logger;
    }

    /**
     * Translates the AudioAttributes usage to the corresponding proto usage enum
     * @param usage the usage from AudioAttributes
     * @return the proto usage enum
     */
    public static int getUsageForProtoLog(int usage) {
        return switch (usage) {
            case AudioAttributes.USAGE_MEDIA -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_MEDIA;
            case AudioAttributes.USAGE_VOICE_COMMUNICATION ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION;
            case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING;
            case AudioAttributes.USAGE_ALARM -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ALARM;
            case AudioAttributes.USAGE_NOTIFICATION ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION;
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE;
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
            case AudioAttributes.USAGE_NOTIFICATION_EVENT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_NOTIFICATION_EVENT;
            case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY;
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANCE_SONIFICATION;
            case AudioAttributes.USAGE_GAME -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_GAME;
            case AudioAttributes.USAGE_VIRTUAL_SOURCE ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VIRTUAL_SOURCE;
            case AudioAttributes.USAGE_ASSISTANT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ASSISTANT;
            case AudioAttributes.USAGE_CALL_ASSISTANT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_CALL_ASSISTANT;
            case AudioAttributes.USAGE_EMERGENCY ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_EMERGENCY;
            case AudioAttributes.USAGE_SAFETY ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_SAFETY;
            case AudioAttributes.USAGE_VEHICLE_STATUS ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_VEHICLE_STATUS;
            case AudioAttributes.USAGE_ANNOUNCEMENT ->
                    AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_ANNOUNCEMENT;
            default -> AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN;
        };
    }

    /**
     * Checks whether the call in the current thread should be allowed or blocked
     * @param volumeMethod name of the method to check, for logging purposes
     * @return false if the method call is allowed, true if it should be a no-op
     */
    protected boolean blockVolumeMethod(int volumeMethod, String packageName, int uid) {
        // Regardless of flag state, always permit callers with privileged audio permissions
        // Prevent them from showing up in metrics as well
        if (holdsPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED) ||
                holdsPermission(Manifest.permission.MODIFY_AUDIO_ROUTING) ||
                holdsPermission(Manifest.permission.MODIFY_PHONE_STATE)) {
            return false;
        }
        // for Auto, volume methods require MODIFY_AUDIO_SETTINGS_PRIVILEGED
        if (mIsAutomotive) {
            if (!autoPublicVolumeApiHardening()) {
                // automotive hardening flag disabled, no blocking on auto
                return false;
            }
            if (uid < UserHandle.AID_APP_START) {
                return false;
            }
            // TODO metrics?
            // TODO log for audio dumpsys?
            Slog.e(TAG, "Preventing volume method " + volumeMethod + " for "
                    + packageName);
            return true;
        } else {
            int allowed;
            // This flag is misnamed: it blocks volume changes at the strict level: app usage of
            // volume modifications between partial and strict level should be extremely limited
            // given we don't want to encourage apps modify volume regardless.
            var overrideState = mHardeningOverride.get();
            boolean enforced = switch (overrideState) {
                case HardeningOverride.ENABLE, AudioManager.HARDENING_THROW -> true;
                case HardeningOverride.DISABLE -> false;
                default -> hardeningPartialVolume();
            };
            if (!noteOp(AppOpsManager.OP_CONTROL_AUDIO_PARTIAL, uid, packageName, null)) {
                // blocked by partial
                Counter.logIncrementWithUid(
                        "media_audio.value_audio_volume_hardening_partial_restriction", uid);
                allowed = DENIED_IF_PARTIAL;
            } else if (!noteOp(AppOpsManager.OP_CONTROL_AUDIO, uid, packageName, null)) {
                // blocked by full, permitted by partial
                Counter.logIncrementWithUid(
                        "media_audio.value_audio_volume_hardening_strict_restriction", uid);
                allowed = DENIED_IF_FULL;
            } else {
                // permitted with strict hardening, log anyway for API metrics
                Counter.logIncrementWithUid(
                        "media_audio.value_audio_volume_hardening_allowed", uid);
                allowed = ALLOWED;
            }
            if (allowed != ALLOWED) {
                String msg = "AudioHardening volume control for api "
                        + volumeMethod
                        + (!enforced ? " would be " : " ")
                        + "ignored for "
                        + getPackNameForUid(uid) + " (" + uid + "), "
                        + "level: " + (allowed == DENIED_IF_PARTIAL ? "partial" : "full");
                mEventLogger.enqueueAndSlog(msg, EventLogger.Event.ALOGW, TAG);
                if (overrideState == AudioManager.HARDENING_THROW) {
                    throw new IllegalStateException(msg);
                }
                boolean isStrict = allowed == DENIED_IF_FULL;
                if (volumeMethod == METHOD_AUDIO_MANAGER_SET_RINGER_MODE) {
                    AudioAtomsLog.write(AudioAtomsLog.AUDIO_HARDENING_REPORTED, uid,
                            AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_RINGER,
                            isStrict, enforced,
                            AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN,
                            AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE);
                } else {
                    AudioAtomsLog.write(AudioAtomsLog.AUDIO_HARDENING_REPORTED, uid,
                            AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_VOLUME,
                            isStrict, enforced,
                            AUDIO_HARDENING_REPORTED__USAGE__AUDIO_USAGE_UNKNOWN,
                            AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE);
                }
            }
            return enforced && allowed != ALLOWED;
        }
    }

    /**
     * Checks whether the call in the current thread should be allowed or blocked
     * @param focusMethod name of the method to check, for logging purposes
     * @param clientId id of the requester
     * @param focusReqType focus type being requested
     * @param attributionTag attribution of the caller
     * @param targetSdk target SDK of the caller
     * @param aa attributes of the request
     * @return false if the method call is allowed, true if it should be a no-op
     */
    @SuppressWarnings("AndroidFrameworkCompatChange")
    protected boolean blockFocusMethod(int callingUid, int focusMethod, @NonNull String clientId,
            int focusReqType, @NonNull String packageName, String attributionTag, int targetSdk,
            @NonNull AudioAttributes aa) {
        if (packageName.isEmpty()) {
            packageName = getPackNameForUid(callingUid);
        }
        int blockLevel = ALLOWED;
        if (!noteOp(AppOpsManager.OP_TAKE_AUDIO_FOCUS, callingUid, packageName, attributionTag)) {
            blockLevel = DENIED_IF_PARTIAL;
        } else if (!noteOp(AppOpsManager.OP_CONTROL_AUDIO, callingUid, packageName,
                           attributionTag)) {
            blockLevel = DENIED_IF_FULL;
        }

        var overrideState = mHardeningOverride.get();
        boolean isPreVic = targetSdk < Build.VERSION_CODES.VANILLA_ICE_CREAM;
        boolean enforcedPartial = switch (overrideState) {
            case HardeningOverride.ENABLE, AudioManager.HARDENING_THROW -> true;
            case HardeningOverride.DISABLE -> false;
            default -> hardeningPartial() || !isPreVic;
        };
        boolean enforcedFull = switch (overrideState) {
            case HardeningOverride.ENABLE, AudioManager.HARDENING_THROW -> true;
            case HardeningOverride.DISABLE -> false;
            default -> hardeningStrict();
        };

        int exemption = AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_NONE;
        if (aa.getUsage() == AudioAttributes.USAGE_ALARM) {
            if (holdsPermission(USE_EXACT_ALARM)
                    || PermissionChecker.checkPermissionForPreflight(mContext,
                            SCHEDULE_EXACT_ALARM, PermissionChecker.PID_UNKNOWN,
                            callingUid, packageName) == PermissionChecker.PERMISSION_GRANTED) {
                enforcedFull = false;
                exemption = AUDIO_HARDENING_REPORTED__EXEMPTION_REASON__HARDENING_EXEMPTION_ALARM;
            }
        }

        int usage = getUsageForProtoLog(aa.getUsage());
        if (blockLevel == DENIED_IF_PARTIAL) {
            String msg = "AudioHardening focus request for req "
                    + focusReqType
                    + (!enforcedPartial ? " would be " : " ")
                    + "ignored for "
                    + packageName + " (" + callingUid + "), "
                    + clientId
                    + ", level: partial";
            mEventLogger.enqueueAndSlog(msg, EventLogger.Event.ALOGW, TAG);
            if (overrideState == AudioManager.HARDENING_THROW) {
                throw new IllegalStateException(msg);
            }
            AudioAtomsLog.write(AudioAtomsLog.AUDIO_HARDENING_REPORTED, callingUid,
                    AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_FOCUS,
                    false /*isStrict*/, enforcedPartial,
                    usage, exemption);
        } else if (blockLevel == DENIED_IF_FULL) {
            String msg = "AudioHardening focus request for req "
                    + focusReqType
                    + (!enforcedFull ? " would be " : " ")
                    + "ignored for "
                    + packageName + " (" + callingUid + "), "
                    + clientId
                    + ", level: full";
            mEventLogger.enqueueAndSlog(msg, EventLogger.Event.ALOGW, TAG);
            if (overrideState == AudioManager.HARDENING_THROW) {
                throw new IllegalStateException(msg);
            }
            AudioAtomsLog.write(AudioAtomsLog.AUDIO_HARDENING_REPORTED, callingUid,
                    AUDIO_HARDENING_REPORTED__API_TYPE__AUDIO_HARDENING_API_TYPE_FOCUS,
                    true /*isStrict*/, enforcedFull,
                    usage, exemption);
        }
        boolean blocked = (blockLevel == DENIED_IF_PARTIAL) && enforcedPartial ||
                              (blockLevel == DENIED_IF_FULL) && enforcedFull;
        metricsLogFocusReq(blocked, focusReqType, callingUid);
        return blocked;
    }

    /**
     * Log metrics for the focus request
     * @param blocked true if the call blocked
     * @param focusReq the type of focus request
     * @param callingUid the UID of the caller
     * @param unblockedBySdk if blocked is false,
     *                       true indicates it was unblocked thanks to an older SDK
     */
    /*package*/ void metricsLogFocusReq(boolean blocked, int focusReq, int callingUid) {
        final String metricId = blocked ? METRIC_COUNTERS_FOCUS_DENIAL.get(focusReq)
                : METRIC_COUNTERS_FOCUS_GRANT.get(focusReq);
        if (TextUtils.isEmpty(metricId)) {
            Slog.e(TAG, "Bad string for focus metrics gain:" + focusReq + " blocked:" + blocked);
            return;
        }
        try {
            Counter.logIncrementWithUid(metricId, callingUid);
        } catch (Exception e) {
            Slog.e(TAG, "Counter error metricId:" + metricId + " for focus req:" + focusReq
                    + " from uid:" + callingUid, e);
        }
    }

    private String getPackNameForUid(int uid) {
        final long token = Binder.clearCallingIdentity();
        try {
            final String[] names = mPackageManager.getPackagesForUid(uid);
            if (names == null
                    || names.length == 0
                    || TextUtils.isEmpty(names[0])) {
                return "[" + uid + "]";
            }
            return names[0];
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Notes the given op without throwing
     * @param op the appOp code
     * @param uid the calling uid
     * @param packageName the package name of the caller
     * @param attributionTag attribution of the caller
     * @return return false if the operation is not allowed
     */
    private boolean noteOp(int op, int uid, @NonNull String packageName,
            @Nullable String attributionTag) {
        if (mAppOps.noteOpNoThrow(op, uid, packageName, attributionTag, null)
                != AppOpsManager.MODE_ALLOWED) {
            return false;
        }
        return true;
    }

    private boolean holdsPermission(String permission) {
        return mContext.checkCallingOrSelfPermission(permission)
            == PackageManager.PERMISSION_GRANTED;
    }
}
