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

package com.android.server.security.talisman;

import java.util.Objects;

/** Represents a {@link TalismanSet} and the associated {@link TalismanKey}. */
class TalismanSetWithKey {
    private final TalismanKey mKey;
    private final TalismanSet mTalismanSet;

    TalismanSetWithKey(TalismanKey key, TalismanSet talismanSet) {
        mKey = key;
        mTalismanSet = talismanSet;
    }

    TalismanKey getKey() {
        return mKey;
    }

    TalismanSet getTalismanSet() {
        return mTalismanSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TalismanSetWithKey)) return false;
        TalismanSetWithKey that = (TalismanSetWithKey) o;
        return Objects.equals(mKey, that.mKey) && Objects.equals(mTalismanSet, that.mTalismanSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mTalismanSet);
    }

    @Override
    public String toString() {
        return "TalismanSetWithKey[" + "key=" + mKey + ", " + "talismanSet=" + mTalismanSet + "]";
    }
}
