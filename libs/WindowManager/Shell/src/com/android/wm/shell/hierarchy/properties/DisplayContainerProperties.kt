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
package com.android.wm.shell.hierarchy.properties

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Display.DEFAULT_DISPLAY
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty

/**
 * Properties for a container that is associated with a display in the WindowManager hierarchy.
 */
class DisplayContainerProperties(
    @WmSyncedProperty val displayId: Int,
) : ContainerProperties() {

    // A display context for this display, this should not be used directly and does not need to be
    // copied or diffed
    private lateinit var cachedContext: Context

    /**
     * Returns a display context for this display.
     */
    fun getDisplayContext(baseContext: Context): Context {
        if (::cachedContext.isInitialized) {
            return cachedContext
        }
        cachedContext = if (displayId == DEFAULT_DISPLAY) {
            baseContext
        } else {
            val displayMgr = baseContext.getSystemService(DisplayManager::class.java)
            val display = displayMgr!!.getDisplay(displayId)
            baseContext.createDisplayContext(display)
        }
        return cachedContext
    }

    /**
     * This method is only called during a display change notification, and may be removed in the
     * future if we can migrate entirely to transitions for reporting display changes.
     */
    fun updateFromDisplayLayout(displayLayout: DisplayLayout) {
        // TODO: Fill in more properties
        config.windowConfiguration.bounds.set(0, 0, displayLayout.width(), displayLayout.height())
        config.windowConfiguration.rotation = displayLayout.rotation()
        config.orientation = if (displayLayout.width() >= displayLayout.height()) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
    }

    /** @see ContainerProperties.copy */
    override fun copy(): DisplayContainerProperties {
        return DisplayContainerProperties(displayId).apply {
            copyFrom(this@DisplayContainerProperties)
        }
    }

    /** @see ContainerProperties.propsToString */
    override fun propsToString(): String {
        return "#$displayId " + super.propsToString()
    }

    /** @see ContainerProperties.getTypeName */
    override fun getTypeName(): String {
        return "Display"
    }
}