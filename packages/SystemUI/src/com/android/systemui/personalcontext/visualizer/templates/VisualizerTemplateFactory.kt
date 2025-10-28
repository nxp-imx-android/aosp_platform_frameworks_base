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

package com.android.systemui.personalcontext.visualizer.templates

import android.service.personalcontext.embedded.InsightSurfaceClientInfo
import android.service.personalcontext.insight.ContextInsight
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

interface VisualizerTemplateFactory {
    fun createTemplate(
        insights: List<ContextInsight>,
        clientInfo: InsightSurfaceClientInfo,
    ): VisualizerTemplate?
}

@SysUISingleton
class VisualizerTemplateFactoryImpl @Inject constructor() : VisualizerTemplateFactory {
    override fun createTemplate(
        insights: List<ContextInsight>,
        clientInfo: InsightSurfaceClientInfo,
    ): VisualizerTemplate? {

        val cuj = findTriggerCuj(insights)
        if (cuj == null) {
            Log.w(TAG, "No appropriate cuj found for insights, client id: ${clientInfo.id}")
            return null
        }

        val template = findTemplate(cuj)
        if (template == null) {
            Log.w(TAG, "No appropriate template found for hints, client id: ${clientInfo.id}")
            return null
        }

        val validated = template.validate(insights)
        if (!validated) {
            Log.w(
                TAG,
                "Template failed validation for cuj $cuj, insights: $insights: ${clientInfo.id}",
            )
            return null
        }

        return template
    }

    companion object {
        const val TAG = "VisualizerTemplateImpl"

        private fun findTriggerCuj(insights: List<ContextInsight>): String? = null

        private fun findTemplate(cuj: String): VisualizerTemplate? = null
    }
}
