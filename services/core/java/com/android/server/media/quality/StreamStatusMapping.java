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

package com.android.server.media.quality;

import android.media.quality.PictureProfile;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StreamStatusMapping {
    private static final String TAG = "StreamStatusMapping";

    // original profile handle -> mapped handles
    private final Map<Long, Set<Long>> mOriginalToHandles = new HashMap<>();

    // original profile handle -> currently used profile object
    private final Map<Long, PictureProfile> mOriginalToCurrent = new HashMap<>();

    // mapped handles -> original profile handle
    private final Map<Long, Long> mHandleToOriginal = new HashMap<>();

    public StreamStatusMapping() {
    }


    /**
     * Maps a number to an original number.
     *
     * If the number is already mapped to a different original, the mapping is moved.
     *
     * @return true if the operation was successful.
     */
    public boolean put(long original, long toAdd) {
        if (original == toAdd) {
            Log.e(TAG, "same key and value:" + original);
            return false;
        }
        if (mOriginalToHandles.containsKey(toAdd)) {
            Log.e(TAG, "the value to add is in mOriginalToHandles:" + toAdd);
            return false;
        }
        if (mHandleToOriginal.containsKey(original)) {
            Log.e(TAG, "the original handle is in mHandleToOriginal:" + original);
            return false;
        }
        // Check if the number to add is already mapped to a different original.
        Long oldOriginal = mHandleToOriginal.get(toAdd);
        if (oldOriginal != null) {
            // If it's already mapped to the *same* original, do nothing.
            if (oldOriginal.equals(original)) {
                return true;
            }
            // If it's mapped to a *different* original, we must remove the old mapping.
            Set<Long> mappedSet = mOriginalToHandles.get(oldOriginal);
            if (mappedSet != null) {
                mappedSet.remove(toAdd);
                if (mappedSet.isEmpty()) {
                    mOriginalToHandles.remove(oldOriginal);
                }
            }
        }
        // Add the new mapping
        Set<Long> mappedSet = mOriginalToHandles.get(original);
        if (mappedSet == null) {
            mappedSet = new HashSet<>();
        }
        mappedSet.add(toAdd);
        mOriginalToHandles.put(original, mappedSet);
        mHandleToOriginal.put(toAdd, original);
        return true;
    }

    /**
     * Checks if the given number is mapped with any original number.
     */
    public long getOriginal(long handle) {
        Long original = mHandleToOriginal.get(handle);
        if (original != null) {
            return original;
        }
        // if no existing mapping, use the handle itself
        return handle;
    }

    /**
     * Sets current profile
     */
    public boolean setCurrent(long original, PictureProfile pp) {
        if (pp == null || pp.getHandle() == null) {
            return false;
        }
        long handle = pp.getHandle().getId();
        if (!put(original, handle)) {
            return false;
        }
        mOriginalToCurrent.put(original, pp);
        return true;
    }

    /**
     * Gets current profile
     */
    public PictureProfile getCurrent(long original) {
        return mOriginalToCurrent.get(original);
    }

    /**
     * Removes all the mapping of a handle
     */
    public void removeMapping(long original) {
        Set<Long> mappedSet = mOriginalToHandles.get(original);
        if (mappedSet != null) {
            for (Long handle : mappedSet) {
                mHandleToOriginal.remove(handle);
            }
            mOriginalToHandles.remove(original);
        }
        mOriginalToCurrent.remove(original);
    }


    /**
     * Checks if a given number is currently registered as an original number.
     * An original number is one that has at least one number mapped to it.
     *
     * @param number The number to check.
     * @return true if the number is an original number, false otherwise or if the input is null.
     */
    public boolean isOriginal(long number) {
        return mOriginalToHandles.containsKey(number);
    }
}
