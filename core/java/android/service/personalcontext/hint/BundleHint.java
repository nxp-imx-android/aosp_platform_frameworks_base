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
import android.os.Bundle;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

/**
 * A hint that stores arbitrary data in a {@link Bundle}. Should only be used if there is no
 * appropriate hint type already defined.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class BundleHint extends ContextHint {
    /**
     * {@link Bundle} of arbitrary data provided to the personal context engine.
     */
    private final Bundle mDataBundle;

    /**
     * DO NOT USE - This constructor will be removed before API finalization.
     *
     * @deprecated Use {@link BundleHint.Builder} instead.
     */
    @Deprecated
    public BundleHint() {
        this(new ConstructorParams.Builder().build(), new Bundle());
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     */
    BundleHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);
        mDataBundle = bundle;
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_BUNDLE;
    }

    /**
     * Returns the data bundle contained within this hint.
     */
    @NonNull
    public Bundle getDataBundle() {
        return mDataBundle;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        return mDataBundle;
    }

    /**
     * Builder used to create a {@link BundleHint}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final Bundle mBundle = new Bundle();

        /**
         * Adds a token to the resulting {@link BundleHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /**
         * Copies all bundle data to be included in the resulting {@link BundleHint}.
         *
         * @param bundle Bundle to copy data from
         */
        @NonNull
        public Builder setDataBundle(@NonNull Bundle bundle) {
            mBundle.putAll(bundle);
            return this;
        }

        /**
         * @return the built {@link BundleHint}.
         */
        @NonNull
        public BundleHint build() {
            return new BundleHint(mBaseBuilder.build(), mBundle);
        }
    }
}
