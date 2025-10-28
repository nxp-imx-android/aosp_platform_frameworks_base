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

package com.android.server.lskfreset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.lskfreset.ILskfResetManager;
import android.app.lskfreset.ILskfResetSession;
import android.app.lskfreset.flags.Flags;
import android.content.Context;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class LskfResetManagerServiceTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final UserHandle TEST_USER_0 = UserHandle.getUserHandleForUid(1000);
    private static final UserHandle TEST_USER_1 = UserHandle.getUserHandleForUid(1001);

    private Context mContext;
    private LskfResetManagerService mService;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mService = new LskfResetManagerService(mContext);
        mService.onStart();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testServiceStarts() {
        ILskfResetManager manager = mService.getBinderService();
        assertNotNull(manager);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCreateSession() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);
        assertNotNull(session);
        assertTrue(mService.isSessionActive(session));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCloseSession() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session0 = manager.createLskfResetSession(TEST_USER_0);
        ILskfResetSession session1 = manager.createLskfResetSession(TEST_USER_1);
        assertTrue(mService.isSessionActive(session0));
        assertTrue(mService.isSessionActive(session1));
        session0.close();
        assertFalse(mService.isSessionActive(session0));
        assertTrue(mService.isSessionActive(session1));
        session1.close();
        assertFalse(mService.isSessionActive(session0));
        assertFalse(mService.isSessionActive(session1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testDoubleCloseSessionFails() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);
        assertTrue(mService.isSessionActive(session));
        session.close();
        assertFalse(mService.isSessionActive(session));
        assertThrows(IllegalStateException.class, () -> session.close());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCreateParallelSessions() throws Exception {
        ILskfResetManager manager = mService.getBinderService();

        final int numThreads = 10;
        CountDownLatch createLatch = new CountDownLatch(numThreads);
        CountDownLatch closeLatch = new CountDownLatch(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    ILskfResetSession session;
                                    try {
                                        session = manager.createLskfResetSession(TEST_USER_0);
                                        assertTrue(mService.isSessionActive(session));
                                    } finally {
                                        createLatch.countDown();
                                    }
                                    createLatch.await();
                                    session.close();
                                    assertFalse(mService.isSessionActive(session));
                                } catch (Exception e) {
                                    fail("Unexpected exception: " + e.getMessage());
                                } finally {
                                    closeLatch.countDown();
                                }
                            });
            thread.start();
        }
        closeLatch.await();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCloseParallelSessions() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(TEST_USER_0);

        final int numThreads = 10;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch closeLatch = new CountDownLatch(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    session.close();
                                    successCount.incrementAndGet();
                                } catch (IllegalStateException unused) {
                                    failureCount.incrementAndGet();
                                } catch (Exception e) {
                                    fail("Unexpected exception: " + e.getMessage());
                                } finally {
                                    closeLatch.countDown();
                                }
                            });
            thread.start();
        }
        closeLatch.await();
        assertEquals(successCount.get(), 1);
        assertEquals(failureCount.get(), numThreads - 1);
    }
}
