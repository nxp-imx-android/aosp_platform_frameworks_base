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

package com.android.server.companion.datatransfer.continuity.handoff;

import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.TaskContinuityManager.HandoffRequestResultCode;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.FrameworkStatsLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class HandoffRequestCallbackHolder {

    private static final String TAG = HandoffRequestCallbackHolder.class.getSimpleName();

    @GuardedBy("mCallbacks")
    private final RemoteCallbackList<IHandoffRequestCallback> mCallbacks =
            new RemoteCallbackList<>();

    private record RequestCookie(int associationId, int taskId) {}

    /**
     * Registers a callback for the given association and task.
     *
     * @param associationId The association ID of the handoff request.
     * @param taskId The task ID of the handoff request.
     * @param callback The callback to register.
     */
    public void registerCallback(
            int associationId, int taskId, @NonNull IHandoffRequestCallback callback) {

        Objects.requireNonNull(callback);
        synchronized (mCallbacks) {
            Slog.i(
                    TAG,
                    "Registering HandoffRequestCallback for association "
                            + associationId
                            + " and task "
                            + taskId);

            RequestCookie requestCookie = new RequestCookie(associationId, taskId);
            mCallbacks.register(callback, requestCookie);
            FrameworkStatsLog.write(FrameworkStatsLog.HANDOFF_REQUESTED, requestCookie.hashCode());
        }
    }

    /**
     * Notifies all callbacks for the given association and task, and removes them from the list of
     * pending callbacks.
     *
     * @param associationId The association ID of the handoff request.
     * @param taskId The task ID of the handoff request.
     * @param statusCode The status code of the handoff request.
     */
    public void notifyAndRemoveCallbacks(int associationId, int taskId, int statusCode) {
        synchronized (mCallbacks) {
            Slog.i(
                    TAG,
                    "Notifying HandoffRequestCallbacks for association "
                            + associationId
                            + " and task "
                            + taskId
                            + " of status code "
                            + statusCode);

            RequestCookie request = new RequestCookie(associationId, taskId);
            List<IHandoffRequestCallback> callbacksToRemove = new ArrayList<>();
            mCallbacks.broadcast(
                    (callback, cookie) -> {
                        if (request.equals(cookie)) {
                            finishCallback(callback, request, statusCode);
                            callbacksToRemove.add(callback);
                        }
                    });

            clearCallbacks(callbacksToRemove);
        }
    }

    /**
     * Notifies all callbacks of the given status code, and removes them from the list of pending
     * callbacks.
     *
     * @param statusCode The status code of the handoff request.
     */
    public void finishAllCallbacks(@HandoffRequestResultCode int statusCode) {
        synchronized (mCallbacks) {
            Slog.i(TAG, "Finishing all callbacks with status code " + statusCode);
            List<IHandoffRequestCallback> callbacksToRemove = new ArrayList<>();
            mCallbacks.broadcast(
                    (callback, cookie) -> {
                        finishCallback(callback, (RequestCookie) cookie, statusCode);
                        callbacksToRemove.add(callback);
                    });
            clearCallbacks(callbacksToRemove);
        }
    }

    private void clearCallbacks(@NonNull List<IHandoffRequestCallback> callbacks) {
        Objects.requireNonNull(callbacks);
        synchronized (mCallbacks) {
            Slog.i(TAG, "Clearing " + callbacks.size() + " callbacks.");
            for (IHandoffRequestCallback callback : callbacks) {
                mCallbacks.unregister(callback);
            }
        }
    }

    private void finishCallback(
            @NonNull IHandoffRequestCallback callback,
            @NonNull RequestCookie requestCookie,
            int statusCode) {
        Objects.requireNonNull(callback);
        Objects.requireNonNull(requestCookie);
        try {
            callback.onHandoffRequestFinished(
                    requestCookie.associationId, requestCookie.taskId, statusCode);

            int statusCodeForMetrics =
                    switch (statusCode) {
                        case HANDOFF_REQUEST_RESULT_SUCCESS ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_SUCCESS;
                        case HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_FAILURE_NO_DATA_PROVIDED_BY_TASK;
                        case HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_FAILURE_TASK_NOT_FOUND;
                        case HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_FAILURE_TIMEOUT;
                        default ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_UNKNOWN;
                    };

            FrameworkStatsLog.write(
                    FrameworkStatsLog.HANDOFF_REQUEST_FINISHED,
                    requestCookie.hashCode(),
                    statusCodeForMetrics);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify callback of handoff request cancellation", e);
        }
    }
}
