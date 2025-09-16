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
package android.widget.location;

import android.annotation.ColorInt;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.flags.Flags;

/**
 * Defines the requested visual properties for a location button.
 *
 * <p>An instance of this class is passed to {@link LocationButtonProvider#openSession} to
 * configure the button's appearance, such as its text, colors, and corner radius etc.
 *
 * @see LocationButtonProvider#openSession
 */
@FlaggedApi(Flags.FLAG_LOCATION_BUTTON_ENABLED)
public final class LocationButtonRequest implements Parcelable {
    private final int mWidth;
    private final int mHeight;
    private final @LocationButtonSession.TextType int mTextType;
    @ColorInt
    private final int mTextColor;
    @ColorInt
    private final int mBackgroundColor;
    @ColorInt
    private final int mIconTint;
    private final float mCornerRadius;

    public LocationButtonRequest(int width, int height, int textType, float cornerRadius,
            int textColor, int backgroundColor, int iconTint) {
        this.mWidth = width;
        this.mHeight = height;
        this.mTextType = textType;
        this.mTextColor = textColor;
        this.mBackgroundColor = backgroundColor;
        this.mIconTint = iconTint;
        this.mCornerRadius = cornerRadius;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
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

    /** Returns the background color of the button as a {@link ColorInt}. */
    @ColorInt
    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    /** Returns the icon tint color as a {@link ColorInt}. */
    @ColorInt
    public int getIconTint() {
        return mIconTint;
    }

    /** Returns the corner radius of the button in pixels. */
    public float getCornerRadius() {
        return mCornerRadius;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mTextType);
        dest.writeFloat(mCornerRadius);
        dest.writeInt(mTextColor);
        dest.writeInt(mBackgroundColor);
        dest.writeInt(mIconTint);
    }

    public static final @NonNull Creator<LocationButtonRequest> CREATOR =
            new Creator<LocationButtonRequest>() {
                @Override
                public LocationButtonRequest createFromParcel(Parcel in) {
                    return new LocationButtonRequest(in.readInt(), in.readInt(), in.readInt(),
                            in.readFloat(), in.readInt(), in.readInt(), in.readInt());
                }

                @Override
                public LocationButtonRequest[] newArray(int size) {
                    return new LocationButtonRequest[size];
                }
            };

}
