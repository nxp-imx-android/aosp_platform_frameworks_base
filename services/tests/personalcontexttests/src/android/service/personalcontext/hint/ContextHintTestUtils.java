/*
 * Copyright 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

/** Utility methods for testing {@link ContextHint} implementation parceling. */
public final class ContextHintTestUtils {

    private ContextHintTestUtils() {}

    /**
     * Parcels and unparcels the given {@link ContextHint} and asserts the same amount of data was
     * written to and read from the {@link Parcel}.
     */
    public static ContextHint assertParcelUnparcel(ContextHint hint) {
        final ContextHintWrapper wrapper = new ContextHintWrapper(hint);

        final Parcel parcel = Parcel.obtain();
        try {
            wrapper.writeToParcel(parcel, 0);
            final int dataSize = parcel.dataPosition();

            // Reset data position for read.
            parcel.setDataPosition(0);
            final ContextHintWrapper fromParcel =
                    ContextHintWrapper.CREATOR.createFromParcel(parcel);

            // Same size of data is written and read.
            assertThat(dataSize).isEqualTo(parcel.dataPosition());
            return fromParcel.getContextHint();
        } finally {
            parcel.recycle();
        }
    }
}
