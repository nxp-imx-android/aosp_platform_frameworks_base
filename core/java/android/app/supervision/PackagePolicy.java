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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.Parcelable.WriteFlags;
import androidx.annotation.Nullable;
import android.app.supervision.flags.Flags;
import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A policy that represents the supervision state of a package.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
final class PackagePolicy extends Policy {
    public static final int RESTRICTION_TYPE_BLOCKED = 1;

    @IntDef({
        RESTRICTION_TYPE_BLOCKED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RestrictionType {}

    /** The package name of the package this policy is applied to. */
    private final String mPackageName;

    /**
     * The type of restriction this policy applies.
     *
     * <p>Must be one of the constants defined in {@link RestrictionType}.
     */
    private final @RestrictionType int mRestrictionType;

    /**
     * Constructs a new {@link PackagePolicy} with the given version and package name.
     *
     * @param version The version of the policy.
     * @param packageName The package name of the package this policy is applied to.
     * @hide
     */
    public PackagePolicy(
            long version,
            String packageName,
            @RestrictionType int restrictionType,
            boolean isEnabled) {
        super(version, isEnabled);
        this.mPackageName = packageName;
        this.mRestrictionType = restrictionType;
    }

    /**
     * Constructs a new {@link PackagePolicy} from a parcel.
     *
     * @param in The parcel to read from.
     * @hide
     */
    public PackagePolicy(Parcel in) {
        super(in);
        mPackageName = in.readString8();
        mRestrictionType = in.readInt();
    }

    /**
     * Retrieves the type of restriction this policy applies.
     *
     * @return The type of restriction this policy applies.
     */
    public @RestrictionType int getRestrictionType() {
        return mRestrictionType;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, @WriteFlags int flags) {
        super.writeToParcel(parcel, flags);
        parcel.writeString8(mPackageName);
        parcel.writeInt(mRestrictionType);
    }

    @Override
    public  int describeContents() {
        return 0;
    }
}
