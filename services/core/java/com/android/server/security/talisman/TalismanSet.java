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

import android.annotation.IntDef;
import android.security.talisman.Talisman;
import android.security.talisman.TalismanIdentitySet;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a set of talismans, which can be either a verified device talisman or a {@link
 * TalismanIdentitySet}. The talismans are under the same public key.
 */
class TalismanSet {
    static final int TYPE_VERIFIED_DEVICE = 1;
    static final int TYPE_VERIFIED_IDENTITIES = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"TYPE_"},
            value = {
                TYPE_VERIFIED_DEVICE,
                TYPE_VERIFIED_IDENTITIES,
            })
    @interface Type {}

    private final @Type int mType;
    private final byte[] mPublicKey;

    /** The serialized talismans. */
    private final byte[] mTalismanSet;

    /**
     * The time at which the talismans was created. If the talismans have different creation time,
     * the earliest one is chosen.
     */
    private final Instant mCreatedAt;

    /**
     * The time at which the talismans expires. If the talismans have different expiry time, the
     * earliest one is chosen.
     */
    private final Instant mExpireAt;

    TalismanSet(
            @Type int type,
            byte[] publicKey,
            byte[] talismanSet,
            Instant createdAt,
            Instant expireAt) {
        mType = type;
        mPublicKey = publicKey;
        mTalismanSet = talismanSet;
        mCreatedAt = createdAt;
        mExpireAt = expireAt;
    }

    @Type
    int getType() {
        return mType;
    }

    byte[] getPublicKey() {
        return mPublicKey;
    }

    byte[] getTalismanSet() {
        return mTalismanSet;
    }

    Instant getCreatedAt() {
        return mCreatedAt;
    }

    Instant getExpireAt() {
        return mExpireAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TalismanSet)) return false;
        TalismanSet that = (TalismanSet) o;
        return mType == that.mType
                && Arrays.equals(mPublicKey, that.mPublicKey)
                && Arrays.equals(mTalismanSet, that.mTalismanSet)
                && Objects.equals(mCreatedAt, that.mCreatedAt)
                && Objects.equals(mExpireAt, that.mExpireAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mType, mCreatedAt, mExpireAt);
        result = 31 * result + Arrays.hashCode(mPublicKey);
        result = 31 * result + Arrays.hashCode(mTalismanSet);
        return result;
    }

    @Override
    public String toString() {
        return "TalismanSet["
                + "type="
                + mType
                + ", "
                + "publicKey="
                + Arrays.toString(mPublicKey)
                + ", "
                + "talismanSet="
                + Arrays.toString(mTalismanSet)
                + ", "
                + "createdAt="
                + mCreatedAt
                + ", "
                + "expireAt="
                + mExpireAt
                + "]";
    }

    /**
     * Converts this TalismanSet to a {@link Talisman} if its type is {@link #TYPE_VERIFIED_DEVICE}.
     *
     * @return A {@link Talisman} instance.
     */
    Talisman asVerifiedDeviceTalisman() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Converts this TalismanSet to a {@link TalismanIdentitySet} if its type is {@link
     * #TYPE_VERIFIED_IDENTITIES}.
     *
     * @return A {@link TalismanIdentitySet} instance.
     */
    TalismanIdentitySet asTalismanIdentitySet() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Creates a {@link TalismanSet} from a {@link Talisman}. The type will be {@link
     * #TYPE_VERIFIED_DEVICE}.
     *
     * @param talisman The {@link Talisman} to convert.
     * @return A new {@link TalismanSet}.
     */
    static TalismanSet fromVerifiedDeviceTalisman(Talisman talisman) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Creates a {@link TalismanSet} from a {@link TalismanIdentitySet}. The type will be {@link
     * #TYPE_VERIFIED_IDENTITIES}.
     *
     * @param identitySet The {@link TalismanIdentitySet} to convert.
     * @return A new {@link TalismanSet}.
     */
    static TalismanSet fromTalismanIdentitySet(TalismanIdentitySet identitySet) {
        throw new UnsupportedOperationException("TODO");
    }

    static final class Builder {
        private @Type int mType;
        private byte[] mPublicKey;
        private byte[] mTalismanSet;
        private Instant mCreatedAt;
        private Instant mExpireAt;

        Builder() {}

        Builder setType(@Type int type) {
            this.mType = type;
            return this;
        }

        Builder setPublicKey(byte[] publicKey) {
            this.mPublicKey = Arrays.copyOf(publicKey, publicKey.length);
            return this;
        }

        Builder setTalismanSet(byte[] talismanSet) {
            this.mTalismanSet = Arrays.copyOf(talismanSet, talismanSet.length);
            return this;
        }

        Builder setCreatedAt(Instant createdAt) {
            this.mCreatedAt = createdAt;
            return this;
        }

        Builder setExpireAt(Instant expireAt) {
            this.mExpireAt = expireAt;
            return this;
        }

        TalismanSet build() {
            return new TalismanSet(mType, mPublicKey, mTalismanSet, mCreatedAt, mExpireAt);
        }
    }
}
