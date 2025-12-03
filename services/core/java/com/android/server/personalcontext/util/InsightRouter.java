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

package com.android.server.personalcontext.util;

import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.DisplayInsight;
import android.service.personalcontext.insight.InsightCollection;

/**
 * A router that dispatches {@link ContextInsight} objects to the appropriate method in an {@link
 * InsightVisitor}.
 *
 * <p>This class contains the dispatch logic, using {@code instanceof} checks to determine the
 * concrete type of the insight and route it to the corresponding visitor method.
 *
 * @hide
 */
public class InsightRouter {
    /**
     * Dispatches the given insight to the appropriate method in the visitor.
     *
     * @param insight The insight to dispatch.
     * @param visitor The visitor that will handle the insight.
     */
    public void dispatch(ContextInsight insight, InsightVisitor visitor) {
        if (insight instanceof ActionableInsight) {
            visitor.visit((ActionableInsight) insight);
        } else if (insight instanceof DisplayInsight) {
            visitor.visit((DisplayInsight) insight);
        } else if (insight instanceof InsightCollection) {
            visitor.visit((InsightCollection) insight);
        } else {
            visitor.visitUnknown(insight);
        }
    }
}
