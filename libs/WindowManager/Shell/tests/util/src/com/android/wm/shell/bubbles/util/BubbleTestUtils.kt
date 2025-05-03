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
import android.os.IBinder
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change.CHANGE_LAUNCH_NEXT_TO_BUBBLE
import com.google.common.truth.Truth.assertThat

/** Verifies the [WindowContainerTransaction] to enter Bubble. */
fun verifyEnterBubbleTransaction(
    wct: WindowContainerTransaction,
    taskToken: IBinder,
    isAppBubble: Boolean,
) {
    // Verify hierarchy ops

    assertThat(wct.hierarchyOps.size).isEqualTo(1)
    val hierarchyOp = wct.hierarchyOps[0]
    assertThat(hierarchyOp.container).isEqualTo(taskToken)
    assertThat(hierarchyOp.isAlwaysOnTop).isTrue()

    // Verify Change

    assertThat(wct.changes.size).isEqualTo(1)
    assertThat(wct.changes[taskToken]).isNotNull()
    val change = wct.changes[taskToken]!!
    assertThat(change.windowingMode).isEqualTo(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
    if (isAppBubble) {
        assertThat(change.launchNextToBubble).isTrue()
    } else {
        assertThat(change.changeMask and CHANGE_LAUNCH_NEXT_TO_BUBBLE).isEqualTo(0)
    }
    assertThat(change.forceExcludedFromRecents).isTrue()
    assertThat(change.disablePip).isTrue()
    assertThat(change.disableLaunchAdjacent).isTrue()
}

/** Verifies the [WindowContainerTransaction] to exit Bubble. */
fun verifyExitBubbleTransaction(wct: WindowContainerTransaction, taskToken: IBinder) {
    // Verify hierarchy ops

    assertThat(wct.hierarchyOps.size).isEqualTo(1)
    val hierarchyOp = wct.hierarchyOps[0]
    assertThat(hierarchyOp.container).isEqualTo(taskToken)
    assertThat(hierarchyOp.isAlwaysOnTop).isFalse()

    // Verify Change

    assertThat(wct.changes.size).isEqualTo(1)
    assertThat(wct.changes[taskToken]).isNotNull()
    val change = wct.changes[taskToken]!!
    assertThat(change.windowingMode).isEqualTo(WindowConfiguration.WINDOWING_MODE_UNDEFINED)
    assertThat(change.launchNextToBubble).isFalse()
    assertThat(change.forceExcludedFromRecents).isFalse()
    assertThat(change.disablePip).isFalse()
    assertThat(change.disableLaunchAdjacent).isFalse()
}