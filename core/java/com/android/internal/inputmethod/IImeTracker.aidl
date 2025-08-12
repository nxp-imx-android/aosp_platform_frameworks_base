/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.inputmethod;

import android.view.inputmethod.ImeTracker;

import com.android.internal.infra.AndroidFuture;

/**
 * Interface to the global IME tracker service, used by all client applications.
 * {@hide}
 */
interface IImeTracker {

    /**
     * Called when an IME request is started.
     *
     * @param statsToken the token tracking the request.
     * @param uid the uid of the client that started the request.
     * @param type the type of the request.
     * @param origin the origin of the request.
     * @param reason the reason for starting the request.
     * @param fromUser whether this request was created directly from user interaction.
     * @param startTime the time in milliseconds when the request was started.
     */
    oneway void onStart(in ImeTracker.Token statsToken, int uid, int type, int origin, int reason,
        boolean fromUser, long startTime);

    /**
     * Called when the IME request progresses to a further phase.
     *
     * @param statsToken the token tracking the request.
     * @param phase the new phase the request reached.
     */
    oneway void onProgress(long id, int phase);

    /**
     * Called when the IME request fails.
     *
     * @param statsToken the token tracking the request.
     * @param phase the phase the request failed at.
     */
    oneway void onFailed(in ImeTracker.Token statsToken, int phase);

    /**
     * Called when the IME request is cancelled.
     *
     * @param statsToken the token tracking the request.
     * @param phase the phase the request was cancelled at.
     */
    oneway void onCancelled(in ImeTracker.Token statsToken, int phase);

    /**
     * Called when the show IME request is successful.
     *
     * @param statsToken the token tracking the request.
     */
    oneway void onShown(in ImeTracker.Token statsToken);

    /**
     * Called when the hide IME request is successful.
     *
     * @param statsToken the token tracking the request.
     */
    oneway void onHidden(in ImeTracker.Token statsToken);

    /**
     * Called when the user-controlled IME request was dispatched to the requesting app. The
     * user animation can take an undetermined amount of time, so it shouldn't be tracked.
     *
     * @param statsToken the token tracking the request.
     */
    oneway void onDispatched(in ImeTracker.Token statsToken);

    /**
     * Checks whether there are any pending IME visibility requests.
     *
     * @return {@code true} iff there are pending IME visibility requests.
     */
    @EnforcePermission("TEST_INPUT_METHOD")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = "
            + "android.Manifest.permission.TEST_INPUT_METHOD)")
    boolean hasPendingImeVisibilityRequests();

    /**
     * Finishes the tracking of any pending IME visibility requests. This won't stop the actual
     * requests, but allows resetting the state when starting up test runs.
     *
     * @param completionSignal used to signal when the tracking has been finished.
     */
    @EnforcePermission("TEST_INPUT_METHOD")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = "
            + "android.Manifest.permission.TEST_INPUT_METHOD)")
    oneway void finishTrackingPendingImeVisibilityRequests(
        in AndroidFuture completionSignal /* T=Void */);
}
