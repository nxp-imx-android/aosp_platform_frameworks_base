/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os.vibrator;

import android.annotation.NonNull;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.util.MathUtils;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Representation of {@link VibrationEffectSegment} that holds a fixed vibration amplitude for a
 * specified duration.
 *
 * <p>The amplitude is expressed by a float value in the range [0, 1], representing the relative
 * output acceleration for the vibrator.
 *
 * @hide
 */
@TestApi
@android.ravenwood.annotation.RavenwoodKeepWholeClass
public final class StepSegment extends VibrationEffectSegment {
    private final float mAmplitude;
    private final int mDuration;

    StepSegment(@NonNull Parcel in) {
        this(in.readFloat(), in.readInt());
    }

    /** @hide */
    public StepSegment(float amplitude, int duration) {
        mAmplitude = amplitude;
        mDuration = duration;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StepSegment)) {
            return false;
        }
        StepSegment other = (StepSegment) o;
        return Float.compare(mAmplitude, other.mAmplitude) == 0
                && mDuration == other.mDuration;
    }

    public float getAmplitude() {
        return mAmplitude;
    }

    @Override
    public long getDuration() {
        return mDuration;
    }

    /** @hide */
    @Override
    public boolean areVibrationFeaturesSupported(@NonNull VibratorInfo vibratorInfo) {
        return !amplitudeRequiresAmplitudeControl(mAmplitude)
                || vibratorInfo.hasAmplitudeControl();
    }

    /** @hide */
    @Override
    public boolean isHapticFeedbackCandidate() {
        return true;
    }

    /** @hide */
    @Override
    public void validate() {
        VibrationEffectSegment.checkDurationArgument(mDuration, "duration");
        if (Float.compare(mAmplitude, VibrationEffect.DEFAULT_AMPLITUDE) != 0) {
            Preconditions.checkArgumentInRange(mAmplitude, 0f, 1f, "amplitude");
        }
    }

    /** @hide */
    @NonNull
    @Override
    public StepSegment resolve(int defaultAmplitude) {
        if (defaultAmplitude > VibrationEffect.MAX_AMPLITUDE || defaultAmplitude <= 0) {
            throw new IllegalArgumentException(
                    "amplitude must be between 1 and 255 inclusive (amplitude="
                            + defaultAmplitude + ")");
        }
        if (Float.compare(mAmplitude, VibrationEffect.DEFAULT_AMPLITUDE) != 0) {
            return this;
        }
        return new StepSegment((float) defaultAmplitude / VibrationEffect.MAX_AMPLITUDE, mDuration);
    }

    /** @hide */
    @NonNull
    @Override
    public StepSegment scale(float scaleFactor) {
        if (Float.compare(mAmplitude, VibrationEffect.DEFAULT_AMPLITUDE) == 0) {
            return this;
        }
        float newAmplitude = VibrationEffect.scale(mAmplitude, scaleFactor);
        if (Float.compare(newAmplitude, mAmplitude) == 0) {
            return this;
        }
        return new StepSegment(newAmplitude, mDuration);
    }

    /** @hide */
    @NonNull
    @Override
    public StepSegment applyAdaptiveScale(float scaleFactor) {
        if (Float.compare(mAmplitude, VibrationEffect.DEFAULT_AMPLITUDE) == 0) {
            return this;
        }
        float newAmplitude = MathUtils.constrain(mAmplitude * scaleFactor, 0f, 1f);
        if (Float.compare(newAmplitude, mAmplitude) == 0) {
            return this;
        }
        return new StepSegment(newAmplitude, mDuration);
    }

    /** @hide */
    @NonNull
    @Override
    public StepSegment applyEffectStrength(int effectStrength) {
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAmplitude, mDuration);
    }

    @Override
    public String toString() {
        return "Step{amplitude=" + mAmplitude
                + ", duration=" + mDuration
                + "}";
    }

    /** @hide */
    @Override
    public String toDebugString() {
        return String.format("Step=%dms(amplitude=%.2f)", mDuration, mAmplitude);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(PARCEL_TOKEN_STEP);
        out.writeFloat(mAmplitude);
        out.writeInt(mDuration);
    }

    @NonNull
    public static final Parcelable.Creator<StepSegment> CREATOR =
            new Parcelable.Creator<StepSegment>() {
                @Override
                public StepSegment createFromParcel(Parcel in) {
                    // Skip the type token
                    in.readInt();
                    return new StepSegment(in);
                }

                @Override
                public StepSegment[] newArray(int size) {
                    return new StepSegment[size];
                }
            };
}