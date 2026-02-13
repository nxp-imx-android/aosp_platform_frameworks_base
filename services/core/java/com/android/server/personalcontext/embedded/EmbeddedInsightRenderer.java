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

import android.content.Context;
import android.os.RemoteException;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.insight.ContextInsight;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.personalcontext.component.Renderer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** @hide */
public class EmbeddedInsightRenderer implements Renderer {
    private static final String TAG = "EmbeddedInsightRenderer";

    private final UUID mComponentId = UUID.randomUUID();
    private final ClientRegistry mClientRegistry;
    private final VisualizerRegistry mVisualizerRegistry;
    private final Executor mExecutor;


    public EmbeddedInsightRenderer(
            Context context,
            Executor executor) {
        this(new ClientRegistry(), new VisualizerRegistry(context, executor), executor);
    }

    /** Construct an {@link EmbeddedInsightRenderer} for test purposes. */
    @VisibleForTesting
    EmbeddedInsightRenderer(
            ClientRegistry clientRegistry,
            VisualizerRegistry visualizerRegistry,
            Executor executor) {
        mClientRegistry = clientRegistry;
        mVisualizerRegistry = visualizerRegistry;
        mExecutor = executor;
    }

    /**
     * The embedded insight renderer has been registered with the ACE framework. Perform any
     * necessary initialization.
     * TODO(b/463464084): Introduce a lifecycle manager to be notified of lifecycle events instead.
     */
    public void onRegistered() {
        // The visualizer registry already pushes this work to the executor's thread.
        mVisualizerRegistry.startRegisteringVisualizers();
    }

    /**
     * Register an insight surface client and return the {@link RenderToken} associated with that
     * client via the onRegistered callback.
     */
    public void registerInsightSurfaceClient(
            InsightSurfaceClientInfo clientInfo,
            Consumer<RenderToken> onRegistered) {
        mExecutor.execute(() -> {
            logDebug("registering insight surface client, id=" + clientInfo.getId());

            final RenderToken clientRenderToken = new RenderToken(mComponentId);

            mClientRegistry.addClient(clientInfo, clientRenderToken);

            // Link the client to death so we can unregister it if it dies.
            try {
                clientInfo.getClient().asBinder().linkToDeath(() -> {
                    logDebug("client has died: " + clientInfo.getId());
                    unregisterInsightSurfaceClient(clientInfo.getId());
                }, 0);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }

            onRegistered.accept(clientRenderToken);
        });
    }

    /** Return the renderer's registered clients. */
    @VisibleForTesting
    public List<InsightSurfaceClientInfo> getClients() {
        return mClientRegistry.getClients();
    }

    /** Unregister the insight surface client with the given id. */
    public void unregisterInsightSurfaceClient(UUID id) {
        mExecutor.execute(() -> {
            logDebug("unregistering insight surface client, id=" + id);
            final InsightSurfaceClientInfo clientInfo = mClientRegistry.removeClient(id);
            if (clientInfo == null) {
                Slog.w(TAG, "client not found for id=" + id);
                return;
            }

            mVisualizerRegistry.performActionOnVisualizers(
                    visualizer -> visualizer.onClientDisconnected(clientInfo));
        });
    }

    /** Gets the {@link RenderToken} for the given {@link InsightSurfaceClientInfo}. */
    public RenderToken getRenderTokenForClient(InsightSurfaceClientInfo clientInfo) {
        return mClientRegistry.getRenderTokenForClient(clientInfo);
    }

    /** Update the client info of an embedded client. */
    public void updateClientInfo(
            InsightSurfaceClientInfo oldClientInfo, InsightSurfaceClientInfo newClientInfo) {
        mClientRegistry.updateClient(oldClientInfo, newClientInfo);
    }

    @Override
    public boolean isInterestedInInsight(ContextInsight insight) {
        // Embedded insights should be rendered due to a RenderToken, which bypasses this filter.
        // We don't want any other random insights.
        return false;
    }

    @Override
    public void render(@NonNull ContextInsight insight, RenderToken renderToken) {
        mExecutor.execute(() -> {
            if (mClientRegistry.isEmpty()) {
                logDebug("NO embedded surface clients!");
                return;
            }

            if (mVisualizerRegistry.isEmpty()) {
                logDebug("NO visualizers!");
                return;
            }

            final InsightSurfaceClientInfo client = clientFromRenderToken(renderToken);
            if (client == null) {
                logDebug("no client found for insight [" + insight + "]");
                return;
            }

            mVisualizerRegistry.createVisualizationForClient(insight, client, renderToken);
        });
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    private InsightSurfaceClientInfo clientFromRenderToken(RenderToken renderToken) {
        if (mClientRegistry.isEmpty()) {
            return null;
        }

        return mClientRegistry.getClientForRenderToken(renderToken);
    }

    private static void logDebug(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, msg);
        }
    }
}
