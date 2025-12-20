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

package android.app.contentsafety;

import static android.app.contentsafety.flags.Flags.FLAG_ENABLE_CONTENTSAFETY;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * The SupportedTypesResult it allows granted apps to retrieve information about the
 * supported file types for that can be used as input parameter for
 * {@link ContentSafetyManager#checkContent} API function.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(FLAG_ENABLE_CONTENTSAFETY)
public final class SupportedTypesResult implements Parcelable {

    /** a list of the supported types of the input parameter of the
     * {@link ContentSafetyManager#checkContent}
     **/
    private final List<String> mSupportedTypes;

    /** True if the {@link SupportedTypesResult#mSupportedTypes} is populated after API call.
     **/
    private final boolean mIsResultAvailable;

    public SupportedTypesResult(boolean isResultAvailable, @Nullable List<String> supportedTypes) {
        this.mIsResultAvailable = isResultAvailable;
        this.mSupportedTypes =
                isResultAvailable && supportedTypes != null ? new ArrayList<>(supportedTypes)
                        : new ArrayList<>();

    }

    private SupportedTypesResult(Parcel in) {
        this.mIsResultAvailable = in.readBoolean();
        ArrayList<String> input = in.readArrayList(null /* classLoader */, String.class);
        if (input != null) {
            this.mSupportedTypes = new ArrayList<>(input);
        } else {
            this.mSupportedTypes = new ArrayList<>();
        }

    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBoolean(mIsResultAvailable);
        dest.writeStringList(mIsResultAvailable ? new ArrayList<>(mSupportedTypes)
                        : new ArrayList<>());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns true if the remote service is able to provide a list of the supported file types
     * for the given feature type. {@link ContentSafetyManager#getSupportedInputTypes}.
     * Returns false if the remote service is failed, and the supportTypes list will be empty.
     */
    public boolean isResultAvailable() {
        return mIsResultAvailable;
    }

    /**
     * Returns a list of the supported file types by the remote service. If the remote service
     * failed to return a list, an empty list will be returned.
     */
    public @NonNull List<String> getSupportedTypes() {
        return mSupportedTypes;
    }

    @NonNull
    public static final Creator<SupportedTypesResult> CREATOR =
            new Creator<>() {
                @Override
                public SupportedTypesResult createFromParcel(Parcel in) {
                    return new SupportedTypesResult(in);
                }

                @Override
                public SupportedTypesResult[] newArray(int size) {
                    return new SupportedTypesResult[size];
                }
            };
}
