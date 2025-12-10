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

package com.android.server.personalcontext.embedded;

import android.annotation.RequiresNoPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.embedded.IEmbeddedInsightSurfaceVisualizer;
import android.service.personalcontext.embedded.IEmbeddedInsightSurfaceVisualizerCallback;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A connection to an insight surface visualizer service.
 *
 * @hide
 */
public class VisualizerConnection {
    private static final String TAG = "VisualizerConnection";

    private static final int MAX_PENDING_ACTIONS = 20;

    private final Injector mInjector;
    private final ComponentName mComponentName;
    private IEmbeddedInsightSurfaceVisualizer mVisualizer;
    // A queue of actions that have been deferred until a visualizer has connected.
    private final List<Runnable> mDeferredActions = new ArrayList<>();
    private boolean mStarted = false;
    private boolean mBound = false;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mInjector.executeAction(() -> {
                mVisualizer = IEmbeddedInsightSurfaceVisualizer.Stub.asInterface(service);

                // Perform all the deferred actions now that a visualizer exists.
                mDeferredActions.forEach(Runnable::run);
                mDeferredActions.clear();
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mInjector.executeAction(() -> teardownVisualizer(name, "service disconnected"));
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mInjector.executeAction(() -> teardownVisualizer(name, "binding died"));
        }
    };

    /**
     * A helper object to inject dependencies into {@link VisualizerConnection}.
     * @hide
     */
    @VisibleForTesting
    public interface Injector {
        /**
         * Connect to the visualizer service specified by the given intent.
         *
         * @param intent the {@link Intent} specifying the visualizer service.
         * @param serviceConnection a {@link ServiceConnection} used to receive service callbacks
         * @return true if the connection was successful
         */
        boolean connectToService(Intent intent, ServiceConnection serviceConnection);

        /**
         * Disconnect from the previously connected visualizer service.
         *
         * @param serviceConnection the {@link ServiceConnection} passed to connectToService
         */
        void disconnectFromService(ServiceConnection serviceConnection);

        /**
         * Execute the given {@link Runnable} action on a shared thread.
         */
        void executeAction(Runnable action);
    }

    private static final class DefaultInjector implements Injector {
        private final Context mContext;
        private final Executor mExecutor;

        DefaultInjector(Context context, Executor executor) {
            mContext = context;
            mExecutor = executor;
        }

        @Override
        public boolean connectToService(Intent intent, ServiceConnection serviceConnection) {
            return mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        @Override
        public void disconnectFromService(ServiceConnection serviceConnection) {
            mContext.unbindService(serviceConnection);
        }

        @Override
        public void executeAction(Runnable action) {
            mExecutor.execute(action);
        }
    }

    VisualizerConnection(
            ComponentName componentName,
            Context context,
            Executor executor) {
        this(componentName, new DefaultInjector(context, executor));
    }

    @VisibleForTesting
    VisualizerConnection(
            ComponentName componentName,
            Injector injector) {
        mComponentName = componentName;
        mInjector = injector;
    }

    /** Return the {@link ComponentName} of the service this connection manages. */
    public ComponentName getComponentName() {
        return mComponentName;
    }

    /** Create a visualization for the given client using the given insights. */
    public void createVisualizationForClient(
            List<ContextInsight> insights,
            InsightSurfaceClientInfo client,
            Consumer<Boolean> callback) {
        executeOrDeferAction(() -> {
            if (mVisualizer == null) {
                // Something went wrong connecting to the visualizer.
                Slog.e(TAG, "Failed to connect to visualizer in createVisualizationForClient");
                callback.accept(false);
                return;
            }
            try {
                mVisualizer.createVisualizationForClient(
                        ContextInsightWrapper.wrapList(insights),
                        client,
                        new IEmbeddedInsightSurfaceVisualizerCallback.Stub() {
                            @RequiresNoPermission
                            @Override
                            public void onResult(boolean success) {
                                callback.accept(success);
                            }
                        });
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        });
    }

    /** An insight surface client has disconnected. */
    public void onClientDisconnected(InsightSurfaceClientInfo client) {
        mInjector.executeAction(() -> {
            try {
                // Don't bother trying to disconnect if the visualizer is null.
                if (mVisualizer != null) {
                    mVisualizer.onClientDisconnected(client);
                }
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        });
    }

    /** Called when this connection has been registered with the {@link VisualizerRegistry}. */
    public void onRegistered() {
        mInjector.executeAction(() -> {
            if (mStarted) return;
            mStarted = true;
            mBound = false;
            mVisualizer = null;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, this + " service is starting");
            }

            final Intent intent = new Intent(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
            intent.setComponent(mComponentName);
            mBound = mInjector.connectToService(intent, mServiceConnection);
            if (!mBound) {
                teardownVisualizer(mComponentName, "failed to connect to visualizer");
            }
        });
    }

    /** Called when this connection has been unregistered with the {@link VisualizerRegistry} */
    public void onUnregistered() {
        mInjector.executeAction(
                () -> teardownVisualizer(mComponentName, "unregistered from visualizer"));
    }

    /**
     * If the visualizer is connected, perform the given action immediately. Otherwise, add the
     * action to the queue of deferred actions, which will be executed once the visualizer has
     * connected.
     */
    private void executeOrDeferAction(Runnable action) {
        mInjector.executeAction(() -> {
            if (mVisualizer != null) {
                // The visualizer is connected so perform the action immediately.
                action.run();
            } else {
                // The visualizer is not connected, so add the action to the deferred actions queue.
                final int actionsCount = mDeferredActions.size();
                if (actionsCount >= MAX_PENDING_ACTIONS) {
                    Slog.w(TAG, "Too many deferred actions, evicting oldest actions");
                    mDeferredActions.subList(
                            0, actionsCount - (actionsCount - MAX_PENDING_ACTIONS) - 1)
                            .clear();
                }
                mDeferredActions.add(action);
            }
        });
    }

    /**
     * Tear down the connection to the visualizer. This method assumes it is being executed using
     * the injector's executeAction() method (i.e. on the injector's shared thread).
     */
    private void teardownVisualizer(ComponentName name, String reason) {
        Slog.w(TAG,
                "Tearing down visualizer connection, name='" + name + "', reason='" + reason + "'");

        if (mBound) {
            mInjector.disconnectFromService(mServiceConnection);
            mBound = false;
        }
        mVisualizer = null;
        mDeferredActions.clear();
        mStarted = false;
    }
}
