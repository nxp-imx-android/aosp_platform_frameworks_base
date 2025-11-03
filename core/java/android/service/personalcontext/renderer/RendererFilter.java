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
import android.service.personalcontext.Token;
import android.service.personalcontext.insight.ContextInsight;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import com.google.android.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter for insight renderers to indicate which insights they want to receive. Create a filter via
 * the {@link RendererFilter.Builder} or use {@link RendererFilter#REQUIRE_RENDER_TOKEN} if your
 * renderer should only ever be invoked with a {@link android.service.personalcontext.RenderToken}.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
public final class RendererFilter implements Parcelable {
    /**
     * Pre-built {@link RendererFilter} that only allows insights with a RenderToken attached.
     */
    public static final RendererFilter REQUIRE_RENDER_TOKEN =
            new Builder().addAllowedInsightToken(new Token()).build();

    private final Set<String> mAllowedClasses;
    private final Set<Token> mAllowedTokens;
    private final Set<Token> mRequiredTokens;

    /** @hide */
    public RendererFilter(
            Collection<String> allowedClasses,
            Collection<Token> allowedTokens,
            Collection<Token> requiredTokens) {
        mAllowedClasses = new HashSet<>(allowedClasses);
        mAllowedTokens = new HashSet<>(allowedTokens);
        mRequiredTokens = new HashSet<>(requiredTokens);
    }

    @SuppressWarnings({"unchecked"})
    private RendererFilter(Parcel in) {
        final ArrayList<Token> allowedTokens = new ArrayList<>();
        final ArrayList<Token> requiredTokens = new ArrayList<>();

        mAllowedClasses = (Set<String>) in.readArraySet(/* classLoader= */ null);
        in.readTypedList(allowedTokens, Token.CREATOR);
        in.readTypedList(requiredTokens, Token.CREATOR);
        mAllowedTokens = new HashSet<>(allowedTokens);
        mRequiredTokens = new HashSet<>(requiredTokens);
    }

    /** @hide */
    public boolean isInterestedInInsight(ContextInsight insight) {
        // If we allow tokens, make sure the insight has at least one of the allowed tokens.
        if (!mAllowedTokens.isEmpty()
                && Collections.disjoint(mAllowedTokens, insight.getTokens())) {
            return false;
        }

        // If we require tokens, make sure the insight has all of the required tokens.
        if (!mRequiredTokens.isEmpty() && !insight.getTokens().containsAll(mRequiredTokens)) {
            return false;
        }

        // If we allow classes, make sure the insight is one of the allowed classes.
        if (!mAllowedClasses.isEmpty() && !mAllowedClasses.contains(insight.getClass().getName())) {
            return false;
        }

        // All checks passed.
        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeArraySet(new ArraySet<>(mAllowedClasses));
        dest.writeTypedList(new ArrayList<>(mAllowedTokens));
        dest.writeTypedList(new ArrayList<>(mRequiredTokens));
    }

    @NonNull
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

    /** Builder for a {@link RendererFilter}. */
    public static final class Builder {
        private final Set<String> mAllowedClasses = Sets.newHashSet();
        private final Set<Token> mAllowedTokens = Sets.newHashSet();
        private final Set<Token> mRequiredTokens = Sets.newHashSet();

        /**
         * Create a new instance of the Builder.
         */
        public Builder() { }

        /**
         * Adds a valid insight class to the filter. By default the filter will allow all insights
         * to be sent to the renderer, regardless of class. Adding one or more valid classes changes
         * the filter so that each insight sent to the renderer will be one of those types.
         */
        @SuppressWarnings("MissingGetterMatchingBuilder")
        @NonNull
        public Builder addAllowedInsightClass(
                @NonNull Class<? extends ContextInsight> insightClass) {
            mAllowedClasses.add(insightClass.getName());
            return this;
        }

        /**
         * Adds a valid insight token to the filter. By default the filter will allow all insights
         * to be sent to the renderer, regardless of tokens. Adding one or more allowed tokens
         * changes the filter so that each insight sent to the renderer will have at least one of
         * those tokens attached to it.
         */
        @SuppressWarnings("MissingGetterMatchingBuilder")
        @NonNull
        public Builder addAllowedInsightToken(@NonNull Token insightToken) {
            mAllowedTokens.add(insightToken);
            return this;
        }

        /**
         * Adds a required insight token to the filter. By default the filter will allow all
         * insights to be sent to the renderer, regardless of tokens. Adding one or more required
         * tokens changes the filter so that each insight sent to the renderer will have at all of
         * those tokens attached to it.
         */
        @SuppressWarnings("MissingGetterMatchingBuilder")
        @NonNull
        public Builder addRequiredInsightToken(@NonNull Token insightToken) {
            mRequiredTokens.add(insightToken);
            return this;
        }

        /** Builds the new RendererFilter. */
        @NonNull
        public RendererFilter build() {
            return new RendererFilter(mAllowedClasses, mAllowedTokens, mRequiredTokens);
        }
    }
}
