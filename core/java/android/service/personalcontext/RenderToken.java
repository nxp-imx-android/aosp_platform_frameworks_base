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

package android.service.personalcontext;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.hint.ContextHint;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * Token for a specific renderer that can be included in a {@link ContextHint} or insight. If
 * included in a hint, indicates that insights generated from the hint should only go to the
 * specific renderer associated with this token. If included in an insight, indicates the insight
 * should only be sent to the specific renderer associated with this token.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class RenderToken implements Parcelable {
    /**
     * Unique identifier for this token.
     */
    private final UUID mId;

    /**
     * Unique identifier of the renderer this token is associated with.
     */
    private final UUID mRendererComponentId;

    /**
     * Creates a new {@link RenderToken} for the renderer with the given ID.
     */
    private RenderToken(@NonNull UUID rendererComponentId) {
        mId = UUID.randomUUID();
        mRendererComponentId = rendererComponentId;
    }

    RenderToken(Parcel in) {
        mId = UUID.fromString(in.readString());
        mRendererComponentId = UUID.fromString(in.readString());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId.toString());
        dest.writeString(mRendererComponentId.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the unique ID of this token.
     */
    public @NonNull UUID getTokenId() {
        return mId;
    }

    /**
     * Returns the unique ID of the renderer this token is associated with.
     */
    public @NonNull UUID getRendererComponentId() {
        return mRendererComponentId;
    }

    public static final @NonNull Creator<RenderToken> CREATOR = new Creator<>() {
        @Override
        public RenderToken createFromParcel(Parcel in) {
            return new RenderToken(in);
        }

        @Override
        public RenderToken[] newArray(int size) {
            return new RenderToken[size];
        }
    };

    /**
     * Builder used to create a {@link RenderToken}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class RenderTokenBuilder {
        private UUID mRendererComponentId;

        /** Set identifier of the renderer this token is associated with. */
        @NonNull
        public RenderTokenBuilder setRendererComponentId(@NonNull UUID rendererComponentId) {
            mRendererComponentId = rendererComponentId;
            return this;
        }

        /** Returns the built {@link RenderToken}. */
        @NonNull
        public RenderToken build() {
            return new RenderToken(mRendererComponentId);
        }
    }
}
