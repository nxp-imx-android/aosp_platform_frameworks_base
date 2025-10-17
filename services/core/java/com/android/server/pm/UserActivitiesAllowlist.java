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

package com.android.server.pm;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.server.utils.Slogf;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class responsible for managing activities allowlists associated with a user.
 *
 * <p>This class is thread safe.
 */
public final class UserActivitiesAllowlist extends GenericAllowlist<ComponentName> {

    UserActivitiesAllowlist(String[] permanentActivities) {
        super("activity", "activities", permanentActivities);
    }

    @Override
    protected ComponentName fromNormalizedName(String name) {
        return ComponentName.unflattenFromString(name);
    }

    @Override
    protected String toNormalizedName(ComponentName element) {
        return element.flattenToShortString();
    }
}

// TODO(b/455912167): move to its own class (in a separate CL, so it's easy to diff what changed
// when this class was created)
/**
 * Base class responsible for managing generic allowlists.
 *
 * <p>The allowlist is divided in 2 lists:
 *
 * <ol>
 *   <li>A permanent allowlist (typically defined by an array config resource).
 *   <li>A temporary allowlist (that can be set programmatically and lasts until reset or restart).
 * </ol>
 *
 * <p>This class is thread safe.
 *
 * @param <E> type of the element being allowlisted.
 */
abstract class GenericAllowlist<E> {

    private static final String TAG = GenericAllowlist.class.getSimpleName();

    @VisibleForTesting
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** Used to set mId (on constructor). */
    private static int sNextId;

    /**
     * Used on {@link #toString()}  only, so the allowlist can be uniquely identified (on
     * {@link #dump(IndentingPrintWriter, String)}).
     */
    @VisibleForTesting
    final int mId;

    /**
     * List of elements that are permanently allowed (i.e., they survive reboots).
     *
     * <p>If empty, allowlist is disabled (and all elements are allowed).
     *
     * <p><b>NOTE:</b> for now it's an array as they're just read from config, but should change to
     * {@code Set} once it supports APIs to change it (for example, from DPM).
     */
    private final String[] mPermanentAllowlist;

    /**
     * List of elements that are temporarily allowed (i.e., until reboot or set back to
     * {@code null}).
     *
     * <p>When set (i.e., not {@code null}), it will override the value of
     * {@code mPermanentAllowlist}. If empty, allowlist is disabled (and all elements are allowed).
     */
    @Nullable
    private volatile CopyOnWriteArrayList<String> mTemporaryAllowlist;

    private final String mSingularName;
    private final String mPluralName;

    protected GenericAllowlist(String singularName, String pluralName,
            String[] permanentNormalizedNames) {
        mId = ++sNextId;
        mSingularName = singularName;
        mPluralName = pluralName;
        mPermanentAllowlist = getValidElements(permanentNormalizedNames);
    }

    /** Converts the given string to an element. */
    protected abstract @Nullable E fromNormalizedName(String name);

    /** Converts the given element to a String. */
    protected abstract String toNormalizedName(E element);

