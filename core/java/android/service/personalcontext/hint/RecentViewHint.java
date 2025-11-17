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

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A hint that provides information about text content that is currently or was recently visible on
 * the user's screen.
 *
 * <p>This data originates from the Android Content Capture framework.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class RecentViewHint extends ContextHint {
    private static final String KEY_CAPTURED_TEXTS = "key_captured_texts";
    private static final String KEY_LOCUS_ID = "key_locus_id";
    private final List<CapturedText> mCapturedTexts;
    private final String mLocusId;

    /** Creates a new {@link RecentViewHint}. */
    private RecentViewHint(
            @NonNull ConstructorParams baseParams,
            @NonNull List<CapturedText> capturedTexts,
            @Nullable String locusId) {
        super(baseParams);
        mCapturedTexts = List.copyOf(capturedTexts);
        mLocusId = locusId;
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    RecentViewHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);
        mCapturedTexts = bundle.getParcelableArrayList(KEY_CAPTURED_TEXTS, CapturedText.class);
        requireNonNull(mCapturedTexts, "Bundle must contain captured text set");
        mLocusId = bundle.getString(KEY_LOCUS_ID);
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_RECENT_VIEW;
    }

    /**
     * Returns a list of {@link CapturedText} objects, each representing a piece of text captured
     * from a UI element on the screen.
     */
    @NonNull
    public List<CapturedText> getCapturedTexts() {
        return mCapturedTexts;
    }

    /** Get the locus ID associated with the hint. */
    @Nullable
    public String getLocusId() {
        return mLocusId;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        final Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(KEY_CAPTURED_TEXTS, new ArrayList<>(mCapturedTexts));
        bundle.putString(KEY_LOCUS_ID, mLocusId);
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecentViewHint)) return false;
        if (!super.equals(o)) return false;
        RecentViewHint that = (RecentViewHint) o;
        return Objects.equals(mCapturedTexts, that.mCapturedTexts)
                && Objects.equals(mLocusId, that.mLocusId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mCapturedTexts, mLocusId);
    }

    @Override
    public String toString() {
        return "RecentViewHint{"
                + "mCapturedTexts="
                + mCapturedTexts
                + ", mLocusId='"
                + mLocusId
                + '\''
                + "} extends "
                + super.toString();
    }

    /** Builder used to create a {@link RecentViewHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final List<CapturedText> mCapturedTexts = new ArrayList<>();
        private String mLocusId;

        /** Creates an instance of {@link Builder}. */
        public Builder() {}

        /**
         * Adds a token to the resulting {@link RecentViewHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * Adds a {@link CapturedText} object containing information about a single captured UI text
         * element.
         */
        @NonNull
        public Builder addCapturedText(@NonNull CapturedText capturedText) {
            requireNonNull(capturedText, "capturedText must not be null");
            mCapturedTexts.add(capturedText);
            return this;
        }

        /** Sets the locus ID for the hint. */
        @NonNull
        public Builder setLocusId(@Nullable String locusId) {
            mLocusId = locusId;
            return this;
        }

        /**
         * @return the built {@link RecentViewHint}.
         */
        @NonNull
        public RecentViewHint build() {
            return new RecentViewHint(mBaseBuilder.build(), mCapturedTexts, mLocusId);
        }
    }
}
