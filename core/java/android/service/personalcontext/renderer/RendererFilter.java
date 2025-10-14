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

package android.service.personalcontext.renderer;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import com.google.android.collect.Sets;

import java.util.Collections;
import java.util.Set;

/**
 * Filter for insight renderers to indicate which insights they want to receive.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
public final class RendererFilter implements Parcelable {
    @ContextInsight.InsightType
    private final Set<Integer> mValidInsightTypes;
    @ContextHint.HintType
    private final Set<Integer> mValidHintTypes;
    private final boolean mIncludeRenderedInsights;

    private RendererFilter(
            @ContextInsight.InsightType Set<Integer> validInsightTypes,
            @ContextHint.HintType Set<Integer> validHintTypes,
            boolean includeRenderedInsights
    ) {
        mValidInsightTypes = validInsightTypes;
        mValidHintTypes = validHintTypes;
        mIncludeRenderedInsights = includeRenderedInsights;
    }

    @SuppressWarnings({"unchecked"})
    private RendererFilter(Parcel in) {
        mValidInsightTypes = (Set<Integer>) in.readArraySet(Integer.class.getClassLoader());
        mValidHintTypes = (Set<Integer>) in.readArraySet(Integer.class.getClassLoader());
        mIncludeRenderedInsights = in.readByte() != 0;
    }

    /**
     * @return an unmodifiable {@code Set} of valid insight types
     */
    @NonNull
    @ContextInsight.InsightType
    public Set<Integer> getValidInsightTypes() {
        return Collections.unmodifiableSet(mValidInsightTypes);
    }

    /**
     * @return an unmodifiable {@code Set} of valid hint types
     */
    @NonNull
    @ContextHint.HintType
    public Set<Integer> getValidHintTypes() {
        return Collections.unmodifiableSet(mValidHintTypes);
    }

    /**
     * @return {@code true} if this renderer should receive insights that have already been
     * rendered by another renderer
     */
    public boolean shouldIncludeRenderedInsights() {
        return mIncludeRenderedInsights;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeArraySet(new ArraySet<>(mValidInsightTypes));
        dest.writeArraySet(new ArraySet<>(mValidHintTypes));
        dest.writeByte((byte) (mIncludeRenderedInsights ? 1 : 0));
    }

    @android.annotation.NonNull
    public static final Creator<RendererFilter> CREATOR =
            new Creator<>() {
                @Override
                public RendererFilter createFromParcel(Parcel in) {
                    return new RendererFilter(in);
                }

                @Override
                public RendererFilter[] newArray(int size) {
                    return new RendererFilter[size];
                }
            };

    /** Builder for a renderer filter. */
    public static final class Builder {
        @ContextInsight.InsightType
        private final Set<Integer> mValidInsightTypes = Sets.newHashSet();
        @ContextHint.HintType
        private final Set<Integer> mValidHintTypes = Sets.newHashSet();
        private boolean mIncludeRenderedInsights = false;

        /** Creates a new builder to build a renderer filter. */
        public Builder() {
        }

        /**
         * Add a valid insight type to the filter. An empty set of valid insight types means that
         * all insight types will be sent to the renderer. If a renderer wishes to restrict the
         * insight types it sees, then it should add those types using this method. By default,
         * the set of valid insight types is empty.
         */
        @NonNull
        public Builder addValidInsightType(@ContextInsight.InsightType int insightType) {
            mValidInsightTypes.add(insightType);
            return this;
        }

        /**
         * Add a valid hint type to the filter. An empty set of valid hint types means that all hint
         * types will be sent to the renderer. If a render wishes to restrict the hint types it
         * sees, then it should add those types using this method. By default, the set of valid hint
         * types is empty.
         */
        @NonNull
        public Builder addValidHintType(@ContextHint.HintType int hintType) {
            mValidHintTypes.add(hintType);
            return this;
        }

        /**
         * Set whether the filter should include rendered insights. The default is 'false',
         * indicating that already rendered insights should not be included in the list of insights
         * sent to the renderer.
         */
        @NonNull
        public Builder setShouldIncludeRenderedInsights(boolean includeRenderedInsights) {
            mIncludeRenderedInsights = includeRenderedInsights;
            return this;
        }

        /** Build a return a new RendererFilter. */
        @NonNull
        public RendererFilter build() {
            return new RendererFilter(
                    mValidInsightTypes, mValidHintTypes, mIncludeRenderedInsights);
        }
    }
}
