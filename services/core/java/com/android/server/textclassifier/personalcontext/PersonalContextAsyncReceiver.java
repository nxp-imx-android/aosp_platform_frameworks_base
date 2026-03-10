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

package com.android.server.textclassifier.personalcontext;

import android.annotation.NonNull;
import android.os.OutcomeReceiver;
import android.util.Slog;
import android.view.textclassifier.TextClassification;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Retriever that supports asynchronously getting Personal Context results with a timeout. */
class PersonalContextAsyncReceiver {

    private static final String TAG = "PersonalContextAsyncReceiver";

    /** Based on MAX_PENDING_REQUESTS of TextClassificationManagerService */
    @VisibleForTesting static final int MAX_SESSIONS = 20;

    private final ScheduledExecutorService mScheduledExecutorService;
    private final ConcurrentHashMap<String, TextClassification> mIdToResults =
            new ConcurrentHashMap<>(MAX_SESSIONS);

    @GuardedBy("mIdToResults")
    private final ConcurrentLinkedQueue<String> mSessionIds = new ConcurrentLinkedQueue<>();

    @GuardedBy("mIdToResults")
    private final ConcurrentHashMap<String, PersonalContextBridgeTask> mIdToTasks =
            new ConcurrentHashMap<>(MAX_SESSIONS);

    private final long mTimeoutInMillis;

    PersonalContextAsyncReceiver(
            ScheduledExecutorService scheduledExecutorService, long timeoutInMillis) {
        mScheduledExecutorService = scheduledExecutorService;
        mTimeoutInMillis = timeoutInMillis;
    }

    /**
     * Puts TextClassification result in the results map. If there is a receiver awaiting this
     * result, notify and pass the result to the receiver, and cancel the timeout task. Else, put
     * the result in the result map to wait for immediate retrieval in {@link #getAsync}.
     */
    public void put(@NonNull String sessionId, @NonNull TextClassification textClassification) {
        if (mIdToTasks.containsKey(sessionId)) {
            synchronized (mIdToResults) {
                mIdToTasks.get(sessionId).receiver.onResult(textClassification);
                mIdToTasks.get(sessionId).cancellationTask.cancel(false);
                clearSession(sessionId);
            }
        } else {
            synchronized (mIdToResults) {
                mIdToResults.put(sessionId, textClassification);
                mSessionIds.add(sessionId);
                maybePopOldestSession();
            }
        }
    }

    /**
     * Gets result for the TextClassification sessionId into the receiver.
     *
     * <p>If result for sessionId is already present, immediately call receiver. Else, register the
     * receiver to await until {@link #put} notifies the receiver, or the timeout task cancels the
     * task.
     *
     * <p>Only one get request can be made for a sessionId at once.
     */
    public void getAsync(
            @NonNull String sessionId,
            @NonNull OutcomeReceiver<TextClassification, TimeoutException> callback) {
        if (mIdToTasks.containsKey(sessionId)) {
            Slog.d(TAG, "Only one callback is allowed to wait on given sessionId.");
            return;
        }
        if (mIdToResults.containsKey(sessionId)) {
            synchronized (mIdToResults) {
                callback.onResult(mIdToResults.remove(sessionId));
                clearSession(sessionId);
            }
            return;
        }

        ScheduledFuture<?> cancellationTask =
                mScheduledExecutorService.schedule(
                        () -> {
                            callback.onError(
                                    new TimeoutException(
                                            "Timed out while waiting for personal context result"));
                            clearSession(sessionId);
                        },
                        mTimeoutInMillis,
                        TimeUnit.MILLISECONDS);
        synchronized (mIdToResults) {
            mIdToTasks.put(sessionId, new PersonalContextBridgeTask(callback, cancellationTask));
            mSessionIds.add(sessionId);
            maybePopOldestSession();
        }
    }

    /**
     * Clears the cache of any sessionId results or tasks.
     *
     * <p>Used when session is destroyed before personal context service returns or result is
     * requested.
     */
    public void clearSession(String sessionId) {
        synchronized (mIdToResults) {
            mSessionIds.remove(sessionId);
            mIdToResults.remove(sessionId);
            mIdToTasks.remove(sessionId);
        }
    }

    /** Pops oldest session if adding a new sessionId will exceed the limit. */
    @GuardedBy("mIdToResults")
    private void maybePopOldestSession() {
        if (mSessionIds.size() <= MAX_SESSIONS) {
            return;
        }
        String oldestSessionId = mSessionIds.poll();
        mIdToResults.remove(oldestSessionId);
        if (mIdToTasks.containsKey(oldestSessionId)) {
            mIdToTasks.get(oldestSessionId).cancellationTask.cancel(false);
            mIdToTasks.remove(oldestSessionId);
        }
    }

    private record PersonalContextBridgeTask(
            OutcomeReceiver<TextClassification, TimeoutException> receiver,
            ScheduledFuture<?> cancellationTask) {}
}
