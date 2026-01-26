/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.ondeviceintelligence.embedding;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.ondeviceintelligence.flags.Flags;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents a raw vector embedding.
 *
 * <p>The generated embeddings from this response are compatible with and can be stored and queried
 * in AppSearch. For more details on how to use embeddings in AppSearch, see
 * {@link android.app.appsearch.EmbeddingVector}.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
public final class EmbeddingVector implements Parcelable {
    private final float[] mVector;

    /**
     * Constructs a new {@link EmbeddingVector} from the given float array.
     *
     * @param vector The vector representing the embedding.
     */
    public EmbeddingVector(@NonNull float[] vector) {
        mVector = Objects.requireNonNull(vector);
    }

    /**
     * Returns the raw vector data representing the embedding.
     */
    @NonNull
    public float[] getVector() {
        return mVector;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeFloatArray(mVector);
    }

    public static final @NonNull Creator<EmbeddingVector> CREATOR =
            new Creator<EmbeddingVector>() {
                @Override
                public EmbeddingVector createFromParcel(Parcel in) {
                    return new EmbeddingVector(in);
                }

                @Override
                public EmbeddingVector[] newArray(int size) {
                    return new EmbeddingVector[size];
                }
            };

    private EmbeddingVector(Parcel in) {
        mVector = in.createFloatArray();
    }
}
