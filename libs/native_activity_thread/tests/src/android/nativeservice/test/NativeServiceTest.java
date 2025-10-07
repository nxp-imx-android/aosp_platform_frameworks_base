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

package android.nativeservice.test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nativeservice.INativeServiceListener;
import android.nativeservice.INativeServiceWrapper;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE
})
public class NativeServiceTest {
    private static final String TARGET_PACKAGE = "android.nativeservice.test";
    private static final String NATIVE_SERVICE_CLASS =
            "android.nativeservice.test.NativeService";
    private static final long TIMEOUT_MS = 10000;
    private static final String TEST_ACTION = "TEST_ACTION";
    private static final Uri TEST_DATA = Uri.parse("content://com.example/people");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private INativeServiceWrapper mService;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static class GlueListener extends INativeServiceListener.Stub {
        private final INativeServiceListener mMock;

        GlueListener(INativeServiceListener mock) {
            mMock = mock;
        }

        @Override
        public void onRegister() throws RemoteException {
            mMock.onRegister();
        }

        @Override
        public void onUnbind() throws RemoteException {
            mMock.onUnbind();
        }

        @Override
        public void onRebind() throws RemoteException {
            mMock.onRebind();
        }
    }

    private ServiceConnection createConnection(INativeServiceListener mockListener) {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mService = INativeServiceWrapper.Stub.asInterface(service);
                try {
                    mService.registerListener(new GlueListener(mockListener));
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
    }

    @Test
    public void testLifeCycle() throws InterruptedException, RemoteException {
        Intent intent = new Intent(TEST_ACTION);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        intent.setData(TEST_DATA);

        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        ServiceConnection conn = createConnection(mockListener);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();
    }

    @Test
    public void testNoRebind() throws InterruptedException, RemoteException {
        Intent intent = new Intent(TEST_ACTION);
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        intent.setData(TEST_DATA);

        INativeServiceListener mockListener = mock(INativeServiceListener.class);
        ServiceConnection conn = createConnection(mockListener);

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();

        assertTrue(mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE));
        verify(mockListener, timeout(TIMEOUT_MS)).onRegister();
        verify(mockListener, never()).onRebind();
        mContext.unbindService(conn);
        verify(mockListener, timeout(TIMEOUT_MS)).onUnbind();
    }
}
