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
import android.os.RemoteException;

/**
 * A callback for the default SMS app to report the status of a message promotion request.
 * @hide
 */
// TODO(b/474087468): Make this a public API.
@FlaggedApi(FLAG_MESSAGE_PROMOTION)
public final class MessagePromotionStatusCallback {
    private final IMessagePromotionCallback mCallback;

    /** @hide */
    public MessagePromotionStatusCallback(@NonNull IMessagePromotionCallback callback) {
        mCallback = callback;
    }

    /**
     * Reports the result of the promotion attempt.
     *
     * @param status The status of the message promotion request.
     */
    public void onPromotionStatusAvailable(int status) {
        try {
            mCallback.onPromotionStatusAvailable(status);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
