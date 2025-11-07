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

package android.graphics.fonts;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * A request to update an unnamed fallback font family in the system.
 *
 * @hide
 */
@FlaggedApi(com.android.text.flags.Flags.FLAG_INSERT_FONT_FAMILY)
@SystemApi
@android.ravenwood.annotation.RavenwoodKeepWholeClass
public final class FallbackFontUpdateRequest {

    private final List<FontFamilyUpdateRequest.Font> mFonts;
    private final String mLanguages;

    // An integer representing the priority of this fallback chain. Higher values mean higher
    // priority.
    private final int mPriority;

    private FallbackFontUpdateRequest(
            @NonNull List<FontFamilyUpdateRequest.Font> fonts, @NonNull String languages,
            int priority) {
        mFonts = fonts;
        mLanguages = languages;
        mPriority = priority;
    }

    @NonNull
    public List<FontFamilyUpdateRequest.Font> getFonts() {
        return mFonts;
    }

    @NonNull
    public String getLanguages() {
        return mLanguages;
    }

    public int getPriority() {
        return mPriority;
    }

    /**
     * A builder for {@link FallbackFontUpdateRequest}.
     */
    public static final class Builder {
        private final List<FontFamilyUpdateRequest.Font> mFonts = new ArrayList<>();
        private String mLanguages = "";
        private int mPriority = -1;

        /**
         * Appends a font to the fallback family.
         *
         * @param font The font to add.
         * @return This builder.
         */
        @NonNull
        public Builder addFont(@NonNull FontFamilyUpdateRequest.Font font) {
            mFonts.add(font);
            return this;
        }

        /**
         * Sets the language tags for this font family.
         *
         * @param languages A comma-separated string of BCP 47 language tags.
         * @return This builder.
         */
        @NonNull
        public Builder setLanguages(@NonNull String languages) {
            mLanguages = languages;
            return this;
        }

        /**
         * Sets the priority for this font family.
         *
         * @param priority The priority of the font family.
         * @return This builder.
         */
        @NonNull
        public Builder setPriority(int priority) {
            mPriority = priority;
            return this;
        }

        /**
         * Builds the {@link FallbackFontUpdateRequest}.
         *
         * @return A new {@link FallbackFontUpdateRequest} instance.
         */
        @NonNull
        public FallbackFontUpdateRequest build() {
            Preconditions.checkArgument(!mFonts.isEmpty(),
                    "At least one font must be provided.");
            Preconditions.checkArgument(mPriority != -1, "Priority must be set.");
            return new FallbackFontUpdateRequest(new ArrayList<>(mFonts), mLanguages, mPriority);
        }
    }
}
