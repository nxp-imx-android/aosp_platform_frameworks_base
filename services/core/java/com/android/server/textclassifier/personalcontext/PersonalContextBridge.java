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

package com.android.server.textclassifier.personalcontext;

import static android.service.personalcontext.Flags.enablePersonalContextService;
import static android.service.personalcontext.Flags.enableTextClassifier;

import android.annotation.NonNull;
import android.content.Context;
import android.service.textclassifier.ITextClassifierCallback;
import android.view.textclassifier.TextClassification;

import com.android.internal.R;
import com.android.server.personalcontext.PersonalContextManagerInternal;

/** Local bridge service to trigger and receive insights from Personal Context Service */
public abstract class PersonalContextBridge {
    /**
     * Sends a {@link TextClassification.Request} to the Personal Context service to trigger
     * TextClassification insight generation. {@link
     * PersonalContextManagerInternal#onTextClassifyRequest} is a oneway lightweight hint publish
     * method that does not block.
     */
    public abstract void trigger(int userId, String sessionId, TextClassification.Request request);

    /**
     * Queues up the TextClassification response from {@link TextClassificationActionRenderer} so
     * that {@link PersonalContextBridgeImpl.MergeCallback} can merge the results.
     */
    public abstract void merge(@NonNull String sessionId, TextClassification response);

    /**
     * Wraps callback with a {@link ITextClassifierCallback} that will merge personal context
     * results with the {@link TextClassification} returned to the original callback. If
     * PersonalContext service is not enabled, returns the original callback without wrapping.
     */
    public abstract ITextClassifierCallback wrap(
            @NonNull String sessionId, @NonNull ITextClassifierCallback callback);

    /**
     * Clears any pending personal context results for the session. Must be called from {@link
     * TextClassificationManagerService#onDestroyTextClassificationSession}.
     */
    public abstract void clearSession(@NonNull String sessionId);

    /** Checks that personal context is enabled. */
    public static boolean isPersonalContextEnabled() {
        return enablePersonalContextService() && enableTextClassifier();
    }

    public record Config(long mTimeoutInMillis) {

        private static long getTimeoutInMillis(Context context) {
            return context.getResources()
                    .getInteger(R.integer.config_textClassifierPersonalContextTimeoutMillis);
        }

        public Config(Context context) {
            this(getTimeoutInMillis(context));
        }
    }
}
