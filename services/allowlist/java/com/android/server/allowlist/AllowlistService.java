/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.allowlist;

import static android.Manifest.permission.QUERY_ALLOWLIST;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.app.appfunctions.flags.Flags;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.allowlist.AllowlistManager;
import android.os.allowlist.AllowlistRequest;
import android.os.allowlist.AllowlistResponse;
import android.os.allowlist.IAllowlistProviderService;
import android.os.allowlist.IAllowlistService;
import android.os.allowlist.IOnAllowlistChangedListener;
import android.os.allowlist.IProviderOnAllowlistChangedListener;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.FunctionalUtils.RemoteExceptionIgnoringConsumer;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appbinding.finders.AllowlistProviderServiceFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A system service which interfaces with allowlist clients via the AllowlistManager, and the
 * AllowlistProviderService, which serves requests made to the Allowlist.
 */
public class AllowlistService extends SystemService {

    private static final String LOG_TAG = AllowlistService.class.getSimpleName();

    private AppBindingService mAppBindingService;
    // Only used for testing purpose
    private volatile IAllowlistProviderService mTestProviderService;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<IBinder, ListenerRecord> mListenerRecords = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<AllowlistRequest, ArraySet<IBinder>> mRequestListeners =
            new ArrayMap<>();

    private final IProviderOnAllowlistChangedListener mOnProviderAllowlistsChangedListener =
            new IProviderOnAllowlistChangedListener.Stub() {
                @RequiresNoPermission
                @Override
                public void onAllowlistChanged(List<AllowlistRequest> requests) {
                    synchronized (mLock) {
                        for (AllowlistRequest request : requests) {
                            dispatchAllowlistChangedLocked(request);
                        }
                    }
                }
            };

    public AllowlistService(@NonNull Context context) {
        super(context);
    }

    /**
     * Send a query to the allowlist through AllowlistProviderService.
     *
     * @param request A request for the allowlist
     * @param callback A callback to receive the response from the allowlist provider.
     *
     * @see AllowlistRequest
     * @see AllowlistResponse
     */
    private void queryAllowlist(@NonNull AllowlistRequest request,
            @NonNull RemoteCallback callback) {
        IAllowlistProviderService testProviderService = mTestProviderService;
        if (testProviderService != null
                && request.getAllowlistId() == AllowlistManager.ALLOWLIST_ID_TEST) {
            try {
                testProviderService.queryAllowlist(request, callback);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Exception when querying test provider", e);
            }
        } else {
            dispatchAllowlistServiceEvent(getContext().getUserId(), (service) -> {
                service.queryAllowlist(request, callback);
            });
        }
    }

    /**
     * Add a listener for changes to a given allowlist.
     * Note: The system service will use its own listener to listen for allowlist changes through
     * allowlist providers.
     * @param request Specify the changes to the allowlist to listen for
     * @param listener A listener for allowlist changes
     */
    private void addOnAllowlistChangedListener(@NonNull AllowlistRequest request,
            @NonNull IOnAllowlistChangedListener listener) {
        synchronized (mLock) {
            ListenerRecord record = mListenerRecords.get(listener.asBinder());
            if (record == null) {
                record = new ListenerRecord(listener);
                mListenerRecords.put(listener.asBinder(), record);
            }
            record.mRequests.add(request);
            mRequestListeners.computeIfAbsent(request, k -> new ArraySet<>()).add(
                    listener.asBinder());
        }

        IAllowlistProviderService testProviderService = mTestProviderService;
        if (testProviderService != null
                && request.getAllowlistId() == AllowlistManager.ALLOWLIST_ID_TEST) {
            try {
                testProviderService.addRequestForAllowlistChange(request,
                        mOnProviderAllowlistsChangedListener);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Exception when adding test provider listener", e);
            }
        } else {
            dispatchAllowlistServiceEvent(getContext().getUserId(), service -> {
                service.addRequestForAllowlistChange(request, mOnProviderAllowlistsChangedListener);
            });
        }
    }

    /**
     * Remove a previously registered listener for an allowlist.
     * @param listener The listener to remove
     */
    private void removeOnAllowlistChangedListener(@NonNull IOnAllowlistChangedListener listener) {
        ArrayList<AllowlistRequest> requestsToRemove = new ArrayList<>();

        synchronized (mLock) {
            ListenerRecord listenerRecord = mListenerRecords.remove(listener.asBinder());
            if (listenerRecord == null) {
                Slog.w(LOG_TAG, "Listener does not exist");
                return;
            }
            listenerRecord.unlinkedToDeath();

            for (AllowlistRequest request : listenerRecord.mRequests) {
                ArraySet<IBinder> listeners = mRequestListeners.get(request);
                if (listeners != null) {
                    listeners.remove(listener.asBinder());
                    if (listeners.isEmpty()) {
                        mRequestListeners.remove(request);
                        requestsToRemove.add(request);
                    }
                }
            }
        }

        IAllowlistProviderService testProviderService = mTestProviderService;
        for (AllowlistRequest request : requestsToRemove) {
            if (testProviderService != null) {
                try {
                    testProviderService.removeRequestForAllowlistChange(request);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when adding test provider listener", e);
                }
            } else {
                dispatchAllowlistServiceEvent(getContext().getUserId(), (service) -> {
                    service.removeRequestForAllowlistChange(request);
                });
            }

        }
    }

