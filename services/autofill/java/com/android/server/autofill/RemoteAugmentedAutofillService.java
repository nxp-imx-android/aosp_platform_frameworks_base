/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.service.autofill.augmented.Helper.logResponse;
import static android.service.personalcontext.Flags.enablePersonalContextService;

import static com.android.server.autofill.Helper.sDebug;
import static com.android.server.autofill.Helper.sVerbose;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppGlobals;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.RemoteException;
import android.os.SystemClock;
import android.service.autofill.Dataset;
import android.service.autofill.augmented.AugmentedAutofillService;
import android.service.autofill.augmented.IAugmentedAutofillService;
import android.service.autofill.augmented.IFillCallback;
import android.service.personalcontext.hint.AutofillInlineRequestHint;
import android.util.Pair;
import android.util.Slog;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.view.autofill.IAutoFillManagerClient;
import android.view.inputmethod.InlineSuggestionsRequest;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AbstractRemoteService;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.os.IResultReceiver;
import com.android.server.LocalServices;
import com.android.server.autofill.ui.InlineFillUi;
import com.android.server.personalcontext.PersonalContextManagerInternal;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class RemoteAugmentedAutofillService {

    private static final String TAG = RemoteAugmentedAutofillService.class.getSimpleName();

    private final Executor mExecutor;
    private final int mUserId;
    private final ServiceConnector.Impl<IAugmentedAutofillService> mServiceConnector;
    private final int mRequestTimeoutMs;
    private final ComponentName mComponentName;
    private final RemoteAugmentedAutofillServiceCallbacks mCallbacks;
    private final AutofillUriGrantsManager mUriGrantsManager;
    private final PersonalContextManagerInternal mPersonalContextManagerInternal;
    private final Injector mInjector;

    private CompletableFuture<AugmentedAutofillInlineSuggestionsResponseData>
            mPersonalContextResultFuture;
    private AutofillInlineRequestHint mAutofillHint;

    RemoteAugmentedAutofillService(Context context, int serviceUid, ComponentName serviceName,
            int userId, RemoteAugmentedAutofillServiceCallbacks callbacks,
            boolean bindInstantServiceAllowed, boolean verbose, int idleUnbindTimeoutMs,
            int requestTimeoutMs) {
        this(
                new DefaultInjector(
                        context,
                        serviceName,
                        userId,
                        bindInstantServiceAllowed, idleUnbindTimeoutMs),
                serviceUid,
                serviceName,
                userId,
                callbacks,
                requestTimeoutMs);
    }

    @VisibleForTesting
    RemoteAugmentedAutofillService(Injector injector, int serviceUid, ComponentName serviceName,
            int userId, RemoteAugmentedAutofillServiceCallbacks callbacks, int requestTimeoutMs) {
        mInjector = injector;
        mRequestTimeoutMs = requestTimeoutMs;
        mComponentName = serviceName;
        mCallbacks = callbacks;
        mUriGrantsManager = new AutofillUriGrantsManager(serviceUid);
        mPersonalContextManagerInternal = LocalServices.getService(
                PersonalContextManagerInternal.class);

        mExecutor = injector.getExecutor();
        mServiceConnector = injector.getServiceConnector();
        mUserId = userId;

        // Bind right away.
        mServiceConnector.connect();
    }

    @Nullable
    static Pair<ServiceInfo, ComponentName> getComponentName(@NonNull String componentName,
            @UserIdInt int userId, boolean isTemporary) {
        int flags = PackageManager.GET_META_DATA;
        if (!isTemporary) {
            flags |= PackageManager.MATCH_SYSTEM_ONLY;
        }

        final ComponentName serviceComponent;
        ServiceInfo serviceInfo = null;
        try {
            serviceComponent = ComponentName.unflattenFromString(componentName);
            serviceInfo = AppGlobals.getPackageManager().getServiceInfo(serviceComponent, flags,
                    userId);
            if (serviceInfo == null) {
                Slog.e(TAG, "Bad service name for flags " + flags + ": " + componentName);
                return null;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error getting service info for '" + componentName + "': " + e);
            return null;
        }
        return new Pair<>(serviceInfo, serviceComponent);
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public AutofillUriGrantsManager getAutofillUriGrantsManager() {
        return mUriGrantsManager;
    }

    public void unbind() {
        mServiceConnector.unbind();
    }

    /**
     * Called by {@link Session} to request augmented autofill and personal context suggestions.
     */
    public void onRequestAutofillLocked(int sessionId, @NonNull IAutoFillManagerClient client,
            int taskId, @NonNull ComponentName activityComponent, @NonNull IBinder activityToken,
            @NonNull AutofillId focusedId, @Nullable AutofillValue focusedValue,
            @Nullable InlineSuggestionsRequest inlineSuggestionsRequest,
            @Nullable Function<InlineFillUi, Boolean> inlineSuggestionsCallback,
            @NonNull Runnable onErrorCallback,
            @Nullable RemoteInlineSuggestionRenderService remoteRenderService, int userId) {
        getAugmentedAutofillClient(client)
                .thenComposeAsync(
                        augmentedAutofillClient -> {
                            CompletableFuture<AugmentedAutofillInlineSuggestionsResponseData>
                                    personalContextFuture =
                                            sendRequestToPersonalContext(
                                                    sessionId,
                                                    taskId,
                                                    activityComponent,
                                                    focusedId,
                                                    augmentedAutofillClient,
                                                    focusedValue,
                                                    inlineSuggestionsRequest);

                            CompletableFuture<AugmentedAutofillInlineSuggestionsResponseData>
                                    augmentedAutofillInlineFuture =
                                            sendRequestToAugmentedAutofillService(
                                                    sessionId,
                                                    taskId,
                                                    activityComponent,
                                                    focusedId,
                                                    augmentedAutofillClient,
                                                    focusedValue,
                                                    inlineSuggestionsRequest,
                                                    SystemClock.elapsedRealtime());

                            // TODO(b/456768621): don't need to wait for personal context depending
                            //  on config value
                            return augmentedAutofillInlineFuture.thenCombine(
                                    personalContextFuture,
                                    (augmentedAutofillResponse, personalContextResponse) -> {
                                        if (augmentedAutofillResponse == null) {
                                            // If augmented autofill didn't return a response, it
                                            // may have timed out, been cancelled, or a fill window
                                            // was active. Don't bother showing inline suggestions.
                                            return null;
                                        }
                                        // TODO(b/478044353): allow choosing priority for which
                                        //  future to look at the result of first.
                                        if (personalContextResponse != null) {
                                            if (sDebug) {
                                                Slog.d(
                                                        TAG,
                                                        "Choosing personal context" + " response");
                                            }
                                            return personalContextResponse;
                                        } else {
                                            if (sDebug) {
                                                Slog.d(
                                                        TAG,
                                                        "Choosing augmented autofill"
                                                                + " response");
                                            }
                                            return augmentedAutofillResponse;
                                        }
                                    });
                        },
                        mExecutor)
                .thenAcceptAsync(
                        result -> {
                            if (result == null) {
                                Slog.e(
                                        TAG,
                                        "No augmented autofill result from either augmented "
                                                + "autofill service or personal context");
                                return;
                            }
                            maybeRequestShowInlineSuggestions(
                                    sessionId,
                                    inlineSuggestionsRequest,
                                    result.inlineSuggestionsData,
                                    result.clientState,
                                    focusedId,
                                    focusedValue,
                                    inlineSuggestionsCallback,
                                    client,
                                    onErrorCallback,
                                    remoteRenderService,
                                    userId,
                                    activityComponent,
                                    activityToken);
                        },
                        mExecutor)
                .exceptionally(
                        err -> {
                            Slog.e(
                                    TAG,
                                    "exception during onRequestAutofillLocked() for " + sessionId,
                                    err);
                            return null;
                        });
    }

    private CompletableFuture<IBinder> getAugmentedAutofillClient(
            @NonNull IAutoFillManagerClient client) {
        AndroidFuture<IBinder> clientFuture = new AndroidFuture<>();
        try {
            client.getAugmentedAutofillClient(
                    new IResultReceiver.Stub() {
                        @Override
                        public void send(int resultCode, Bundle resultData) {
                            final IBinder realClient =
                                    resultData.getBinder(
                                            AutofillManager
                                                    .EXTRA_AUGMENTED_AUTOFILL_CLIENT);
                            clientFuture.complete(realClient);
                        }
                    });
        } catch (RemoteException e) {
            clientFuture.complete(null);
            throw new RuntimeException(e);
        }
        return clientFuture;
    }

    /**
     * Sends the inline suggestions request to the augmented autofill service.
     *
     * <p>The returned future will timeout after {@link #mRequestTimeoutMs}.
     *
     * @param augmentedAutofillClient augmented autofill client fetched from {@link
     *     IAutoFillManagerClient#getAugmentedAutofillClient(IResultReceiver)}
     * @return a future that contains the response from the augmented autofill service, or null if a
     *     fill window is showing, the request timed out, was cancelled, or otherwise failed.
     */
    private CompletableFuture<AugmentedAutofillInlineSuggestionsResponseData>
            sendRequestToAugmentedAutofillService(
                    int sessionId,
                    int taskId,
                    @NonNull ComponentName activityComponent,
                    @NonNull AutofillId focusedId,
                    @NonNull IBinder augmentedAutofillClient,
                    @Nullable AutofillValue focusedValue,
                    @Nullable InlineSuggestionsRequest inlineSuggestionsRequest,
                    long requestTime) {
        final AtomicReference<ICancellationSignal> cancellationRef = new AtomicReference<>();
        AndroidFuture<AugmentedAutofillInlineSuggestionsResponseData> autofillResponse =
                new AndroidFuture<>();

        mServiceConnector.run(service -> service.onFillRequest(
                sessionId,
                augmentedAutofillClient,
                taskId,
                activityComponent,
                focusedId,
                focusedValue,
                requestTime,
                inlineSuggestionsRequest,
                new IFillCallback.Stub() {
                    @Override
                    public void onSuccess(
                            @Nullable List<Dataset> inlineSuggestionsData,
                            @Nullable Bundle clientState,
                            boolean showingFillWindow) {
                        mCallbacks.resetLastResponse();

                        if (showingFillWindow) {
                            // When fill window is showing, current request is expected to be
                            // cancellable and also inline suggestions won't be shown anyway, so
                            // don't complete the future. This is done as
                            // AugmentedAutofillService checks isCompleted before cancelling. In
                            // addition, cancelling a completed future is a no-op and will
                            // result in the cancellation not being dispatched.
                            Slog.w(
                                    TAG,
                                    "Not showing augmented autofill result due to fill "
                                            + "window");
                        } else {
                            if (sDebug) {
                                Slog.d(TAG, "Augmented autofill returned response");
                            }
                            autofillResponse.complete(
                                    new AugmentedAutofillInlineSuggestionsResponseData(
                                            inlineSuggestionsData, clientState));
                        }
                    }

                    @Override
                    public boolean isCompleted() {
                        return autofillResponse.isDone() && !autofillResponse.isCancelled();
                    }

                    @Override
                    public void onCancellable(ICancellationSignal cancellation) {
                        if (autofillResponse.isCancelled()) {
                            dispatchCancellation(cancellation);
                        } else {
                            cancellationRef.set(cancellation);
                        }
                    }

                    @Override
                    public void cancel() {
                        if (sDebug) {
                            Slog.d(TAG, "Augmented autofill request cancelled");
                        }
                        autofillResponse.cancel(true);
                    }
                }));

        return mInjector
                .orTimeout(autofillResponse, mRequestTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(
                    err -> {
                        if (err instanceof CancellationException) {
                            dispatchCancellation(cancellationRef.get());
                        } else if (err instanceof TimeoutException) {
                            Slog.w(
                                    TAG,
                                    "PendingAutofillRequest timed out ("
                                            + mRequestTimeoutMs
                                            + "ms) for "
                                            + RemoteAugmentedAutofillService.this);
                            dispatchCancellation(cancellationRef.get());
                            if (mComponentName != null) {
                                logResponse(
                                        MetricsEvent.TYPE_ERROR,
                                        mComponentName.getPackageName(),
                                        activityComponent,
                                        sessionId,
                                        mRequestTimeoutMs);
                            }
                        } else {
                            Slog.e(
                                    TAG,
                                    "exception handling"
                                            + " sendRequestToAugmentedAutofillService() for "
                                            + sessionId,
                                    err);
                        }
                        return null;
                    });
    }

    void dispatchCancellation(@Nullable ICancellationSignal cancellation) {
        if (cancellation == null) {
            return;
        }
        Handler.getMain().post(() -> {
            try {
                cancellation.cancel();
            } catch (RemoteException e) {
                Slog.e(TAG, "Error requesting a cancellation", e);
            }
        });
    }

    /**
     * Sends the inline suggestions request to the personal context manager as a {@link
     * AutofillInlineRequestHint}.
     *
     * <p>The returned future will timeout after {@link #mRequestTimeoutMs}. If the future fails, it
     * will clear out the {@link #mPersonalContextResultFuture} and {@link #mAutofillHint} fields.
     *
     * @param augmentedAutofillClient augmented autofill client fetched from {@link
     *     IAutoFillManagerClient#getAugmentedAutofillClient(IResultReceiver)}
     * @return a future that will be completed when the personal context manager returns a matching
     *     insight. The result may be null if the request times out or otherwise fails.
     */
    private CompletableFuture<AugmentedAutofillInlineSuggestionsResponseData>
            sendRequestToPersonalContext(
                    int sessionId,
                    int taskId,
                    @NonNull ComponentName activityComponent,
                    @NonNull AutofillId focusedId,
                    @NonNull IBinder augmentedAutofillClient,
            @Nullable AutofillValue focusedValue,
                    @Nullable InlineSuggestionsRequest inlineSuggestionsRequest) {
        CompletableFuture<AugmentedAutofillInlineSuggestionsResponseData> future =
                new CompletableFuture<>();
        if (!enablePersonalContextService() || mPersonalContextManagerInternal == null) {
            if (sDebug) {
                Slog.d(TAG, "Personal context trigger ignored due to flag being disabled");
            }
            future.complete(null);
            return future;
        }
        if (focusedValue == null || inlineSuggestionsRequest == null) {
            if (sDebug) {
                Slog.d(TAG, "No inline suggestions request, not sending personal context trigger");
            }
            future.complete(null);
            return future;
        }
        if (sDebug) {
            Slog.d(TAG, "Triggering personal context manager with autofill inline request hint");
        }
        AutofillInlineRequestHint hint =
                new AutofillInlineRequestHint.Builder(
                        sessionId,
                        taskId,
                        Instant.now(),
                        activityComponent,
                        focusedId,
                        focusedValue,
                        inlineSuggestionsRequest,
                        augmentedAutofillClient)
                        .build();
        mAutofillHint = hint;
        mPersonalContextManagerInternal.publishTriggeringHint(
                Set.of(hint), new HashSet<>(), mUserId);
        mPersonalContextResultFuture =
                mInjector
                        .orTimeout(future, mRequestTimeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(
                                err -> {
                                    mPersonalContextResultFuture = null;
                                    mAutofillHint = null;
                                    if (err instanceof TimeoutException) {
                                        Slog.w(
                                                TAG,
                                                "Personal context autofill request timed out ("
                                                        + mRequestTimeoutMs
                                                        + "ms) for "
                                                        + RemoteAugmentedAutofillService.this);
                                    } else {
                                        Slog.e(
                                                TAG,
                                                "exception handling personal context autofill "
                                                        + "request for "
                                                        + sessionId,
                                                err);
                                    }
                                    return null;
                                });
        return mPersonalContextResultFuture;
    }

    @SuppressWarnings("ReturnValueIgnored")
    private void maybeRequestShowInlineSuggestions(int sessionId,
            @Nullable InlineSuggestionsRequest request,
            @Nullable List<Dataset> inlineSuggestionsData, @Nullable Bundle clientState,
            @NonNull AutofillId focusedId, @Nullable AutofillValue focusedValue,
            @Nullable Function<InlineFillUi, Boolean> inlineSuggestionsCallback,
            @NonNull IAutoFillManagerClient client, @NonNull Runnable onErrorCallback,
            @Nullable RemoteInlineSuggestionRenderService remoteRenderService,
            int userId,
            @NonNull ComponentName targetActivity, @NonNull IBinder targetActivityToken) {
        if (inlineSuggestionsData == null || inlineSuggestionsData.isEmpty()
                || inlineSuggestionsCallback == null || request == null
                || remoteRenderService == null) {
            // If it was an inline request and the response doesn't have any inline suggestions,
            // we will send an empty response to IME.
            if (inlineSuggestionsCallback != null && request != null) {
                inlineSuggestionsCallback.apply(InlineFillUi.emptyUi(focusedId));
            }
            return;
        }
        mCallbacks.setLastResponse(sessionId);

        final String filterText =
                focusedValue != null && focusedValue.isText()
                        ? focusedValue.getTextValue().toString() : null;

        final InlineFillUi.InlineFillUiInfo inlineFillUiInfo =
                new InlineFillUi.InlineFillUiInfo(request, focusedId, filterText,
                        remoteRenderService, userId, sessionId);

        final InlineFillUi inlineFillUi =
                InlineFillUi.forAugmentedAutofill(
                        inlineFillUiInfo, inlineSuggestionsData,
                        new InlineFillUi.InlineSuggestionUiCallback() {
                            @Override
                            public void autofill(Dataset dataset, int datasetIndex) {
                                if (dataset.getAuthentication() != null) {
                                    mCallbacks.logAugmentedAutofillAuthenticationSelected(sessionId,
                                            dataset.getId(), clientState);
                                    final IntentSender action = dataset.getAuthentication();
                                    final int authenticationId =
                                            AutofillManager.makeAuthenticationId(
                                                    Session.AUGMENTED_AUTOFILL_REQUEST_ID,
                                                    datasetIndex);
                                    final Intent fillInIntent = new Intent();
                                    fillInIntent.putExtra(AutofillManager.EXTRA_CLIENT_STATE,
                                            clientState);
                                    try {
                                        client.authenticate(sessionId, authenticationId, action,
                                                fillInIntent, false);
                                    } catch (RemoteException e) {
                                        Slog.w(TAG, "Error starting auth flow");
                                        inlineSuggestionsCallback.apply(
                                                InlineFillUi.emptyUi(focusedId));
                                    }
                                    return;
                                }
                                mCallbacks.logAugmentedAutofillSelected(sessionId,
                                        dataset.getId(), clientState);
                                try {
                                    final ArrayList<AutofillId> fieldIds = dataset.getFieldIds();
                                    final ClipData content = dataset.getFieldContent();
                                    if (content != null) {
                                        mUriGrantsManager.grantUriPermissions(targetActivity,
                                                targetActivityToken, userId, content);
                                        final AutofillId fieldId = fieldIds.get(0);
                                        if (sDebug) {
                                            Slog.d(TAG, "Calling client autofillContent(): "
                                                    + "id=" + fieldId + ", content=" + content);
                                        }
                                        client.autofillContent(sessionId, fieldId, content);
                                    } else {
                                        final int size = fieldIds.size();
                                        final boolean hideHighlight = size == 1
                                                && fieldIds.get(0).equals(focusedId);
                                        if (sDebug) {
                                            Slog.d(TAG, "Calling client autofill(): "
                                                    + "ids=" + fieldIds
                                                    + ", values=" + dataset.getFieldValues());
                                        }
                                        client.autofill(
                                                sessionId,
                                                fieldIds,
                                                dataset.getFieldValues(),
                                                hideHighlight);
                                    }
                                    inlineSuggestionsCallback.apply(
                                            InlineFillUi.emptyUi(focusedId));
                                } catch (RemoteException e) {
                                    Slog.w(TAG, "Encounter exception autofilling the values");
                                }
                            }

                            @Override
                            public void authenticate(int requestId, int datasetIndex) {
                                Slog.e(TAG, "authenticate not implemented for augmented autofill");
                            }

                            @Override
                            public void startIntentSender(IntentSender intentSender) {
                                try {
                                    client.startIntentSender(intentSender, new Intent());
                                } catch (RemoteException e) {
                                    Slog.w(TAG, "RemoteException starting intent sender");
                                }
                            }

                            @Override
                            public void onError() {
                                onErrorCallback.run();
                            }

                            @Override
                            public void onInflate() {
                                /* nothing */
                            }
                        });

        if (inlineSuggestionsCallback.apply(inlineFillUi)) {
            mCallbacks.logAugmentedAutofillShown(sessionId, clientState);
        }
    }

    @Override
    public String toString() {
        return "RemoteAugmentedAutofillService["
                + ComponentName.flattenToShortString(mComponentName) + "]";
    }

    public void dump(@NonNull String prefix, @NonNull PrintWriter pw) {
        mServiceConnector.dump(prefix, pw);
    }

    /**
     * Called by {@link Session} when it's time to destroy all augmented autofill requests.
     */
    public void onDestroyAutofillWindowsRequest() {
        mServiceConnector.run(IAugmentedAutofillService::onDestroyAllFillWindowsRequest);
    }

    public void notifySystemInlineSuggestions(int sessionId, List<Dataset> inlineSuggestionsData) {
        if (mPersonalContextResultFuture != null
                && mAutofillHint != null
                && mAutofillHint.getSessionId() == sessionId) {
            if (sDebug) {
                Slog.d(TAG, "notifySystemInlineSuggestions() called on sessionId: "
                        + sessionId);
            }
            mPersonalContextResultFuture.complete(
                    new AugmentedAutofillInlineSuggestionsResponseData(
                            inlineSuggestionsData, null));
            mPersonalContextResultFuture = null;
        }
    }

    /** Data class holding the response data for an {@link InlineSuggestionsRequest}. */
    record AugmentedAutofillInlineSuggestionsResponseData(
            @Nullable List<Dataset> inlineSuggestionsData,
            @Nullable Bundle clientState) {}

    public interface RemoteAugmentedAutofillServiceCallbacks
            extends AbstractRemoteService.VultureCallback<RemoteAugmentedAutofillService> {
        void resetLastResponse();

        void setLastResponse(int sessionId);

        void logAugmentedAutofillShown(int sessionId, @Nullable Bundle clientState);

        void logAugmentedAutofillSelected(int sessionId, @Nullable String suggestionId,
                @Nullable Bundle clientState);

        void logAugmentedAutofillAuthenticationSelected(int sessionId,
                @Nullable String suggestionId, @Nullable Bundle clientState);

        void logAugmentedAutofillResponseDiscarded(int sessionId, @Nullable Bundle clientState);
    }

    /**
     * Interface used during construction to allow tests to provide dependencies.
     */
    @VisibleForTesting
    interface Injector {
        Executor getExecutor();
        ServiceConnector.Impl<IAugmentedAutofillService> getServiceConnector();

        /**
         * This method allows tests to intercept futures that are intended to timeout. This is
         * needed as {@link CompletableFuture#orTimeout(long, TimeUnit)} uses its own thread pool,
         * making it difficult to test.
         */
        default <T> CompletableFuture<T> orTimeout(
                CompletableFuture<T> future, long timeout, TimeUnit unit) {
            return future.orTimeout(timeout, unit);
        }
    }

    private static class DefaultInjector implements Injector {
        private final Executor mExecutor;
        private final long mIdleUnbindTimeoutMs;
        private final ServiceConnector.Impl<IAugmentedAutofillService> mServiceConnector;

        DefaultInjector(
                Context context,
                ComponentName serviceName,
                int userId,
                boolean bindInstantServiceAllowed,
                long idleUnbindTimeoutMs) {
            mExecutor = Executors.newSingleThreadExecutor();
            mIdleUnbindTimeoutMs = idleUnbindTimeoutMs;
            mServiceConnector = new ServiceConnector.Impl<IAugmentedAutofillService>(
                    context,
                    new Intent(AugmentedAutofillService.SERVICE_INTERFACE)
                            .setComponent(serviceName),
                    bindInstantServiceAllowed ? Context.BIND_ALLOW_INSTANT : 0,
                    userId,
                    IAugmentedAutofillService.Stub::asInterface) {
                public Context getContext() {
                    return mContext;
                }

                @Override
                protected void onServiceConnectionStatusChanged(
                        @NonNull IAugmentedAutofillService service, boolean connected) {
                    try {
                        if (connected) {
                            service.onConnected(sDebug, sVerbose);
                        } else {
                            service.onDisconnected();
                        }
                    } catch (Exception e) {
                        Slog.w(
                                TAG,
                                "Exception calling onServiceConnectionStatusChanged("
                                        + connected
                                        + "): ",
                                e);
                    }
                }

                @Override // from AbstractRemoteService
                protected long getAutoDisconnectTimeoutMs() {
                    return mIdleUnbindTimeoutMs;
                }
            };
        }

        @Override
        public Executor getExecutor() {
            return mExecutor;
        }

        @Override
        public ServiceConnector.Impl<IAugmentedAutofillService> getServiceConnector() {
            return mServiceConnector;
        }
    }
}
