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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWithSignatureWrapper;
import android.service.personalcontext.refiner.IRefiner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ServiceClientRefinerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String TEST_PACKAGE_NAME = "com.test.package";

    @Mock private Context mContext;
    @Mock private UserHandle mUserHandle;
    @Mock private IRefiner mIRefiner;
    @Mock private PermissionManager mPermissionManager;
    @Mock private Handler mHandler;

    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    private ServiceClientRefiner mServiceClientRefiner;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(PermissionManager.class)).thenReturn(mPermissionManager);

        final ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = TEST_PACKAGE_NAME;
        serviceInfo.name = "baz";
        mServiceClientRefiner =
                new ServiceClientRefiner(
                        mContext,
                        UUID.randomUUID(),
                        serviceInfo,
                        mUserHandle,
                        mFakeExecutor,
                        mHandler);
        mFakeExecutor.runAll();
    }

    @Test
    public void testRefine() throws Exception {
        BundleHint bundleHint = new BundleHint.Builder().build();
        ContextHintWithSignature hint =
                new ContextHintWithSignature.Builder(
                                bundleHint, ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Set<ContextHintWithSignature> signedHints = Set.of(hint);

        // Submit hints to refine.
        mServiceClientRefiner.refine(signedHints, (hints) -> {});
        mFakeExecutor.runAll();
        mServiceClientRefiner.onStarted(mIRefiner);
        mFakeExecutor.runAll();

        // Hints are sent to the refiner service.
        ArgumentCaptor<List<ContextHintWithSignatureWrapper>> hintCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mIRefiner).refine(any(), hintCaptor.capture(), any(), any());
        assertThat(hintCaptor.getValue().getFirst().getContextHintWithSignature()).isEqualTo(hint);
    }

    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testRefine_noPermissions_failsImmediately() throws Exception {
        when(mPermissionManager.checkPackageNamePermission(
                        eq(Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS),
                        eq(TEST_PACKAGE_NAME),
                        anyInt(),
                        anyInt()))
                .thenReturn(PermissionManager.PERMISSION_HARD_DENIED);

        BundleHint bundleHint = new BundleHint.Builder().build();
        ContextHintWithSignature hint =
                new ContextHintWithSignature.Builder(
                                bundleHint, ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Set<ContextHintWithSignature> signedHints = Set.of(hint);

        // Submit hints to refine
        AtomicReference<Set<ContextHint>> refinedHints = new AtomicReference<>();
        mServiceClientRefiner.refine(signedHints, (hints) -> refinedHints.set(hints));
        mFakeExecutor.runAll();
        mServiceClientRefiner.onStarted(mIRefiner);
        mFakeExecutor.runAll();

        // Hints are not sent to refiner, and an empty set is returned immediately in the callback.
        verify(mIRefiner, never()).refine(any(), any(), any(), any());
        assertThat(refinedHints.get()).isEmpty();
    }

    private static final class FakeExecutor implements Executor {
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
