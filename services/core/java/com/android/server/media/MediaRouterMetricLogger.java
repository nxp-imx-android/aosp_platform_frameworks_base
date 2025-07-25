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

package com.android.server.media;

import static android.media.RoutingChangeInfo.ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED;
import static android.media.RoutingChangeInfo.ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED;
import static android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
import static android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
import static android.media.RoutingChangeInfo.ENTRY_POINT_TV_OUTPUT_SWITCHER;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_APP;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_FALLBACK;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST;

import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STARTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STOPPED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_LOCAL_ROUTER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_PROXY_ROUTER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_TV_OUTPUT_SWITCHER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_APP;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_FALLBACK;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED;

import android.annotation.NonNull;
import android.media.MediaRoute2ProviderService;
import android.media.RoutingChangeInfo;
import android.media.RoutingChangeInfo.EntryPoint;
import android.media.RoutingSessionInfo;
import android.media.RoutingSessionInfo.TransferReason;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/**
 * Logs metrics for MediaRouter2.
 *
 * @hide
 */
final class MediaRouterMetricLogger {
    private static final String TAG = "MediaRouterMetricLogger";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int REQUEST_INFO_CACHE_CAPACITY = 100;

    /** LRU cache to store request info. */
    private final EvictionCallbackLruCache<Long, RequestInfo> mRequestInfoCache;

    /** LRU cache to store information about the invocation of a routing change. */
    private final EvictionCallbackLruCache<Long, RoutingChangeInfo> mRoutingChangeInfoCache;

    /** LRU cache to store information for an ongoing routing change. */
    private final EvictionCallbackLruCache<String, OngoingRoutingChange> mOngoingRoutingChangeCache;

    /** Constructor for {@link MediaRouterMetricLogger}. */
    public MediaRouterMetricLogger() {
        mRequestInfoCache =
                new EvictionCallbackLruCache<>(
                        REQUEST_INFO_CACHE_CAPACITY, new OnRequestInfoEvictedListener());
        mRoutingChangeInfoCache =
                new EvictionCallbackLruCache<>(
                        REQUEST_INFO_CACHE_CAPACITY, new OnRoutingChangeInfoEvictedListener());
        mOngoingRoutingChangeCache =
                new EvictionCallbackLruCache<>(
                        REQUEST_INFO_CACHE_CAPACITY, new OnOngoingRoutingChangeEvictedListener());
    }

    /**
     * Converts a reason code from {@link MediaRoute2ProviderService} to a result code for logging.
     *
     * @param reason The reason code from {@link MediaRoute2ProviderService}.
     * @return The result code for logging.
     */
    public static int convertResultFromReason(int reason) {
        switch (reason) {
            case MediaRoute2ProviderService.REASON_UNKNOWN_ERROR:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR;
            case MediaRoute2ProviderService.REASON_REJECTED:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
            case MediaRoute2ProviderService.REASON_NETWORK_ERROR:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR;
            case MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE;
            case MediaRoute2ProviderService.REASON_INVALID_COMMAND:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND;
            case MediaRoute2ProviderService.REASON_UNIMPLEMENTED:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED;
            case MediaRoute2ProviderService.REASON_FAILED_TO_REROUTE_SYSTEM_MEDIA:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA;
            default:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;
        }
    }

    /**
     * Adds a new request info to the cache.
     *
     * @param uniqueRequestId The unique request id.
     * @param eventType The event type.
     */
    public void addRequestInfo(long uniqueRequestId, int eventType) {
        RequestInfo requestInfo = new RequestInfo(uniqueRequestId, eventType);
        mRequestInfoCache.put(requestInfo.mUniqueRequestId, requestInfo);
    }

    /**
     * Removes a request info from the cache.
     *
     * @param uniqueRequestId The unique request id.
     */
    public void removeRequestInfo(long uniqueRequestId) {
        mRequestInfoCache.remove(uniqueRequestId);
    }

    /**
     * Logs an operation failure.
     *
     * @param eventType The event type.
     * @param result The result of the operation.
     */
    public void logOperationFailure(int eventType, int result) {
        logMediaRouterEvent(eventType, result);
    }

    /**
     * Logs an operation triggered.
     *
     * @param eventType The event type.
     */
    public void logOperationTriggered(int eventType, int result) {
        logMediaRouterEvent(eventType, result);
    }

    /**
     * Logs the result of a request.
     *
     * @param uniqueRequestId The unique request id.
     * @param result The result of the request.
     */
    public void logRequestResult(long uniqueRequestId, int result) {
        RequestInfo requestInfo = mRequestInfoCache.get(uniqueRequestId);
        if (requestInfo == null) {
            Slog.w(
                    TAG,
                    "logRequestResult: No RequestInfo found for uniqueRequestId="
                            + uniqueRequestId);
            return;
        }

        int eventType = requestInfo.mEventType;
        logMediaRouterEvent(eventType, result);

        removeRequestInfo(uniqueRequestId);
    }

