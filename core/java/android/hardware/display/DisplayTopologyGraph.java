/*
 * Copyright 2024 The Android Open Source Project
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

package android.hardware.display;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Graph of the displays in {@link android.hardware.display.DisplayTopology} tree.
 *
 * @hide
 */
public class DisplayTopologyGraph {

    private final int mPrimaryDisplayId;
    private final DisplayNode[] mDisplayNodes;

    public DisplayTopologyGraph(int primaryDisplayId, DisplayNode[] displayNodes) {
        mPrimaryDisplayId = primaryDisplayId;
        mDisplayNodes = displayNodes;
    }

    public int getPrimaryDisplayId() {
        return mPrimaryDisplayId;
    }

    public @NonNull List<DisplayNode> getDisplayNodes() {
        return Arrays.asList(mDisplayNodes);
    }

    /** Display in the topology */
    public static class DisplayNode {

        private final int mDisplayId;
        private final int mDensity;
        private final RectF mBoundsInGlobalDp;
        private final AdjacentDisplay[] mAdjacentDisplays;

        public DisplayNode(
                int displayId,
                int density,
                @NonNull RectF boundsInGlobalDp,
                AdjacentDisplay[] adjacentDisplays) {
            mDisplayId = displayId;
            mDensity = density;
            mBoundsInGlobalDp = boundsInGlobalDp;
            mAdjacentDisplays = adjacentDisplays;
        }

        public int getDisplayId() {
            return mDisplayId;
        }

        public int getDensity() {
            return mDensity;
        }

        public @NonNull RectF getBoundsInGlobalDp() {
            return mBoundsInGlobalDp;
        }

        public @NonNull List<AdjacentDisplay> getAdjacentDisplays() {
            return Arrays.asList(mAdjacentDisplays);
        }
    }

    /** Edge to adjacent display */
    public static final class AdjacentDisplay {

        // The logical Id of this adjacent display
        private final int mDisplayId;

        // Side of the other display which touches this adjacent display.
        @DisplayTopology.TreeNode.Position private final int mPosition;

        // The distance from the top edge of the other display to the top edge of this display
        // (in case of POSITION_LEFT or POSITION_RIGHT) or from the left edge of the parent
        // display to the left edge of this display (in case of POSITION_TOP or
        // POSITION_BOTTOM). The unit used is density-independent pixels (dp).
        private final float mOffsetDp;

        /** Constructor for AdjacentDisplay. */
        public AdjacentDisplay(
                int displayId, @DisplayTopology.TreeNode.Position int position, float offsetDp) {
            mDisplayId = displayId;
            mPosition = position;
            mOffsetDp = offsetDp;
        }

        public int getDisplayId() {
            return mDisplayId;
        }

        @DisplayTopology.TreeNode.Position
        public int getPosition() {
            return mPosition;
        }

        public float getOffsetDp() {
            return mOffsetDp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AdjacentDisplay rhs = (AdjacentDisplay) o;
            return this.mDisplayId == rhs.mDisplayId
                    && this.mPosition == rhs.mPosition
                    && this.mOffsetDp == rhs.mOffsetDp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDisplayId, mPosition, mOffsetDp);
        }

        @Override
        public String toString() {
            return "AdjacentDisplay{"
                    + "displayId="
                    + mDisplayId
                    + ", position="
                    + DisplayTopology.TreeNode.positionToString(mPosition)
                    + ", offsetDp="
                    + mOffsetDp
                    + '}';
        }
    }
}
