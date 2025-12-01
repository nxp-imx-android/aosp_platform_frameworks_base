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

package com.android.server.personalcontext;

import android.annotation.NonNull;
import android.service.personalcontext.hint.NotificationEvent;
import android.view.textclassifier.TextClassification;

/**
 * Personal context manager local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class PersonalContextManagerInternal {
    /**
     * Called when a notification event occurs.
     *
     * @param event The notification event.
     */
    public abstract void onNotificationEvent(@NonNull NotificationEvent event);

    /**
     * Called when a classify text request occurs.
     *
     * @param userId The userId that triggered this
     * @param sessionId The TextClassification sessionId value for this request
     * @param request The TextClassification request
     */
    public abstract void onTextClassifyRequest(
            int userId, String sessionId, @NonNull TextClassification.Request request);
}
