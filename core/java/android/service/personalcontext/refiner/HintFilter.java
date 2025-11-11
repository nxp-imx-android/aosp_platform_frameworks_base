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

package android.service.personalcontext.refiner;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import com.google.android.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Filter for hint refiners to indicate which hints they want to receive.
 *
 * <p>Create a filter via the {@link HintFilter.Builder} with the {@link HintRefinerService} and
 * {@link android.service.personalcontext.understander.ContextUnderstanderService}
 * {@code filter___()} methods.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
public final class HintFilter implements Parcelable {
    private final Set<String> mAllowedClasses;
    private final Set<String> mRequiredClasses;
    private final Set<Token> mAllowedTokens;
    private final Set<Token> mRequiredTokens;
    private final Set<String> mAllowedPackages;

    private HintFilter(
            Collection<String> allowedClasses,
            Collection<String> requiredClasses,
            Collection<Token> allowedTokens,
            Collection<Token> requiredTokens,
            Collection<String> allowedPackages) {
        mAllowedClasses = Set.copyOf(allowedClasses);
        mRequiredClasses = Set.copyOf(requiredClasses);
        mAllowedTokens = Set.copyOf(allowedTokens);
        mRequiredTokens = Set.copyOf(requiredTokens);
        mAllowedPackages = Set.copyOf(allowedPackages);
    }

    @SuppressWarnings({"unchecked"})
    private HintFilter(Parcel in) {
        mAllowedClasses = Set.copyOf((Set<String>) in.readArraySet(/* classLoader= */ null));
        mRequiredClasses = Set.copyOf((Set<String>) in.readArraySet(/* classLoader= */ null));

        final ArrayList<Token> allowedTokens = new ArrayList<>();
        in.readTypedList(allowedTokens, Token.CREATOR);
        mAllowedTokens = Set.copyOf(allowedTokens);

        final ArrayList<Token> requiredTokens = new ArrayList<>();
        in.readTypedList(requiredTokens, Token.CREATOR);
        mRequiredTokens = Set.copyOf(requiredTokens);

        mAllowedPackages = Set.copyOf((Set<String>) in.readArraySet(/* classLoader= */ null));
    }

    /** Gets the hint classes the filter was configured with. */
    @NonNull
    public Set<String> getHintClasses() {
        return mAllowedClasses;
    }

    /** Gets the hint classes the filter was configured with that were marked as required. */
    @NonNull
    public Set<String> getRequiredHintClasses() {
        return mRequiredClasses;
    }

    /** Gets the hint tokens the filter was configured with. */
    @NonNull
    public Set<Token> getHintTokens() {
        return mAllowedTokens;
    }

    /** Gets the hint tokens the filter was configured with that were marked as required. */
    @NonNull
    public Set<Token> getRequiredHintTokens() {
        return mRequiredTokens;
    }

    /** Gets the packages the filter was configured with. */
    @NonNull
    public Set<String> getPackages() {
        return mAllowedPackages;
    }

    /** @hide */
    @TestApi
    @NonNull
    public Set<ContextHintWithSignature> getInterestedHintClusters(
            @NonNull Set<ContextHintWithSignature> allContextHints,
            @NonNull Set<UUID> seenIDs) {
        final Set<ContextHintWithSignature> interestingHints = new HashSet<>();
        final Set<Token> foundTokens = new HashSet<>();
        final Set<String> foundClasses = new HashSet<>();
        for (ContextHintWithSignature hintWithSignature : allContextHints) {
            final ContextHint hint = hintWithSignature.getContextHint();

            // Ignore hints that the refiner has seen before.
            if (!seenIDs.contains(hint.getHintId())) {
                // If we allow tokens, make sure the hint has at least one of the allowed tokens.
                if (!mAllowedTokens.isEmpty()
                        && Collections.disjoint(mAllowedTokens, hint.getTokens())) {
                    continue;
                }

                // If we allow classes, make sure the hint is one of the allowed classes.
                if (!mAllowedClasses.isEmpty()
                        && !mAllowedClasses.contains(hint.getClass().getName())) {
                    continue;
                }

                // If we allow classes, make sure the hint is one of the allowed classes.
                if (!mAllowedPackages.isEmpty()
                        && !mAllowedPackages.contains(hintWithSignature.getOriginatingPackage())) {
                    continue;
                }

                interestingHints.add(hintWithSignature);
                foundTokens.addAll(hint.getTokens());
                foundClasses.add(hint.getClass().getName());
            }
        }

        // If we require tokens, make sure that the collection of hints has all required tokens.
        if (!mRequiredTokens.isEmpty() && !foundTokens.containsAll(mRequiredTokens)) {
            return Collections.emptySet();
        }

        // If we require classes, make sure that the collection of hints has all required classes.
        if (!mRequiredClasses.isEmpty() && !foundClasses.containsAll(mRequiredClasses)) {
            return Collections.emptySet();
        }

        return interestingHints;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeArraySet(new ArraySet<>(mAllowedClasses));
        dest.writeArraySet(new ArraySet<>(mRequiredClasses));
        dest.writeTypedList(new ArrayList<>(mAllowedTokens));
        dest.writeTypedList(new ArrayList<>(mRequiredTokens));
        dest.writeArraySet(new ArraySet<>(mAllowedPackages));
    }

