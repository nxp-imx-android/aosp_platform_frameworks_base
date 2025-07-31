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

package com.android.server.display;

import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.util.SparseIntArray;

import com.android.internal.util.FrameworkStatsLog;

public final class DisplayFrameworkStatsLogger {

    /** Logs DisplayEventCallbackOccurred push atom */
    public void logDisplayEvent(@DisplayManagerGlobal.DisplayEvent int event,
            SparseIntArray notifiedUids) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                toProtoEventType(event),
                notifiedUids.copyKeys());
    }

    /**
     * Maps DisplayEvent to atom. Default case "unknown" is required when defining an atom.
     * Currently private display events {@link DisplayManager.PrivateEventType} are marked as
     * unknown.
     */
    private int toProtoEventType(@DisplayManagerGlobal.DisplayEvent int event) {
        return switch (event) {
            case DisplayManagerGlobal.EVENT_DISPLAY_ADDED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_ADDED;
            case DisplayManagerGlobal.EVENT_DISPLAY_REMOVED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_REMOVED;
            case DisplayManagerGlobal.EVENT_DISPLAY_BASIC_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_CHANGED;
            case DisplayManagerGlobal.EVENT_DISPLAY_REFRESH_RATE_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_REFRESH_RATE_CHANGED;
            case DisplayManagerGlobal.EVENT_DISPLAY_STATE_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_STATE_CHANGED;
            case DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_BRIGHTNESS_CHANGED;
            default -> FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_UNKNOWN;
        };
    }
}
