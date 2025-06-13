/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.log.table

import com.android.systemui.Dumpable

/**
 * Base interface for a logger that logs changes in table format.
 *
 * This is a plugin interface for classes outside of SystemUI core.
 */
public interface TableLogBuffer : Dumpable {
    /**
     * Logs a String? change.
     *
     * For Java overloading.
     */
    public fun logChange(prefix: String = "", columnName: String, value: String?) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /**
     * Logs a String? change.
     *
     * @param isInitial see [TableLogBuffer.logChange(String, Boolean, (TableRowLogger) -> Unit].
     */
    public fun logChange(
        prefix: String = "",
        columnName: String,
        value: String?,
        isInitial: Boolean,
    )

    /**
     * Logs a Boolean change.
     *
     * For Java overloading.
     */
    public fun logChange(prefix: String = "", columnName: String, value: Boolean) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /**
     * Logs a Boolean change.
     *
     * @param isInitial see [TableLogBuffer.logChange(String, Boolean, (TableRowLogger) -> Unit].
     */
    public fun logChange(
        prefix: String = "",
        columnName: String,
        value: Boolean,
        isInitial: Boolean,
    )

    /**
     * Logs an Int? change.
     *
     * For Java overloading.
     */
    public fun logChange(prefix: String = "", columnName: String, value: Int?) {
        logChange(prefix, columnName, value, isInitial = false)
    }

    /**
     * Logs an Int? change.
     *
     * @param isInitial see [TableLogBuffer.logChange(String, Boolean, (TableRowLogger) -> Unit].
     */
    public fun logChange(prefix: String = "", columnName: String, value: Int?, isInitial: Boolean)

    /**
     * Log the differences between [prevVal] and [newVal].
     *
     * The [newVal] object's method [Diffable.logDiffs] will be used to fetch the diffs.
     *
     * @param columnPrefix a prefix that will be applied to every column name that gets logged. This
     *   ensures that all the columns related to the same state object will be grouped together in
     *   the table.
     * @throws IllegalArgumentException if [columnPrefix] or column name contain "|". "|" is used as
     *   the separator token for parsing, so it can't be present in any part of the column name.
     */
    public fun <T : Diffable<T>> logDiffs(columnPrefix: String = "", prevVal: T, newVal: T)

    /**
     * Logs change(s) to the buffer using [rowInitializer].
     *
     * @param columnPrefix see [logDiffs].
     * @param rowInitializer a function that will be called immediately to store relevant data on
     *   the row.
     * @param isInitial true if this change represents the starting value for a particular column
     *   (as opposed to a value that was updated after receiving new information). This is used to
     *   help us identify which values were just default starting values, and which values were
     *   derived from updated information. Most callers should use false for this value.
     */
    public fun logChange(
        columnPrefix: String = "",
        isInitial: Boolean = false,
        rowInitializer: (TableRowLogger) -> Unit,
    )
}
