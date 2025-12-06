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

package com.android.server.textclassifier;

import static android.service.personalcontext.Flags.enablePersonalContextService;
import static android.service.personalcontext.Flags.enableTextClassifier;

import android.util.Log;
import android.view.textclassifier.TextClassification;

import com.android.server.LocalServices;
import com.android.server.personalcontext.PersonalContextManagerInternal;

/** Local bridge service to trigger and receive insights from Personal Context Service */
public abstract class PersonalContextBridge {

    private static final String TAG = "PersonalContextBridge";

    /**
     * Sends a {@link TextClassification.Request} to the Personal Context service to trigger
     * TextClassification insight generation. {@link
     * PersonalContextManagerInternal#onTextClassifyRequest} is a oneway lightweight hint publish
     * method that does not block.
     */
    public abstract void trigger(int userId, String sessionId, TextClassification.Request request);

    static boolean isPersonalContextEnabled() {
        return enablePersonalContextService() && enableTextClassifier();
    }

    static class LocalService extends PersonalContextBridge {
        LocalService() {}

        @Override
        public void trigger(int userId, String sessionId, TextClassification.Request request) {
            PersonalContextManagerInternal pcmi =
                    LocalServices.getService(PersonalContextManagerInternal.class);
            if (pcmi == null) {
                Log.w(TAG, "Did not find PersonalContextManagerInternal system service");
                return;
            }
            pcmi.onTextClassifyRequest(userId, sessionId, request);
        }
    }
}
