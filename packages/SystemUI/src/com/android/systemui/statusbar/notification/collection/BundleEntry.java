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

import static android.app.NotificationChannel.NEWS_ID;
import static android.app.NotificationChannel.PROMOTIONS_ID;
import static android.app.NotificationChannel.RECS_ID;
import static android.app.NotificationChannel.SOCIAL_MEDIA_ID;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;

import java.util.List;

/**
 * Abstract class to represent notification section bundled by AI.
 */
public class BundleEntry extends PipelineEntry {

    private final String mKey;
    private final BundleEntryAdapter mEntryAdapter;

    // TODO (b/389839319): implement the row
    private ExpandableNotificationRow mRow;

    public BundleEntry(String key) {
        mKey = key;
        mEntryAdapter = new BundleEntryAdapter();
    }

    @VisibleForTesting
    public BundleEntryAdapter getEntryAdapter() {
        return mEntryAdapter;
    }

    public class BundleEntryAdapter implements EntryAdapter {

        /**
         * TODO (b/394483200): convert to PipelineEntry.ROOT_ENTRY when pipeline is migrated?
         */
        @Override
        public GroupEntry getParent() {
            return GroupEntry.ROOT_ENTRY;
        }

        @Override
        public boolean isTopLevelEntry() {
            return true;
        }

        @Override
        public String getKey() {
            return mKey;
        }

        @Override
        public ExpandableNotificationRow getRow() {
            return mRow;
        }

        @Nullable
        @Override
        public EntryAdapter getGroupRoot() {
            return this;
        }
    }

    public static final List<BundleEntry> ROOT_BUNDLES = List.of(
            new BundleEntry(PROMOTIONS_ID),
            new BundleEntry(SOCIAL_MEDIA_ID),
            new BundleEntry(NEWS_ID),
            new BundleEntry(RECS_ID));
}
