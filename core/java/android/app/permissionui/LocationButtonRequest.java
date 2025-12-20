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
     *
     * @param width The width of the button.
     * @param height The height of the button.
     * @param backgroundColor The background color of the button as a {@link ColorInt}.
     * @param strokeColor The button outline/border color as a {@link ColorInt}.
     * @param strokeWidth The button outline/border width.
     * @param cornerRadius The corner radius of the button in pixels.
     * @param iconTint The icon tint color as a {@link ColorInt}.
     * @param textType The text type displayed on the button.
     * @param textColor The color of the button's text as a {@link ColorInt}.
     * @param configuration The configuration of the button.
     */
    public LocationButtonRequest(int width, int height, @ColorInt int backgroundColor,
            @ColorInt int strokeColor, int strokeWidth, float cornerRadius,
            @ColorInt int iconTint, @LocationButtonSession.TextType int textType,
            @ColorInt int textColor, @NonNull Configuration configuration) {
        this.mWidth = width;
        this.mHeight = height;
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
                + "mWidth=" + mWidth
                + ", mHeight=" + mHeight
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
                            in.readInt(), in.readInt(), in.readFloat(), in.readInt(), in.readInt(),
                            in.readInt(),
                            Objects.requireNonNull(in.readTypedObject(Configuration.CREATOR)));
                }

                @Override
                public LocationButtonRequest[] newArray(int size) {
                    return new LocationButtonRequest[size];
                }
            };
}
