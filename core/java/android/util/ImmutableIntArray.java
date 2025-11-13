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
package android.util;

import java.util.Arrays;
import java.util.Objects;

/**
 * Implements a fixed array of {@code int} primitives.
 *
 * @hide
 */
@android.ravenwood.annotation.RavenwoodKeepWholeClass
public final class ImmutableIntArray implements Cloneable {

    private final int[] mValues;

    /**
     * Creates a new instance from the current values in the given {@code source}.
     */
    public static ImmutableIntArray from(int[] array) {
        Objects.requireNonNull(array, "array cannot be null");
        return new ImmutableIntArray(array);
    }

    private ImmutableIntArray(int[] array) {
        if (array.length == 0) {
            mValues = EmptyArray.INT;
            return;
        }
        int size = array.length;
        mValues = new int[size];
        System.arraycopy(array, 0, mValues, 0, size);
    }

    private ImmutableIntArray(ImmutableIntArray cloned) {
        mValues = cloned.mValues;
    }

    /** Gets the size of the array. */
    public int getSize() {
        return mValues.length;
    }

    /**
     * Gets the element at the given {@code index}.
     *
     * @throws ArrayIndexOutOfBoundsException if {@code index} is invalid.
     */
    public int get(int index) {
        return mValues[index];
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(mValues);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ImmutableIntArray other = (ImmutableIntArray) obj;
        return Arrays.equals(mValues, other.mValues);
    }

    @Override
    public ImmutableIntArray clone() {
        return new ImmutableIntArray(this);
    }

    @Override
    public String toString() {
        return Arrays.toString(mValues);
    }
}
