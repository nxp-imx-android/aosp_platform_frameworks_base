/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.servicestests.apps.memorylimitertestapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;

public class ClientTestReceiver extends BroadcastReceiver {
    private static final String TAG = TestActivity.TAG;

    private final TestActivity mTest;

    ClientTestReceiver(TestActivity activity) {
        mTest = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Trace.beginSection("ClientTestReceiver.onReceive");
        int size = getSize(intent);
        Log.i(TAG, "handling memory size " + size + "MB");
        mTest.setMemory(size);
        Trace.endSection();
    }
    /**
     * Fetch the delay from the intent.  A negative delay signifies an error.
     */
    static int getSize(Intent intent) {
        if (!intent.getAction().contains("MEMORY")) {
            Log.w(TAG, "unexpected intent: " + intent.toString());
            return -1;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.i(TAG, "no extras");
            return 0;
        }
        String s = intent.getStringExtra("size");
        if (s != null) {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }
}
