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

/** @hide */
public class EmbeddedInsightRenderer implements Renderer {
    private static final String TAG = "EmbeddedInsightRenderer";

    private final UUID mComponentId = UUID.randomUUID();
    private final ClientRegistry mClientRegistry = new ClientRegistry();

    public EmbeddedInsightRenderer() {
    }

    /**
     * Register an insight surface client and return the {@link RenderToken} associated with that
     * client.
     */
    public RenderToken registerInsightSurfaceClient(InsightSurfaceClientInfo clientInfo) {
        logDebug("registering insight surface client, id=" + clientInfo.getId());

        final RenderToken clientRenderToken = new RenderToken.RenderTokenBuilder()
                .setRendererComponentId(mComponentId)
                .build();
        mClientRegistry.addClient(clientInfo, clientRenderToken);

        // Link the client to death so we can unregister it if it dies.
        try {
            clientInfo.getCallback().asBinder().linkToDeath(() -> {
                logDebug("client has died: " + clientInfo.getId());
                unregisterInsightSurfaceClient(clientInfo.getId());
            }, 0);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        return clientRenderToken;
    }

    /** Return the renderer's registered clients. */
    @VisibleForTesting
    public List<InsightSurfaceClientInfo> getClients() {
        return mClientRegistry.getClients();
    }

    /** Unregister the insight surface client with the given id. */
    public void unregisterInsightSurfaceClient(UUID id) {
        logDebug("unregistering insight surface client, id=" + id);
        mClientRegistry.removeClient(id);
    }

    /** Gets the {@link RenderToken} for the given {@link InsightSurfaceClientInfo}. */
    public RenderToken getRenderTokenForClient(InsightSurfaceClientInfo clientInfo) {
        return mClientRegistry.getRenderTokenForClient(clientInfo);
    }

    @Override
    public boolean isInterestedInInsight(ContextInsight insight) {
        // Embedded insights should be rendered due to a RenderToken, which bypasses this filter.
        // We don't want any other random insights.
        return false;
    }

    @Override
    public void render(@NonNull ContextInsight insight) {
        final InsightSurfaceClientInfo client = clientFromInsight(insight);
        if (client == null) {
            logDebug("no client found for insight [" + insight + "]");
        }
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    private InsightSurfaceClientInfo clientFromInsight(ContextInsight insight) {
        if (mClientRegistry.isEmpty()) {
            return null;
        }

        return mClientRegistry.getClientForRenderToken(insight.getRenderToken());
    }

    private static void logDebug(String msg) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, msg);
        }
    }
}
