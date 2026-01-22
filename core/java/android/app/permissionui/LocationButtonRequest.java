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
package android.app.permissionui;

import android.annotation.ColorInt;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;
import android.permission.flags.Flags;

import java.util.Objects;

/**
 * Defines the requested visual properties for a location button.
 *
 * <p>An instance of this class is passed to {@link LocationButtonProvider#openSession} to
 * configure the button's appearance, such as its text, colors, and corner radius etc.
 * The appearance can be changed after the session has been opened.
 *
 * @see LocationButtonProvider#openSession
 * @see LocationButtonSession
 */
@FlaggedApi(Flags.FLAG_LOCATION_BUTTON_ENABLED)
public final class LocationButtonRequest implements Parcelable {
    private final int mWidth;
    private final int mHeight;
    private final int mPaddingLeft;
    private final int mPaddingRight;
    private final int mPaddingTop;
    private final int mPaddingBottom;
    @ColorInt
    private final int mBackgroundColor;
    @ColorInt
    private final int mStrokeColor;
    private final int mStrokeWidth;
    private final float mCornerRadius;
    @ColorInt
    private final int mIconTint;
    private final @LocationButtonSession.TextType int mTextType;
    @ColorInt
    private final int mTextColor;
    private final Configuration mConfiguration;

    /**
     * Creates a new {@link LocationButtonRequest} instance.
     * Use {@link Builder} to create instances.
     */
    private LocationButtonRequest(int width, int height, int paddingLeft, int paddingTop,
            int paddingRight, int paddingBottom, @ColorInt int backgroundColor,
            @ColorInt int strokeColor, int strokeWidth, float cornerRadius,
            @ColorInt int iconTint, @LocationButtonSession.TextType int textType,
            @ColorInt int textColor, @NonNull Configuration configuration) {
        this.mWidth = width;
        this.mHeight = height;
        this.mPaddingLeft = paddingLeft;
        this.mPaddingTop = paddingTop;
        this.mPaddingRight = paddingRight;
        this.mPaddingBottom = paddingBottom;
        this.mBackgroundColor = backgroundColor;
        this.mStrokeColor = strokeColor;
        this.mStrokeWidth = strokeWidth;
        this.mCornerRadius = cornerRadius;
        this.mIconTint = iconTint;
        this.mTextType = textType;
        this.mTextColor = textColor;
        this.mConfiguration = configuration;
    }

    /** Returns the width of the button. */
    public int getWidth() {
        return mWidth;
    }

    /** Returns the height of the button. */
    public int getHeight() {
        return mHeight;
    }

    /** Returns the left padding of the button. */
    public int getPaddingLeft() {
        return mPaddingLeft;
    }

    /** Returns the right padding of the button. */
    public int getPaddingRight() {
        return mPaddingRight;
    }

    /** Returns the top padding of the button. */
    public int getPaddingTop() {
        return mPaddingTop;
    }

    /** Returns the bottom padding of the button. */
    public int getPaddingBottom() {
        return mPaddingBottom;
    }

    /** Returns the background color of the button as a {@link ColorInt}. */
    @ColorInt
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    /** Returns the button outline/border color as a {@link ColorInt}. */
    @ColorInt
    public int getStrokeColor() {
        return mStrokeColor;
    }

    /** Returns the button outline/border width. */
    public int getStrokeWidth() {
        return mStrokeWidth;
    }

    /** Returns the corner radius of the button in pixels. */
    public float getCornerRadius() {
        return mCornerRadius;
    }

    /** Returns the icon tint color as a {@link ColorInt}. */
    @ColorInt
    public int getIconTint() {
        return mIconTint;
    }

    /** Returns the text type displayed on the button. */
    public @LocationButtonSession.TextType int getTextType() {
        return mTextType;
    }

    /** Returns the color of the button's text as a {@link ColorInt}. */
    @ColorInt
    public int getTextColor() {
        return mTextColor;
    }

