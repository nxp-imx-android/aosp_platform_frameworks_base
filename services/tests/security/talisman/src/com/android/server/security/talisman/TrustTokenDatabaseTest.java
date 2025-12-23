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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.os.Clock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class TrustTokenDatabaseTest {
    TrustTokenDatabase mDatabase;
    MockClock mClock = new MockClock();

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            File databaseFile =
                    File.createTempFile("trust_token.sqlite", null, context.getCacheDir());
            mDatabase = TrustTokenSqliteDatabase.create(context, databaseFile, mClock);
        } catch (IOException e) {
            throw new AssertionError("cannot create the database file");
        }
    }

    @Test
    public void addTrustTokenSets_success() {
        TrustTokenSetWithKey token1 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "someToken").build());
        TrustTokenSetWithKey token2 =
                new TrustTokenSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        tokenSetBuilder("otherPublicKey", "otherToken").build());
        mDatabase.addTrustTokenSets(Arrays.asList(token2, token1));
        // No exception means success for this test.
    }

    @Test
    public void addTrustTokenSets_key_already_exists() {
        TrustTokenSetWithKey token1 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "someToken").build());
        mDatabase.addTrustTokenSets(Arrays.asList(token1));
        TrustTokenSetWithKey token2 =
                new TrustTokenSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        tokenSetBuilder("otherPublicKey", "otherToken").build());
        TrustTokenSetWithKey token3 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "yetAnotherToken").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> mDatabase.addTrustTokenSets(Arrays.asList(token3, token2)));
    }

    @Test
    public void getTrustTokenSet_success() throws Exception {
        TrustTokenSetWithKey token1 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "someToken").build());
        TrustTokenSetWithKey token2 =
                new TrustTokenSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        tokenSetBuilder("otherPublicKey", "otherToken").build());
        mDatabase.addTrustTokenSets(Arrays.asList(token2, token1));

        TrustTokenSetWithKey result1 =
                mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE);
        TrustTokenSetWithKey result2 =
                mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE);
        assertThat(Arrays.asList(result1, result2)).containsExactly(token1, token2);

        assertThrows(
                TrustTokenExhaustedException.class,
                () -> mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE));
    }

    @Test
    public void getTrustTokenSet_skip_expired() throws Exception {
        TrustTokenSetWithKey token1 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "someToken")
                                .setCreatedAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(2)))
                                .setExpireAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(1))) // Expired
                                .build());
        TrustTokenSetWithKey token2 =
                new TrustTokenSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        tokenSetBuilder("otherPublicKey", "otherToken").build());
        mDatabase.addTrustTokenSets(Arrays.asList(token1, token2));

        assertThat(mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE))
                .isEqualTo(token2);
    }

    @Test
    public void getTrustTokenSet_no_token() throws Exception {
        assertThrows(
                TrustTokenExhaustedException.class,
                () -> mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE));
    }

    @Test
    public void getTrustTokenSet_all_expired() throws Exception {
        TrustTokenSetWithKey token1 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "someToken")
                                .setCreatedAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(2)))
                                .setExpireAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(1))) // Expired
                                .build());
        TrustTokenSetWithKey token2 =
                new TrustTokenSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        tokenSetBuilder("otherPublicKey", "otherToken")
                                .setCreatedAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(3)))
                                .setExpireAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(2))) // Expired
                                .build());

        mDatabase.addTrustTokenSets(Arrays.asList(token1, token2));

        assertThrows(
                TrustTokenExhaustedException.class,
                () -> mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_DEVICE));
    }

    @Test
    public void getTrustTokenSet_type_mismatch() throws Exception {
        TrustTokenSetWithKey token1 =
                new TrustTokenSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        tokenSetBuilder("somePublicKey", "someToken").build());
        mDatabase.addTrustTokenSets(Arrays.asList(token1));

        // Requesting a different type
        assertThrows(
                TrustTokenExhaustedException.class,
                () -> mDatabase.getTrustTokenSet(TrustTokenSet.TYPE_VERIFIED_IDENTITIES));
    }

    private TrustTokenKey buildKey(String publicKey, String privateKey) {
        return new TrustTokenKey(publicKey.getBytes(), privateKey.getBytes());
    }

    private TrustTokenSet.Builder tokenSetBuilder(String publicKey, String token) {
        Instant now = Instant.ofEpochMilli(mClock.currentTimeMillis());
        return new TrustTokenSet.Builder()
                .setType(TrustTokenSet.TYPE_VERIFIED_DEVICE)
                .setPublicKey(publicKey.getBytes())
                .setTokenSet(token.getBytes())
                .setCreatedAt(now)
                .setExpireAt(now.plus(Duration.ofDays(1)));
    }

    private static final class MockClock extends Clock {

        @Override
        public long currentTimeMillis() {
            return 23333333;
        }
    }
}
