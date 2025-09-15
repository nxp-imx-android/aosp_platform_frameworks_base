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

package com.android.systemui.screencapture

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger.UiEventEnum

/** Enum of available screen capture events. */
enum class ScreenCaptureEvent(private val mId: Int) : UiEventEnum {

    @UiEvent(doc = "Closed the large-screen pre-capture UI without any capture")
    SCREEN_CAPTURE_LARGE_SCREEN_CLOSE_UI_WITHOUT_CAPTURE(2486),
    @UiEvent(doc = "Requested a fullscreen screenshot from the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_FULLSCREEN_SCREENSHOT_REQUESTED(2490),
    @UiEvent(doc = "Requested a partial screenshot from the large-screen pre-capture UI")
    SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_REQUESTED(2491),
    @UiEvent(doc = "Invoked partial screenshot using the keyboard shortcut \"Meta + Ctrl + S\"")
    SCREEN_CAPTURE_LARGE_SCREEN_PARTIAL_SCREENSHOT_KEYBOARD_SHORTCUT(2495);

    override fun getId(): Int = mId
}
