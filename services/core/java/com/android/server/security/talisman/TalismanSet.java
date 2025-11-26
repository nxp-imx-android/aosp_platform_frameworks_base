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

import com.android.framework.protobuf.ByteString;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;

/**
 * Represents a set of talismans, which can be either a verified device talisman or a {@link
 * TalismanIdentitySet}. The talismans are under the same public key.
 */
final class TalismanSet {

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

    private final ByteString mPublicKey;

    /** The serialized talismans. */
    private final ByteString mTalismanSet;

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

    /**
     * Constructs a {@link TalismanSet} from a {@link Builder}.
     *
     * @param builder The builder instance.
     */
    private TalismanSet(Builder builder) {
        mType = builder.mType;
        mPublicKey = ByteString.copyFrom(builder.mPublicKey);
        mTalismanSet = ByteString.copyFrom(builder.mTalismanSet);
        mCreatedAt = builder.mCreatedAt;
        mExpireAt = builder.mExpireAt;
    }

    @Type
    int getType() {
        return mType;
    }

    ByteString getPublicKey() {
        return mPublicKey;
    }

    ByteString getTalismanSet() {
        return mTalismanSet;
    }

    Instant getCreatedAt() {
        return mCreatedAt;
    }

    Instant getExpireAt() {
        return mExpireAt;
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
            this.mPublicKey = publicKey;
            return this;
        }

        Builder setTalismanSet(byte[] talismanSet) {
            this.mTalismanSet = talismanSet;
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
            return new TalismanSet(this);
        }
    }
}