    /** Returns the configuration of the button. */
    @NonNull
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    @Override
    public String toString() {
        return "LocationButtonRequest{"
                + " mWidth=" + mWidth
                + ", mHeight=" + mHeight
                + ", mPaddingLeft=" + mPaddingLeft
                + ", mPaddingTop=" + mPaddingTop
                + ", mPaddingRight=" + mPaddingRight
                + ", mPaddingBottom=" + mPaddingBottom
                + ", mBackgroundColor=" + mBackgroundColor
                + ", mStrokeColor=" + mStrokeColor
                + ", mStrokeWidth=" + mStrokeWidth
                + ", mCornerRadius=" + mCornerRadius
                + ", mIconTint=" + mIconTint
                + ", mTextType=" + mTextType
                + ", mTextColor=" + mTextColor
                + ", mConfiguration=" + mConfiguration
                + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mPaddingLeft);
        dest.writeInt(mPaddingTop);
        dest.writeInt(mPaddingRight);
        dest.writeInt(mPaddingBottom);
        dest.writeInt(mBackgroundColor);
        dest.writeInt(mStrokeColor);
        dest.writeInt(mStrokeWidth);
        dest.writeFloat(mCornerRadius);
        dest.writeInt(mIconTint);
        dest.writeInt(mTextType);
        dest.writeInt(mTextColor);
        dest.writeTypedObject(mConfiguration, flags);
    }

    public static final @NonNull Creator<LocationButtonRequest> CREATOR =
            new Creator<LocationButtonRequest>() {
                @Override
                public LocationButtonRequest createFromParcel(Parcel in) {
                    return new LocationButtonRequest(in.readInt(), in.readInt(), in.readInt(),
                            in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                            in.readInt(), in.readFloat(), in.readInt(), in.readInt(),
                            in.readInt(),
                            Objects.requireNonNull(in.readTypedObject(Configuration.CREATOR)));
                }

                @Override
                public LocationButtonRequest[] newArray(int size) {
                    return new LocationButtonRequest[size];
                }
            };

    /**
     * Builder for {@link LocationButtonRequest}.
     *
     * <p>This builder enforces strict validation: <b>all properties must be set explicitly</b>.
     * Attempting to call {@link #build()} without setting every property will result
     * in an {@link IllegalStateException}.
     */
    public static final class Builder {
        // Bit flags for tracking which fields have been set
        private static final int PROPERTY_WIDTH = 1 << 0;
        private static final int PROPERTY_HEIGHT = 1 << 1;
        private static final int PROPERTY_PADDING_LEFT = 1 << 2;
        private static final int PROPERTY_PADDING_TOP = 1 << 3;
        private static final int PROPERTY_PADDING_RIGHT = 1 << 4;
        private static final int PROPERTY_PADDING_BOTTOM = 1 << 5;
        private static final int PROPERTY_BACKGROUND_COLOR = 1 << 6;
        private static final int PROPERTY_STROKE_COLOR = 1 << 7;
        private static final int PROPERTY_STROKE_WIDTH = 1 << 8;
        private static final int PROPERTY_CORNER_RADIUS = 1 << 9;
        private static final int PROPERTY_ICON_TINT = 1 << 10;
        private static final int PROPERTY_TEXT_TYPE = 1 << 11;
        private static final int PROPERTY_TEXT_COLOR = 1 << 12;
        private static final int PROPERTY_CONFIGURATION = 1 << 13;

        private static final int MASK_ALL_PROPERTIES_SET =
                PROPERTY_WIDTH | PROPERTY_HEIGHT | PROPERTY_PADDING_LEFT | PROPERTY_PADDING_TOP
                        | PROPERTY_PADDING_RIGHT | PROPERTY_PADDING_BOTTOM
                        | PROPERTY_BACKGROUND_COLOR
                        | PROPERTY_STROKE_COLOR | PROPERTY_STROKE_WIDTH | PROPERTY_CORNER_RADIUS
                        | PROPERTY_ICON_TINT | PROPERTY_TEXT_TYPE | PROPERTY_TEXT_COLOR
                        | PROPERTY_CONFIGURATION;

        private int mBuilderFieldsSet = 0;

        private int mWidth;
        private int mHeight;
        private int mPaddingLeft;
        private int mPaddingRight;
        private int mPaddingTop;
        private int mPaddingBottom;
        @ColorInt
        private int mBackgroundColor;
        @ColorInt
        private int mStrokeColor;
        private int mStrokeWidth;
        private float mCornerRadius;
        @ColorInt
        private int mIconTint;
        private @LocationButtonSession.TextType int mTextType;
        @ColorInt
        private int mTextColor;
        private Configuration mConfiguration;

        /**
         * Creates a new Builder.
         */
        public Builder() {
        }

        /**
         * Creates a new Builder from an existing {@link LocationButtonRequest}.
         *
         * @param original The original request to copy properties from.
         */
        public Builder(@NonNull LocationButtonRequest original) {
            mWidth = original.mWidth;
            mHeight = original.mHeight;
            mPaddingLeft = original.mPaddingLeft;
            mPaddingTop = original.mPaddingTop;
            mPaddingRight = original.mPaddingRight;
            mPaddingBottom = original.mPaddingBottom;
            mBackgroundColor = original.mBackgroundColor;
            mStrokeColor = original.mStrokeColor;
            mStrokeWidth = original.mStrokeWidth;
            mCornerRadius = original.mCornerRadius;
            mIconTint = original.mIconTint;
            mTextType = original.mTextType;
            mTextColor = original.mTextColor;
            mConfiguration = original.mConfiguration;
            // Since we are copying a valid object, we know all properties are set.
            mBuilderFieldsSet = MASK_ALL_PROPERTIES_SET;
        }

        /** Sets the width of the button. */
        @NonNull
        public Builder setWidth(int width) {
            mWidth = width;
            mBuilderFieldsSet |= PROPERTY_WIDTH;
            return this;
        }

        /** Sets the height of the button. */
        @NonNull
        public Builder setHeight(int height) {
            mHeight = height;
            mBuilderFieldsSet |= PROPERTY_HEIGHT;
            return this;
        }

        /** Sets the left padding of the button. */
        @NonNull
        public Builder setPaddingLeft(int paddingLeft) {
            mPaddingLeft = paddingLeft;
            mBuilderFieldsSet |= PROPERTY_PADDING_LEFT;
            return this;
        }

        /** Sets the top padding of the button. */
        @NonNull
        public Builder setPaddingTop(int paddingTop) {
            mPaddingTop = paddingTop;
            mBuilderFieldsSet |= PROPERTY_PADDING_TOP;
            return this;
        }

        /** Sets the right padding of the button. */
        @NonNull
        public Builder setPaddingRight(int paddingRight) {
            mPaddingRight = paddingRight;
            mBuilderFieldsSet |= PROPERTY_PADDING_RIGHT;
            return this;
        }

        /** Sets the bottom padding of the button. */
        @NonNull
        public Builder setPaddingBottom(int paddingBottom) {
            mPaddingBottom = paddingBottom;
            mBuilderFieldsSet |= PROPERTY_PADDING_BOTTOM;
            return this;
        }

        /** Sets the background color of the button. */
        @NonNull
        public Builder setBackgroundColor(@ColorInt int backgroundColor) {
            mBackgroundColor = backgroundColor;
            mBuilderFieldsSet |= PROPERTY_BACKGROUND_COLOR;
            return this;
        }

        /** Sets the stroke color of the button. */
        @NonNull
        public Builder setStrokeColor(@ColorInt int strokeColor) {
            mStrokeColor = strokeColor;
            mBuilderFieldsSet |= PROPERTY_STROKE_COLOR;
            return this;
        }

        /** Sets the stroke width of the button. */
        @NonNull
        public Builder setStrokeWidth(int strokeWidth) {
            mStrokeWidth = strokeWidth;
            mBuilderFieldsSet |= PROPERTY_STROKE_WIDTH;
            return this;
        }

        /** Sets the corner radius of the button. */
        @NonNull
        public Builder setCornerRadius(float cornerRadius) {
            mCornerRadius = cornerRadius;
            mBuilderFieldsSet |= PROPERTY_CORNER_RADIUS;
            return this;
        }

        /** Sets the icon tint color. */
        @NonNull
        public Builder setIconTint(@ColorInt int iconTint) {
            mIconTint = iconTint;
            mBuilderFieldsSet |= PROPERTY_ICON_TINT;
            return this;
        }

        /** Sets the text type of the button. */
        @NonNull
        public Builder setTextType(@LocationButtonSession.TextType int textType) {
            mTextType = textType;
            mBuilderFieldsSet |= PROPERTY_TEXT_TYPE;
            return this;
        }

        /** Sets the text color of the button. */
        @NonNull
        public Builder setTextColor(@ColorInt int textColor) {
            mTextColor = textColor;
            mBuilderFieldsSet |= PROPERTY_TEXT_COLOR;
            return this;
        }

        /** Sets the configuration of the button. */
        @NonNull
        public Builder setConfiguration(@NonNull Configuration configuration) {
            mConfiguration = Objects.requireNonNull(configuration);
            mBuilderFieldsSet |= PROPERTY_CONFIGURATION;
            return this;
        }

        /**
         * Builds a {@link LocationButtonRequest} instance.
         *
         * @throws IllegalStateException if any required properties have not been set.
         */
        @NonNull
        public LocationButtonRequest build() {
            if (mBuilderFieldsSet != MASK_ALL_PROPERTIES_SET) {
                throw new IllegalStateException("Missing required properties. "
                        + "All fields must be set explicitly.");
            }

            return new LocationButtonRequest(mWidth, mHeight, mPaddingLeft, mPaddingTop,
                    mPaddingRight, mPaddingBottom, mBackgroundColor, mStrokeColor, mStrokeWidth,
                    mCornerRadius, mIconTint, mTextType, mTextColor, mConfiguration);
        }
    }
}
