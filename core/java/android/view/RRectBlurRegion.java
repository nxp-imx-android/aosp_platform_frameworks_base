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

package android.view;

import static android.view.flags.Flags.FLAG_SURFACE_VIEW_SET_BLUR_REGIONS;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Defines a rounded rectangular blur region on the SurfaceView.
 */
@FlaggedApi(FLAG_SURFACE_VIEW_SET_BLUR_REGIONS)
public final class RRectBlurRegion extends BlurRegion {
    private float mLeft;
    private float mTop;
    private float mRight;
    private float mBottom;
    @NonNull
    private float[] mCornerRadius;

    private RRectBlurRegion(@NonNull Builder builder) {
        super(builder);
        this.mLeft = builder.mLeft;
        this.mTop = builder.mTop;
        this.mRight = builder.mRight;
        this.mBottom = builder.mBottom;
        this.mCornerRadius = Arrays.copyOf(builder.mCornerRadius, 8);
    }

    /**
     * Copy constructor.
     *
     * @param other region to copy, must be non-{@code null}
     * @throws IllegalArgumentException if {@code other} is {@code null}
     */
    public RRectBlurRegion(@NonNull RRectBlurRegion other) {
        super(other);
        if (other == null) {
            throw new IllegalArgumentException("RRectBlurRegion is null");
        }
        this.mLeft = other.mLeft;
        this.mTop = other.mTop;
        this.mRight = other.mRight;
        this.mBottom = other.mBottom;
        this.mCornerRadius = Arrays.copyOf(other.mCornerRadius, 8);
    }

