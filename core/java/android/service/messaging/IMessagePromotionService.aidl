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

package android.service.messaging;

import android.net.Uri;
import android.service.messaging.IMessagePromotionCallback;

/**
 * A service that allows the default SMS app to handle message promotions. When an SMS or MMS is
 * being sent, the Telephony framework checks if the default SMS app implements this service. If
 * so, it binds to the service to request message promotions, allowing the SMS app to send the
 * message via a supported protocol.
 *
 * <p>If the service accepts the promotion request, the default SMS app is solely responsible for
 * sending the message to the recipient and moving it from the OUTBOX to SENT or FAILED folder.</p>
 *
 * @hide
 */
oneway interface IMessagePromotionService {
    /**
     * Enable the default SMS app to promote SMS/MMS messages. SMS app will have limited time to
     * respond to the promotion request with either
     * {@link MessagePromotionService#PROMOTION_STATUS_ACCEPTED} or
     * {@link MessagePromotionService#PROMOTION_STATUS_REJECTED}. It's not expected to keep running
     * while the message is being sent.
     *
     * @param contentUri the content URI of the SMS/MMS message.
     * @param callback the callback to be invoked with the promotion status.
     */
    void promoteMessage(in Uri contentUri, in IMessagePromotionCallback callback);
}
