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
 * A visitor interface for traversing a {@link ContextInsight} hierarchy.
 *
 * <p>This interface defines methods for visiting each type of insight, allowing for different
 * operations to be performed on each type.
 *
 * @hide
 */
public interface InsightVisitor {
    /**
     * Visits an {@link ActionableInsight}.
     *
     * @param insight The insight to visit.
     */
    default void visit(ActionableInsight insight) {}

    /**
     * Visits a {@link DisplayInsight}.
     *
     * @param insight The insight to visit.
     */
    default void visit(DisplayInsight insight) {}

    /**
     * Visits an {@link InsightCollection}.
     *
     * @param insight The insight collection to visit.
     */
    default void visit(InsightCollection insight) {}

    /**
     * Visits an unknown {@link ContextInsight} type.
     *
     * @param insight The unknown insight to visit.
     */
    default void visitUnknown(ContextInsight insight) {}
}
