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

package android.app.privatecompute;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents an entity that is configured to run in the PCC (Private Compute Core) sandbox.
 *
 * <p>A PCC entity can be an entire application (a package) or an individual component within an
 * application (such as a {@link android.app.Service}) that is designated to run in the sandbox.
 *
 * @see PccSandboxManager#getPccEntities()
 */
@FlaggedApi(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public final class PccEntity implements Parcelable {

    private final String mPackageName;

    /**
     * Creates a new instance representing a PCC entity.
     *
     * @param packageName The package name of the application containing the PCC entity.
     * @hide
     */
    public PccEntity(@NonNull String packageName) {
        mPackageName = packageName;
    }

    private PccEntity(Parcel in) {
        mPackageName = in.readString8();
    }

    /**
     * Returns the package name of the application that contains this PCC entity.
     *
     * @return The package name of the application.
     */
    public @NonNull String getPackageName() {
        return mPackageName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mPackageName);
    }

    /**
     * @see Parcelable.Creator
     */
    @NonNull
    public static final Creator<PccEntity> CREATOR = new Creator<PccEntity>() {
        @Override
        public PccEntity createFromParcel(Parcel in) {
            return new PccEntity(in);
        }

        @Override
        public PccEntity[] newArray(int size) {
            return new PccEntity[size];
        }
    };
}
