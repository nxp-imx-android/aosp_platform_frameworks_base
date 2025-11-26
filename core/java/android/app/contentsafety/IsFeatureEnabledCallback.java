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

package android.app.contentsafety;


import static android.app.contentsafety.flags.Flags.FLAG_ENABLE_CONTENTSAFETY;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.SystemApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Callback functions used for isFeatureEnabled via
 * {@link ContentSafetyManager#isFeatureEnabled}.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(FLAG_ENABLE_CONTENTSAFETY)
public interface IsFeatureEnabledCallback {
    int FEATURE_SETTINGS_ERROR_UNKNOWN = 0;

    /**
     * Sent when the remote service failed to fetch feature settings status due to an internal
     * error.
     */
    int FEATURE_SETTINGS_ERROR = 1;

    /**
     * Sent when the remote {@link android.service.contentsafety.ContentSafetySettingsService}
     * is unavailable.
     */
    int FEATURE_SETTINGS_SERVICE_UNAVAILABLE = 2;

    /**
     * @hide
     */
    @IntDef(
            value = {
                    FEATURE_SETTINGS_ERROR_UNKNOWN,
                    FEATURE_SETTINGS_ERROR,
                    FEATURE_SETTINGS_SERVICE_UNAVAILABLE

            })
    @Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.SOURCE)
    @interface FeatureSettingsFailureStatus{ }

    /**
     * Called when calling to the remote settings service is successful
     * regardless the feature is enabled or not.
     *
     * @param result true if the feature setting is enabled and false otherwise.
     */
    void onSuccess(boolean result);

    /**
     * Called when calling the remote settings service failed.
     *
     * @param failureStatus the failure status.
     */
    void onFailure(@FeatureSettingsFailureStatus int failureStatus);
}
