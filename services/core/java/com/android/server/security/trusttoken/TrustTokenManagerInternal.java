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

package com.android.server.security.trusttoken;

import android.security.trusttoken.TrustConfiguration;

import java.util.List;

/** The internal interface for TrustTokenManagerService. */
interface TrustTokenManagerInternal {
    /**
     * Generates new trust token keys.
     *
     * @param num The number of keys to generate.
     * @hide
     */
    List<TrustTokenKey> generateKeys(int num);

    /**
     * Attests to the trust token keys.
     *
     * @param keys the keys to attest.
     * @hide
     */
    TrustTokenBatchAttestation attestKeys(List<TrustTokenKey> keys);

    /**
     * Adds pre-fetched trust tokens to the system.
     *
     * <p>This method can add both verified device tokens and verified identities tokens.
     *
     * <p>For verified identity tokens, all tokens corresponding to the same public key must be
     * added together. There must also be a verified device token with the same public key as well.
     *
     * @param keys A list of the keys of the tokens.
     * @param tokens A list of encoded tokens to add.
     */
    void addTrustTokens(List<TrustTokenKey> keys, List<byte[]> tokens)
            throws TrustConfigurationUnavailableException;

    /**
     * Updates the trust configuration.
     *
     * @param configuration The updated configuration.
     */
    void updateTrustConfiguration(TrustConfiguration configuration);

    /**
     * Cleans up the internal database.
     * @hide
     */
    void cleanUpDatabase();
}
