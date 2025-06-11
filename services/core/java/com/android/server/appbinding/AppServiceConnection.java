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

package com.android.server.appbinding;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;

import com.android.server.am.PersistentConnection;
import com.android.server.appbinding.finders.AppServiceFinder;
import com.android.server.utils.Slogf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Establishes a persistent connection to a given service component for a given user
 * Each connection is associated with an AppServiceFinder to facilitate the service exchange if the
 * default app changes.
 */
public class AppServiceConnection extends PersistentConnection<IInterface> {
    public static final String TAG = "AppServiceConnection";
    private final AppBindingConstants mConstants;
    private final AppServiceFinder mFinder;
    private final String mPackageName;

    /**
     * Listener for connection status updates
     * TODO: Refactor this (b/423644620)
     */
    public interface ConnectionStatusListener {
        void onConnected(@NonNull AppServiceConnection connection, @NonNull IInterface service);
        void onDisconnected(@NonNull AppServiceConnection connection);
        void onBinderDied(@NonNull AppServiceConnection connection);
    }

    private final List<ConnectionStatusListener> mConnectionListeners =
            new CopyOnWriteArrayList<>();

    AppServiceConnection(Context context, int userId, AppBindingConstants constants,
            Handler handler, AppServiceFinder finder, String packageName,
            @NonNull ComponentName componentName) {
        super(TAG, context, handler, userId, componentName,
                constants.SERVICE_RECONNECT_BACKOFF_SEC,
                constants.SERVICE_RECONNECT_BACKOFF_INCREASE,
                constants.SERVICE_RECONNECT_MAX_BACKOFF_SEC,
                constants.SERVICE_STABLE_CONNECTION_THRESHOLD_SEC);
        mFinder = finder;
        mConstants = constants;
        mPackageName = packageName;
    }

    @Override
    protected int getBindFlags() {
        return mFinder.getBindFlags(mConstants);
    }

    @Override
    protected IInterface asInterface(IBinder obj) {
        final IInterface service = mFinder.asInterface(obj);

        if (service != null) {
            // Notify all listeners.
            Slogf.d(TAG, "Service for %s is connected. Notifying %s listeners.",
                    getComponentName(), mConnectionListeners.size());
            for (ConnectionStatusListener listener : mConnectionListeners) {
                listener.onConnected(this, service);
            }
        } else {
            Slogf.w(TAG, "Service for %s is null.", getComponentName());
        }
        return service;
    }

    public AppServiceFinder getFinder() {
        return mFinder;
    }

    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Adds a listener to be notified of connection changes.
     * If service is already connected, notify immediately.
     */
    public void addConnectionStatusListener(@NonNull ConnectionStatusListener listener) {
        mConnectionListeners.add(listener);
        final IInterface service = getServiceBinder();
        if (isConnected() && service != null) {
            listener.onConnected(this, service);
        }
    }

    /**
     * Removes an existing listener.
     */
    public void removeConnectionStatusListener(@NonNull ConnectionStatusListener listener) {
        mConnectionListeners.remove(listener);
    }
}