    /**
     * Logs the overall scanning state.
     *
     * @param isScanning The scanning state for the user.
     */
    public void updateScanningState(boolean isScanning) {
        if (!isScanning) {
            logScanningStopped();
        } else {
            logScanningStarted();
        }
    }

    /**
     * Stores {@link RoutingChangeInfo} when a routing change is requested. This is appended with
     * routing session information when {@link MediaRouterMetricLogger#notifyRoutingChange(long,
     * RoutingSessionInfo, int)} is called.
     *
     * @param uniqueRequestId the unique request id for a routing change.
     * @param routingChangeInfo the routing change request information.
     */
    public void notifyRoutingChangeRequested(
            long uniqueRequestId, RoutingChangeInfo routingChangeInfo) {
        mRoutingChangeInfoCache.put(uniqueRequestId, routingChangeInfo);
    }

    /**
     * Stores an {@link OngoingRoutingChange} when a routing change occurs. This is logged when
     * {@link MediaRouterMetricLogger#notifySessionEnd(String)} is called with the corresponding
     * sessionId.
     *
     * @param uniqueRequestId the unique request id corresponding to the routing change request.
     *     This should be same as the one passed into {@link
     *     MediaRouterMetricLogger#notifyRoutingChangeRequested(long, RoutingChangeInfo)}
     * @param routingSessionInfo information of the media routing session.
     * @param clientPackageUid uid of the client package for which the routing is taking place.
     */
    public void notifyRoutingChange(
            long uniqueRequestId, RoutingSessionInfo routingSessionInfo, int clientPackageUid) {
        RoutingChangeInfo routingChangeInfo = mRoutingChangeInfoCache.get(uniqueRequestId);
        if (routingChangeInfo == null) {
            Slog.e(TAG, "Unable to get routing change info for the specified request id.");
            return;
        }
        OngoingRoutingChange ongoingRoutingChange =
                new OngoingRoutingChange(
                        routingChangeInfo.getEntryPoint(),
                        clientPackageUid,
                        routingSessionInfo.isSystemSession(),
                        routingSessionInfo.getTransferReason(),
                        routingChangeInfo.isSuggested(),
                        SystemClock.elapsedRealtime());
        mOngoingRoutingChangeCache.put(routingSessionInfo.getOriginalId(), ongoingRoutingChange);
        mRoutingChangeInfoCache.remove(uniqueRequestId);
    }

