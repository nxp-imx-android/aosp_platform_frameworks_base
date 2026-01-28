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

package com.android.internal.widget.remotecompose.core.operations.layout.managers;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.widget.remotecompose.core.Operation;
import com.android.internal.widget.remotecompose.core.Operations;
import com.android.internal.widget.remotecompose.core.PaintContext;
import com.android.internal.widget.remotecompose.core.WireBuffer;
import com.android.internal.widget.remotecompose.core.operations.layout.Component;
import com.android.internal.widget.remotecompose.core.operations.layout.LayoutComponent;
import com.android.internal.widget.remotecompose.core.operations.layout.measure.ComponentMeasure;
import com.android.internal.widget.remotecompose.core.operations.layout.measure.MeasurePass;
import com.android.internal.widget.remotecompose.core.operations.layout.measure.Size;
import com.android.internal.widget.remotecompose.core.operations.layout.modifiers.WidthInModifierOperation;

import java.util.ArrayList;
import java.util.List;

public class FlowLayout extends RowLayout {

    public FlowLayout(
            @Nullable Component parent,
            int componentId, int animationId, float x, float y, float width, float height,
            int horizontalPositioning, int verticalPositioning, float spacedBy) {
        super(parent, componentId, animationId, x, y, width, height, horizontalPositioning,
                verticalPositioning, spacedBy);
    }

    public FlowLayout(@Nullable Component parent, int componentId, int animationId,
            int horizontalPositioning, int verticalPositioning, float spacedBy) {
        super(parent, componentId, animationId, horizontalPositioning, verticalPositioning,
                spacedBy);
    }

    /**
     * The OP_CODE for this command
     *
     * @return the opcode
     */
    public static int id() {
        return Operations.LAYOUT_FLOW;
    }

    /**
     * Write the operation to the buffer
     *
     * @param buffer                wire buffer
     * @param componentId           component id
     * @param animationId           animation id (-1 if not set)
     * @param horizontalPositioning horizontal positioning rules
     * @param verticalPositioning   vertical positioning rules
     * @param spacedBy              spaced by value
     */
    public static void apply(
            @NonNull WireBuffer buffer,
            int componentId,
            int animationId,
            int horizontalPositioning,
            int verticalPositioning,
            float spacedBy) {
        buffer.start(id());
        buffer.writeInt(componentId);
        buffer.writeInt(animationId);
        buffer.writeInt(horizontalPositioning);
        buffer.writeInt(verticalPositioning);
        buffer.writeFloat(spacedBy);
    }

    /**
     * Read this operation and add it to the list of operations
     *
     * @param buffer     the buffer to read
     * @param operations the list of operations that will be added to
     */
    public static void read(@NonNull WireBuffer buffer, @NonNull List<Operation> operations) {
        int componentId = buffer.readInt();
        int animationId = buffer.readInt();
        int horizontalPositioning = buffer.readInt();
        int verticalPositioning = buffer.readInt();
        float spacedBy = buffer.readFloat();
        operations.add(
                new FlowLayout(
                        null,
                        componentId,
                        animationId,
                        horizontalPositioning,
                        verticalPositioning,
                        spacedBy));
    }

    private boolean hasWeight(@NonNull Component c) {
        if (c instanceof LayoutComponent
                && ((LayoutComponent) c).getWidthModifier().hasWeight()) {
            return true;
        }
        return false;
    }

    private ArrayList<ArrayList<Component>> segmentComponents(@NonNull PaintContext context,
            float maxWidth,
            float maxHeight,
            @NonNull MeasurePass measure) {
        ArrayList<ArrayList<Component>> rows = new ArrayList<>();

        // New Row
        ArrayList<Component> currentRow = new ArrayList<>();
        rows.add(currentRow);
        float currentWidth = 0;

        for (Component c : mChildrenComponents) {
            // get the width of the component
            float componentWidth = 0f;
            if (measure.get(c).isGone()) {
                componentWidth = 0f;
            } else if (hasWeight(c)) {
                // need to check if we have minimum width
                WidthInModifierOperation widthInConstraints =
                        ((LayoutComponent) c).getWidthModifier().getWidthIn();
                if (widthInConstraints != null) {
                    float min = widthInConstraints.getMin();
                    if (min != -1) {
                        componentWidth = min;
                    }
                }
            } else {
                // Let's measure it
                c.measure(context, 0f, maxWidth, 0f, maxHeight, measure);
                ComponentMeasure m = measure.get(c);
                componentWidth = m.getW();
            }

            if (componentWidth + currentWidth > maxWidth) {
                // New row
                currentRow = new ArrayList<>();
                rows.add(currentRow);
                currentWidth = 0;
            }
            currentRow.add(c);
            currentWidth += componentWidth;
        }
        return rows;
    }

