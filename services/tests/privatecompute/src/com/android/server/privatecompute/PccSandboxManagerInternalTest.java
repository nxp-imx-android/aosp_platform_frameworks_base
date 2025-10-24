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

package com.android.server.privatecompute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.privatecompute.IPccService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.ShellCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;

/**
 * Tests for {@link PccSandboxManagerServiceInternal}.
 */
@RunWith(AndroidJUnit4.class)
public class PccSandboxManagerInternalTest {

    private static final int USER_ID_1 = 1;
    private static final int USER_ID_2 = 2;
    private static final int NON_PCC_CLIENT_UID = 10001;
    private static final int PCC_CLIENT_UID = 30001;

    private PccSandboxManagerInternal mPccSandboxManagerInternal;
    private IBinder mRealBinder;
    private ComponentName mServiceName;
    private Intent mIntent;

    @Before
    public void setUp() throws Exception {
        mPccSandboxManagerInternal = new PccSandboxManagerInternal();
        mRealBinder = new IPccService.Stub() {

        };
        mServiceName = new ComponentName("com.example.test", "com.example.test.MyPccService");
        mIntent = new Intent().setComponent(mServiceName);
    }

    @Test
    public void createPccProxyIfNeeded_asRegularClient_returnsProxyBinder() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, NON_PCC_CLIENT_UID);

        assertNotEquals("Should return a proxy binder", mRealBinder, returnedBinder);
    }

    @Test
    public void createPccProxyIfNeeded_asPccClient_returnsDirectBinder() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, PCC_CLIENT_UID);

        assertEquals("Should return a direct binder", mRealBinder, returnedBinder);
    }

    @Test
    public void createPccProxyIfNeeded_asSystemService_returnsDirectBinder() {
        IBinder returnedBinder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0,
                mIntent, mRealBinder, Process.SYSTEM_UID);

        assertEquals("Should return a direct binder", mRealBinder, returnedBinder);
    }

    @Test
    public void multipleClientsBind_singleServiceInstance() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);

        assertEquals("Should only have one service connection info", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
        assertEquals("Should have two clients for the service", 2,
                mPccSandboxManagerInternal.mPccServiceConnections.get(
                        mRealBinder).getConnectionCount());
    }

    @Test
    public void allClientsUnbind_serviceDestroyed() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);
        PccSandboxManagerInternal.PccServiceInfo serviceInfo =
                mPccSandboxManagerInternal.mPccServiceConnections.get(mRealBinder);
        assertNotNull(serviceInfo);
        PccSandboxManagerInternal.PccServiceProxy proxy = serviceInfo.getWrappedBinder();

        mPccSandboxManagerInternal.removePccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);

        assertNull("realBinder should be null after destroy()", proxy.getRealBinder());
        assertEquals(0, mPccSandboxManagerInternal.mPccServiceConnections.size());
    }

    @Test
    public void removePccProxy_oneOfMultipleClients() {
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);

        // Unbind one client
        mPccSandboxManagerInternal.removePccProxyIfNeeded(mServiceName, 0, mIntent, mRealBinder,
                NON_PCC_CLIENT_UID);

        assertEquals("Should still have one service connection info", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
        assertEquals("Should have one client remaining", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.get(
                        mRealBinder).getConnectionCount());
    }

    @Test
    public void singleClientUnbinds_thenRebinds_newProxyCreated() {
        IBinder proxyBinder1 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID);
        mPccSandboxManagerInternal.removePccProxyIfNeeded(mServiceName, USER_ID_1, mIntent,
                mRealBinder, NON_PCC_CLIENT_UID);
        assertNotNull(proxyBinder1);

        IBinder proxyBinder2 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID);

        assertNotNull(proxyBinder2);
        assertNotEquals(proxyBinder1, proxyBinder2);
    }

    @Test
    public void multipleClients_oneUnbindsAndRebinds_sameProxyReturned() {
        IBinder proxyBinder1 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID);
        mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_2, mIntent,
                mRealBinder, NON_PCC_CLIENT_UID);
        assertNotNull(proxyBinder1);

        mPccSandboxManagerInternal.removePccProxyIfNeeded(mServiceName, USER_ID_1, mIntent,
                mRealBinder, NON_PCC_CLIENT_UID);
        IBinder proxyBinder2 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName,
                USER_ID_1, mIntent, mRealBinder, NON_PCC_CLIENT_UID);
        assertNotNull(proxyBinder2);

        assertEquals(proxyBinder1, proxyBinder2);
    }

    @Test
    public void bindWithInvalidServiceBinder_returnsNullBinder() {
        IBinder nullBinder = null;
        IBinder invalidBinder = createInvalidBinder();

        IBinder binder1 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_1,
                mIntent, nullBinder, NON_PCC_CLIENT_UID);
        IBinder binder2 = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_1,
                mIntent, invalidBinder, NON_PCC_CLIENT_UID);

        assertNull(binder1);
        assertNull(binder2);
    }

    @Test
    public void binderDied_removesConnectionAndDestroysProxy() throws Exception {
        IBinder binder = mPccSandboxManagerInternal.createPccProxyIfNeeded(mServiceName, USER_ID_1,
                mIntent, mRealBinder, NON_PCC_CLIENT_UID);
        assertEquals("Should have one active connection", 1,
                mPccSandboxManagerInternal.mPccServiceConnections.size());

        IBinder.DeathRecipient deathRecipient =
                mPccSandboxManagerInternal.mPccServiceConnections.get(
                        mRealBinder).getDeathRecipient();
        deathRecipient.binderDied();

        assertEquals("Connection should be removed after binder death", 0,
                mPccSandboxManagerInternal.mPccServiceConnections.size());
        assertNull("Proxy should be destroyed after binder death",
                ((PccSandboxManagerInternal.PccServiceProxy) binder).getRealBinder());
    }

    private IBinder createInvalidBinder() {
        return new IBinder() {
            @Override
            public String getInterfaceDescriptor() {
                return "";
            }

            @Override
            public boolean pingBinder() {
                return false;
            }

            @Override
            public boolean isBinderAlive() {
                return false;
            }

            @Nullable
            @Override
            public IInterface queryLocalInterface(@NonNull String descriptor) {
                return null;
            }

            @Override
            public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) {

            }

            @Override
            public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) {

            }

            @Override
            public void shellCommand(@Nullable FileDescriptor in, @Nullable FileDescriptor out,
                    @Nullable FileDescriptor err, @NonNull String[] args,
                    @Nullable ShellCallback shellCallback, @NonNull ResultReceiver resultReceiver) {

            }

            @Override
            public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                    int flags) {
                return false;
            }

            @Override
            public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {

            }

            @Override
            public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
                return false;
            }
        };
    }
}