    /**
    * @return deep copy of this region
    * @hide
    */
    @Override
    @NonNull
    public RRectBlurRegion copy() {
        return new RRectBlurRegion(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RRectBlurRegion)) return false;
        if (!super.equals(o)) return false;
        RRectBlurRegion that = (RRectBlurRegion) o;
        if (Float.compare(that.mLeft, mLeft) != 0) return false;
        if (Float.compare(that.mTop, mTop) != 0) return false;
        if (Float.compare(that.mRight, mRight) != 0) return false;
        if (Float.compare(that.mBottom, mBottom) != 0) return false;
        return Arrays.equals(mCornerRadius, that.mCornerRadius);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hash(mLeft, mTop, mRight, mBottom);
        result = 31 * result + Arrays.hashCode(mCornerRadius);
        return result;
    }

    /**
     * Flattens the rounded rectangular region into a float array.
     *
     * @param offsetX horizontal offset to apply to the region's coordinates
     * @param offsetY vertical offset to apply to the region's coordinates
     * @return float array containing the region parameters
     * @hide
     */
    @Override
    @NonNull
    public float[] toFloatArray(int offsetX, int offsetY) {
        final float[] floatArray = new float[14];
        floatArray[0] = getBlurRadius();
        floatArray[1] = getAlpha();
        floatArray[2] = mLeft + offsetX;
        floatArray[3] = mTop + offsetY;
        floatArray[4] = mRight + offsetX;
        floatArray[5] = mBottom + offsetY;
        floatArray[6] = mCornerRadius[0];
        floatArray[7] = mCornerRadius[1];
        floatArray[8] = mCornerRadius[2];
        floatArray[9] = mCornerRadius[3];
        floatArray[10] = mCornerRadius[4];
        floatArray[11] = mCornerRadius[5];
        floatArray[12] = mCornerRadius[6];
        floatArray[13] = mCornerRadius[7];
        return floatArray;
    }

    /**
     * Returns the left coordinate of the blur region.
     *
     * @return left coordinate in pixels
     */
    public float getLeft() {
        return mLeft;
    }


    /**
     * Sets the left coordinate of the blur region.
     *
     * @param left left coordinate in pixels
     */
    public void setLeft(float left) {
        mLeft = left;
    }

    /**
     * Returns the top coordinate of the blur region.
     *
     * @return top coordinate in pixels
     */
    public float getTop() {
        return mTop;
    }

    /**
     * Sets the top coordinate of the blur region.
     *
     * @param top top coordinate in pixels
     */
    public void setTop(float top) {
        mTop = top;
    }

    /**
     * Returns the right coordinate of the blur region.
     *
     * @return right coordinate in pixels
     */
    public float getRight() {
        return mRight;
    }

    /**
     * Sets the right coordinate of the blur region.
     *
     * @param right right coordinate in pixels
     */
    public void setRight(float right) {
        mRight = right;
    }

    /**
     * Returns the bottom coordinate of the blur region.
     *
     * @return bottom coordinate in pixels
     */
    public float getBottom() {
        return mBottom;
    }

    /**
     * Sets the bottom coordinate of the blur region.
     *
     * @param bottom bottom coordinate in pixels
     */
    public void setBottom(float bottom) {
        mBottom = bottom;
    }

    /**
     * Returns the corner radii for the blur region.
     *
     * @return array of 8 floats representing the corner radii
     */
    @NonNull
    public float[] getCornerRadius() {
        return Arrays.copyOf(mCornerRadius, mCornerRadius.length);
    }


    /**
     * Sets the corner radii for the blur region.
     *
     * <p>The {@code cornerRadius} values are ordered [topLeftX, topLeftY, topRightX, topRightY,
     * bottomLeftX, bottomLeftY, bottomRightX, bottomRightY].
     *
     * @param cornerRadius array of 8 floats representing the corner radii
     * @throws IllegalArgumentException if {@code cornerRadius} is {@code null} or its length
     * is not 8
     */
    public void setCornerRadius(@NonNull float[] cornerRadius) {
        if (cornerRadius == null || cornerRadius.length != 8) {
            throw new IllegalArgumentException("cornerRadius must contain exactly 8 values");
        }
        mCornerRadius = Arrays.copyOf(cornerRadius, 8);
    }

    /**
     * A builder for creating {@link RRectBlurRegion} objects.
     */
    public static final class Builder extends BlurRegion.Builder<Builder> {
        private float mLeft = 0.0f;
        private float mTop = 0.0f;
        private float mRight = 0.0f;
        private float mBottom = 0.0f;
        @NonNull
        private float[] mCornerRadius = new float[8];

        /**
         * Default constructor for the Builder.
         */
        Builder() {
            super();
        }

        /**
         * Constructs a builder from an existing {@link RRectBlurRegion}.
         *
         * @param other region to copy properties from, must be non-{@code null}
         * @throws IllegalArgumentException if {@code other} is {@code null}
         */
        public Builder(@NonNull RRectBlurRegion other) {
            super(other);
            this.mLeft = other.mLeft;
            this.mTop = other.mTop;
            this.mRight = other.mRight;
            this.mBottom = other.mBottom;
            this.mCornerRadius = Arrays.copyOf(other.mCornerRadius, 8);
        }

        /**
         * Constructs a builder with the specified rounded rectangle coordinates and corner radii.
         *
         * <p>The {@code cornerRadius} values are ordered [topLeftX, topLeftY, topRightX, topRightY,
         * bottomLeftX, bottomLeftY, bottomRightX, bottomRightY]. If {@code cornerRadius} is
         * {@code null}, the corner radii will default to 0.
         *
         * @param left left bound in pixels
         * @param top top bound in pixels
         * @param right right bound in pixels
         * @param bottom bottom bound in pixels
         * @param cornerRadius array of 8 floats representing the corner radii,or {@code null}
         * @throws IllegalArgumentException if {@code cornerRadius} is not {@code null}and
         * its length is not 8
         */
        public Builder(float left, float top, float right, float bottom,
                @Nullable float[] cornerRadius) {
            this.mLeft = left;
            this.mTop = top;
            this.mRight = right;
            this.mBottom = bottom;
            if (cornerRadius == null) {
                this.mCornerRadius = new float[8];
            } else {
                if (cornerRadius.length != 8) {
                    throw new IllegalArgumentException("cornerRadius must contain 8 values");
                }
                this.mCornerRadius = Arrays.copyOf(cornerRadius, 8);
            }
        }

        @Override
        @NonNull
        Builder self() {
            return this;
        }

        /**
         * Builds the {@link RRectBlurRegion} object.
         * @return built {@link RRectBlurRegion}
         */
        @Override
        @NonNull
        public RRectBlurRegion build() {
            return new RRectBlurRegion(this);
        }
    }
}
