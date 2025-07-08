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

package com.android.server.userrecovery;

import static android.app.userrecovery.flags.Flags.enableUserRecoveryManager;

import android.app.userrecovery.IUserRecoveryManager;
import android.content.Context;

import com.android.server.SystemService;

/**
 * Service that manages user recovery functions.
 */
public class UserRecoveryManagerService extends SystemService {

    public UserRecoveryManagerService(Context context) {
        super(context);
    }
    @Override
    public void onStart() {
        if (enableUserRecoveryManager()) {
            publishBinderService(Context.USER_RECOVERY_SERVICE, new RecoveryManagerStub());
        }
    }

    private static class RecoveryManagerStub extends IUserRecoveryManager.Stub {

    }
}
