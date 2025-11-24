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

import android.view.FrameRateVelocityPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Represents the mapping between frame rate and velocity.
 * This data is used to determine the appropriate frame rate based on the velocity of motion.
 */
public class FrameRateVelocityData {

    private FrameRateVelocityData() {}

    /**
     * Load the frame rate / velocity mapping from DisplayConfiguration
     * and return the value.
     * Sample input from DisplayConfiguration:
     *
     * <frameRateVelocityMapping>
     *   <point>
     *     <first>120.0</first>
     *     <second>300.0</second>
     *   </point>
     *   <point>
     *     <first>80.0</first>
     *     <second>125.0</second>
     *   </point>
     *   <point>
     *     <first>60.0</first>
     *     <second>0.0</second>
     *   </point>
     * </frameRateVelocityMapping>
     *
     * @return A List of FrameRateVelocityPoint
     */
    public static List<FrameRateVelocityPoint> load(DisplayConfiguration config) {
        List<FrameRateVelocityPoint> pointList = getDefaultMapping();

        if (config.getFrameRateVelocityMapping() != null) {
            var points = config.getFrameRateVelocityMapping().getPoint();
            if (points != null && !points.isEmpty()) {
                List<FrameRateVelocityPoint> entries = new ArrayList<>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    final var point = points.get(i);
                    entries.add(new FrameRateVelocityPoint(
                            point.getFirst().floatValue(),
                            point.getSecond().floatValue()));
                }
                pointList = entries;
            }
        }

        Collections.sort(pointList, Comparator.comparingDouble(
                point -> point.getFramePerSecond()));
        verifyFrameRateVelocityData(pointList); // Verify the sorted list
        return pointList;
    }

    private static void verifyFrameRateVelocityData(List<FrameRateVelocityPoint> sortedPoints) {
        if (sortedPoints == null || sortedPoints.size() <= 1) return;
        for (int i = 1; i < sortedPoints.size(); i++) {
            FrameRateVelocityPoint prev = sortedPoints.get(i - 1);
            FrameRateVelocityPoint curr = sortedPoints.get(i);

            if (prev.getFramePerSecond() == curr.getFramePerSecond()
                    || prev.getPixelPerSecond() == curr.getPixelPerSecond()) {
                throw new IllegalStateException("Found two entries in the frame rate"
                        + " veolicy map with the same frame per second: " + prev + ", " + curr);
            } else if (prev.getFramePerSecond() > curr.getFramePerSecond()
                    || prev.getPixelPerSecond() >= curr.getPixelPerSecond()) {
                throw new IllegalStateException("Found two entries in the frame rate veolicy map"
                        + " with increasing frame per second but decreasing pixel per second: "
                        + prev + ", " + curr);
            }
        }
    }

    private static List<FrameRateVelocityPoint> getDefaultMapping() {
        return Arrays.asList(
            new FrameRateVelocityPoint(120, 300),
            new FrameRateVelocityPoint(80, 125),
            new FrameRateVelocityPoint(60, 0)
        );
    }
}
