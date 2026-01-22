/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.ondeviceintelligence.imagedescription;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ondeviceintelligence.flags.Flags;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents a request to generate a description for an image.
 *
 * <p>This class encapsulates the input image and an optional prompt to guide
 * the description generation.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
public final class ImageDescriptionRequest implements Parcelable {
    private final Bitmap mImage;
    private final String mPrompt;

    /**
     * Constructs a new {@link ImageDescriptionRequest}.
     *
     * @param image The input image for which a description is requested.
     * @param prompt An optional prompt to contextually guide the description generation.
     */
    public ImageDescriptionRequest(@NonNull Bitmap image, @Nullable String prompt) {
        mImage = Objects.requireNonNull(image).asShared();
        mPrompt = prompt;
    }

    /**
     * Returns the input image for which description is to be requested.
     */
    @NonNull
    public Bitmap getImage() {
        return mImage;
    }

    /**
     * Returns the optional prompt.
     */
    @Nullable
    public String getPrompt() {
        return mPrompt;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mImage, flags);
        dest.writeString8(mPrompt);
    }

    public static final @NonNull Creator<ImageDescriptionRequest> CREATOR =
            new Creator<ImageDescriptionRequest>() {
                @Override
                public ImageDescriptionRequest createFromParcel(Parcel in) {
                    return new ImageDescriptionRequest(in);
                }

                @Override
                public ImageDescriptionRequest[] newArray(int size) {
                    return new ImageDescriptionRequest[size];
                }
            };

    private ImageDescriptionRequest(Parcel in) {
        mImage = in.readTypedObject(Bitmap.CREATOR);
        mPrompt = in.readString8();
    }
}
