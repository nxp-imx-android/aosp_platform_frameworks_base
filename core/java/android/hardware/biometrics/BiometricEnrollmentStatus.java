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

package android.hardware.biometrics;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * This class contains enrollment information. It keeps track of the modality type (e.g.
 * fingerprint, face) and the number of times the biometric has been enrolled.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_MOVE_FM_API_TO_BM)
public final class BiometricEnrollmentStatus implements Parcelable {
    @BiometricManager.BiometricModality
    private final int mModality;
    private final int mEnrollCount;

    /**
     * @hide
     */
    public BiometricEnrollmentStatus(
            @BiometricManager.BiometricModality int modality, int enrollCount) {
        mModality = modality;
        mEnrollCount = enrollCount;
    }

    /**
     * Returns the modality associated with this enrollment status.
     *
     * @return The int value representing the biometric sensor type, e.g.
     * {@link BiometricManager#TYPE_FACE} or
     * {@link BiometricManager#TYPE_FINGERPRINT}.
     */
    @BiometricManager.BiometricModality
    public int getModality() {
        return mModality;
    }

    /**
     * Returns the number of enrolled biometric for the associated modality.
     *
     * @return The number of enrolled biometric.
     */
    @IntRange(from = 0)
    public int getEnrollCount() {
        return mEnrollCount;
    }

    private BiometricEnrollmentStatus(Parcel in) {
        this(in.readInt(), in.readInt());
    }

    @NonNull
    public static final Creator<BiometricEnrollmentStatus> CREATOR = new Creator<>() {
        @Override
        public BiometricEnrollmentStatus createFromParcel(Parcel in) {
            return new BiometricEnrollmentStatus(in);
        }

        @Override
        public BiometricEnrollmentStatus[] newArray(int size) {
            return new BiometricEnrollmentStatus[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mModality);
        dest.writeInt(mEnrollCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        String modality = "";
        if (mModality == BiometricManager.TYPE_FINGERPRINT) {
            modality = "Fingerprint";
        } else if (mModality == BiometricManager.TYPE_FACE) {
            modality = "Face";
        }
        return "Modality: " + modality + ", Enrolled Count: " + mEnrollCount;
    }
}
