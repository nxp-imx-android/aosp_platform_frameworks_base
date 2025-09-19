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

package android.service.personalcontext;

import android.annotation.SystemService;
import android.content.Context;
import android.util.Log;

/**
 * Client facing access to the PersonalContext service.
 * @hide
 */
@SystemService(Context.PERSONAL_CONTEXT_SERVICE)
public class PersonalContextManager {
    /** The name of the Personal Context service. */
    public static final String PERSONAL_CONTEXT_SERVICE = "personal_context";

    private static final String TAG = "PersonalContextManager";

    private IPersonalContextManager mService;

    /**
     * @hide
     */
    public PersonalContextManager(IPersonalContextManager service)  {
        mService = service;
        Log.d(TAG, "Set up service: " + mService);
    }
}
