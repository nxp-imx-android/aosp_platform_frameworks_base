/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags.appCompatRefactoring
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleController
import com.android.wm.shell.compatui.letterbox.lifecycle.toLetterboxLifecycleEvent
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/**
 * The [TransitionObserver] to handle Letterboxing events in Shell delegating to a
 * [LetterboxLifecycleController].
 */
class DelegateLetterboxTransitionObserver(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val letterboxLifecycleController: LetterboxLifecycleController,
) : Transitions.TransitionObserver {

    companion object {
        @JvmStatic
        private val TAG = "DelegateLetterboxTransitionObserver"
    }

    init {
        if (appCompatRefactoring()) {
            logV("Initializing LetterboxTransitionObserver")
            shellInit.addInitCallback({
                transitions.registerObserver(this)
            }, this)
        }
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction
    ) {
        info.changes.forEach {
            letterboxLifecycleController.onLetterboxLifecycleEvent(
                it.toLetterboxLifecycleEvent(),
                startTransaction,
                finishTransaction
            )
        }
    }

    private fun logV(msg: String) {
        ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", TAG, msg)
    }
}
