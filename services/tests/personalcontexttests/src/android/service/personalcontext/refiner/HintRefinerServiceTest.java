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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.RemoteException;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HintRefinerServiceTest {
    private UUID mComponentId;
    private HintRefinerService mService;
    private IRefiner mBinder;

    @Before
    public void setup() throws RemoteException {
        mComponentId = UUID.randomUUID();
        mService = mock(HintRefinerService.class, Answers.CALLS_REAL_METHODS);
        mBinder = (IRefiner) mService.onBind(null);
    }

    @Test
    public void testComponentId() {
        // Cannot access component id before other calls have occurred.
        assertThrows(Exception.class, () -> mService.getComponentId());
    }

    @Test
    public void testOnRefineList() throws RemoteException {
        final List<ContextHint> inputHints =
                Arrays.asList(new BundleHint.Builder().build(), new BundleHint.Builder().build());
        final List<ContextHint> outputHints =
                Arrays.asList(new BundleHint.Builder().build(), new BundleHint.Builder().build());

        doAnswer(invocation -> {
            assertThat(inputHints).containsExactlyElementsIn((List<?>) invocation.getArgument(0));

            Consumer<List<ContextHint>> callback = invocation.getArgument(1);
            callback.accept(outputHints);

            return null;
        }).when(mService).onRefine(any(), any());

        final Consumer<List<ContextHint>> callback = mock(Consumer.class);
        mService.onRefine(inputHints, callback);

        verify(callback).accept(eq(outputHints));
    }

    @Test
    public void testOnRefineEmptyLists() throws RemoteException {
        final List<ContextHint> inputHints = Arrays.asList();
        final List<ContextHint> outputHints = Arrays.asList();

        doAnswer(invocation -> {
            assertThat(inputHints).containsExactlyElementsIn((List<?>) invocation.getArgument(0));

            Consumer<List<ContextHint>> callback = invocation.getArgument(1);
            callback.accept(outputHints);

            return null;
        }).when(mService).onRefine(any(), any());

        final Consumer<List<ContextHint>> callback = mock(Consumer.class);
        mService.onRefine(inputHints, callback);

        verify(callback).accept(eq(outputHints));
    }

    @Test
    public void testOnRefineNull() throws RemoteException {
        final List<ContextHint> inputHints = Arrays.asList();

        doAnswer(invocation -> {
            assertThat(inputHints).containsExactlyElementsIn((List<?>) invocation.getArgument(0));

            Consumer<List<ContextHint>> callback = invocation.getArgument(1);
            callback.accept(null);

            return null;
        }).when(mService).onRefine(any(), any());

        final Consumer<List<ContextHint>> callback = mock(Consumer.class);
        mService.onRefine(inputHints, callback);

        verify(callback).accept(isNull());
    }
}