    // NOTE: only called by 'cmd user' (which needs to "build" the temporary allowlist based on
    // incremental actions, like add or remove an element) and unit tests, so we don't have to
    // worry about performance (like caching the result or not using streams)
    final List<E> getEffectiveAllowlist() {
        Stream<String> stream = mTemporaryAllowlist != null
                ? mTemporaryAllowlist.stream()
                : Arrays.stream(mPermanentAllowlist);
        return stream.map(e -> fromNormalizedName(e))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
    * Returns whether the given element is allowed.
    *
    * <p>It will check the temporary list first (if set), then the permanent one. If the checked
    * list is empty, then allowlisting is disabled and all elements (except {@code null}) are
    * allowed.
    */
    public final boolean isAllowed(E element) {
        Objects.requireNonNull(element, "element cannot be null");
        String normalizedName = toNormalizedName(element);

        // Checks the temporary list first...
        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;
        if (temporaryList != null) {
            if (temporaryList.isEmpty()) {
                if (DEBUG) {
                    Slogf.d(TAG, "isAllowed(%s): returning true because temporary "
                            + "allowlist overrides permanent allowlist and is empty, so any "
                            + "%s is allowed", normalizedName, mSingularName);
                }
                return true;
            }
            if (DEBUG) {
                Slogf.d(TAG, "isAllowed(%s): checking temporary list (%s)",
                        normalizedName, temporaryList);
            }
            return temporaryList.contains(normalizedName);
        }

        // ...then the permanent one.
        if (mPermanentAllowlist.length == 0) {
            if (DEBUG) {
                Slogf.d(TAG, "isAllowed(%s): returning true because permanent allowlist"
                        + "is empty, so any %s is allowed", normalizedName, mSingularName);
            }
            return true;
        }
        if (DEBUG) {
            Slogf.d(TAG, "isAllowed(%s): checking permanent list (%s)", normalizedName,
                    Arrays.toString(mPermanentAllowlist));
        }
        return ArrayUtils.contains(mPermanentAllowlist, normalizedName);
    }

    /** Sets the temporary allowlist (or resets it when passed with {@code null}. */
    final void setTemporaryAllowlist(@Nullable Collection<E> elements) {
        if (DEBUG) {
            Slogf.d(TAG, "setTemporaryAllowList(%s)", elements);
        }
        if (elements == null) {
            mTemporaryAllowlist = null;
            return;
        }
        List<String> tempList = new ArrayList<>(elements.size());
        for (E element : elements) {
            tempList.add(toNormalizedName(element));
        }
        mTemporaryAllowlist = new CopyOnWriteArrayList<>(tempList);

        if (DEBUG) {
            Slogf.d(TAG, "setTemporaryAllowList(): set as %s", mTemporaryAllowlist);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + mId;
    }

    final void dump(PrintWriter writer, String prefix, String header) {
        dump(new IndentingPrintWriter(writer, /* singleIndent=*/ "  ", prefix), header);
    }

    final void dump(IndentingPrintWriter writer, String header) {
        writer.printf("%s:\n", header);
        writer.increaseIndent();

        writer.printf("id: %s\n", toString());
        writer.printf("DEBUG: %b\n", DEBUG);

        dumpAllowlistStatus(writer);
        dumpPermanentAllowlist(writer);
        dumpTemporaryAllowlist(writer);

        writer.decreaseIndent();
    }

    private void dumpAllowlistStatus(IndentingPrintWriter writer) {
        writer.printf("%s allowlist status: ", mPluralName);
        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;
        if (temporaryList != null) {
            if (temporaryList.isEmpty()) {
                writer.println("allowlisting disabled");
            } else {
                writer.println("using temporary allowlist");
            }
            return;
        }
        if (mPermanentAllowlist.length == 0) {
            writer.println("allowlisting disabled");
        } else {
            writer.println("using permanent allowlist");
        }
    }

    private void dumpPermanentAllowlist(IndentingPrintWriter writer) {
        int size = mPermanentAllowlist.length;

        // Header / number of elements
        dumpAllowlistSize(writer, "permanent", size);

        // Body / elements
        writer.increaseIndent();
        for (String item : mPermanentAllowlist) {
            writer.println(item);
        }
        writer.decreaseIndent();
    }

    private void dumpTemporaryAllowlist(IndentingPrintWriter writer) {
        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;

        // Header / number of elements
        if (temporaryList == null) {
            writer.printf("temporary %s allowlist is not set.\n", mPluralName);
            return;
        }
        int size = temporaryList.size();
        dumpAllowlistSize(writer, "temporary", size);

        // Body / elements
        writer.increaseIndent();
        for (int i = 0; i < size; i++) {
            writer.println(temporaryList.get(i));
        }
        writer.decreaseIndent();
    }

    private void dumpAllowlistSize(IndentingPrintWriter writer, String name,
            int size) {
        if (size == 0) {
            writer.printf("%s %s allowlist is empty.\n", name, mPluralName);
            return;
        }
        String suffix = size > 1 ? mPluralName : mSingularName;
        writer.printf("%s %s allowlist has %d %s:\n", name, mPluralName, size, suffix);
    }

    private String[] getValidElements(String[] elements) {
        // NOTE: must use LinkedHashSet to preserve order, otherwise test case would fail and dump()
        // wouldn't show the same order as the config.xml
        LinkedHashSet<String> set = new LinkedHashSet<>(elements.length);
        for (String element : elements) {
            E validElement = fromNormalizedName(element);
            if (validElement == null) {
                Slogf.w(TAG, "Invalid %s from config: %s", mSingularName, element);
                continue;
            }
            // Must "normalize" the component into the flattened format, as the class part could
            // have been expressed as FQCN (Fully-Qualified Class Name).
            String normalizedName = toNormalizedName(validElement);
            if (set.contains(normalizedName)) {
                Slogf.w(TAG, "%s %s already added (as %s)", mSingularName, element,
                        normalizedName);
            }
            set.add(normalizedName);
        }
        String[] valid = new String[set.size()];
        set.toArray(valid);
        if (DEBUG) {
            Slogf.d(TAG, "Valid %s from config: %s", Arrays.toString(valid), mPluralName);
        }
        return valid;
    }
}
