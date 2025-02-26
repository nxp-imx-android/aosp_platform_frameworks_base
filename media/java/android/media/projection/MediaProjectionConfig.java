/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media.projection;

import static android.view.Display.DEFAULT_DISPLAY;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcelable;

import com.android.internal.util.AnnotationValidations;

import java.lang.annotation.Retention;

/**
 * Configure the {@link MediaProjection} session requested from
 * {@link MediaProjectionManager#createScreenCaptureIntent(MediaProjectionConfig)}.
 */
public final class MediaProjectionConfig implements Parcelable {

    /**
     * The user, rather than the host app, determines which region of the display to capture.
     *
     * @hide
     */
    public static final int CAPTURE_REGION_USER_CHOICE = 0;

    /**
     * The host app specifies a particular display to capture.
     *
     * @hide
     */
    public static final int CAPTURE_REGION_FIXED_DISPLAY = 1;

    /** @hide */
    @IntDef(prefix = "CAPTURE_REGION_", value = {CAPTURE_REGION_USER_CHOICE,
            CAPTURE_REGION_FIXED_DISPLAY})
    @Retention(SOURCE)
    public @interface CaptureRegion {
    }

    /**
     * The particular display to capture. Only used when {@link #getRegionToCapture()} is
     * {@link #CAPTURE_REGION_FIXED_DISPLAY}; ignored otherwise.
     * <p>
     * Only supports values of {@link android.view.Display#DEFAULT_DISPLAY}.
     */
    @IntRange(from = DEFAULT_DISPLAY, to = DEFAULT_DISPLAY)
    private int mDisplayToCapture;

    /**
     * The region to capture. Defaults to the user's choice.
     */
    @CaptureRegion
    private int mRegionToCapture;

    /**
     * Customized instance, with region set to the provided value.
     */
    private MediaProjectionConfig(@CaptureRegion int captureRegion) {
        mRegionToCapture = captureRegion;
    }

    /**
     * Returns an instance which restricts the user to capturing the default display.
     */
    @NonNull
    public static MediaProjectionConfig createConfigForDefaultDisplay() {
        MediaProjectionConfig config = new MediaProjectionConfig(CAPTURE_REGION_FIXED_DISPLAY);
        config.mDisplayToCapture = DEFAULT_DISPLAY;
        return config;
    }

    /**
     * Returns an instance which allows the user to decide which region is captured. The consent
     * dialog presents the user with all possible options. If the user selects display capture,
     * then only the {@link android.view.Display#DEFAULT_DISPLAY} is supported.
     *
     * <p>
     * When passed in to
     * {@link MediaProjectionManager#createScreenCaptureIntent(MediaProjectionConfig)}, the consent
     * dialog shown to the user will be the same as if just
     * {@link MediaProjectionManager#createScreenCaptureIntent()} was invoked.
     * </p>
     */
    @NonNull
    public static MediaProjectionConfig createConfigForUserChoice() {
        return new MediaProjectionConfig(CAPTURE_REGION_USER_CHOICE);
    }

    /**
     * Returns string representation of the captured region.
     */
    @NonNull
    private static String captureRegionToString(int value) {
        return switch (value) {
            case CAPTURE_REGION_USER_CHOICE -> "CAPTURE_REGION_USERS_CHOICE";
            case CAPTURE_REGION_FIXED_DISPLAY -> "CAPTURE_REGION_GIVEN_DISPLAY";
            default -> Integer.toHexString(value);
        };
    }

    @Override
    public String toString() {
        return "MediaProjectionConfig { " + "displayToCapture = " + mDisplayToCapture + ", "
                + "regionToCapture = " + captureRegionToString(mRegionToCapture) + " }";
    }


    /**
     * The particular display to capture. Only used when {@link #getRegionToCapture()} is
     * {@link #CAPTURE_REGION_FIXED_DISPLAY}; ignored otherwise.
     * <p>
     * Only supports values of {@link android.view.Display#DEFAULT_DISPLAY}.
     *
     * @hide
     */
    public int getDisplayToCapture() {
        return mDisplayToCapture;
    }

    /**
     * The region to capture. Defaults to the user's choice.
     *
     * @hide
     */
    public @CaptureRegion int getRegionToCapture() {
        return mRegionToCapture;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaProjectionConfig that = (MediaProjectionConfig) o;
        return mDisplayToCapture == that.mDisplayToCapture
                && mRegionToCapture == that.mRegionToCapture;
    }

    @Override
    public int hashCode() {
        int _hash = 1;
        _hash = 31 * _hash + mDisplayToCapture;
        _hash = 31 * _hash + mRegionToCapture;
        return _hash;
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        dest.writeInt(mDisplayToCapture);
        dest.writeInt(mRegionToCapture);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    /* package-private */ MediaProjectionConfig(@NonNull android.os.Parcel in) {
        int displayToCapture = in.readInt();
        int regionToCapture = in.readInt();

        mDisplayToCapture = displayToCapture;
        mRegionToCapture = regionToCapture;
        AnnotationValidations.validate(CaptureRegion.class, null, mRegionToCapture);
    }

    public static final @NonNull Parcelable.Creator<MediaProjectionConfig> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public MediaProjectionConfig[] newArray(int size) {
                    return new MediaProjectionConfig[size];
                }

                @Override
                public MediaProjectionConfig createFromParcel(@NonNull android.os.Parcel in) {
                    return new MediaProjectionConfig(in);
                }
            };
}