    @Override
    public void computeWrapSize(
            @NonNull PaintContext context,
            float minWidth, float maxWidth,
            float minHeight, float maxHeight,
            boolean horizontalWrap,
            boolean verticalWrap,
            @NonNull MeasurePass measure,
            @NonNull Size size) {
        ArrayList<ArrayList<Component>> rows = segmentComponents(context, maxWidth, maxHeight,
                measure);
        Size rowSize = new Size(0f, 0f);
        float width = minWidth;
        float height = 0;
        for (ArrayList<Component> row : rows) {
            super.computeWrapSize(context, minWidth, maxWidth, minHeight, maxHeight, horizontalWrap,
                    verticalWrap, measure, rowSize, row);
            width = Math.max(width, rowSize.getWidth());
            height += rowSize.getHeight();
        }
        width = Math.min(Math.max(minWidth, width), maxWidth);
        height = Math.min(Math.max(minHeight, height), maxHeight);
        size.setWidth(width);
        size.setHeight(height);
    }

    @Override
    public void computeSize(
            @NonNull PaintContext context,
            float minWidth,
            float maxWidth,
            float minHeight,
            float maxHeight,
            @NonNull MeasurePass measure) {
        ArrayList<ArrayList<Component>> rows = segmentComponents(context, maxWidth,
                maxHeight, measure);
        for (ArrayList<Component> row : rows) {
            float mw = maxWidth;
            for (Component child : row) {
                child.measure(context, minWidth, mw, minHeight, maxHeight, measure);
                ComponentMeasure m = measure.get(child);
                if (!m.isGone()) {
//                    mw -= m.getW();
                }
            }
        }
    }

    @Override
    public void internalLayoutMeasure(@NonNull PaintContext context, @NonNull MeasurePass measure) {
        if (mChildrenComponents.isEmpty()) {
            return;
        }
        ComponentMeasure selfMeasure = measure.get(this);
        float selfWidth = selfMeasure.getW() - mPaddingLeft - mPaddingRight;
        float selfHeight = selfMeasure.getH() - mPaddingTop - mPaddingBottom;
        if (mComponentModifiers.hasHorizontalScroll()) {
            selfWidth =
                    mComponentModifiers.getHorizontalScrollDimension()
                            - mPaddingLeft
                            - mPaddingRight;
        }
        if (mComponentModifiers.hasVerticalScroll()) {
            selfHeight =
                    mComponentModifiers.getVerticalScrollDimension() - mPaddingTop - mPaddingBottom;
        }
        ArrayList<ArrayList<Component>> rows = segmentComponents(context, selfWidth, selfHeight,
                measure);
        float positionX = 0f;
        float positionY = 0f;
        Size rowSize = new Size(0f, 0f);
        float rowWidth = selfWidth;
        float rowsHeight = 0f;
        for (ArrayList<Component> row : rows) {
            rowsHeight += minIntrinsicHeight(context.getContext(), row);
        }
        switch (mVerticalPositioning) {
            case RowLayout.CENTER:
                positionY = (selfHeight - rowsHeight) / 2f;
                break;
            case RowLayout.BOTTOM:
                positionY = selfHeight - rowsHeight;
        }

        for (ArrayList<Component> row : rows) {
            float rowHeight = minIntrinsicHeight(context.getContext(), row);
            internalLayoutMeasure(context, measure, row, rowWidth, rowHeight, positionX, positionY,
                    rowSize);
            positionY += rowSize.getHeight();
        }
    }
}
