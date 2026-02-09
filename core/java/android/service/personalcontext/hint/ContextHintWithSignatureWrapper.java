/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.personalcontext.hint;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parcelable version of ContextHintWithSignature.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@TestApi
public final class ContextHintWithSignatureWrapper implements Parcelable {
    private final ContextHintWithSignature mHint;

    /** Creates a new Parcelable wrapper around a {@link ContextHintWithSignature}. */
    public ContextHintWithSignatureWrapper(@NonNull ContextHintWithSignature hint) {
        mHint = hint;
    }

    private ContextHintWithSignatureWrapper(Parcel source) {
        mHint = new ContextHintWithSignature(source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        mHint.writeToParcel(dest, flags);
    }

    @NonNull
    public ContextHintWithSignature getContextHintWithSignature() {
        return mHint;
    }

    /**
     * Utility method to unwrap a collection of {@link ContextHintWithSignature} into a list of
     * {@link ContextHint}.
     *
     * @hide
     */
    @NonNull
    public static List<ContextHintWithSignature> unwrapList(
            @NonNull Collection<ContextHintWithSignatureWrapper> wrappers) {
        return unwrapInto(wrappers, new ArrayList<>());
    }

    /**
     * Utility method to unwrap a collection of {@link ContextHintWithSignatureWrapper} into a
     * collection of {@link ContextHintWithSignature}.
     */
    @NonNull
    public static <T extends Collection<ContextHintWithSignature>> T unwrapInto(
            @NonNull Collection<ContextHintWithSignatureWrapper> wrappers,
            @NonNull T into) {
        for (ContextHintWithSignatureWrapper wrapper : wrappers) {
            into.add(wrapper.getContextHintWithSignature());
        }
        return into;
    }

    /**
     * Utility method to wrap a collection of {@link ContextHintWithSignature} into a list of
     * {@link ContextHintWithSignatureWrapper}.
     */
    @NonNull
    public static List<ContextHintWithSignatureWrapper> wrapList(
            @NonNull Collection<ContextHintWithSignature> hints) {
        ArrayList<ContextHintWithSignatureWrapper> list = new ArrayList<>();
        for (ContextHintWithSignature hint : hints) {
            list.add(new ContextHintWithSignatureWrapper(hint));
        }
        return list;
    }

    public static final @NonNull Creator<ContextHintWithSignatureWrapper> CREATOR =
            new Creator<>() {
                @Override
                public ContextHintWithSignatureWrapper createFromParcel(Parcel source) {
                    return new ContextHintWithSignatureWrapper(source);
                }

                @Override
                public ContextHintWithSignatureWrapper[] newArray(int size) {
                    return new ContextHintWithSignatureWrapper[size];
                }
            };
}
