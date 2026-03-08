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
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display.DEFAULT_DISPLAY
import android.view.InsetsState
import android.view.WindowInsets.Type.displayCutout
import android.view.WindowInsets.Type.ime
import android.view.WindowInsets.Type.navigationBars
import android.view.WindowInsets.Type.statusBars
import androidx.core.graphics.toRect
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_INSETS

/**
 * Tracks the current insets state for this display.
 */
class DisplayInsetsState(private val props: DisplayContainerProperties) {
    var insetsState = InsetsState()

    /**
     * Returns the current display bounds inset by the IME.
     */
    fun getImeInsetBounds(): Rect {
        val insetBounds = props.bounds.toRect()
        val insets =
            insetsState.calculateInsets(insetBounds, insetBounds, ime(), false)
        insetBounds.inset(insets)
        return insetBounds
    }

    fun copyFrom(other: DisplayInsetsState) {
        insetsState = other.insetsState
    }

    fun diff(other: DisplayInsetsState, chgs: HierarchyChangeFlags) {
        chgs.compareAndSet(insetsState, other.insetsState, CHANGED_INSETS)
    }

    override fun toString(): String {
        val interestingSources = mutableListOf<String>()
        for (i in 0..<insetsState.sourceSize()) {
            val src = insetsState.sourceAt(i)
            if (insetsTypeToStr.containsKey(src.type)) {
                interestingSources.add("${insetsTypeToStr[src.type]}=${src.frame.toShortString()}")
            }
        }
        return "INSETS " + interestingSources.joinToString(
            separator = ", ",
            prefix = "{",
            postfix = "}"
        )
    }

    companion object {
        private val insetsTypeToStr = mapOf(
            displayCutout() to "cutout",
            ime() to "ime",
            statusBars() to "status",
            navigationBars() to "nav",
        )
    }
}

/**
 * Properties for a container that is associated with a display in the WindowManager hierarchy.
 */
class DisplayContainerProperties(
    @WmSyncedProperty val displayId: Int,
) : ContainerProperties() {

    @WmSyncedProperty
    val insetsState = DisplayInsetsState(this)

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
        val width = displayLayout.width()
        val height = displayLayout.height()
        config.windowConfiguration.bounds.set(0, 0, width, height)
        config.windowConfiguration.maxBounds.set(0, 0, width, height)
        config.windowConfiguration.rotation = displayLayout.rotation()
        config.orientation = if (width >= height) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
    }

    /**
     * Updates the insets state for this display and returns whether the change happened.
     */
    fun updateInsetsState(insetsState: InsetsState): Boolean {
        if (this.insetsState.insetsState != insetsState) {
            this.insetsState.insetsState = insetsState
            return true
        }
        return false
    }

    /** @see ContainerProperties.copyFrom */
    override fun copyFrom(other: ContainerProperties) {
        val otherDisplay = other as DisplayContainerProperties
        insetsState.copyFrom(otherDisplay.insetsState)
        super.copyFrom(other)
    }

    /** @see ContainerProperties.copy */
    override fun copy(): DisplayContainerProperties {
        return DisplayContainerProperties(displayId).apply {
            copyFrom(this@DisplayContainerProperties)
        }
    }

    /** @see ContainerProperties.diff */
    override fun diff(other: ContainerProperties, chgs: HierarchyChangeFlags) {
        super.diff(other, chgs)
        val otherDisplay = other as DisplayContainerProperties
        insetsState.diff(otherDisplay.insetsState, chgs)
    }

    /** @see ContainerProperties.propsToString */
    override fun propsToString(): String {
        return "#$displayId | " + super.propsToString() + " | $insetsState"
    }

    /** @see ContainerProperties.getTypeName */
    override fun getTypeName(): String {
        return "Display#$displayId"
    }
}