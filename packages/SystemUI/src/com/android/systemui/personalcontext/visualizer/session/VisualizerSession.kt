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

package com.android.systemui.personalcontext.visualizer.session

import android.util.Log
import android.view.View
import com.android.systemui.compose.ComposeInitializer
import java.util.UUID

/**
 * A {@link VisualizerSession} maintains the lifecycle state needed to support sending {@link
 * ComposeView}s to remote {@link SurfaceView}s. It is also responsible for other view interactions
 * (e.g. embedded scrolling, etc.).
 */
class VisualizerSession(val uuid: UUID) {

    private var view: View? = null

    fun attachToView(view: View) {
        if (this.view == null) {
            Log.e(TAG, "Session is already attached to a view!")
            return
        }

        this.view = view
        ComposeInitializer.onAttachedToWindow(view)
    }

    fun destroy() {
        view?.let { ComposeInitializer.onDetachedFromWindow(it) }
        view = null
    }

    private companion object {
        const val TAG = "VisualizerSession"
    }
}
