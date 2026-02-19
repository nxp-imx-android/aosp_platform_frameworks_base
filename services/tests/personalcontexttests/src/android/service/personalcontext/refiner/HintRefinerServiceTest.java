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

package android.service.personalcontext.refiner;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.ParcelUuid;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWithSignatureWrapper;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.testutil.FakeExecutor;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HintRefinerServiceTest {
    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    @Test
    public void testComponentId() throws Exception {
        final UUID componentId = UUID.randomUUID();

        final HintRefinerService service = new HintRefinerService() {
            @NonNull
            @Override
            public HintFilter onInitializeFilter() {
                return null;
            }

            @Override
            public void onRefine(@NonNull List<ContextHint> inputHints,
                    @NonNull Consumer<List<ContextHint>> callback) {
                assertThat(getComponentId()).isEqualTo(componentId);
            }
        };

        service.setExecutor(mFakeExecutor);
        final IRefiner refiner = (IRefiner) service.onBind(null);

        final IRefineCallback refineCallback = mock(IRefineCallback.class);
        final IOpCallback callback = mock(IOpCallback.class);
        refiner.refine(new ParcelUuid(componentId), new ArrayList<>(), refineCallback,
                callback);
        mFakeExecutor.runAll();
        verify(callback).signalCompletion();
    }

    private void verifyCallbackFromInput(List<ContextHint> inHints, List<ContextHint> outputHints)
            throws Exception {
        ArrayList<ContextHintWithSignature> signedHints = new ArrayList<>();

        for (ContextHint inHint : inHints) {
            final ContextHintWithSignature contextHintWithSignature = mock(
                    ContextHintWithSignature.class);
            when(contextHintWithSignature.getContextHint()).thenReturn(inHint);
            signedHints.add(contextHintWithSignature);
        }

        final HintRefinerService service = new HintRefinerService() {
            @NonNull
            @Override
            public HintFilter onInitializeFilter() {
                return null;
            }

            @Override
            public void onRefine(@NonNull List<ContextHint> inputHints,
                    @NonNull Consumer<List<ContextHint>> callback) {
                assertThat(inputHints).containsExactlyElementsIn(inHints);
                callback.accept(outputHints);
            }
        };

        service.setExecutor(mFakeExecutor);
        final IRefiner refiner = (IRefiner) service.onBind(null);

        final IRefineCallback refineCallback = mock(IRefineCallback.class);
        final IOpCallback callback = mock(IOpCallback.class);
        refiner.refine(new ParcelUuid(UUID.randomUUID()),
                ContextHintWithSignatureWrapper.wrapList(signedHints),
                refineCallback, callback);
        mFakeExecutor.runAll();
        verify(callback).signalCompletion();
        ArgumentCaptor<List<ContextHintWrapper>> outputCaptor = ArgumentCaptor.forClass(List.class);
        verify(refineCallback).onHintsRefined(outputCaptor.capture());

        assertThat(ContextHintWrapper.unwrapList(
                outputCaptor.getValue())).containsExactlyElementsIn(
                outputHints != null ? outputHints : new ArrayList());
    }

    @Test
    public void testOnRefineList() throws Exception {
        verifyCallbackFromInput(List.of(mock(ContextHint.class)),
                List.of(mock(ContextHint.class), mock(ContextHint.class)));
    }

    @Test
    public void testOnRefineEmptyLists() throws Exception {
        verifyCallbackFromInput(new ArrayList<>(), new ArrayList<>());
    }

    @Test
    public void testOnRefineNull() throws Exception {
        verifyCallbackFromInput(new ArrayList<>(), null);
    }
}
