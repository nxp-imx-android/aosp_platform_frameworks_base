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

package com.android.systemui.personalcontext

import android.content.Context
import android.service.personalcontext.RenderToken
import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService
import android.service.personalcontext.insight.PublishedContextInsight
import android.util.Log
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.personalcontext.visualizer.session.VisualizerSession
import com.android.systemui.personalcontext.visualizer.session.VisualizerSessionFactory
import com.android.systemui.personalcontext.visualizer.templates.LocalInsightSurfaceClientInfo
import com.android.systemui.personalcontext.visualizer.templates.VisualizerTemplateFactory
import java.util.UUID
import javax.inject.Inject

/**
 * Implements an {@link InsightSurfaceVisualizerService}, which is responsible for creating {@link
 * SurfaceView}s for remote clients when {@link ContextInsight}s are received.
 */
class SysuiVisualizerService
@Inject
constructor(
    private val templateFactory: VisualizerTemplateFactory,
    private val sessionFactory: VisualizerSessionFactory,
    private val composeViewFactory: ComposeViewFactory,
) : InsightSurfaceVisualizerService() {

    @VisibleForTesting val sessions = mutableMapOf<UUID, VisualizerSession>()
    private val viewsAwaitingClientConnection = mutableMapOf<UUID, ComposeView>()

    override fun onClientConnected(info: InsightSurfaceClientInfo) {
        Log.d(TAG, "onClientConnected: ${info.id}")

        val view = viewsAwaitingClientConnection.remove(info.id)
        if (view == null) {
            Log.e(TAG, "No view for connected client")
            return
        }

        sessions[info.id] = sessionFactory.createSession(info.id, view)
    }

    override fun onCreateEmbeddedView(
        context: Context,
        publishedInsight: PublishedContextInsight,
        renderToken: RenderToken?,
        info: InsightSurfaceClientInfo,
    ): View? {
        Log.d(TAG, "onCreateEmbeddedView: ${info.id}")

        if (sessions[info.id] != null) {
            Log.e(TAG, "Session already exists for client: ${info.id}")
            return null
        }

        val insight = publishedInsight.insight
        val template = templateFactory.createTemplate(insight, info) ?: return null

        return composeViewFactory.createComposeView(context).apply {
            viewsAwaitingClientConnection[info.id] = this
            setContent {
                CompositionLocalProvider(LocalInsightSurfaceClientInfo provides info) {
                    template.Content(insight)
                }
            }
        }
    }

    override fun onClientDisconnected(info: InsightSurfaceClientInfo) {
        Log.d(TAG, "onClientDisconnected: ${info.id}")

        val session = sessions.remove(info.id)
        if (session == null) {
            Log.e(TAG, "Session not found: ${info.id}")
            return
        }

        session.destroy()
    }

    companion object {
        const val TAG = "SysuiVisualizerService"
    }
}