    @NonNull
    public static final Creator<HintFilter> CREATOR =
            new Creator<>() {
                @Override
                public HintFilter createFromParcel(Parcel in) {
                    return new HintFilter(in);
                }

                @Override
                public HintFilter[] newArray(int size) {
                    return new HintFilter[size];
                }
            };

    /** Builder for a {@link HintFilter}. */
    public static final class Builder {
        private final Set<Token> mAllowedTokens = Sets.newHashSet();
        private final Set<Token> mRequiredTokens = Sets.newHashSet();
        private final Set<String> mAllowedClasses = Sets.newHashSet();
        private final Set<String> mRequiredClasses = Sets.newHashSet();
        private final Set<String> mAllowedPackages = Sets.newHashSet();

        /**
         * Creates a new instance of a {@link HintFilter} {@link Builder}.
         */
        public Builder() { }

        /**
         * Adds a hint class to the filter. By default the filter will allow all hints to be sent to
         * the refiner, regardless of class. Adding one or more classes changes the filter so that
         * each hint sent to the refiner will be one of those types. If the class is marked as
         * required then the collection will not be sent to the refiner until all of the required
         * hint classes are available.
         *
         * @param hintClass class of {@link ContextHint} that this refiner will accept
         * @param required determine whether the refiner can be called without this hint class
         */
        @NonNull
        public Builder addHintClass(
                @NonNull Class<? extends ContextHint> hintClass, boolean required) {
            requireNonNull(hintClass, "hintClass must not be null");
            mAllowedClasses.add(hintClass.getName());
            if (required) mRequiredClasses.add(hintClass.getName());
            return this;
        }

        /**
         * Adds a hint token to the filter. By default the filter will allow all hints to be sent to
         * the refiner, regardless of token. Adding one or more tokens changes the filter so that
         * each hint sent to the refiner will be one of those types. If the token is marked as
         * required then the collection will not be sent to the refiner until all of the required
         * hint tokens are available.
         *
         * @param hintToken token that this refiner will accept
         * @param required determine whether the refiner can be called without this hint token
         */
        @NonNull
        public Builder addHintToken(@NonNull Token hintToken, boolean required) {
            requireNonNull(hintToken, "hintToken must not be null");
            mAllowedTokens.add(hintToken);
            if (required) mRequiredTokens.add(hintToken);
            return this;
        }

        /**
         * Adds a valid package to the filter. By default the filter will allow all hints
         * to be sent to the renderer, regardless of originating package. Adding one or more valid
         * packages changes the filter so that each hint sent to the renderer will have originated
         * from one of those packages.
         *
         * @param packageName name of the package that this refiner will accept
         */
        @NonNull
        public Builder addPackage(@NonNull String packageName) {
            mAllowedPackages.add(requireNonNull(packageName));
            return this;
        }

        /** Builds the new HintFilter. */
        @NonNull
        public HintFilter build() {
            return new HintFilter(
                    mAllowedClasses,
                    mRequiredClasses,
                    mAllowedTokens,
                    mRequiredTokens,
                    mAllowedPackages);
        }
    }
}