    @Override
    public void onStart() {
        if (!Flags.enableAppFunctionPermissionV2()) {
            return;
        }
        publishBinderService(Context.ALLOWLIST_SERVICE, new AllowlistBinderService());
    }

    @NonNull
    private AppBindingService getAppBindingService() {
        if (mAppBindingService == null) {
            mAppBindingService = LocalServices.getService(AppBindingService.class);
        }
        return mAppBindingService;
    }

    @GuardedBy("mLock")
    private void dispatchAllowlistChangedLocked(@NonNull AllowlistRequest request) {
        ArraySet<IBinder> listeners = mRequestListeners.get(request);

        if (listeners != null && !listeners.isEmpty()) {
            for (IBinder binder : listeners) {
                try {
                    IOnAllowlistChangedListener listener =
                            IOnAllowlistChangedListener.Stub.asInterface(binder);
                    listener.onAllowlistChanged(request);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when triggering listener", e);
                }
            }
        }
    }

    private void dispatchAllowlistServiceEvent(@UserIdInt int userId,
            @NonNull RemoteExceptionIgnoringConsumer<IAllowlistProviderService> action) {
        getAppBindingService().dispatchAppServiceEvent(AllowlistProviderServiceFinder.class, userId,
                connection -> {
                    IAllowlistProviderService binder =
                            (IAllowlistProviderService) connection.getServiceBinder();
                    action.accept(binder);
                });
    }

    private class ListenerRecord implements IBinder.DeathRecipient {
        final ArraySet<AllowlistRequest> mRequests = new ArraySet<>();
        final IOnAllowlistChangedListener mListener;
        private boolean mLinkedToDeath;

        ListenerRecord(@NonNull IOnAllowlistChangedListener listener) {
            mListener = listener;
            try {
                mListener.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Exception when linking to death recipient", e);
            }
            mLinkedToDeath = true;
        }

        void unlinkedToDeath() {
            if (!mLinkedToDeath) {
                return;
            }

            try {
                mListener.asBinder().unlinkToDeath(this, 0);
            } catch (Exception e) {
                Slog.w(LOG_TAG, "Exception when unlinking to death recipient", e);
            }
            mLinkedToDeath = false;
        }

        @Override
        public void binderDied() {
            mLinkedToDeath = false;
            removeOnAllowlistChangedListener(mListener);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ListenerRecord that)) {
                return false;
            }
            return Objects.equals(mRequests, that.mRequests) && Objects.equals(mListener,
                    that.mListener);
        }

        @Override
        public int hashCode() {
            return mRequests.hashCode() ^ mListener.hashCode();
        }
    }

    private final class AllowlistBinderService extends IAllowlistService.Stub {

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void addOnAllowlistChangedListener(AllowlistRequest request,
                IOnAllowlistChangedListener listener) {
            addOnAllowlistChangedListener_enforcePermission();
            AllowlistService.this.addOnAllowlistChangedListener(request, listener);
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void removeOnAllowlistChangedListener(IOnAllowlistChangedListener listener) {
            removeOnAllowlistChangedListener_enforcePermission();
            AllowlistService.this.removeOnAllowlistChangedListener(listener);
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void queryAllowlist(AllowlistRequest request, RemoteCallback callback) {
            queryAllowlist_enforcePermission();
            AllowlistService.this.queryAllowlist(request, callback);
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void setTestProviderEnabled(boolean enabled) {
            setTestProviderEnabled_enforcePermission();

            synchronized (mLock) {
                if (enabled && mTestProviderService == null) {
                    TestAllowlistProviderService testAllowlistProviderService =
                            new TestAllowlistProviderService();
                    testAllowlistProviderService.onCreate();
                    mTestProviderService = IAllowlistProviderService.Stub.asInterface(
                            testAllowlistProviderService.onBind(null));
                } else if (!enabled) {
                    mTestProviderService = null;
                }
            }
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void notifyAllowlistChangedListenersForTestProvider(
                List<AllowlistRequest> requests) {
            notifyAllowlistChangedListenersForTestProvider_enforcePermission();

            synchronized (mLock) {
                if (mTestProviderService == null) {
                    throw new IllegalStateException("Test provider not enabled");
                }

                try {
                    mTestProviderService.notifyAllowlistChangedListenersForTestProvider(
                            requests);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when triggering listeners in test "
                            + "AllowlistProvider", e);
                }
            }
        }
    }
}
