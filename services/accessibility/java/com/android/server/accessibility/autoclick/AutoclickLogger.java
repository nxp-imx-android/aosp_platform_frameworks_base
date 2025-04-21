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

package com.android.server.accessibility.autoclick;

import com.android.internal.util.FrameworkStatsLog;

public class AutoclickLogger {
    /**
     * Logs an autoclick clicked type, emit when autoclick is sent.
     *
     * @param autoclickTypeFromPanel the selected click type from AutoclickTypePanel
     */
    public static void logSelectedClickType(int autoclickTypeFromPanel) {
        int autoclickType = switch (autoclickTypeFromPanel) {
            case AutoclickTypePanel.AUTOCLICK_TYPE_LEFT_CLICK ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_LEFT_CLICK;
            case AutoclickTypePanel.AUTOCLICK_TYPE_RIGHT_CLICK ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_RIGHT_CLICK;
            case AutoclickTypePanel.AUTOCLICK_TYPE_DOUBLE_CLICK ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_DOUBLE_CLICK;
            case AutoclickTypePanel.AUTOCLICK_TYPE_DRAG ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_DRAG;
            case AutoclickTypePanel.AUTOCLICK_TYPE_LONG_PRESS ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_LONG_PRESS;
            case AutoclickTypePanel.AUTOCLICK_TYPE_SCROLL ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_SCROLL;
            default ->
                    FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED__CLICK_TYPE__AUTOCLICK_TYPE_UNKNOWN;
        };

        FrameworkStatsLog.write(FrameworkStatsLog.AUTOCLICK_EVENT_REPORTED,
                autoclickType);
    }

    /**
     * Logs when autoclick feature is enabled.
     */
    public static void logAutoclickEnabled() {
        FrameworkStatsLog.write(FrameworkStatsLog.AUTOCLICK_ENABLED_REPORTED, true);
    }

    /**
     * Logs autoclick session duration when the feature is disabled.
     *
     * @param sessionDurationSeconds How long the feature was enabled.
     */
    public static void logAutoclickSessionDuration(int sessionDurationSeconds) {
        FrameworkStatsLog.write(FrameworkStatsLog.AUTOCLICK_SESSION_DURATION_REPORTED,
                sessionDurationSeconds);
    }
}
