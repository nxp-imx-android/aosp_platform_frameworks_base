/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.backup.adb;

import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;

import libcore.io.IoUtils;

import java.io.InputStream;

class FullAdbRestoreEngineThread implements Runnable {

    FullAdbRestoreEngine mEngine;
    InputStream mEngineStream;
    private final boolean mMustKillAgent;

    FullAdbRestoreEngineThread(FullAdbRestoreEngine engine, ParcelFileDescriptor engineSocket) {
        mEngine = engine;
        engine.setRunning(true);
        // We *do* want this FileInputStream to own the underlying fd, so that
        // when we are finished with it, it closes this end of the pipe in a way
        // that signals its other end.
        mEngineStream = new AutoCloseInputStream(engineSocket);
        // Tell it to be sure to leave the agent instance up after finishing
        mMustKillAgent = false;
    }

    //for adb restore
    FullAdbRestoreEngineThread(FullAdbRestoreEngine engine, InputStream inputStream) {
        mEngine = engine;
        engine.setRunning(true);
        mEngineStream = inputStream;
        // philippov: in adb agent is killed after restore.
        mMustKillAgent = true;
    }

    public boolean isRunning() {
        return mEngine.isRunning();
    }

    public int waitForResult() {
        return mEngine.waitForResult();
    }

    @Override
    public void run() {
        try {
            while (mEngine.isRunning()) {
                mEngine.restoreOneFile(mEngineStream, mMustKillAgent, mEngine.mBuffer,
                        mEngine.mOnlyPackage, mEngine.mAllowApks, mEngine.mEphemeralOpToken,
                        mEngine.mMonitor);
            }
        } finally {
            // Because mEngineStream adopted its underlying FD, this also
            // closes this end of the pipe.
            IoUtils.closeQuietly(mEngineStream);
        }
    }

    public void handleTimeout() {
        IoUtils.closeQuietly(mEngineStream);
        mEngine.handleTimeout();
    }
}
