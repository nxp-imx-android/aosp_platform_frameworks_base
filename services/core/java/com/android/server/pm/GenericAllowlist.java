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

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.util.DebugUtils;
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

    @VisibleForTesting
    static final boolean DEBUG = Log.isLoggable(GenericAllowlist.class.getSimpleName(), Log.DEBUG);

    @VisibleForTesting
    static final String ALLOWED_BY_LOG_ONLY_MESSAGE_TEMPLATE =
            "isAllowed(%s): returning true only because mode is %s";

    // NOTE: public because of allowlistModeToString()
    /** Allowlist is disabled because it was set with an invalid mode. */
    public static final int ALLOWLIST_MODE_INVALID = -1;
    /** Allowlist is disabled. */
    public static final int ALLOWLIST_MODE_DISABLED = 0;
    /** Allowlist is enabled. */
    public static final int ALLOWLIST_MODE_ENABLED = 1;
    /**
     * Allowlist is disabled, but {@link #isAllowed(Object)} will log when it returns {@code false}.
     */
    public static final int ALLOWLIST_MODE_LOG_ONLY = 2;

    @IntDef(prefix = { "ALLOWLIST_MODE_" }, value = {
            ALLOWLIST_MODE_INVALID,
            ALLOWLIST_MODE_DISABLED,
            ALLOWLIST_MODE_ENABLED,
            ALLOWLIST_MODE_LOG_ONLY,
            })
    public @interface AllowlistMode {}

    @VisibleForTesting
    final String mTag = getClass().getSimpleName();

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

    /** Mode of the allowlist - see {@link AllowlistMode} */
    private @AllowlistMode int mMode;

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

    protected GenericAllowlist(@AllowlistMode int mode, String singularName, String pluralName,
            String[] permanentNormalizedNames) {
        mId = ++sNextId;
        try {
            mMode = validateMode(mode);
        } catch (Exception e) {
            Slogf.wtf(mTag, e, "Invalid mode (%d) on constructor; using ALLOWLIST_MODE_INVALID (%d)"
                    + "instead", mode, ALLOWLIST_MODE_INVALID);
            mMode = ALLOWLIST_MODE_INVALID;
        }
        mSingularName = singularName;
        mPluralName = pluralName;
        mPermanentAllowlist = getValidElements(permanentNormalizedNames);
    }

    /** Converts the given string to an element. */
    protected abstract @Nullable E fromNormalizedName(String name);

    /** Converts the given element to a String. */
    protected abstract String toNormalizedName(E element);

    @VisibleForTesting
    final @AllowlistMode int getMode() {
        return mMode;
    }

    final void setMode(@AllowlistMode int mode) {
        int oldMode = mMode;
        mMode = validateMode(mode);
        if (DEBUG) {
            Slogf.d(mTag, "setMode(): changed from %d (%s) to %d (%s)",
                    oldMode, allowlistModeToString(oldMode), mode, allowlistModeToString(mode));
        }
    }

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

        if (mMode == ALLOWLIST_MODE_DISABLED || mMode == ALLOWLIST_MODE_INVALID) {
            if (DEBUG) {
                Slogf.d(mTag, "isAllowed(%s): returning true because mode is (%d) %s",
                        normalizedName, mMode, allowlistModeToString(mMode));
            }
            return true;
        }

        // Checks the temporary list first...
        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;
        if (temporaryList != null) {
            if (temporaryList.isEmpty()) {
                if (DEBUG) {
                    Slogf.d(mTag, "isAllowed(%s): returning true because temporary "
                            + "allowlist overrides permanent allowlist and is empty, so any "
                            + "%s is allowed", normalizedName, mSingularName);
                }
                return true;
            }
            if (DEBUG) {
                Slogf.d(mTag, "isAllowed(%s): checking temporary list (%s)",
                        normalizedName, temporaryList);
            }
            boolean allowed = temporaryList.contains(normalizedName);
            return checkModeAndLog(normalizedName, allowed);
        }

        // ...then the permanent one.
        if (mPermanentAllowlist.length == 0) {
            if (DEBUG) {
                Slogf.d(mTag, "isAllowed(%s): returning true because permanent allowlist"
                        + "is empty, so any %s is allowed", normalizedName, mSingularName);
            }
            return true;
        }
        if (DEBUG) {
            Slogf.d(mTag, "isAllowed(%s): checking permanent list (%s)", normalizedName,
                    Arrays.toString(mPermanentAllowlist));
        }
        boolean allowed = ArrayUtils.contains(mPermanentAllowlist, normalizedName);
        return checkModeAndLog(normalizedName, allowed);
    }

    private boolean checkModeAndLog(String normalizedName, boolean allowed) {
        if (allowed) {
            if (DEBUG) {
                Slogf.d(mTag, "isAllowed(%s): returning true", normalizedName);
            }
            return true;
        }
        if (mMode == ALLOWLIST_MODE_LOG_ONLY) {
            Slogf.w(mTag, ALLOWED_BY_LOG_ONLY_MESSAGE_TEMPLATE, normalizedName,
                    allowlistModeToString(mMode));
            return true;
        }
        return false;
    }

    /** Sets the temporary allowlist (or resets it when passed with {@code null}. */
    final void setTemporaryAllowlist(@Nullable Collection<E> elements) {
        if (DEBUG) {
            Slogf.d(mTag, "setTemporaryAllowList(%s)", elements);
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
            Slogf.d(mTag, "setTemporaryAllowList(): set as %s", mTemporaryAllowlist);
        }
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "#" + mId;
    }

    /** Gets a user-friendly representation of the given {@code mode}. */
    public static String allowlistModeToString(@AllowlistMode int mode) {
        return DebugUtils.constantToString(GenericAllowlist.class, "ALLOWLIST_MODE_", mode);
    }

    final void dump(PrintWriter writer, String prefix, String header) {
        dump(new IndentingPrintWriter(writer, /* singleIndent=*/ "  ", prefix), header);
    }

    final void dump(IndentingPrintWriter writer, String header) {
        writer.printf("%s:\n", header);
        writer.increaseIndent();

        writer.printf("id: %s\n", toString());
        writer.printf("mode: %d (%s)\n", mMode, allowlistModeToString(mMode));
        writer.printf("DEBUG: %b\n", DEBUG);

        dumpAllowlistStatus(writer);
        dumpPermanentAllowlist(writer);
        dumpTemporaryAllowlist(writer);

        writer.decreaseIndent();
    }

    private void dumpAllowlistStatus(IndentingPrintWriter writer) {
        writer.printf("%s allowlist status: ", mPluralName);

        switch (mMode) {
            case ALLOWLIST_MODE_DISABLED -> {
                writer.println("disabled (by config)");
                return;
            }
            case ALLOWLIST_MODE_LOG_ONLY-> {
                writer.println("disabled (log-only)");
                return;
            }
        }

        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;
        if (temporaryList != null) {
            if (temporaryList.isEmpty()) {
                writer.println("disabled (empty temporary list)");
            } else {
                writer.println("using temporary allowlist");
            }
            return;
        }
        if (mPermanentAllowlist.length == 0) {
            writer.println("disabled (empty permanent list)");
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
                Slogf.w(mTag, "Invalid %s from config: %s", mSingularName, element);
                continue;
            }
            // Must "normalize" the component into the flattened format, as the class part could
            // have been expressed as FQCN (Fully-Qualified Class Name).
            String normalizedName = toNormalizedName(validElement);
            if (set.contains(normalizedName)) {
                Slogf.w(mTag, "%s %s already added (as %s)", mSingularName, element,
                        normalizedName);
            }
            set.add(normalizedName);
        }
        String[] valid = new String[set.size()];
        set.toArray(valid);
        if (DEBUG) {
            Slogf.d(mTag, "Valid %s from config: %s", mPluralName, Arrays.toString(valid));
        }
        return valid;
    }

    private static @AllowlistMode int validateMode(@AllowlistMode int mode) {
        return switch (mode) {
            case ALLOWLIST_MODE_ENABLED, ALLOWLIST_MODE_DISABLED, ALLOWLIST_MODE_LOG_ONLY -> mode;
            default -> throw new IllegalArgumentException("invalid mode: " + mode);
        };
    }
}
