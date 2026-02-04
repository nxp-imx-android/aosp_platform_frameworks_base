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

package com.android.wm.shell.windowdecor.caption

import com.android.wm.shell.windowdecor.HandleMenu
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/** Verifies a handle menu was created the given number of times. */
fun HandleMenu.HandleMenuFactory.verifyHandleMenuCreated(times: Int = 1) {
    verify(this, times(times))
        .create(
            mainDispatcher = any(),
            mainScope = any(),
            context = any(),
            taskInfo = any(),
            parentSurface = any(),
            display = any(),
            windowManagerWrapper = any(),
            windowDecorationActions = any(),
            taskResourceLoader = any(),
            layoutResId = anyInt(),
            splitScreenController = any(),
            shouldShowWindowingPill = anyBoolean(),
            shouldShowNewWindowButton = anyBoolean(),
            shouldShowManageWindowsButton = anyBoolean(),
            shouldShowChangeAspectRatioButton = anyBoolean(),
            shouldShowGameControlsButton = anyBoolean(),
            shouldShowDesktopModeButton = anyBoolean(),
            shouldShowRestartButton = anyBoolean(),
            appToWebData = anyOrNull(),
            desktopModeUiEventLogger = any(),
            captionView = any(),
            captionWidth = anyInt(),
            captionHeight = anyInt(),
            captionX = anyInt(),
            captionY = anyInt(),
            surfaceControlBuilderSupplier = any(),
            surfaceControlTransactionSupplier = any(),
            surfaceControlViewHostFactory = any(),
        )
}

/** Verifies a handle menu was created with the given [AppToWebData]. */
fun HandleMenu.HandleMenuFactory.verifyHandleMenuCreated(appToWebData: HandleMenu.AppToWebData?) {
    verify(this)
        .create(
            mainDispatcher = any(),
            mainScope = any(),
            context = any(),
            taskInfo = any(),
            parentSurface = any(),
            display = any(),
            windowManagerWrapper = any(),
            windowDecorationActions = any(),
            taskResourceLoader = any(),
            layoutResId = anyInt(),
            splitScreenController = any(),
            shouldShowWindowingPill = anyBoolean(),
            shouldShowNewWindowButton = anyBoolean(),
            shouldShowManageWindowsButton = anyBoolean(),
            shouldShowChangeAspectRatioButton = anyBoolean(),
            shouldShowGameControlsButton = anyBoolean(),
            shouldShowDesktopModeButton = anyBoolean(),
            shouldShowRestartButton = anyBoolean(),
            appToWebData = eq(appToWebData),
            desktopModeUiEventLogger = any(),
            captionView = any(),
            captionWidth = anyInt(),
            captionHeight = anyInt(),
            captionX = anyInt(),
            captionY = anyInt(),
            surfaceControlBuilderSupplier = any(),
            surfaceControlTransactionSupplier = any(),
            surfaceControlViewHostFactory = any(),
        )
}
