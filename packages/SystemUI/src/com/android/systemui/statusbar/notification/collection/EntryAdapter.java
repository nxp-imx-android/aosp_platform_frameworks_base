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

package com.android.systemui.statusbar.notification.collection;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.statusbar.notification.icon.IconPack;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;

/**
 * Adapter interface for UI to get relevant info.
 */
public interface EntryAdapter {

    /**
     * Gets the parent of this entry, or null if the entry's view is not attached
     */
    @Nullable PipelineEntry getParent();

    /**
     * Returns whether the entry is attached and appears at the top level of the shade
     */
    boolean isTopLevelEntry();

    /**
     * @return the unique identifier for this entry
     */
    @NonNull String getKey();

    /**
     * Gets the view that this entry is backing.
     */
    @NonNull
    ExpandableNotificationRow getRow();

    /**
     * Gets the EntryAdapter that is the nearest root of the collection of rows the given entry
     * belongs to. If the given entry is a BundleEntry or an isolated child of a BundleEntry, the
     * BundleEntry will be returned. If the given notification is a group summary NotificationEntry,
     * or a child of a group summary, the summary NotificationEntry will be returned, even if that
     * summary belongs to a BundleEntry. If the entry is a notification that does not belong to any
     * group or bundle grouping, null will be returned.
     */
    @Nullable
    EntryAdapter getGroupRoot();

    /**
     * @return whether the row can be removed with the 'Clear All' action
     */
    boolean isClearable();

    /**
     * Returns whether the entry is attached to the current shade list
     */
    default boolean isAttached() {
        return getParent() != null;
    }

    /**
     * Returns the target sdk of the package that owns this entry.
     */
    int getTargetSdk();

    /**
     * Returns the summarization for this entry, if there is one
     */
    @Nullable String getSummarization();

    /**
     * Performs any steps needed to set or reset data before an inflation or reinflation.
     */
    default void prepareForInflation() {}

    /**
     * Gets a color that would have sufficient contrast on the given background color.
     */
    int getContrastedColor(Context context, boolean isLowPriority, int backgroundColor);

    /**
     * Whether this entry can peek on screen as a heads up view
     */
    boolean canPeek();

    /**
     * Returns the visible 'time', in milliseconds, of the entry
     */
    long getWhen();

    /**
     * Retrieves the pack of icons associated with this entry
     */
    IconPack getIcons();
}
