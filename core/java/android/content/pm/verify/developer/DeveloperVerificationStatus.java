/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.pm.verify.developer;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.pm.Flags;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * This class is used by the developer verifier to describe the status of the verification request,
 * whether it's successful or it has failed along with any relevant details.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_VERIFICATION_SERVICE)
public final class DeveloperVerificationStatus implements Parcelable {
    /**
     * The ASL status has not been determined.
     * <p>This happens in situations where the verification
     * service is not monitoring ASLs, and means the ASL data in the app is not necessarily bad but
     * can't be trusted.
     * </p>
     */
    public static final int DEVELOPER_VERIFIER_STATUS_ASL_UNDEFINED = 0;

    /**
     * The app's ASL data is considered to be in a good state.
     */
    public static final int DEVELOPER_VERIFIER_STATUS_ASL_GOOD = 1;

    /**
     * There is something bad in the app's ASL data.
     * <p>
     * The user should be warned about this when shown
     * the ASL data and/or appropriate decisions made about the use of this data by the platform.
     * </p>
     */
    public static final int DEVELOPER_VERIFIER_STATUS_ASL_BAD = 2;

    /** @hide */
    @IntDef(prefix = {"DEVELOPER_VERIFIER_STATUS_ASL_"}, value = {
            DEVELOPER_VERIFIER_STATUS_ASL_UNDEFINED,
            DEVELOPER_VERIFIER_STATUS_ASL_GOOD,
            DEVELOPER_VERIFIER_STATUS_ASL_BAD,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeveloperVerifierStatusAsl {}

    private final boolean mIsVerified;
    private final boolean mIsLiteVerification;
    private final @DeveloperVerifierStatusAsl int mAslStatus;
    @NonNull
    private final String mFailuresMessage;

    private DeveloperVerificationStatus(boolean isVerified, boolean isLiteVerification,
            @DeveloperVerifierStatusAsl int aslStatus, @NonNull String failuresMessage) {
        mIsVerified = isVerified;
        mIsLiteVerification = isLiteVerification;
        mAslStatus = aslStatus;
        mFailuresMessage = failuresMessage;
    }

    /**
     * @return whether the status is set to verified or not.
     */
    public boolean isVerified() {
        return mIsVerified;
    }

    /**
     * @return true when the only the lite variation of the verification was conducted.
     */
    public boolean isLiteVerification() {
        return mIsLiteVerification;
    }

    /**
     * @return the failure message associated with the failure status.
     */
    @NonNull
    public String getFailureMessage() {
        return mFailuresMessage;
    }

    /**
     * @return the asl status.
     */
    public @DeveloperVerifierStatusAsl int getAslStatus() {
        return mAslStatus;
    }

    /**
     * Builder to construct a {@link DeveloperVerificationStatus} object.
     */
    public static final class Builder {
        private boolean mIsVerified = false;
        private boolean mIsLiteVerification = false;
        private @DeveloperVerifierStatusAsl int mAslStatus =
                DEVELOPER_VERIFIER_STATUS_ASL_UNDEFINED;
        private String mFailuresMessage = "";

        /**
         * Set in the status whether the verification has succeeded or failed.
         */
        @NonNull
        public Builder setVerified(boolean isVerified) {
            mIsVerified = isVerified;
            return this;
        }

        /**
         * Set in the status whether the lite variation of the verification was conducted
         * instead of the full verification.
         */
        @NonNull
        public Builder setLiteVerification(boolean isLiteVerification) {
            mIsLiteVerification = isLiteVerification;
            return this;
        }

        /**
         * Set a developer-facing failure message to include in the verification failure status.
         */
        @NonNull
        public Builder setFailureMessage(@NonNull String failureMessage) {
            Objects.requireNonNull(failureMessage, "failureMessage cannot be null");
            mFailuresMessage = failureMessage;
            return this;
        }

        /**
         * Set the ASL status, as defined in {@link DeveloperVerifierStatusAsl}.
         */
        @NonNull
        public Builder setAslStatus(@DeveloperVerifierStatusAsl int aslStatus) {
            mAslStatus = aslStatus;
            return this;
        }

        /**
         * Build the status object.
         */
        @NonNull
        public DeveloperVerificationStatus build() {
            return new DeveloperVerificationStatus(mIsVerified, mIsLiteVerification, mAslStatus,
                    mFailuresMessage);
        }
    }

    private DeveloperVerificationStatus(Parcel in) {
        mIsVerified = in.readBoolean();
        mIsLiteVerification = in.readBoolean();
        mAslStatus = in.readInt();
        mFailuresMessage = in.readString8();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBoolean(mIsVerified);
        dest.writeBoolean(mIsLiteVerification);
        dest.writeInt(mAslStatus);
        dest.writeString8(mFailuresMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<DeveloperVerificationStatus> CREATOR = new Creator<>() {
        @Override
        public DeveloperVerificationStatus createFromParcel(@NonNull Parcel in) {
            return new DeveloperVerificationStatus(in);
        }

        @Override
        public DeveloperVerificationStatus[] newArray(int size) {
            return new DeveloperVerificationStatus[size];
        }
    };
}
