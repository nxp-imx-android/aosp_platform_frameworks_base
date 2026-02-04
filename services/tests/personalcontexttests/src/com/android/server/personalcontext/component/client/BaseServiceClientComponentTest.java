/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.personalcontext.component.client;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BaseServiceClientComponentTest {

    @Mock
    private Context mContext;
    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    @Mock
    private UserHandle mUserHandle;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBindOnRequest() {
        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = "com.foo.bar";
        serviceInfo.name = "baz";
        BaseServiceClientComponent<IBinder> component =
                new BaseServiceClientComponent<>(mContext, UUID.randomUUID(), serviceInfo,
                        mUserHandle, mFakeExecutor) {
                    @Override
                    protected IBinder getServiceWrapper(IBinder binder) {
                        return null;
                    }

                    @Override
                    protected void initializeClient(IBinder client) {

                    }
                };

        component.start();
        mFakeExecutor.runAll();
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).bindServiceAsUser(intentArgumentCaptor.capture(), any(), anyInt(),
                eq(mUserHandle));
        assertThat(intentArgumentCaptor.getValue().getComponent())
                .isEqualTo(serviceInfo.getComponentName());
    }

    private static class FakeExecutor implements Executor {
        private final Queue<Runnable> mQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            mQueue.add(command);
        }

        public void runAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove().run();
            }
        }

        public void clearAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove();
            }
        }
    }
}
