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
package android.app.voiceinteraction;

import static android.permission.flags.Flags.FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.content.Intent;

import com.android.internal.app.IVoiceInteractionManagerService;

/** Provides access to VoiceInteraction API */
@FlaggedApi(FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED)
@SystemService(Context.VOICE_INTERACTION_MANAGER_SERVICE)
public class VoiceInteractionManager {
    private final IVoiceInteractionManagerService mService;
    private final Context mContext;

    /**
     * Activity action: Launch UI to for an assistant to request access to assist structure.
     *
     * <p>Output: Nothing.
     *
     * @hide
     */
    @FlaggedApi(FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED)
    @SystemApi
    @SdkConstant(SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_ASSIST_STRUCTURE =
            "android.app.voiceinteraction.action.REQUEST_ASSIST_STRUCTURE";

    /**
     * Creates an instance.
     *
     * @param service An interface to the backing service.
     * @param context A {@link Context}.
     * @hide
     */
    public VoiceInteractionManager(@NonNull IVoiceInteractionManagerService service,
            @NonNull Context context) {
        mService = service;
        mContext = context;
    }

    /**
     * Creates an intent which can be used to request access to assist structure for current
     * assistant role holder. This intent MUST be used with
     * {@link android.app.Activity#startActivityForResult}.The result code of the activity will be
     * {@link android.app.Activity#RESULT_OK} if the request was granted,
     * {@link android.app.Activity#RESULT_CANCELED} if not. If the caller is not the current
     * default assistant, the request will be denied automatically.
     *
     * @return The created intent.
     */
    @FlaggedApi(FLAG_ASSIST_SETTINGS_PRIVACY_IMPROVEMENTS_ENABLED)
    public @NonNull Intent createRequestAssistStructureIntent() {
        Intent intent = new Intent(ACTION_REQUEST_ASSIST_STRUCTURE);
        intent.setPackage(mContext.getPackageManager().getPermissionControllerPackageName());
        return intent;
    }
}
