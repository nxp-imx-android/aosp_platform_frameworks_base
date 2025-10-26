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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private boolean mIsPccServiceBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            logAndShowToast(Log.INFO, "Service connected!");
            mIsPccServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            logAndShowToast(Log.INFO, "Service disconnected unexpectedly.");
            mIsPccServiceBound = false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setGravity(Gravity.CENTER);

        // Button to bind to pcc service.
        Button bindButton = new Button(this);
        bindButton.setText("Bind to PCC service");
        bindButton.setOnClickListener(v -> {
            if (!mIsPccServiceBound) {
                Log.i(TAG, "Attempting to bind service...");
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.example.pcctestapp",
                        "com.example.pcctestapp.TestPccService"));
                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            } else {
                logAndShowToast(Log.WARN, "Service is already bound.");
            }
        });

        // Button to unbind from the pcc service.
        Button unbindButton = new Button(this);
        unbindButton.setText("Unbind from PCC service");
        unbindButton.setOnClickListener(v -> {
            if (mIsPccServiceBound) {
                Log.i(TAG, "Unbinding service...");
                unbindService(mConnection);
                logAndShowToast(Log.INFO, "Service disconnected.");
                mIsPccServiceBound = false;
            } else {
                logAndShowToast(Log.ERROR, "Service is not bound, cannot unbind.");
            }
        });

        layout.addView(bindButton);
        layout.addView(unbindButton);
        setContentView(layout);
    }

    private void logAndShowToast(int logLevel, String message) {
        switch (logLevel) {
            case Log.INFO -> Log.i(TAG, message);
            case Log.WARN -> Log.w(TAG, message);
            case Log.ERROR -> Log.e(TAG, message);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