    /**
     * Logs the {@link OngoingRoutingChange} corresponding to the sessionId
     *
     * @param sessionId id of the session which has ended
     */
    public void notifySessionEnd(String sessionId) {
        OngoingRoutingChange ongoingRoutingChange = mOngoingRoutingChangeCache.get(sessionId);
        if (ongoingRoutingChange == null) {
            Slog.e(TAG, "Unable to get routing change logging info for the specified sessionId.");
            return;
        }
        long sessionLengthInMillis =
                SystemClock.elapsedRealtime() - ongoingRoutingChange.startTimeInMillis;

        if (DEBUG) {
            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "notifySessionEnd | EntryPoint: %d, ClientPackageUid: %d,"
                                    + " IsSystemSession: %b, TransferReason: %d, IsSuggested: %b,"
                                    + " SessionLengthInMillis: %d",
                            ongoingRoutingChange.entryPoint,
                            ongoingRoutingChange.clientPackageUid,
                            ongoingRoutingChange.isSystemSession,
                            ongoingRoutingChange.transferReason,
                            ongoingRoutingChange.isSuggested,
                            sessionLengthInMillis));
        }

        MediaRouterStatsLog.write(
                MediaRouterStatsLog.ROUTING_CHANGE_REPORTED,
                convertEntryPointForLogging(ongoingRoutingChange.entryPoint),
                ongoingRoutingChange.clientPackageUid,
                ongoingRoutingChange.isSystemSession,
                convertTransferReasonForLogging(ongoingRoutingChange.transferReason),
                ongoingRoutingChange.isSuggested,
                sessionLengthInMillis);

        mOngoingRoutingChangeCache.remove(sessionId);
    }

    /**
     * Converts {@link TransferReason} from {@link RoutingSessionInfo} to the transfer reason enum
     * defined for logging.
     *
     * @param transferReason the transfer reason as specified in {@link RoutingSessionInfo}
     * @return the transfer reason as per the enum defined for logging.
     */
    @VisibleForTesting
    /*package*/ static int convertTransferReasonForLogging(@TransferReason int transferReason) {
        return switch (transferReason) {
            case TRANSFER_REASON_FALLBACK ->
                    ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_FALLBACK;
            case TRANSFER_REASON_SYSTEM_REQUEST ->
                    ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST;
            case TRANSFER_REASON_APP ->
                    ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_APP;
            default -> ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED;
        };
    }

    /**
     * Converts {@link EntryPoint} from {@link RoutingChangeInfo} to the entry point enum defined
     * for logging.
     *
     * @param entryPoint the entry point as specified in {@link RoutingChangeInfo}
     * @return the entry point as per the enum defined for logging.
     */
    @VisibleForTesting
    /* package */ static int convertEntryPointForLogging(@EntryPoint int entryPoint) {
        return switch (entryPoint) {
            case ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
            case ENTRY_POINT_SYSTEM_MEDIA_CONTROLS ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
            case ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_LOCAL_ROUTER;
            case ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_PROXY_ROUTER;
            case ENTRY_POINT_TV_OUTPUT_SWITCHER ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_TV_OUTPUT_SWITCHER;
            default ->
                    throw new IllegalArgumentException(
                            "No mapping found for the given entry point: " + entryPoint);
        };
    }

    @VisibleForTesting
    /* package */ int getRequestInfoCacheCapacity() {
        return mRequestInfoCache.maxSize();
    }

    /**
     * Gets the size of the request info cache.
     *
     * @return The size of the request info cache.
     */
    @VisibleForTesting
    /* package */ int getRequestCacheSize() {
        return mRequestInfoCache.size();
    }

    private void logMediaRouterEvent(int eventType, int result) {
        MediaRouterStatsLog.write(
                MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED, eventType, result);

        if (DEBUG) {
            Slog.d(TAG, "logMediaRouterEvent: " + eventType + " " + result);
        }
    }

    /** Logs the scanning started event. */
    private void logScanningStarted() {
        logMediaRouterEvent(
                MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STARTED,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED);
    }

    /** Logs the scanning stopped event. */
    private void logScanningStopped() {
        logMediaRouterEvent(
                MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STOPPED,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED);
    }

    /** Class to store request info. */
    static class RequestInfo {
        public final long mUniqueRequestId;
        public final int mEventType;

        /**
         * Constructor for {@link RequestInfo}.
         *
         * @param uniqueRequestId The unique request id.
         * @param eventType The event type.
         */
        RequestInfo(long uniqueRequestId, int eventType) {
            mUniqueRequestId = uniqueRequestId;
            mEventType = eventType;
        }

        /**
         * Dumps the request info.
         *
         * @param pw The print writer.
         * @param prefix The prefix for the output.
         */
        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "RequestInfo");
            String indent = prefix + "  ";
            pw.println(indent + "mUniqueRequestId=" + mUniqueRequestId);
            pw.println(indent + "mEventType=" + mEventType);
        }
    }

    /**
     * A subclass of {@link LruCache} which takes in an {@link OnEntryEvictedListener} to be invoked
     * on eviction of an entry.
     */
    private static class EvictionCallbackLruCache<K, V> extends LruCache<K, V> {

        private final OnEntryEvictedListener<K, V> mOnEntryEvictedListener;

        EvictionCallbackLruCache(int maxSize, OnEntryEvictedListener<K, V> onEntryEvictedListener) {
            super(maxSize);
            mOnEntryEvictedListener = onEntryEvictedListener;
        }

        @Override
        protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
            if (evicted) {
                mOnEntryEvictedListener.onEntryEvicted(key, oldValue);
            }
        }
    }

    private class OnRequestInfoEvictedListener
            implements OnEntryEvictedListener<Long, RequestInfo> {

        @Override
        public void onEntryEvicted(Long key, RequestInfo value) {
            Slog.d(TAG, "Evicted request info: " + value.mUniqueRequestId);
            logOperationTriggered(
                    value.mEventType, MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED);
        }
    }

    private static class OnRoutingChangeInfoEvictedListener
            implements OnEntryEvictedListener<Long, RoutingChangeInfo> {

        @Override
        public void onEntryEvicted(Long key, RoutingChangeInfo value) {
            Slog.w(TAG, "Routing change info evicted from cache with requestId: " + key);
        }
    }

    private static class OnOngoingRoutingChangeEvictedListener
            implements OnEntryEvictedListener<String, OngoingRoutingChange> {
        @Override
        public void onEntryEvicted(String key, OngoingRoutingChange ongoingRoutingChange) {
            Slog.w(TAG, "Routing change info evicted from cache with sessionId: " + key);
        }
    }

    private interface OnEntryEvictedListener<K, V> {
        void onEntryEvicted(K key, V value);
    }

    /** Information about an ongoing routing change. */
    private record OngoingRoutingChange(
            @EntryPoint int entryPoint,
            int clientPackageUid,
            boolean isSystemSession,
            @TransferReason int transferReason,
            boolean isSuggested,
            long startTimeInMillis) {}
}
