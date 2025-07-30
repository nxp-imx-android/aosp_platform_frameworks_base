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

package android.companion.virtual.computercontrol;

import android.view.Surface;

/**
 * Parameters for creating a computer control session.
 *
 * @hide
 */
parcelable ComputerControlSessionParams {

    /** The name of the session. Only used internally and not shown to users. */
    String name;

    /** The width of the display used for the session. */
    int displayWidthPx;

    /** The height of the display used for the session. */
    int displayHeightPx;

    /** The DPI of the display used for the session. */
    int displayDpi;

    /** The surface of the display used for the session. */
    Surface displaySurface;

    /** Whether the display used for the session should remain always unlocked. */
    boolean isDisplayAlwaysUnlocked;
}
