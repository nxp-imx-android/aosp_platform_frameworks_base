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
import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService
import android.service.personalcontext.insight.ContextInsight
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
) : InsightSurfaceVisualizerService() {

    @VisibleForTesting val sessions = mutableMapOf<UUID, VisualizerSession>()

    override fun onClientConnected(info: InsightSurfaceClientInfo) {
        if (info.id in sessions) {
            Log.e(TAG, "Client already connected: ${info.id}")
            return
        }

        sessions[info.id] = sessionFactory.createSession(info.id)
    }

    override fun onCreateEmbeddedView(
        context: Context,
        insights: List<ContextInsight>,
        info: InsightSurfaceClientInfo,
    ): View? {
        val session = sessions[info.id]
        if (session == null) {
            Log.e(TAG, "Session not found: ${info.id}")
            return null
        }

        val template = templateFactory.createTemplate(insights, info) ?: return null

        return ComposeView(context).apply {
            session.attachToView(this)
            setContent {
                CompositionLocalProvider(LocalInsightSurfaceClientInfo provides info) {
                    template.Content(insights)
                }
            }
        }
    }

    override fun onClientDisconnected(info: InsightSurfaceClientInfo) {
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
