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

package android.service.personalcontext.insight;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.hint.ContextHint;

import java.util.ArrayList;
import java.util.List;

/**
 * An insight that stores arbitrary data in a {@link Bundle}. Should only be used if there is no
 * appropriate insight type already defined.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class BundleInsight extends ContextInsight {
    private final Bundle mDataBundle;

    /** Private constructor used by the builder. */
    private BundleInsight(@NonNull List<ContextHint> originHints, @NonNull Bundle dataBundle) {
        super(originHints);
        mDataBundle = dataBundle;
    }

    /**
     * Internal constructor only for use by
     * {@link ContextInsight#createInsightFromBundle(Bundle)} (Bundle)}.
     */
    BundleInsight(@NonNull Bundle b) {
        super(b);
        mDataBundle = b.getBundle(KEY_INSIGHT_DATA);
    }

    @Override
    @InsightType
    int getInsightType() {
        return INSIGHT_TYPE_BUNDLE;
    }

    /** Returns the insight's data {@link Bundle}. */
    @NonNull
    public Bundle getDataBundle() {
        return mDataBundle;
    }

    @Override
    @NonNull
    Bundle toBundleImpl() {
        return mDataBundle;
    }

    /** Builder for {@link BundleInsight}. */
    public static final class Builder {
        private final List<ContextHint> mOriginHints = new ArrayList<>();
        private final Bundle mDataBundle = new Bundle();

        /** Construct a new empty builder. */
        public Builder() {
        }

        /**
         * Adds an origin {@link ContextHint}s to the resulting {@link BundleInsight}.
         *
         * @param hint the origin {@link ContextHint} to add.
         */
        @NonNull
        public Builder addOriginHint(@NonNull ContextHint hint) {
            mOriginHints.add(hint);
            return this;
        }

        /**
         * Sets the data in the given {@link Bundle} to the resulting {@link BundleInsight}'s data
         * bundle.
         *
         * @param dataBundle the {@link Bundle} containing the data to set.
         */
        @NonNull
        public Builder setDataBundle(@NonNull Bundle dataBundle) {
            mDataBundle.clear();
            mDataBundle.putAll(dataBundle);
            return this;
        }

        /** Create and return a new {@link BundleInsight}. */
        @NonNull
        public BundleInsight build() {
            return new BundleInsight(mOriginHints, mDataBundle);
        }
    }
}
