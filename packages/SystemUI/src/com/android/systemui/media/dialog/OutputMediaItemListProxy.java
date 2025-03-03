/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A proxy of holding the list of Output Switcher's output media items. */
public class OutputMediaItemListProxy {
    private final List<MediaItem> mOutputMediaItemList;

    public OutputMediaItemListProxy() {
        mOutputMediaItemList = new CopyOnWriteArrayList<>();
    }

    /** Returns the list of output media items. */
    public List<MediaItem> getOutputMediaItemList() {
        return mOutputMediaItemList;
    }

    /** Updates the list of output media items with the given list. */
    public void clearAndAddAll(List<MediaItem> updatedMediaItems) {
        mOutputMediaItemList.clear();
        mOutputMediaItemList.addAll(updatedMediaItems);
    }

    /** Removes the media items with muting expected devices. */
    public void removeMutingExpectedDevices() {
        mOutputMediaItemList.removeIf((MediaItem::isMutingExpectedDevice));
    }

    /** Clears the output media item list. */
    public void clear() {
        mOutputMediaItemList.clear();
    }

    /** Returns whether the output media item list is empty. */
    public boolean isEmpty() {
        return mOutputMediaItemList.isEmpty();
    }
}
