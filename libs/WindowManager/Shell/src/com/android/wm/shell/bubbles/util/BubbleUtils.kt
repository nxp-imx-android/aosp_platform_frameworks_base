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

package com.android.wm.shell.bubbles.util

import android.app.WindowConfiguration
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.window.flags.Flags
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper

/**
 * Returns a [WindowContainerTransaction] that includes the necessary operations of entering or
 * exiting Bubble.
 */
private fun getBubbleTransaction(
    token: WindowContainerToken,
    toBubble: Boolean,
    isAppBubble: Boolean,
): WindowContainerTransaction {
    val wct = WindowContainerTransaction()
    wct.setWindowingMode(
        token,
        if (toBubble)
            WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
        else
            WindowConfiguration.WINDOWING_MODE_UNDEFINED,
    )
    wct.setAlwaysOnTop(token, toBubble /* alwaysOnTop */)
    if (!toBubble || isAppBubble) {
        // We only set launch next to Bubble for App Bubble, since new Task opened from Chat
        // Bubble should be launched in fullscreen.
        // Always reset everything when exit bubble.
        wct.setLaunchNextToBubble(token, toBubble /* launchNextToBubble */)
    }
    if (Flags.excludeTaskFromRecents()) {
        wct.setTaskForceExcludedFromRecents(token, toBubble /* forceExcluded */)
    }
    if (Flags.disallowBubbleToEnterPip()) {
        wct.setDisablePip(token, toBubble /* disablePip */)
    }
    if (BubbleAnythingFlagHelper.enableBubbleAnything()) {
        wct.setDisableLaunchAdjacent(token, toBubble /* disableLaunchAdjacent */)
    }
    return wct
}

/**
 * Returns a [WindowContainerTransaction] that includes the necessary operations of entering Bubble.
 *
 * @param isAppBubble App Bubble has some different UX from Chat Bubble.
 */
fun getEnterBubbleTransaction(
    token: WindowContainerToken,
    isAppBubble: Boolean,
): WindowContainerTransaction {
    return getBubbleTransaction(
        token,
        toBubble = true,
        isAppBubble,
    )
}

/**
 * Returns a [WindowContainerTransaction] that includes the necessary operations of exiting Bubble.
 */
fun getExitBubbleTransaction(
    token: WindowContainerToken,
): WindowContainerTransaction {
    return getBubbleTransaction(
        token,
        toBubble = false,
        // Everything will be reset, so doesn't matter for exit.
        isAppBubble = true,
    )
}

