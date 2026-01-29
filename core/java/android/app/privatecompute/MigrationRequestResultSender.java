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

package android.app.privatecompute;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.RemoteException;

/**
 * Callback used by the service to communicate the result of the migration request.
 */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public final class MigrationRequestResultSender {

    private final IMigrationRequestResultSender mBinder;

    /** @hide */
    public MigrationRequestResultSender(IMigrationRequestResultSender binder) {
        mBinder = binder;
    }

    /**
     * Sends the result back to the PCC component and signals the system
     * that this service is done.
     */
    public void sendResult(@NonNull MigrationRequestResult result) {
        try {
            mBinder.sendResult(result);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
