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

package android.app.privatecompute;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Abstract base class for a PCC service that receives data from other components. Developers must
 * extend this class and implement its abstract methods to handle data ingress.
 */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public abstract class PccService extends Service {
    private final IPccService.Stub mBinder = new IPccService.Stub() {

    };


    /**
     * Returns the communication channel to the service.
     *
     * @param intent The Intent that was used to bind to this service.
     * @return An IBinder through which clients can call back to the service. A system proxy binder
     * will be returned to the caller, if it is not executing in a PCC UID, or isn't a trusted
     * component.
     */
    @NonNull
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        return mBinder;
    }
}
