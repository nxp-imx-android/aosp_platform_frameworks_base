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

package com.android.server.personalcontext.component.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import com.android.server.personalcontext.component.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class for component service clients.
 *
 * @param <C> type of client interface
 * @hide
 */
public abstract class BaseServiceClientComponent<C> implements Component {
    protected static final String TAG = "PersonalContextClient";
    private final Executor mExecutor;

    private final UserHandle mUserHandle;

    private final Context mContext;
    private final UUID mComponentId;
    private final Intent mServiceIntent;
    private final ComponentName mComponentName;
    private final List<RunWithBinderCallback<C>> mPendingCalls = new ArrayList<>();
    private boolean mServiceStarted = false;
    private C mClient;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, BaseServiceClientComponent.this + " is online");
            }

            try {
                C client = getServiceWrapper(binder);
                initializeClient(client);
                onStarted(client);
            } catch (Exception e) {
                Slog.w(TAG, BaseServiceClientComponent.this + " failed", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, BaseServiceClientComponent.this + " is offline");
            }
            mExecutor.execute(() -> {
                mServiceStarted = false;
                mClient = null;
            });
        }
    };

    public BaseServiceClientComponent(Context context, UUID componentId, ServiceInfo serviceInfo,
            UserHandle userHandle) {
        this(context, componentId, serviceInfo, userHandle, Executors.newSingleThreadExecutor());
    }
    protected BaseServiceClientComponent(Context context, UUID componentId, ServiceInfo serviceInfo,
            UserHandle userHandle, Executor executor) {
        mExecutor = executor;
        mContext = context;
        mUserHandle = userHandle;
        mComponentId = componentId;
        mComponentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
        mServiceIntent = new Intent();
        mServiceIntent.setComponent(mComponentName);
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    @Override
    public String toString() {
        return TextUtils.formatSimple(
                "%s{%s} -> %s",
                getClass().getSimpleName(),
                mComponentId,
                mComponentName.flattenToShortString());
    }

    protected void runWithBinder(RunWithBinderCallback<C> callback) {
        mExecutor.execute(() -> {
            start();

            if (mClient != null) {
                callback.run(mClient);
            } else {
                mPendingCalls.add(callback);
            }
        });
    }

    protected final void start() {
        mExecutor.execute(() -> {
            if (mServiceStarted) return;
            mServiceStarted = true;
            mClient = null;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, this + " service is starting");
            }

            mContext.bindServiceAsUser(
                    mServiceIntent,
                    mServiceConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
                    mUserHandle);
        });
    }

    protected final void onStarted(C client) throws RemoteException {
        mExecutor.execute(() -> {
            if (!mServiceStarted) return;
            mClient = client;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, BaseServiceClientComponent.this + " is available");
            }

            for (RunWithBinderCallback<C> callback : mPendingCalls) {
                callback.run(client);
            }
            mPendingCalls.clear();
            stop();
        });
    }

    protected final void stop() {
        mExecutor.execute(() -> {
            mServiceStarted = false;
            mClient = null;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, this + " service is stopping");
            }
            mContext.unbindService(mServiceConnection);
        });
    }

    protected abstract C getServiceWrapper(IBinder binder);

    protected abstract void initializeClient(C client) throws RemoteException;

    /**
     * Callback interface for calls made on a client.
     *
     * @param <C> type of client interface
     * @hide
     */
    public interface RunWithBinderCallback<C> {
        /** Runs the callback with a connected client. */
        void run(C client);
    }
}
