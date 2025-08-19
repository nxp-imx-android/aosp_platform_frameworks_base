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

package android.app.memory.testhelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import dalvik.system.VMDebug;

import org.apache.harmony.dalvik.ddmc.DdmVmInternal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

public class EmptyActivity extends Activity {
    private static final String TAG = "AppMemoryTest";

    // A local string, whose value must be visible in the heap dump.  If the value is not found,
    // the heap dump is not being created correctly.
    private String mSentinelString;

    // A large array that is used to calibrate the heap profile walker.
    private int[] mExtraBytes;

    // Configure for a heap dump

    private static void configureForHeap() {
        VMDebug.setAllocTrackerStackDepth(64);
        DdmVmInternal.setRecentAllocationsTrackingEnabled(true);
    }

    // Configure allocation tracking in the constructor.
    public EmptyActivity() {
        configureForHeap();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        if (intent == null) {
            Log.e(TAG, "no intent to process");
            throw new RuntimeException("no intent to process");
        }
        String dumpPath = intent.getStringExtra("dump-path");
        if (dumpPath == null) {
            Log.e(TAG, "no dump path available");
            throw new RuntimeException("no dump path available");
        }
        long soakDelay = intent.getLongExtra("soak-delay", -1);
        if (soakDelay < 0) {
            Log.e(TAG, "no soak delay available");
            throw new RuntimeException("no soak delay available");
        }
        int extraSize = intent.getIntExtra("extra-size", 0);
        if (extraSize > 0) {
            mExtraBytes = new int[extraSize];
        }

        // Create a sentinel string.  If "a sample string" is not found in the heap dump, something
        // is wrong.
        StringBuilder b;
        b = new StringBuilder();
        b.append("a").append(" sample").append(" string");
        mSentinelString = b.toString();

        // Delay for the soak time.  The purpose is to give the background tasks time to
        // stabilize.
        final long alarm = SystemClock.uptimeMillis() + soakDelay;
        while (SystemClock.uptimeMillis() < alarm) {
            try {
                Thread.sleep(alarm - SystemClock.uptimeMillis());
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted during wait for boot-up");
            }
        }

        final String allocated = VMDebug.getRuntimeStat("art.gc.bytes-allocated");

        try {
            Debug.dumpHprofData(dumpPath + ".hprof");
        } catch (IOException e) {
            Log.e(TAG, "failed to create hprof " + dumpPath + ": ", e);
        }

        // The remaining logic occurs after the heap dump.
        Log.i(TAG, "heap dump=" + dumpPath + " soak-delay=" + soakDelay
                + " extra-size=" + extraSize);

        File statFile = new File(dumpPath + ".stats");
        try (PrintWriter out = new PrintWriter(statFile)) {
            // Write a header that identifies the start of the file.  The current time is used by
            // report generators to sequence statistics files from multiple runs on the same
            // build.
            out.format("memory.testhelper.runtime %d\n", System.currentTimeMillis() / 1000);
            out.format("art.gc.bytes-allocated %s\n", allocated);
            out.format("extra-size.length %d\n", (mExtraBytes != null) ? mExtraBytes.length : 0);
            out.format("sentinel.string %s\n", mSentinelString);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "cannot write stats file", e);
            throw new RuntimeException(e.toString());
        }

        // It is not necessary to throw for the following errors.  The main test app will detect
        // that the result files (statistics and heap dump) are not readable and will fail the test.
        if (!statFile.setReadable(true, false)) {
            Log.e(TAG, "failed to make stats file readable");
        } else if (!statFile.setWritable(true, false)) {
            Log.e(TAG, "failed to make stats file writable");
        }

        // Make the heap dump world-readable so that the main test can read it.
        File dumpFile = new File(dumpPath + ".hprof");
        if (!dumpFile.setReadable(true, false)) {
            Log.e(TAG, "failed to make dump readable");
        }
    }
}
