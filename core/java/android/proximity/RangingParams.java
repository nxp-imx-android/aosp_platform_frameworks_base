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

package android.proximity;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public final class RangingParams implements Parcelable {

    private final double mThresholdMeters;
    private final int mTimeoutMillis;

    private RangingParams(double thresholdMeters, int timeoutMillis) {
        this.mThresholdMeters = thresholdMeters;
        this.mTimeoutMillis = timeoutMillis;
    }

    public double getThresholdMeters() {
        return mThresholdMeters;
    }

    public int getTimeoutMillis() {
        return mTimeoutMillis;
    }

    public static final Parcelable.Creator<RangingParams> CREATOR =
            new Parcelable.Creator<RangingParams>() {
                @Override
                public RangingParams createFromParcel(Parcel in) {
                    return new RangingParams(in.readDouble(), in.readInt());
                }

                @Override
                public RangingParams[] newArray(int size) {
                    return new RangingParams[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(mThresholdMeters);
        dest.writeInt(mTimeoutMillis);
    }

    @Override
    public String toString() {
        return "RangingParams{"
                + "mThresholdMeters="
                + mThresholdMeters
                + ", mTimeoutMillis="
                + mTimeoutMillis
                + '}';
    }

    public static final class Builder {
        private double mThresholdMeters = 0.0;
        private int mTimeoutMillis = 0;

        public Builder(@NonNull RangingParams src) {
            mThresholdMeters = src.mThresholdMeters;
            mTimeoutMillis = src.mTimeoutMillis;
        }

        public @NonNull Builder setThresholdMeters(double thresholdMeters) {
            mThresholdMeters = thresholdMeters;
            return this;
        }

        public @NonNull Builder setTimeoutMillis(int timeoutMillis) {
            mTimeoutMillis = timeoutMillis;
            return this;
        }

        public @NonNull RangingParams build() {
            return new RangingParams(mThresholdMeters, mTimeoutMillis);
        }
    }
}
