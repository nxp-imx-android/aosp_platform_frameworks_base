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

package com.android.memorylimitertests.apps.memorylimitertestapp;

import android.app.ListActivity;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

/**
 * This application acts as a client that provides data to the SharedFileClientTest.
 */
public class TestActivity extends ListActivity {

    static final String TAG = "MemoryLimiterTestApp";

    // Self.
    static final String SELF = "com.android.memorylimitertests.apps.memorylimitertestapp";

    // The broadcast receiver for instructions.
    ClientTestReceiver mReceiver;

    // A list of tests: this app can be run interactively.
    Test[] mTests = new Test[] {
        new Test("Memory 0") {
            void run() {
                setMemory(0);
            }
        },
        new Test("Memory 1024") {
            void run() {
                setMemory(1024);
            }
        },
        new Test("Memory 2048") {
            void run() {
                setMemory(2048);
            }
        },
        new Test("Memory 4096") {
            void run() {
                setMemory(4096);
            }
        },
    };

    abstract static class Test {
        String mName;
        Test(String n) {
            mName = n;
        }
        abstract void run();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        String[] labels = new String[mTests.length];
        for (int i = 0; i < mTests.length; i++) {
            labels[i] = mTests[i].mName;
        }
        setListAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, labels));

        mReceiver = new ClientTestReceiver(this);
        IntentFilter memFilter = new IntentFilter(SELF + ".MEMORY");
        IntentFilter exitFilter = new IntentFilter(SELF + ".EXIT");
        int flags = Context.RECEIVER_EXPORTED;
        registerReceiver(mReceiver, memFilter, flags);
        registerReceiver(mReceiver, exitFilter, flags);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Test t = mTests[position];
        Log.e(TAG, "Test: " + t.mName);
        t.run();
    }

    // 1M
    static final int MEG = 1024 * 1024;

    // The memory that is currently in use.  This is implemented as an list or 1M arrays.  There
    // is no reason to be fast or efficient.  The array is merely holding memory to bloat the
    // application.
    final ArrayList<byte[]> mMemory = new ArrayList<>();

    // Change the bloat memory to <n> units. The units are 1M.  The target is absolute, not
    // relative.
    void setMemory(int size) {
        while (mMemory.size() > size) {
            mMemory.remove(0);
        }
        while (mMemory.size() < size) {
            var b = new byte[MEG];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) (i % 256);
            }
            mMemory.add(b);
        }
        Log.i(TAG, String.format("Memory set to %dMB (%d blocks)", size, mMemory.size()));
    }
}
