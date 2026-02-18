/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.understander;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWithSignatureWrapper;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextUnderstanderServiceTest {
    private UUID mComponentId;
    private ContextUnderstanderService mService;
    private IRefiner mBinder;

    @Before
    public void setup() throws RemoteException {
        mComponentId = UUID.randomUUID();
        mService = mock(ContextUnderstanderService.class, Answers.CALLS_REAL_METHODS);
        mBinder = (IRefiner) mService.onBind(null);
    }

    @Test
    public void testOnUnderstandList() throws RemoteException, GeneralSecurityException {
        final ContextHintWithSignature hint1 =
                new ContextHintWithSignature.Builder(
                        new BundleHint.Builder().build(),
                        ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final ContextHintWithSignature hint2 =
                new ContextHintWithSignature.Builder(
                        new BundleHint.Builder().build(),
                        ContextHintTestUtils.generateSignedHintKey())
                        .build();

        final List<ContextHintWithSignature> hints = Arrays.asList(hint1, hint2);
        IRefineCallback callback = mock(IRefineCallback.Stub.class);

        final IOpCallback opCallback = mock(IOpCallback.Stub.class);

        mBinder.refine(new ParcelUuid(mComponentId),
                ContextHintWithSignatureWrapper.wrapList(hints), callback, opCallback);

        ArgumentCaptor<List> hintCaptor = ArgumentCaptor.forClass(List.class);
        verify(mService).onUnderstand(hintCaptor.capture());
        verify(opCallback).signalCompletion();

        assertThat(hintCaptor.getValue()).containsExactlyElementsIn(hints);
    }
}
