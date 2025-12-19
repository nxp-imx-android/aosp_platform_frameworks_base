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

package android.app.motioncues;

import android.annotation.ColorInt;
import android.annotation.DrawableRes;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.Flags;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents the core visual properties of the motion cues bubbles that are likely to change
 * dynamically while motion cues are active. This object is designed to be sent from the motion cues
 * service to the drawing layer.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
public final class MotionCuesData implements Parcelable {

    private final @ColorInt int mColor;
    private final @DrawableRes int mShapeRes;

    /**
     * Constructs a new MotionCuesData.
     *
     * @param color The color for the cues.
     * @param shapeRes The drawable resource ID for the shape. Must be a valid resource ID.
     *                 If 0 is provided, a default shape will be used.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    public MotionCuesData(@ColorInt int color, @DrawableRes int shapeRes) {
        mColor = color;
        mShapeRes = shapeRes;
    }

    /**
     * Constructs a MotionCuesData instance as a copy of another.
     *
     * @param original The {@link MotionCuesData} instance to copy values from.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    public MotionCuesData(@NonNull MotionCuesData original) {
        mColor = original.mColor;
        mShapeRes = original.mShapeRes;
    }

    /**
     * Constructs a MotionCuesData instance from a Parcel.
     *
     * @param in The Parcel to read the object's data from.
     */
    private MotionCuesData(Parcel in) {
        mColor = in.readInt();
        mShapeRes = in.readInt();
    }

    /**
     * Gets the color of the bubbles.
     *
     * @return An integer representing the color.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    @ColorInt
    public int getColor() {
        return mColor;
    }

    /**
     * Gets the shape of the bubbles.
     *
     * @return A vector drawable resource representing the shape.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    @DrawableRes
    public int getShapeRes() {
        return mShapeRes;
    }

    /** @hide */
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mColor);
        dest.writeInt(mShapeRes);
    }

    /**
     * Standard Parcelable CREATOR field.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_MOTION_CUES)
    public static final @NonNull Parcelable.Creator<MotionCuesData> CREATOR =
            new Parcelable.Creator<MotionCuesData>() {
                @Override
                public MotionCuesData createFromParcel(Parcel in) {
                    return new MotionCuesData(in);
                }

                @Override
                public MotionCuesData[] newArray(int size) {
                    return new MotionCuesData[size];
                }
            };
}
