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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelableException;
import android.service.personalcontext.Flags;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.collect.Sets;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Filter for hint refiners and understanders to indicate which hints they want to receive. The
 * filter can be a combination of different criteria, such as the publisher's package or the type
 * of {@link ContextHint}. Each parameter can be customized with a number of filter flags.
 * Specifying {@code FILTER_TYPE_ALLOWED} limits the returned set to only include the those hints
 * that match the associated filter. Specifying {@code FILTER_TYPE_FILTER_TYPE_REQUIRED} means that
 * a hint matching this filter must be present for the set to be returned.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
public final class HintFilter implements Parcelable {
    private static final String TAG = "HintFilter";

    /**
     * An interface for {@link PublishedContextHint} filters to implement.
     */
    private interface FilterEntry extends Parcelable {
        /**
         * Returns {@code true} if the supplied hint matches this filter.
         * @param hint The {@link PublishedContextHint} to be chcked
         * @return {@code true} if matches, {@code false} otherwise.
         */
        boolean matches(PublishedContextHint hint);

        /**
         * Passes self to visit.
         * @param visitor
         */
        void visit(FilterEntryVisitor visitor);
    }

    /**
     * An interface in order to visit the various filter entry types.
     */
    private interface FilterEntryVisitor {
        default void onVisit(PackageEntry entry) {}
        default void onVisit(ContextHintTypeEntry entry) {}
        default void onVisit(BundleHintTypeNameEntry entry) {}
    }

    /**
     * {@link PublishedContextHint} filter for package names.
     */
    private static class PackageEntry implements FilterEntry {
        private final String mPackageName;

        PackageEntry(String packageName) {
            mPackageName = packageName;
        }

        PackageEntry(Parcel in) {
            mPackageName = in.readString8();
        }

        public String getPackageName() {
            return mPackageName;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString8(mPackageName);
        }

        @Override
        public boolean matches(PublishedContextHint hint) {
            return TextUtils.equals(hint.getOriginatingPackage(), mPackageName);
        }

        public static final Creator<PackageEntry> CREATOR = new Creator<>() {
            @Override
            public PackageEntry createFromParcel(Parcel in) {
                return new PackageEntry(in);
            }

            @Override
            public PackageEntry[] newArray(int size) {
                return new PackageEntry[size];
            }
        };

        @Override
        public void visit(FilterEntryVisitor visitor) {
            visitor.onVisit(this);
        }
    }

    /**
     * {@link PublishedContextHint} filter for types specified on {@link BundleHint}.
     */
    private static class BundleHintTypeNameEntry implements FilterEntry {
        private final String mBundleHintTypeName;

        protected BundleHintTypeNameEntry(String contextHintClassType) {
            mBundleHintTypeName = contextHintClassType;
        }
        protected BundleHintTypeNameEntry(Parcel in) {
            mBundleHintTypeName = in.readString();
        }

        public String getBundleHintTypeName() {
            return mBundleHintTypeName;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(mBundleHintTypeName);
        }


        public static final Creator<BundleHintTypeNameEntry> CREATOR =
                new Creator<>() {
                    @Override
                    public BundleHintTypeNameEntry createFromParcel(Parcel in) {
                        return new BundleHintTypeNameEntry(in);
                    }

                    @Override
                    public BundleHintTypeNameEntry[] newArray(int size) {
                        return new BundleHintTypeNameEntry[size];
                    }
                };

        @Override
        public boolean matches(PublishedContextHint hint) {
            return TextUtils.equals(hint.getContextHint().getHintTypeName(), mBundleHintTypeName);
        }

        @Override
        public void visit(FilterEntryVisitor visitor) {
            visitor.onVisit(this);
        }
    }

    /**
     * {@link PublishedContextHint} filter for {@link ContextHint} subtypes.
     */
    private static class ContextHintTypeEntry implements FilterEntry {
        final String mContextHintClassType;

        ContextHintTypeEntry(Class<? extends ContextHint> contextHintClassType) {
            mContextHintClassType = contextHintClassType.getName();
        }

        public Class<? extends ContextHint> getContextHintClass() throws ClassNotFoundException {
            return (Class<? extends ContextHint>) Class.forName(mContextHintClassType);
        }

        public Class getContextHintClassType() throws ClassNotFoundException {
            return Class.forName(mContextHintClassType);
        }

        @Override
        public boolean matches(PublishedContextHint hint) {
            return TextUtils.equals(hint.getClass().getName(), mContextHintClassType);
        }

        @Override
        public void visit(FilterEntryVisitor visitor) {
            visitor.onVisit(this);
        }

        protected ContextHintTypeEntry(Parcel in) {
            mContextHintClassType = in.readString8();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString8(mContextHintClassType);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ContextHintTypeEntry> CREATOR =
                new Creator<>() {
                    @Override
                    public ContextHintTypeEntry createFromParcel(Parcel in) {
                        return new ContextHintTypeEntry(in);
                    }

                    @Override
                    public ContextHintTypeEntry[] newArray(int size) {
                        return new ContextHintTypeEntry[size];
                    }
                };
    }

