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

package android.os;

import android.os.IBinder;
import android.os.Parcel;
import android.util.ArrayMap;

import java.util.ArrayList;

/**
 * A helper that deduplicates IBinder objects written to a Parcel to reduce kernel overhead.
 *
 * <p>Note: A new instance of {@link DedupBinderHelper} must be used for each Parcel, and it should
 * not be reused across multiple Parcels.
 *
 * @hide
 */
public class DedupBinderHelper extends Parcel.ReadWriteHelper {
    // Write-side cache: Maps specific Binder instances to their first-seen index.
    private final ArrayMap<IBinder, Integer> mWrittenBinders = new ArrayMap<>();

    // Read-side cache: Maps indices back to the Binder instances received.
    private final ArrayList<IBinder> mReadBinders = new ArrayList<>();

    private static final int NEW_BINDER_INDEX = -1;

    @Override
    public void writeStrongBinder(Parcel p, IBinder val) {
        Integer cachedIndex = mWrittenBinders.get(val);

        if (cachedIndex != null) {
            // CASE: DUPLICATE
            p.writeInt(cachedIndex);
        } else {
            // CASE: NEW
            // Write -1 to indicate a new binder object follows
            p.writeInt(NEW_BINDER_INDEX);
            mWrittenBinders.put(val, mWrittenBinders.size());
            super.writeStrongBinder(p, val);
        }
    }

    @Override
    public IBinder readStrongBinder(Parcel p) {
        int index = p.readInt();

        if (index == NEW_BINDER_INDEX) {
            // CASE: NEW
            IBinder b = super.readStrongBinder(p);
            mReadBinders.add(b);
            return b;
        } else {
            // CASE: DUPLICATE
            if (index < 0 || index >= mReadBinders.size()) {
                throw new IllegalStateException("Corrupt Parcel: Bad dedup index " + index);
            }
            return mReadBinders.get(index);
        }
    }
}
