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

package com.example.pcctestapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class TestPccService extends Service {
    private static final String TAG = TestPccService.class.getSimpleName();

    private final IPccService.Stub mBinder = new IPccService.Stub() {
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "PCC service created.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