    /** @hide */
    @IntDef(flag = true, prefix = { "FILTER_TYPE_" }, value = {
            FILTER_TYPE_NONE,
            FILTER_TYPE_ALLOWED,
            FILTER_TYPE_REQUIRED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FilterType {}

    /**
     * Flag indicating no filter is applied
     */
    public static final int FILTER_TYPE_NONE = 0;

    /**
     * Flag indicating that only hints matching this filter should be included.
     */
    public static final int FILTER_TYPE_ALLOWED = 1;

    /**
     * Flag indicating that the returned set must include a hint that matches this filter.
     */
    public static final int FILTER_TYPE_REQUIRED = 1 << 1;

    private static class FilterRecord<C extends FilterEntry> implements Parcelable {
        private final C mEntry;

        private final @FilterType int mFilterType;

        FilterRecord(C entry, @FilterType int filterType) {
            int adjustedFilter = filterType;

            if ((filterType & FILTER_TYPE_REQUIRED) == FILTER_TYPE_REQUIRED) {
                adjustedFilter |= FILTER_TYPE_ALLOWED;
            }

            mEntry = entry;
            mFilterType = adjustedFilter;
        }

        @FilterType int getFilterType() {
            return mFilterType;
        }

        public C getEntry() {
            return mEntry;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString8(mEntry.getClass().getName());
            dest.writeParcelable(mEntry, flags);
            dest.writeInt(mFilterType);
        }

        @SuppressWarnings("unchecked")
        protected FilterRecord(Parcel in) throws ClassNotFoundException {
            final String entryClassName = in.readString();
            final FilterEntry entry;

            if (ContextHintTypeEntry.class.getName().matches(entryClassName)) {
                entry = ContextHintTypeEntry.CREATOR.createFromParcel(in);
            } else if (PackageEntry.class.getName().matches(entryClassName)) {
                entry = PackageEntry.CREATOR.createFromParcel(in);
            } else if (BundleHintTypeNameEntry.class.getName().matches(entryClassName)) {
                entry = BundleHintTypeNameEntry.CREATOR.createFromParcel(in);
            } else {
                throw new ClassNotFoundException();
            }

            mEntry = (C) entry;
            mFilterType = in.readInt();
        }

        public static final Creator<FilterRecord> CREATOR = new Creator<>() {
            @Override
            public FilterRecord createFromParcel(Parcel in) {
                try {
                    return new FilterRecord<>(in);
                } catch (ClassNotFoundException e) {
                    throw new ParcelableException(e);
                }
            }

            @Override
            public FilterRecord[] newArray(int size) {
                return new FilterRecord[size];
            }
        };
    }
    private final Set<FilterRecord<FilterEntry>> mFilterRecords;

    private HintFilter(Collection<FilterRecord<FilterEntry>> filterRecords) {
        mFilterRecords = Set.copyOf(filterRecords);
    }

    @SuppressWarnings({"unchecked"})
    private HintFilter(Parcel in) {
        mFilterRecords = Set.copyOf((Set<FilterRecord<FilterEntry>>)
                in.readArraySet(/* classLoader= */ null));
    }

    /**
     * Returns the publishing packages checked for within this filter.
     * @param filter the {@link FilterType} flags that should be set on the matching filters.
     * @return A set of package names in the filter that match the supplied filter type flags.
     */
    @NonNull
    public Set<String> getPackages(@FilterType int filter) {
        final HashSet<String> returnSet = new HashSet<>();
        final Set<FilterRecord> candidateRecords = getFilterRecords(filter);
        for (FilterRecord record : candidateRecords) {
            record.getEntry().visit(new FilterEntryVisitor() {
                @Override
                public void onVisit(PackageEntry entry) {
                    returnSet.add(entry.getPackageName());
                }
            });
        }

        return returnSet;
    }

    /**
     * Returns the publishing packages checked for within this filter.
     * @return A set of package names in the filter.
     */
    @NonNull
    public Set<String> getPackages() {
        return getPackages(FILTER_TYPE_NONE);
    }

    /**
     * Returns the {@link ContextHint} classes within this filter.
     * @param filter the {@link FilterType} flags that should be set on the filters for returned
     * {@link ContextHint} classes
     * @return A set of classes in the filter that match the supplied filter type flags.
     */
    @NonNull
    public Set<Class> getHintTypes(@FilterType int filter) {
        final HashSet<Class> returnSet = new HashSet<>();
        final Set<FilterRecord> candidateRecords = getFilterRecords(filter);
        for (FilterRecord record : candidateRecords) {
            record.getEntry().visit(new FilterEntryVisitor() {
                @Override
                public void onVisit(ContextHintTypeEntry entry) {
                    try {
                        returnSet.add(entry.getContextHintClassType());
                    } catch (Exception e) {
                        Log.e(TAG, "could not get class type for:" + entry, e);
                    }
                }
            });
        }

        return returnSet;
    }

    /**
     * Returns the {@link ContextHint} classes within this filter.
     * @return A set of classes in the filter that match the supplied filter type flags.
     */
    @NonNull
    public Set<Class> getHintTypes() {
        return getHintTypes(FILTER_TYPE_NONE);
    }

    /**
     * Returns the {@link BundleHint} hint type names within the filter.
     *
     * @param filter the {@link FilterType} flags that should be present on the filtered type names.
     * @see BundleHint#getHintTypeName()
     * @return A set of {@link BundleHint} hint type names within this filter that match the
     * specified filter flags.
     */
    @NonNull
    public Set<String> getBundleHintTypeNames(@FilterType int filter) {
        final HashSet<String> returnSet = new HashSet<>();
        final Set<FilterRecord> candidateRecords = getFilterRecords(filter);
        for (FilterRecord record : candidateRecords) {
            record.getEntry().visit(new FilterEntryVisitor() {
                @Override
                public void onVisit(BundleHintTypeNameEntry entry) {
                    returnSet.add(entry.getBundleHintTypeName());
                }
            });
        }

        return returnSet;
    }

    /**
     * Returns the {@link BundleHint} hint type names within the filter.
     * @return A set of {@link BundleHint} hint type names within this filter
     */
    @NonNull
    public Set<String> getBundleHintTypeNames() {
        return getBundleHintTypeNames(FILTER_TYPE_NONE);
    }

    private ArraySet<FilterRecord> getFilterRecords(@FilterType int filterType) {
        final ArraySet<FilterRecord> matchingFilters = new ArraySet<>();
        // Get required filters
        for (FilterRecord record : mFilterRecords) {
            if ((record.getFilterType() & filterType) == filterType) {
                matchingFilters.add(record);
            }
        }

        return matchingFilters;
    }

    private FilterRecord findFirstMatch(PublishedContextHint hintWithSignature,
            Set<FilterRecord> records) {
        for (FilterRecord record : records) {
            if (record.getEntry().matches(hintWithSignature)) {
                return record;
            }
        }

        return null;
    }

    private Set<FilterRecord> findAllMatches(PublishedContextHint hintWithSignature,
            Set<FilterRecord> records) {
        final HashSet<FilterRecord> matches = new HashSet<>();
        for (FilterRecord record : records) {
            if (record.getEntry().matches(hintWithSignature)) {
                matches.add(record);
            }
        }

        return matches;
    }

    /** @hide */
    @TestApi
    @NonNull
    public Set<PublishedContextHint> getInterestedHintClusters(
            @NonNull Set<PublishedContextHint> allContextHints,
            @NonNull Set<UUID> seenIDs) {
        final ArraySet<FilterRecord> requiredFilters = getFilterRecords(FILTER_TYPE_REQUIRED);
        final ArraySet<FilterRecord> allowedFilters = getFilterRecords(FILTER_TYPE_ALLOWED);

        final Set<PublishedContextHint> interestingHints = new HashSet<>();

        for (PublishedContextHint hintWithSignature : allContextHints) {
            final ContextHint hint = hintWithSignature.getContextHint();
            if (seenIDs.contains(hint.getHintId())) {
                continue;
            }

            if (!allowedFilters.isEmpty()
                    && findFirstMatch(hintWithSignature, allowedFilters) == null) {
                continue;
            }

            requiredFilters.removeAll(findAllMatches(hintWithSignature, requiredFilters));
            interestingHints.add(hintWithSignature);
        }

        if (!requiredFilters.isEmpty()) {
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
        dest.writeArraySet(new ArraySet<>(mFilterRecords));
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
        private final Set<FilterRecord<FilterEntry>> mFilterRecords = Sets.newHashSet();

        /**
         * Creates a new instance of a {@link HintFilter} {@link Builder}.
         */
        public Builder() { }

        /**
         * Adds a filter that matches based on the hint type name specified on a {@link BundleHint}.
         * @see BundleHint#getHintTypeName()
         *
         * @param hintTypeName the {@link BundleHint} type name to match on
         * @param filterType the filter flags that should be applied when looking for the type name
         */
        @NonNull
        public Builder addBundleHintTypeName(@NonNull String hintTypeName,
                @FilterType int filterType) {
            mFilterRecords.add(
                    new FilterRecord<>(new BundleHintTypeNameEntry(hintTypeName), filterType));
            return this;
        }

        /**
         * Adds a filter that matches on a {@link ContextHint} subclass type.
         * @param hintClass the {@link ContextHint} subclass to match on
         * @param filterType the filter flags that should be applied when looking for the class
         */
        @NonNull
        public Builder addHintType(@NonNull Class<? extends ContextHint> hintClass,
                @FilterType int filterType) {
            mFilterRecords.add(new FilterRecord<>(new ContextHintTypeEntry(hintClass), filterType));
            return this;
        }

        /**
         * Adds a filter that matches based on the hint publisher's package.
         * @param packageName the name of the publishing package to look for.
         * @param filterType the filter flags that should be applied when looking for this package
         */
        @NonNull
        public Builder addPackage(@NonNull String packageName, @FilterType int filterType) {
            mFilterRecords.add(new FilterRecord<>(new PackageEntry(packageName), filterType));
            return this;
        }

        /** Builds the new HintFilter. */
        @NonNull
        public HintFilter build() {
            return new HintFilter(mFilterRecords);
        }
    }
}
