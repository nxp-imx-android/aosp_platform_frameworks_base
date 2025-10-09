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

package android.app.supervision;

import android.annotation.StringDef;
import android.annotation.NonNull;
import android.annotation.FlaggedApi;
import android.annotation.SuppressLint;
import android.app.supervision.flags.Flags;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Generic, base class for supervision policies. All specific policies (e.g., app blocking, time
 * limits) should extend this class.
 *
 * @hide
 */
@SuppressLint({"ParcelNotFinal", "ParcelCreator"})
@FlaggedApi(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
public abstract class Policy implements Parcelable {
    /**
     * The identifier of the package policy. This is used to identify the policy type when
     * reconstructing the policy from storage, parcel or deserializing.
     *
     * @hide
     */
    public static final String PACKAGE_POLICY_IDENTIFIER = "package";

    /**
     * The identifier of the policy. This is used to identify the policy type when reconstructing
     * the policy from storage, parcel or deserializing.
     *
     * @hide
     */
    @StringDef({PACKAGE_POLICY_IDENTIFIER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PolicyIdentifier {}

    /**
     * The version of this policy. The version is managed by the system and indicates the policy's
     * freshness.
     */
    private long mVersion;

    /**
     * Whether the policy is enabled.
     *
     * <p>Default is true.
     */
    private boolean mIsEnabled = true;

    /**
     * Retrieves the version of this policy.
     *
     * @return The policy's version as a long.
     */
    public long getVersion() {
        return mVersion;
    }

    /**
     * Retrieves the enabled state of this policy.
     *
     * @return Whether the policy is enabled.
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * Sets the version of this policy.
     *
     * @param version The new version of the policy.
     */
    public void setVersion(long version) {
        mVersion = version;
    }

    /**
     * Constructs a new policy with the given key and version.
     *
     * @param version The version of the policy.
     * @param isEnabled Whether the policy is enabled.
     */
    public Policy(long version, boolean isEnabled) {
        mVersion = version;
        mIsEnabled = isEnabled;
    }

    /**
     * Constructs a new policy from a parcel.
     *
     * <p>This method is used to create a policy from a parcel. It is called by the {@link Creator}
     * when unmarshalling a parcel.
     *
     * @param in The parcel to read from.
     * @hide
     */
    public Policy(@NonNull Parcel in) {
        mVersion = in.readLong();
        mIsEnabled = in.readBoolean();
    }

    /**
     * Returns the identifier of the policy to be used to identify the policy type.
     *
     * @hide
     */
    @NonNull
    public @PolicyIdentifier String getIdentifier() {
        return switch (this) {
            case PackagePolicy pp -> Policy.PACKAGE_POLICY_IDENTIFIER;
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported policy type: " + this.getClass().getSimpleName());
        };
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, @WriteFlags int flags) {
        parcel.writeString8(getIdentifier());
        parcel.writeLong(mVersion);
        parcel.writeBoolean(mIsEnabled);
    }

    @NonNull
    public static final Creator<Policy> CREATOR =
            new Creator<Policy>() {
                @Override
                public Policy createFromParcel(@NonNull Parcel in) {
                    String policyIdentifier = in.readString8();

                    return switch (policyIdentifier) {
                        case PACKAGE_POLICY_IDENTIFIER -> new PackagePolicy(in);
                        default ->
                                throw new IllegalArgumentException(
                                        "Unsupported policy identifier value: " + policyIdentifier);
                    };
                }

                @Override
                public PackagePolicy[] newArray(int size) {
                    return new PackagePolicy[size];
                }
            };
}
