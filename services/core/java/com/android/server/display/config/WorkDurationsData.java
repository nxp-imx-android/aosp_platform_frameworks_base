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

package com.android.server.display.config;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.feature.flags.Flags;

import java.util.Objects;

public class WorkDurationsData {

    public final int lateWorkDuration;
    public final int earlyWorkDuration;
    public final int appWorkDuration;

    @VisibleForTesting
    public WorkDurationsData(int lateWorkDuration, int earlyWorkDuration, int appWorkDuration) {
        this.lateWorkDuration = lateWorkDuration;
        this.earlyWorkDuration = earlyWorkDuration;
        this.appWorkDuration = appWorkDuration;
    }

    /**
     * Loads the default work durations from the given refresh rate configuration.
     *
     * @param refreshRateConfigs The {@link RefreshRateConfigs} to load the work durations from.
     * @return The loaded default {@link WorkDurationsData}, or null if not available or disabled
     */
    public static WorkDurationsData loadDefaultWorkDurations(
            RefreshRateConfigs refreshRateConfigs) {
        if (Flags.enableWorkDurations() && refreshRateConfigs != null) {
            WorkDurations defaultWorkDurations = refreshRateConfigs.getDefaultWorkDurations();
            return load(defaultWorkDurations);
        }
        return null;
    }

    /**
     * Loads the low-power work durations from the given refresh rate configuration.
     *
     * @param refreshRateConfigs The {@link RefreshRateConfigs} to load the work durations from.
     * @return The loaded low-power {@link WorkDurationsData}, or null if not available or disabled
     */
    public static WorkDurationsData loadLowPowerWorkDurations(
            RefreshRateConfigs refreshRateConfigs) {
        if (Flags.enableWorkDurations() && refreshRateConfigs != null) {
            WorkDurations lowPowerWorkDurations = refreshRateConfigs.getLowPowerWorkDurations();
            return load(lowPowerWorkDurations);
        }
        return null;
    }

    /**
     * Loads the thermal-throttling work durations from the given thermal throttling configuration.
     *
     * @param thermalThrottling The {@link WorkDurationsThrottlingPair} to load the work durations
     * from.
     * @return The loaded thermal-throttling {@link WorkDurations}, or null if not available or has
     * flag disabled
     */
    public static WorkDurationsData loadThermalThrottlingWorkDurations(
            WorkDurationsThrottlingPair thermalThrottling) {
        if (Flags.enableWorkDurations() && thermalThrottling != null) {
            WorkDurations thermalThrottlingWorkDurations =
                    thermalThrottling.getThermalThrottlingWorkDurations();
            return load(thermalThrottlingWorkDurations);
        }
        return null;
    }

    private static WorkDurationsData load(WorkDurations workDurations) {
        if (workDurations != null) {
            return new WorkDurationsData(
                    workDurations.getLateWorkDuration().intValue(),
                    workDurations.getEarlyWorkDuration().intValue(),
                    workDurations.getAppWorkDuration().intValue());
        }
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lateWorkDuration, earlyWorkDuration, appWorkDuration);
    }
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WorkDurationsData other)) return false;
        return (this.lateWorkDuration == other.lateWorkDuration)
                && (this.earlyWorkDuration == other.earlyWorkDuration)
                && (this.appWorkDuration == other.appWorkDuration);
    }

    @Override
    public String toString() {
        return "WorkDurationsData{"
                + "lateWorkDuration=" + lateWorkDuration
                + ", earlyWorkDuration=" + earlyWorkDuration
                + ", appWorkDuration=" + appWorkDuration
                + "}";
    }
}
