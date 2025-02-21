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
package com.android.systemui.model

import android.util.Log
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.settings.DisplayTracker
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags
import dalvik.annotation.optimization.NeverCompile
import java.io.PrintWriter

/** Contains sysUi state flags and notifies registered listeners whenever changes happen. */
@SysUISingleton
class SysUiState(
    private val displayTracker: DisplayTracker,
    private val sceneContainerPlugin: SceneContainerPlugin?,
) : Dumpable {
    /** Returns the current sysui state flags. */
    @get:SystemUiStateFlags
    @SystemUiStateFlags
    var flags: Long = 0
        private set

    private val callbacks: MutableList<SysUiStateCallback> = ArrayList()
    private var flagsToSet: Long = 0
    private var flagsToClear: Long = 0

    /**
     * Add listener to be notified of changes made to SysUI state. The callback will also be called
     * as part of this function.
     */
    fun addCallback(callback: SysUiStateCallback) {
        callbacks.add(callback)
        callback.onSystemUiStateChanged(flags)
    }

    /** Callback will no longer receive events on state change */
    fun removeCallback(callback: SysUiStateCallback) {
        callbacks.remove(callback)
    }

    fun isFlagEnabled(@SystemUiStateFlags flag: Long): Boolean {
        return (flags and flag) != 0L
    }

    /** Methods to this call can be chained together before calling [.commitUpdate]. */
    fun setFlag(@SystemUiStateFlags flag: Long, enabled: Boolean): SysUiState {
        var enabled = enabled
        val overrideOrNull = sceneContainerPlugin?.flagValueOverride(flag)
        if (overrideOrNull != null && enabled != overrideOrNull) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "setFlag for flag $flag and value $enabled overridden to $overrideOrNull by scene container plugin",
                )
            }

            enabled = overrideOrNull
        }

        if (enabled) {
            flagsToSet = flagsToSet or flag
        } else {
            flagsToClear = flagsToClear or flag
        }
        return this
    }

    /** Call to save all the flags updated from [.setFlag]. */
    fun commitUpdate(displayId: Int) {
        updateFlags(displayId)
        flagsToSet = 0
        flagsToClear = 0
    }

    private fun updateFlags(displayId: Int) {
        if (displayId != displayTracker.defaultDisplayId) {
            // Ignore non-default displays for now
            Log.w(TAG, "Ignoring flag update for display: $displayId", Throwable())
            return
        }

        var newState = flags
        newState = newState or flagsToSet
        newState = newState and flagsToClear.inv()
        notifyAndSetSystemUiStateChanged(newState, flags)
    }

    /** Notify all those who are registered that the state has changed. */
    private fun notifyAndSetSystemUiStateChanged(newFlags: Long, oldFlags: Long) {
        if (DEBUG) {
            Log.d(TAG, "SysUiState changed: old=$oldFlags new=$newFlags")
        }
        if (newFlags != oldFlags) {
            callbacks.forEach { callback: SysUiStateCallback ->
                callback.onSystemUiStateChanged(newFlags)
            }

            flags = newFlags
        }
    }

    @NeverCompile
    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println("SysUiState state:")
        pw.print("  mSysUiStateFlags=")
        pw.println(flags)
        pw.println("    " + QuickStepContract.getSystemUiStateString(flags))
        pw.print("    backGestureDisabled=")
        pw.println(QuickStepContract.isBackGestureDisabled(flags, false /* forTrackpad */))
        pw.print("    assistantGestureDisabled=")
        pw.println(QuickStepContract.isAssistantGestureDisabled(flags))
    }

    /** Callback to be notified whenever system UI state flags are changed. */
    interface SysUiStateCallback {
        /** To be called when any SysUiStateFlag gets updated */
        fun onSystemUiStateChanged(@SystemUiStateFlags sysUiFlags: Long)
    }

    companion object {
        private val TAG: String = SysUiState::class.java.simpleName
        const val DEBUG: Boolean = false
    }
}
