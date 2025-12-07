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

package android.hardware.contexthub;

import android.annotation.DurationMillisLong;
import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.Size;
import android.annotation.SystemApi;
import android.chre.flags.Flags;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.system.SystemCleaner;
import android.util.CloseGuard;

import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

/**
 * Represents a sink on a data flow whose source is an offload endpoint. New instances of this class
 * are obtained on {@link DataFlowCallback#onReceivedDataFlowSink(DataFlowSink, HubEndpointInfo,
 * HubMessage, int)} events. See {@link HubEndpoint#createDataFlowSource(Set, DataFlowDataConfig,
 * int, int)} for an overview of data flows.
 *
 * <p>This class's API is expected to be invoked from a single thread at a time. It may be invoked
 * from different threads so long as the user ensures this using an appropriate mechanism (e.g.
 * mutex, dispatching to a single-threaded executor).
 *
 * <p>From the point that an instance of this class is created, it has the ability to access the
 * data flow until either the user calls {@link DataFlowSink#stop()}, the source stops the data flow
 * (user recognizable via {@link DataFlowCallback#onDataFlowStopped(DataFlowSink)}), the source
 * endpoint disconnects from the messaging network (see {@link
 * android.hardware.location.ContextHubManager#registerEndpointDiscoveryCallback(Executor,
 * HubEndpointDiscoveryCallback, long)}), or a call to any of the API methods fails with a {@link
 * RuntimeException}, indicating a lower-level error in communication that makes this data flow
 * unusable.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_FMCQ_API)
public class DataFlowSink implements AutoCloseable {
    /** The configuration of data read from this data flow. */
    private final DataFlowDataConfig mConfig;

    /** Close guard to warn when the user hasn't explicitly {@link #close()}d this instance. */
    private final CloseGuard mCloseGuard = new CloseGuard();

    /** Returns the configuration of elements read from this data flow. */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    @NonNull
    public DataFlowDataConfig getDataFlowConfig() {
        return mConfig;
    }

    /**
     * Disables this sink and alerts the source.
     *
     * The user should call this when finished reading from the data flow to release underlying
     * resources as soon as possible.
     *
     * <p>Resources associated with this sink will be released asynchronously. The user will no
     * longer receive new events via the {@link DataFlowCallback} for this data flow.
     *
     * <p>NOTE: Any events currently in flight when this method is called may trigger the callback.
     *
     * It is safe to call this method at any time. The user does not need to explicitly call this
     * method if they receive a {@link DataFlowCallback#onDataFlowSinkEvent(DataFlowSink, int,
     * SinkEventData)} event of type {@link DataFlowCallback#SINK_EVENT_STOPPED} for this instance.
     */
    @Override
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    public void close() {
        mCloseGuard.close();
        // Implemented in ag/36998982 in this topic.
    }

    /**
     * Reads one or more elements from a data flow if available.
     *
     * @param elementCount The number of elements to attempt to read
     * @param allOrNothing If {@code true}, this method will return {@code null} if there are fewer
     *     {@code count} elements to read
     * @return A {@link DataFlowData} containing the data read, otherwise {@code null} if no data
     *     was available to be read or if {@code allOrNothing} is {@code true} and there are fewer
     *     than {@code count} elements to read
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    @Nullable
    public DataFlowData requestData(@Size(min = 1) int elementCount, boolean allOrNothing) {
        // Implemented in ag/36998982 in this topic.
        return null;
    }

    /**
     * Blocking version of {@link #requestData(int, boolean)} that necessarily returns {@code
     * elementCount} elements.
     *
     * <p>While this method is blocking, {@link DataFlowCallback#onDataFlowSinkEvent(DataFlowSink,
     * int, DataFlowCallback.SinkEventData)} events of type {@link
     * DataFlowCallback#SINK_EVENT_READABLE} will be suppressed for this sink. It blocks until the
     * requested data is available or the timeout is reached.
     *
     * @param elementCount The number of elements to read
     * @param timeout The timeout for the operation, otherwise {@code null} for no timeout
     * @return A {@link DataFlowData} containing {@code elementCount} elements
     * @throws TimeoutException if the operation times out
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    @NonNull
    public DataFlowData awaitData(
            @Size(min = 1) int elementCount, @Nullable @DurationMillisLong Duration timeout)
            throws TimeoutException {
        // Implemented in ag/36998982 in this topic.
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * Asynchronous version of {@link #awaitData(int, Duration)}.
     *
     * <p>Dispatches an asynchronous operation to await the requested data. The {@code receiver}
     * will be dispatched when the requested data is available or on error. {@link
     * DataFlowCallback#onDataFlowSinkEvent(DataFlowSink, int, DataFlowCallback.SinkEventData)}
     * events of type {@link DataFlowCallback#SINK_EVENT_READABLE} will be suppressed for this sink
     * while the operation is in progress.
     *
     * <p>If provided, the {@code executor} will be used to dispatch the {@code receiver}.
     * Otherwise, the {@code receiver} will be dispatched on the executor registered with {@link
     * HubEndpoint.Builder#setDataFlowCallback(Executor, DataFlowCallback)}.
     *
     * <p>This operation may be cancelled using an optionally provided {@link
     * android.os.CancellationSignal}. Cancellation may race with the completion of the operation,
     * so the user should use the result or error propagated to {@code receiver} to determine the
     * status.
     *
     * @param elementCount The number of elements to read
     * @param cancellationSignal An optional object for cancelling this operation
     * @param executor An optional {@link Executor} to override the default executor for dispatching
     *     the {@code receiver}
     * @param receiver The {@link OutcomeReceiver} to dispatch when a dispatched pop operation
     *     either succeeds or fails
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    public void awaitDataAsync(
            @Size(min = 1) int elementCount,
            @Nullable CancellationSignal cancellationSignal,
            @Nullable Executor executor,
            @NonNull OutcomeReceiver<DataFlowData, Throwable> receiver) {
        Objects.requireNonNull(receiver);
        // Implemented in ag/37282024 in this topic.
    }

    /**
     * Syncs this sink to a position at an offset behind the source's current write position.
     *
     * <p>The purpose of this method is to allow a sink to fast-forward through stale data to data
     * that is actually relevant to it. For example, a sink may only care about data from the last
     * second, but be configured with a {@link DataFlowNewDataAlertPolicy#getOpportunistic()} policy
     * by the source. When the source does actually alerts this sink, the data flow may be full of
     * very old data that the sink cannot use. This method allows the sink to discard all but the
     * most recent data.
     *
     * <p>This API is non-blocking.
     *
     * @param offset The offset in bytes behind the source's current write position
     * @throws IllegalArgumentException if the offset is not a multiple of the element size for
     *     fixed-size element data flows or if the offset is greater than the current size of the
     *     data flow
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    public void syncToSource(@IntRange(from = 0) int offset) {
        if (mConfig.getFormat() == DataFlowDataConfig.FORMAT_FIXED_SIZE
                && offset % mConfig.getElementSize() != 0) {
            throw new IllegalStateException(
                    "syncToSource() offset must be a multiple of the element size for fixed-size"
                            + " element data flows.");
        } else if (offset > size()) {
            throw new IllegalStateException(
                    "syncToSource() offset must be less than or equal to the current size of the"
                            + " data flow.");
        }
        // Implemented in ag/36998982 in this topic.
    }

    /**
     * Returns whether this sink's read position can be overwritten by the source when it wraps
     * around the shared memory region.
     *
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    public boolean sourceCanOverwriteReadPosition() {
        // Implemented in ag/36998982 in this topic.
        return false;
    }

    /**
     * Returns whether this sink has data available to read.
     *
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    public boolean isEmpty() {
        // Implemented in ag/36998982 in this topic.
        return false;
    }

    /**
     * Returns the size of the data flow in bytes with respect to this sink's read position.
     *
     * @throws IllegalStateException if this sink has been stopped
     * @throws ConcurrentModificationException if another API is being called concurrently or an
     *     async operation is in progress
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_CONTEXT_HUB)
    @IntRange(from = 0)
    public int size() {
        // Implemented in ag/36998982 in this topic.
        return 0;
    }

    /* package */ DataFlowSink(@NonNull DataFlowDataConfig config) {
        mConfig = config;
        SystemCleaner.cleaner()
                .register(
                        this,
                        () -> {
                            mCloseGuard.warnIfOpen();
                            close();
                        });
    }
}
