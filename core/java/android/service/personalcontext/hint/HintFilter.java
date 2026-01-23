/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import com.google.android.collect.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Filter for hint refiners and understanders to indicate which hints they want to receive.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
public final class HintFilter implements Parcelable {
    private final Set<String> mAllowedTypes;
    private final Set<String> mRequiredTypes;
    private final Set<String> mAllowedPackages;

    private HintFilter(
            Collection<String> allowedTypes,
            Collection<String> requiredTypes,
            Collection<String> allowedPackages) {
        mAllowedTypes = Set.copyOf(allowedTypes);
        mRequiredTypes = Set.copyOf(requiredTypes);
        mAllowedPackages = Set.copyOf(allowedPackages);
    }

    @SuppressWarnings({"unchecked"})
    private HintFilter(Parcel in) {
        mAllowedTypes = Set.copyOf((Set<String>) in.readArraySet(/* classLoader= */ null));
        mRequiredTypes = Set.copyOf((Set<String>) in.readArraySet(/* classLoader= */ null));
        mAllowedPackages = Set.copyOf((Set<String>) in.readArraySet(/* classLoader= */ null));
    }

    /** Gets the hint types the filter was configured with. */
    @NonNull
    public Set<String> getHintTypes() {
        return mAllowedTypes;
    }

    /** Gets the hint types the filter was configured with that were marked as required. */
    @NonNull
    public Set<String> getRequiredHintTypes() {
        return mRequiredTypes;
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
        final Set<String> foundTypes = new HashSet<>();
        for (ContextHintWithSignature hintWithSignature : allContextHints) {
            final ContextHint hint = hintWithSignature.getContextHint();

            // Ignore hints that the refiner has seen before.
            if (!seenIDs.contains(hint.getHintId())) {
                // If we allow types, make sure the hint is one of the allowed types.
                if (!mAllowedTypes.isEmpty()
                        && !mAllowedTypes.contains(hint.getHintTypeName())) {
                    continue;
                }

                // If we allow types, make sure the hint is one of the allowed types.
                if (!mAllowedPackages.isEmpty()
                        && !mAllowedPackages.contains(hintWithSignature.getOriginatingPackage())) {
                    continue;
                }

                interestingHints.add(hintWithSignature);
                foundTypes.add(hint.getHintTypeName());
            }
        }

        // If we require types, make sure that the collection of hints has all required types.
        if (!mRequiredTypes.isEmpty() && !foundTypes.containsAll(mRequiredTypes)) {
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
        dest.writeArraySet(new ArraySet<>(mAllowedTypes));
        dest.writeArraySet(new ArraySet<>(mRequiredTypes));
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
        private final Set<String> mAllowedTypes = Sets.newHashSet();
        private final Set<String> mRequiredTypes = Sets.newHashSet();
        private final Set<String> mAllowedPackages = Sets.newHashSet();

        /**
         * Creates a new instance of a {@link HintFilter} {@link Builder}.
         */
        public Builder() { }

        /**
         * Adds a hint type to the filter. By default the filter will allow all hints to be sent to
         * the refiner, regardless of type. Adding one or more types changes the filter so that
         * each hint sent to the refiner will be one of those types. If the type is marked as
         * required then the collection will not be sent to the refiner until all of the required
         * hint types are available.
         *
         * @param hintType type name of {@link ContextHint} that this refiner will accept
         * @param required determine whether the refiner can be called without this hint type
         */
        @NonNull
        public Builder addHintType(@NonNull String hintType, boolean required) {
            requireNonNull(hintType, "hintType must not be null");
            mAllowedTypes.add(hintType);
            if (required) mRequiredTypes.add(hintType);
            return this;
        }

        /**
         * Adds a hint type to the filter. By default the filter will allow all hints to be sent to
         * the refiner, regardless of type. Adding one or more types changes the filter so that
         * each hint sent to the refiner will be one of those types. If the type is marked as
         * required then the collection will not be sent to the refiner until all of the required
         * hint types are available.
         *
         * @param hintClass class of {@link ContextHint} that this refiner will accept
         * @param required determine whether the refiner can be called without this hint type
         */
        @NonNull
        public Builder addHintType(
                @NonNull Class<? extends ContextHint> hintClass, boolean required) {
            return addHintType(hintClass.getCanonicalName(), required);
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
                    mAllowedTypes,
                    mRequiredTypes,
                    mAllowedPackages);
        }
    }
}
