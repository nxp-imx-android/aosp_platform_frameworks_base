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

import static com.android.internal.telephony.flags.Flags.FLAG_MESSAGE_PROMOTION;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SdkConstant;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import java.util.concurrent.Executor;

/**
 * A service that allows the default SMS app to handle message promotions. When an SMS or MMS is
 * being sent, the Telephony framework checks if the default SMS app implements this service. If so,
 * it binds to the service to request message promotions, allowing the SMS app to send the message
 * via a supported protocol.
 *
 * <p>If the service accepts the promotion request, the default SMS app is solely responsible for
 * sending the message to the recipient and moving it from the OUTBOX to SENT or FAILED folder.</p>
 *
 * <p>To extend this class, you must declare the service in your manifest file to require the
 * {@link android.Manifest.permission#BIND_MESSAGE_PROMOTION_SERVICE} permission and include an
 * intent filter with the {@link #SERVICE_INTERFACE} action. For example:</p>
 * <pre>
 * &lt;service android:name=".MyMessagePromotionService"
 *          android:permission="android.permission.BIND_MESSAGE_PROMOTION_SERVICE"
 *          android:exported="true"&gt;
 *     &lt;intent-filter&gt;
 *         &lt;action android:name="android.service.messaging.MessagePromotionService" /&gt;
 *     &lt;/intent-filter&gt;
 * &lt;/service&gt;
 * </pre>
 * @hide
 */
// TODO(b/474087468): Make this a public API.
@FlaggedApi(FLAG_MESSAGE_PROMOTION)
public abstract class MessagePromotionService extends Service {
    private static final String TAG = MessagePromotionService.class.getSimpleName();

    /**
     * The {@link android.content.Intent} that must be declared as handled by the service.
     */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.service.messaging.MessagePromotionService";

    /**
     * Status code when the default SMS app has accepted the message for promotion and will now
     * attempt to send it as per the supported protocol.
     */
    public static final int PROMOTION_STATUS_ACCEPTED = 1;

    /**
     * Status code when the default SMS app could not accept the message for promotion.
     */
    public static final int PROMOTION_STATUS_REJECTED = 2;

    private final IMessagePromotionServiceImpl mImpl = new IMessagePromotionServiceImpl();

    /**
     * Called by the service upon receiving a new message promotion request. SMS app will have
     * limited time to respond to the promotion request with either
     * {@link #PROMOTION_STATUS_ACCEPTED} or {@link #PROMOTION_STATUS_REJECTED}. It's not expected
     * to keep running while the message is being sent.
     *
     * @param contentUri the content URI of the MMS message.
     * @param executor the executor to run the callback on.
     * @param callback the callback to notify about promotion status.
     */
    public abstract void onPromoteMessage(
            @NonNull Uri contentUri, @NonNull Executor executor,
            @NonNull MessagePromotionStatusCallback callback);

    @Override
    @Nullable
    public final IBinder onBind(@Nullable Intent intent) {
        if (intent == null || !SERVICE_INTERFACE.equals(intent.getAction())) {
            return null;
        }
        return mImpl;
    }

    /**
     * A wrapper around IMessagePromotionService to enable the default messaging app to implement
     * methods it cares about in the {@link IMessagePromotionService} interface.
     */
    private final class IMessagePromotionServiceImpl extends IMessagePromotionService.Stub {
        @Override
        public void promoteMessage(
                @NonNull Uri contentUri, @NonNull final IMessagePromotionCallback callback) {
            MessagePromotionService.this.onPromoteMessage(
                    contentUri, getMainExecutor(), new MessagePromotionStatusCallback(callback));
        }
    }
}
