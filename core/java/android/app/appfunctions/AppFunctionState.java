/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app.appfunctions;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.appfunctions.flags.Flags;
import android.os.Parcel;
import android.os.Parcelable;

import android.util.ArraySet;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Runtime state of an app function.
 *
 * <p>This class holds properties of an app function that can change dynamically during the app's
 * operation, such as whether the function is enabled.
 *
 * <p>This is distinct from {@link android.app.appfunctions.AppFunctionMetadata}, which represents
 * the static configuration of a function (such as its schema, name, and package) that remains
 * constant until the providing package is updated. While metadata defines <em>what</em> a function
 * is, the {@link AppFunctionState} defines its current operational status.
 *
 * @see android.app.appfunctions.AppFunctionMetadata
 * @see android.app.appfunctions.AppFunctionManager#getAppFunctionState
 */
@FlaggedApi(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
public final class AppFunctionState implements Parcelable {

    @NonNull
    public static final Creator<AppFunctionState> CREATOR =
            new Creator<AppFunctionState>() {
                @Override
                public AppFunctionState createFromParcel(Parcel in) {
                    return new AppFunctionState(in);
                }

                @Override
                public AppFunctionState[] newArray(int size) {
                    return new AppFunctionState[size];
                }
            };

    @NonNull private final AppFunctionName mFunctionName;
    private final boolean mIsEnabled;
    @Nullable private final ArraySet<AppFunctionActivityId> mActivityIds;

    /**
     * Constructs an {@link AppFunctionState} object.
     *
     * @param functionName The identifier for the app function.
     * @param isEnabled Whether this app function can be executed.
     * @param activityIds The {@link AppFunctionActivityId}s this app function is associated with.
     * @hide
     */
    public AppFunctionState(
            @NonNull AppFunctionName functionName,
            boolean isEnabled,
            @Nullable ArraySet<AppFunctionActivityId> activityIds) {
        mFunctionName = Objects.requireNonNull(functionName);
        mIsEnabled = isEnabled;
        mActivityIds = activityIds;
    }

    private AppFunctionState(Parcel in) {
        mFunctionName = Objects.requireNonNull(in.readTypedObject(AppFunctionName.CREATOR));
        mIsEnabled = in.readBoolean();
        mActivityIds =
                (ArraySet<AppFunctionActivityId>)
                        in.readArraySet(AppFunctionActivityId.class.getClassLoader());
    }

    /** The app function this state is associated with. */
    @NonNull
    public AppFunctionName getFunctionName() {
        return mFunctionName;
    }

    /** Whether this app function can be executed. */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * The {@link AppFunctionActivityId}s this app function is associated with, or null if none.
     *
     * <p>This will be non-null only when {@link AppFunctionMetadata#getScope} is {@link
     * AppFunctionMetadata#SCOPE_ACTIVITY}.
     *
     * @see AppFunctionMetadata#SCOPE_ACTIVITY
     */
    // Performance optimization to avoid creating empty collections for an unbound list of
    // AppFunctionStates (using null instead), and to allow indexed for-loop (using ArraySet).
    @SuppressLint({"NullableCollection", "ConcreteCollection"})
    @Nullable
    public ArraySet<AppFunctionActivityId> getActivityIds() {
        return mActivityIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppFunctionState that)) return false;
        return mIsEnabled == that.mIsEnabled
                && mFunctionName.equals(that.mFunctionName)
                && Objects.equals(mActivityIds, that.mActivityIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFunctionName, mIsEnabled, mActivityIds);
    }

    @Override
    public String toString() {
        return "AppFunctionState("
                + "functionName="
                + mFunctionName
                + ", "
                + "isEnabled="
                + mIsEnabled
                + ", "
                + "activityIds="
                + mActivityIds
                + ")";
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mFunctionName, flags);
        dest.writeBoolean(mIsEnabled);
        dest.writeArraySet(mActivityIds);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
