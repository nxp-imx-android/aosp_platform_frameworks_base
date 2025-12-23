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

import android.annotation.NonNull;

import java.util.List;

/** The database for pending and existing trust tokens. */
abstract class TrustTokenDatabase {
    /**
     * Gets a new {@link TrustTokenSet} of the specified type from the database.
     *
     * @throws TrustTokenExhaustedException if the database contains no more valid trust tokens of
     *     the specified type.
     */
    @NonNull
    abstract TrustTokenSetWithKey getTrustTokenSet(@TrustTokenSet.Type int type)
            throws TrustTokenExhaustedException;

    /**
     * Adds a batch of {@link TrustTokenSet} to the database. The keys associated with the trust
     * tokens must be unique in the database.
     *
     * @throws IllegalArgumentException if the keys associated with the trust tokens do already
     *     exist in the database.
     */
    abstract void addTrustTokenSets(@NonNull List<TrustTokenSetWithKey> tokens)
            throws IllegalArgumentException;
}
