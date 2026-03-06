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

package com.android.server.autofill;

import static android.service.personalcontext.Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.slice.Slice;
import android.app.slice.SliceSpec;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.autofill.Dataset;
import android.service.autofill.Field;
import android.service.autofill.InlinePresentation;
import android.service.autofill.InlineSuggestionRenderService;
import android.service.autofill.augmented.IAugmentedAutofillService;
import android.service.autofill.augmented.IFillCallback;
import android.service.personalcontext.hint.AutofillInlineRequestHint;
import android.service.personalcontext.hint.ContextHint;
import android.testing.TestableContext;
import android.util.Size;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.autofill.IAutoFillManagerClient;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.infra.ServiceConnector;
import com.android.internal.infra.ServiceConnector.Job;
import com.android.internal.os.IResultReceiver;
import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.autofill.RemoteAugmentedAutofillService.RemoteAugmentedAutofillServiceCallbacks;
import com.android.server.autofill.ui.InlineFillUi;
import com.android.server.personalcontext.PersonalContextManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RemoteAugmentedAutofillServiceTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    @Rule
    public final TestableContext mContext =
            spy(new TestableContext(getInstrumentation().getContext()));

    private static final int USER_ID = 0;
    private static final int REQUEST_TIMEOUT_MS = 2000;

    private static final InlinePresentationSpec AUTOFILL_INLINE_PRESENTATION_SPEC =
            new InlinePresentationSpec.Builder(new Size(100, 100), new Size(100, 100)).build();
    private static final InlinePresentationSpec PERSONAL_CONTEXT_INLINE_PRESENTATION_SPEC =
            new InlinePresentationSpec.Builder(new Size(200, 200), new Size(200, 200)).build();
    private static final ComponentName SERVICE_COMPONENT_NAME =
            new ComponentName("test_service_package", "test_service_class");
    private static final ComponentName ACTIVITY_COMPONENT_NAME =
            new ComponentName("test_activity_package", "test_activity_class");
    private static final AutofillValue AUTOFILL_VALUE = AutofillValue.forText("test_value");

    private final Function<InlineFillUi, Boolean> mInlineSuggestionsCallback =
            inlineFillUi -> {
                mInlineFillUiResult = inlineFillUi;
                return true;
            };

    @Mock private PersonalContextManagerInternal mContextManagerInternal;
    @Mock private RemoteAugmentedAutofillServiceCallbacks mAutofillServiceCallbacks;

    @Mock
    private RemoteInlineSuggestionRenderService.InlineSuggestionRenderCallbacks
            mInlineSuggestionRenderCallbacks;

    @Mock private IAutoFillManagerClient mClient;
    @Mock private IBinder mActivityToken;
    @Mock private IAugmentedAutofillService mAugmentedService;
    @Mock private ServiceConnector.Impl<IAugmentedAutofillService> mServiceConnector;

    private InlineFillUi mInlineFillUiResult;
    private AutoCloseable mMockitoSession;
    private RemoteInlineSuggestionRenderService mRemoteInlineSuggestionRenderService;
    private RemoteAugmentedAutofillService mService;
    private FakeExecutor mTestExecutor;

    /**
     * Captured futures from {@link
     * RemoteAugmentedAutofillService.Injector#orTimeout(CompletableFuture, long, TimeUnit)}.
     */
    private List<CompletableFuture> mAutofillResponseFutures;

    @SuppressLint("VisibleForTests")
    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mTestExecutor = new FakeExecutor();

        // Immediately run any jobs posted to the service connector.
        when(mServiceConnector.run(any()))
                .thenAnswer(
                        invocation -> {
                            final Job job = (Job) invocation.getArguments()[0];
                            job.run(mAugmentedService);
                            return null;
                        });

        mLocalServiceKeeperRule.overrideLocalService(
                PersonalContextManagerInternal.class, mContextManagerInternal);

        mRemoteInlineSuggestionRenderService =
                new RemoteInlineSuggestionRenderService(
                        mContext,
                        SERVICE_COMPONENT_NAME,
                        InlineSuggestionRenderService.SERVICE_INTERFACE,
                        USER_ID,
                        mInlineSuggestionRenderCallbacks,
                        false,
                        false);

        mAutofillResponseFutures = new ArrayList<>();
        mService =
                new RemoteAugmentedAutofillService(
                        new RemoteAugmentedAutofillService.Injector() {
                            @Override
                            public Executor getExecutor() {
                                return mTestExecutor;
                            }

                            @Override
                            public ServiceConnector.Impl<IAugmentedAutofillService>
                                    getServiceConnector() {
                                return mServiceConnector;
                            }

                            @Override
                            public <T> CompletableFuture<T> orTimeout(
                                    CompletableFuture<T> future, long timeout, TimeUnit unit) {
                                mAutofillResponseFutures.add(future);
                                return future;
                            }
                        },
                        0, // serviceUid
                        SERVICE_COMPONENT_NAME,
                        USER_ID, // userId
                        mAutofillServiceCallbacks,
                        REQUEST_TIMEOUT_MS);
    }

    @After
    public void tearDown() throws Exception {
        if (mMockitoSession != null) {
            mMockitoSession.close();
            mMockitoSession = null;
        }
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_bothResponses_choosesPersonalContextResult()
            throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE,
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService,
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Both augmented autofill and personal context provide a response.
        sendAutofillResponse(focusedId, fillCallback, false);
        sendPersonalContextResponse(sessionId, focusedId);

        // Verify inline suggestions are applied.
        assertInlinePresentationResult(PERSONAL_CONTEXT_INLINE_PRESENTATION_SPEC);
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_onlyAutofillResponse_choosesAutofillResult()
            throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE,
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService,
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Augmented autofill provides a response, personal context does not.
        sendAutofillResponse(focusedId, fillCallback, false);

        timeoutFutures();

        // Verify inline suggestions are applied.
        assertInlinePresentationResult(AUTOFILL_INLINE_PRESENTATION_SPEC);
    }

    @DisableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_personalContextDisabled_choosesAutofillResult()
            throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE,
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Augmented autofill provides a response.
        sendAutofillResponse(focusedId, fillCallback, false);

        // Send personal context response, but since flag is disabled, it will be a no-op.
        sendPersonalContextResponse(sessionId, focusedId);

        // Verify inline suggestions are applied.
        assertInlinePresentationResult(AUTOFILL_INLINE_PRESENTATION_SPEC);
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_fillWindowShowing_noResult() throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE,
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService,
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Both augmented autofill and personal context provide a response. The fill window is
        // showing, meaning that we should not provide a result.
        sendAutofillResponse(focusedId, fillCallback, /* showingFillWindow= */ true);
        sendPersonalContextResponse(sessionId, focusedId);

        // Not all futures will have finished. When the fill window is showing, one of the futures
        // remain running so that cancellation can still occur.
        assertThat(mAutofillResponseFutures.stream().allMatch(CompletableFuture::isDone)).isFalse();
        // No result is provided.
        assertThat(mInlineFillUiResult).isNull();
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_emptyAutofillResponse_choosesPersonalContextResult()
            throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE, // focusedValue
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Augmented autofill provides an empty response.
        sendEmptyAutofillResponse(fillCallback);

        // Personal context provides a valid response.
        sendPersonalContextResponse(sessionId, focusedId);

        // Verify inline suggestions are applied.
        assertInlinePresentationResult(PERSONAL_CONTEXT_INLINE_PRESENTATION_SPEC);
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_cancelledAutofillResponse_noResult() throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE, // focusedValue
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Augmented autofill cancels the request.
        fillCallback.cancel();

        // Personal context provides a valid response.
        sendPersonalContextResponse(sessionId, focusedId);

        // No result is provided since the augmented autofill request was cancelled.
        assertThat(mInlineFillUiResult).isNull();
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_noInlineSuggestionsRequest_noResult() throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill without an InlineSuggestionsRequest specified.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE, // focusedValue
                null, // inlineSuggestionsRequest
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Augmented autofill provides a valid response.
        sendAutofillResponse(focusedId, fillCallback, /* showingFillWindow= */ false);

        // Personal context request is not sent at all.
        verifyNoInteractions(mContextManagerInternal);

        // Since there is no inlineSuggestionsRequest, no response is sent.
        assertThat(mInlineFillUiResult).isNull();
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_noPersonalContextResponse_emptyUi() throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE, // focusedValue
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        // Augmented autofill service receives fill request.
        IFillCallback fillCallback =
                triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);

        // Augmented autofill provides an empty response.
        sendEmptyAutofillResponse(fillCallback);

        // No personal context result, time out futures.
        timeoutFutures();

        // Since at least one response was returned, an empty UI is the result.
        assertThat(mInlineFillUiResult.getInlineSuggestionsResponse().getInlineSuggestions())
                .isEmpty();
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_neitherResponseReturns_noResult() throws Exception {
        final int sessionId = 1234;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                4567, // taskId
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE, // focusedValue
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        // Augmented autofill service receives fill request.
        triggerAugmentedAutofillRequest(sessionId, ACTIVITY_COMPONENT_NAME, focusedId);
        mTestExecutor.runAll();

        // Neither result is sent, time out futures.
        timeoutFutures();
        mTestExecutor.runAll();

        // Verify no result is provided.
        assertThat(mInlineFillUiResult).isNull();
    }

    @EnableFlags(FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    @Test
    public void onRequestAutofillLocked_augmentedAutofillBinderMatchesHint() throws Exception {
        final int sessionId = 1234;
        final int taskId = 4567;
        AutofillId focusedId = new AutofillId(3);
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(AUTOFILL_INLINE_PRESENTATION_SPEC))
                        .build();
        // Request augmented autofill.
        mService.onRequestAutofillLocked(
                sessionId,
                mClient,
                taskId,
                ACTIVITY_COMPONENT_NAME,
                mActivityToken,
                focusedId,
                AUTOFILL_VALUE, // focusedValue
                inlineSuggestionsRequest,
                mInlineSuggestionsCallback,
                () -> {}, // onErrorCallback
                mRemoteInlineSuggestionRenderService, // render service?
                USER_ID);

        ArgumentCaptor<IResultReceiver.Stub> resultReceiverCaptor =
                ArgumentCaptor.forClass(IResultReceiver.Stub.class);
        verify(mClient).getAugmentedAutofillClient(resultReceiverCaptor.capture());

        Bundle resultData = new Bundle();
        Binder augmentedAutofillBinder = new Binder();
        resultData.putBinder(
                AutofillManager.EXTRA_AUGMENTED_AUTOFILL_CLIENT, augmentedAutofillBinder);
        resultReceiverCaptor.getValue().send(0, resultData);

        mTestExecutor.runAll();

        // Triggering hint is sent to context engine.
        ArgumentCaptor<Set<ContextHint>> hintCaptor = ArgumentCaptor.forClass(Set.class);
        verify(mContextManagerInternal)
                .publishTriggeringHint(hintCaptor.capture(), any(), eq(USER_ID));
        assertThat(hintCaptor.getValue()).hasSize(1);

        // The binder inside the autofill hint matches the one provided by the service.
        ContextHint triggeringHint = hintCaptor.getValue().stream().findFirst().get();
        assertThat(triggeringHint).isInstanceOf(AutofillInlineRequestHint.class);
        AutofillInlineRequestHint autofillHint = (AutofillInlineRequestHint) triggeringHint;
        assertThat(autofillHint.getAugmentedAutofillProxy().asBinder())
                .isEqualTo(augmentedAutofillBinder);

        // Verify other data matches the request to onRequestAutofillLocked.
        assertThat(autofillHint.getSessionId()).isEqualTo(sessionId);
        assertThat(autofillHint.getTaskId()).isEqualTo(taskId);
        assertThat(autofillHint.getActivityComponent()).isEqualTo(ACTIVITY_COMPONENT_NAME);
        assertThat(autofillHint.getFocusedId()).isEqualTo(focusedId);
        assertThat(autofillHint.getAutofillValue()).isEqualTo(AUTOFILL_VALUE);
        assertThat(autofillHint.getInlineSuggestionsRequest()).isEqualTo(inlineSuggestionsRequest);
    }

    private IFillCallback triggerAugmentedAutofillRequest(
            int sessionId, ComponentName activityComponent, AutofillId focusedId)
            throws RemoteException {
        ArgumentCaptor<IResultReceiver.Stub> resultReceiverCaptor =
                ArgumentCaptor.forClass(IResultReceiver.Stub.class);
        verify(mClient).getAugmentedAutofillClient(resultReceiverCaptor.capture());

        Bundle resultData = new Bundle();
        resultData.putBinder(AutofillManager.EXTRA_AUGMENTED_AUTOFILL_CLIENT, new Binder());
        resultReceiverCaptor.getValue().send(0, resultData);

        mTestExecutor.runAll();

        ArgumentCaptor<IFillCallback> callbackCaptor = ArgumentCaptor.forClass(IFillCallback.class);
        verify(mAugmentedService)
                .onFillRequest(
                        eq(sessionId),
                        any(),
                        anyInt(),
                        eq(activityComponent),
                        eq(focusedId),
                        any(),
                        anyLong(),
                        any(),
                        callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    private void sendAutofillResponse(
            AutofillId focusedId, IFillCallback callback, boolean showingFillWindow)
            throws RemoteException {
        Slice slice = new Slice.Builder(Uri.parse("test_uri"), new SliceSpec("type", 1)).build();
        InlinePresentation presentation =
                new InlinePresentation(slice, AUTOFILL_INLINE_PRESENTATION_SPEC, false);
        List<Dataset> datasets = new ArrayList<>();
        datasets.add(
                new Dataset.Builder(presentation)
                        .setField(focusedId, new Field.Builder().setValue(AUTOFILL_VALUE).build())
                        .build());
        callback.onSuccess(datasets, null, showingFillWindow);
        mTestExecutor.runAll();
    }

    private void sendEmptyAutofillResponse(IFillCallback callback) throws RemoteException {
        callback.onSuccess(null, null, false);
        mTestExecutor.runAll();
    }

    private void sendPersonalContextResponse(int sessionId, AutofillId focusedId) {
        Slice slice = new Slice.Builder(Uri.parse("test_uri"), new SliceSpec("type", 1)).build();
        InlinePresentation presentation2 =
                new InlinePresentation(slice, PERSONAL_CONTEXT_INLINE_PRESENTATION_SPEC, false);
        List<Dataset> datasets2 = new ArrayList<>();
        datasets2.add(
                new Dataset.Builder(presentation2)
                        .setField(focusedId, new Field.Builder().setValue(AUTOFILL_VALUE).build())
                        .build());
        mService.notifySystemInlineSuggestions(sessionId, datasets2);
        mTestExecutor.runAll();
    }

    private void timeoutFutures() {
        boolean timedOutAny = false;
        for (CompletableFuture<?> future : mAutofillResponseFutures) {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("timing out for test"));
                timedOutAny = true;
            }
        }
        if (!timedOutAny) {
            throw new IllegalStateException("no futures to timeout");
        }
        mTestExecutor.runAll();
    }

    /**
     * Asserts that all futures that were scheduled to time out are finished. Useful when a test
     * wants to assert that no result is provided even though all the responses were provided.
     */
    private void assertFuturesDone() {
        assertThat(mAutofillResponseFutures.stream().allMatch(CompletableFuture::isDone)).isTrue();
    }

    private void assertInlinePresentationResult(InlinePresentationSpec expected) {
        assertThat(mInlineFillUiResult).isNotNull();
        InlinePresentationSpec resultSpec =
                mInlineFillUiResult
                        .getInlineSuggestionsResponse()
                        .getInlineSuggestions()
                        .getFirst()
                        .getInfo()
                        .getInlinePresentationSpec();
        assertThat(resultSpec).isEqualTo(expected);
    }

    /** Helper class for using {@link Executor} in tests. */
    public static class FakeExecutor implements Executor {
        private final Queue<Runnable> mQueue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            mQueue.add(command);
        }

        /** Runs all pending {@link Runnable}s */
        public void runAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove().run();
            }
        }

        /** Removes all queued {@link Runnable}s */
        public void clearAll() {
            while (!mQueue.isEmpty()) {
                mQueue.remove();
            }
        }
    }
}
