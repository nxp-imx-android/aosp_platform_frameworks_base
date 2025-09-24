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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for parceling/unparceling {@link ContextHint}.
 *
 * @hide
 */
public final class ContextHintWrapper implements Parcelable {
    private final ContextHint mContextHint;

    /**
     * Creates a {@link ContextHintWrapper} from the given {@link ContextHint}.
     */
    public ContextHintWrapper(@NonNull ContextHint contextHint) {
        mContextHint = contextHint;
    }

    /**
     * Returns the {@link ContextHint} contained in this wrapper.
     */
    @NonNull
    public ContextHint getContextHint() {
        return mContextHint;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBundle(mContextHint.toBundle());
    }

    /**
     * Utility method to unwrap a list of {@link ContextHintWrapper} into a list of
     * {@link ContextHint}.
     */
    @NonNull
    public static List<ContextHint> unwrapList(@NonNull List<ContextHintWrapper> wrappers) {
        List<ContextHint> list = new ArrayList<>();
        for (ContextHintWrapper wrapper : wrappers) {
            list.add(wrapper.getContextHint());
        }
        return list;
    }

    /**
     * Utility method to wrap a list of {@link ContextHint} into a list of
     * {@link ContextHintWrapper}.
     */
    @NonNull
    public static List<ContextHintWrapper> wrapList(@NonNull List<ContextHint> hints) {
        List<ContextHintWrapper> list = new ArrayList<>();
        for (ContextHint hint : hints) {
            list.add(new ContextHintWrapper(hint));
        }
        return list;
    }

    public static final @NonNull Parcelable.Creator<ContextHintWrapper> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public ContextHintWrapper createFromParcel(Parcel source) {
                    return new ContextHintWrapper(
                            ContextHint.createHintFromBundle(source.readBundle()));
                }

                @Override
                public ContextHintWrapper[] newArray(int size) {
                    return new ContextHintWrapper[size];
                }
            };
}
