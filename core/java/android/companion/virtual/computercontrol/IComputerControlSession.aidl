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

import android.companion.virtual.computercontrol.IInteractiveMirrorDisplay;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.view.Surface;

/**
 * Interface for computer control session management.
 *
 * @hide
 */
interface IComputerControlSession {

    /** Returns the ID of the single trusted virtual display for this session. */
    int getVirtualDisplayId();

    /** Injects a key event into the trusted virtual display. */
    void sendKeyEvent(in VirtualKeyEvent event);

    /** Injects a touch event into the trusted virtual display. */
    void sendTouchEvent(in VirtualTouchEvent event);

    /** Creates an interactive virtual display, mirroring the trusted one. */
    IInteractiveMirrorDisplay createInteractiveMirrorDisplay(
            int width, int height, in Surface surface);

    /** Closes this session. */
    void close();
}
