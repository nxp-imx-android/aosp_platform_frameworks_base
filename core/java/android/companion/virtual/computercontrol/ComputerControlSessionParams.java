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

package android.companion.virtual.computercontrol;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters for creating a {@link ComputerControlSession}.
 *
 * @hide
 */
public final class ComputerControlSessionParams implements Parcelable {

    private final String mName;
    private final List<String> mTargetPackageNames;

    private ComputerControlSessionParams(
            @NonNull String name, @NonNull List<String> targetPackageNames) {
        mName = name;
        mTargetPackageNames = targetPackageNames;
    }

    private ComputerControlSessionParams(Parcel parcel) {
        mName = parcel.readString8();
        mTargetPackageNames = new ArrayList<>();
        parcel.readStringList(mTargetPackageNames);
    }

    /** Returns the name of this computer control session. */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Returns the package names of the applications that can be automated during this session. */
    @NonNull
    public List<String> getTargetPackageNames() {
        return mTargetPackageNames;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mName);
        dest.writeStringList(mTargetPackageNames);
    }

    @NonNull
    public static final Creator<ComputerControlSessionParams> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ComputerControlSessionParams createFromParcel(@NonNull Parcel in) {
            return new ComputerControlSessionParams(in);
        }

        @Override
        @NonNull
        public ComputerControlSessionParams[] newArray(int size) {
            return new ComputerControlSessionParams[size];
        }
    };

    /** Builder for {@link ComputerControlSessionParams}. */
    public static final class Builder {
        private String mName;
        private List<String> mTargetPackageNames;

        /**
         * Sets the name of this computer control session.
         *
         * @param name The name of the session.
         * @return This builder.
         */
        @NonNull
        public Builder setName(@NonNull String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Name must not be empty");
            }
            mName = name;
            return this;
        }

        /**
         * Set the package names of all applications that may be automated during this session.
         *
         * <p>All package names specified in the list must meet the following requirements:
         * <ol>
         *     <li>The package name has a valid launcher Intent.</li>
         *     <li>The package name is not the device permission controller.</li>
         * </ol>
         */
        @NonNull
        public Builder setTargetPackageNames(@NonNull List<String> targetPackageNames) {
            if (targetPackageNames == null || targetPackageNames.isEmpty()) {
                throw new IllegalArgumentException("targetPackageNames must not be empty");
            }
            mTargetPackageNames = targetPackageNames;
            return this;
        }

        /**
         * Builds the {@link ComputerControlSessionParams} instance.
         *
         * @return The built {@link ComputerControlSessionParams}.
         * @throws IllegalArgumentException if any of the required arguments are not set.
         */
        @NonNull
        public ComputerControlSessionParams build() {
            if (mName == null || mName.isEmpty()) {
                throw new IllegalArgumentException("Name must be set");
            }
            if (mTargetPackageNames == null || mTargetPackageNames.isEmpty()) {
                throw new IllegalArgumentException("TargetPackageNames must be set");
            }
            return new ComputerControlSessionParams(mName, mTargetPackageNames);
        }
    }
}
