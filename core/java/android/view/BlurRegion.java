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
import android.annotation.FloatRange;
import android.annotation.NonNull;
import android.annotation.SuppressLint;

import java.util.Objects;

/**
 * The abstract base class defining a region on the SurfaceView that should have a blur applied.
 */
@FlaggedApi(FLAG_SURFACE_VIEW_SET_BLUR_REGIONS)
public abstract class BlurRegion {

    private float mBlurRadius;
    private float mAlpha;

    /**
     * @param builder builder to construct the BlurRegion from
     * @hide
     */
    protected BlurRegion(@NonNull Builder<?> builder) {
        this.mBlurRadius = builder.mBlurRadius;
        this.mAlpha = builder.mAlpha;
    }

    /**
     * Copy constructor.
     *
     * @param other blur region to copy, must be non-{@code null}
     * @throws IllegalArgumentException if {@code other} is {@code null}
     * @hide
     */
    protected BlurRegion(@NonNull BlurRegion other) {
        if (other == null) {
            throw new IllegalArgumentException("BlurRegion is null");
        }
        this.mBlurRadius = other.mBlurRadius;
        this.mAlpha = other.mAlpha;
    }

    /**
     * Flattens the blur region into a float array.
     *
     * @param offsetX horizontal offset to apply to the region's coordinates
     * @param offsetY vertical offset to apply to the region's coordinates
     * @param scaleX horizontal scale to apply to the region's coordinates
     * @param scaleY vertical scale to apply to the region's coordinates
     * @return float array containing the region parameters
     * @hide
     */
    @NonNull
    public abstract float[] toFloatArray(int offsetX, int offsetY, float scaleX, float scaleY);

    /**
    * @return deep copy of this blur region
    */
    @NonNull
    public abstract BlurRegion copy();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlurRegion)) return false;
        BlurRegion that = (BlurRegion) o;
        return Float.compare(that.mAlpha, mAlpha) == 0
               && Float.compare(that.mBlurRadius, mBlurRadius) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBlurRadius, mAlpha);
    }

    /**
     * Returns the blur radius for this region.
     *
     * @return blur radius in pixels
     */
    public float getBlurRadius() {
        return mBlurRadius;
    }

    /**
     * Sets the blur radius for this region.
     *
     * @param blurRadius blur radius in pixels
     */
    public void setBlurRadius(float blurRadius) {
        mBlurRadius = blurRadius;
    }

    /**
     * Returns the alpha for this region.
     *
     * @return alpha value of the region
     */
    public float getAlpha() {
        return mAlpha;
    }

    /**
     * Sets the alpha for this region.
     *
     * @param alpha alpha value of the region [0.0 - 1.0]
     */
    public void setAlpha(@FloatRange(from = 0.0, to = 1.0) float alpha) {
        mAlpha = alpha;
    }

    /**
     * The builder for creating {@link BlurRegion} objects.
     * @param <T> subclass to be built
     */
    @SuppressLint("StaticFinalBuilder")
    public abstract static class Builder<T extends Builder<T>> {
        private float mBlurRadius = 0.0f;
        private float mAlpha = 1.0f;

        /**
         * @return builder instance
         */
        @SuppressLint("BuilderSetStyle")
        abstract T self();

        /**
         * Default constructor for the Builder.
         */
        Builder() {}

        /**
         * Constructs a builder from an existing BlurRegion.
         *
         * @param other blur to copy properties from
         */
        Builder(@NonNull BlurRegion other) {
            this.mBlurRadius = other.mBlurRadius;
            this.mAlpha = other.mAlpha;
        }

        /**
         * Sets the blur radius.
         *
         * @param blurRadius blur radius in pixels
         * @return this builder
         */
        @NonNull
        public T setBlurRadius(float blurRadius) {
            this.mBlurRadius = blurRadius;
            return self();
        }

        /**
         * Sets the alpha.
         *
         * @param alpha alpha value to set on the region [0.0 - 1.0]
         * @return this builder
         */
        @NonNull
        public T setAlpha(@FloatRange(from = 0.0, to = 1.0) float alpha) {
            this.mAlpha = alpha;
            return self();
        }

        /**
         * Builds the {@link BlurRegion} object.
         * @return built {@link BlurRegion}
         */
        @NonNull
        public abstract BlurRegion build();
    }
}
