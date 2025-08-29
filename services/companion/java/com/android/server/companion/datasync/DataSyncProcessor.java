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

package com.android.server.companion.datasync;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.companion.Flags;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.server.companion.association.AssociationStore;

/**
 * This processor orchestrates metadata synchronization between two companion devices.
 */
public class DataSyncProcessor {

    private static final String TAG = "CDM_DataSyncProcessor";

    private final AssociationStore mAssociationStore;
    private final LocalMetadataStore mLocalMetadataStore;

    public DataSyncProcessor(
            AssociationStore associationStore,
            LocalMetadataStore localMetadataStore) {
        mAssociationStore = associationStore;
        mLocalMetadataStore = localMetadataStore;
    }

    /**
     * Set the local metadata for the current device.
     */
    public void setLocalMetadata(@UserIdInt int userId,
            @NonNull String key,
            @Nullable PersistableBundle metadata) {
        if (!Flags.enableDataSync()) {
            return;
        }
        Slog.i(TAG, "Setting local metadata for user=[" + userId
                + "] key=[" + key + "] value=[" + metadata + "]...");

        final PersistableBundle localMetadata = mLocalMetadataStore.getMetadataForUser(userId);
        if (metadata == null) {
            localMetadata.remove(key);
        } else {
            localMetadata.putPersistableBundle(key, metadata);
        }
        mLocalMetadataStore.setMetadataForUser(userId, localMetadata);
    }
}
