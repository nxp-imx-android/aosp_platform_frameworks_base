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
public final class TalismanDatabaseTest {
    TalismanDatabase mDatabase;
    MockClock mClock = new MockClock();

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            File databaseFile = File.createTempFile("talisman.sqlite", null, context.getCacheDir());
            mDatabase = TalismanSqliteDatabase.create(context, databaseFile, mClock);
        } catch (IOException e) {
            throw new AssertionError("cannot create the database file");
        }
    }

    @Test
    public void addTalismanSets_success() {
        TalismanSetWithKey talisman1 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "someTalisman").build());
        TalismanSetWithKey talisman2 =
                new TalismanSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        talismanSetBuilder("otherPublicKey", "otherTalisman").build());
        mDatabase.addTalismanSets(Arrays.asList(talisman2, talisman1));
        // No exception means success for this test.
    }

    @Test
    public void addTalismanSets_key_already_exists() {
        TalismanSetWithKey talisman1 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "someTalisman").build());
        mDatabase.addTalismanSets(Arrays.asList(talisman1));
        TalismanSetWithKey talisman2 =
                new TalismanSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        talismanSetBuilder("otherPublicKey", "otherTalisman").build());
        TalismanSetWithKey talisman3 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "yetAnotherTalisman").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> mDatabase.addTalismanSets(Arrays.asList(talisman3, talisman2)));
    }

    @Test
    public void getTalismanSet_success() throws Exception {
        TalismanSetWithKey talisman1 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "someTalisman").build());
        TalismanSetWithKey talisman2 =
                new TalismanSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        talismanSetBuilder("otherPublicKey", "otherTalisman").build());
        mDatabase.addTalismanSets(Arrays.asList(talisman2, talisman1));

        TalismanSetWithKey result1 = mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_DEVICE);
        TalismanSetWithKey result2 = mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_DEVICE);
        assertThat(Arrays.asList(result1, result2)).containsExactly(talisman1, talisman2);

        assertThrows(
                TalismanExhaustedException.class,
                () -> mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_DEVICE));
    }

    @Test
    public void getTalismanSet_skip_expired() throws Exception {
        TalismanSetWithKey talisman1 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "someTalisman")
                                .setCreatedAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(2)))
                                .setExpireAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(1))) // Expired
                                .build());
        TalismanSetWithKey talisman2 =
                new TalismanSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        talismanSetBuilder("otherPublicKey", "otherTalisman").build());
        mDatabase.addTalismanSets(Arrays.asList(talisman1, talisman2));

        assertThat(mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_DEVICE)).isEqualTo(talisman2);
    }

    @Test
    public void getTalismanSet_no_talisman() throws Exception {
        assertThrows(
                TalismanExhaustedException.class,
                () -> mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_DEVICE));
    }

    @Test
    public void getTalismanSet_all_expired() throws Exception {
        TalismanSetWithKey talisman1 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "someTalisman")
                                .setCreatedAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(2)))
                                .setExpireAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(1))) // Expired
                                .build());
        TalismanSetWithKey talisman2 =
                new TalismanSetWithKey(
                        buildKey("otherPublicKey", "otherSecretKey"),
                        talismanSetBuilder("otherPublicKey", "otherTalisman")
                                .setCreatedAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(3)))
                                .setExpireAt(
                                        Instant.ofEpochMilli(mClock.currentTimeMillis())
                                                .minus(Duration.ofDays(2))) // Expired
                                .build());

        mDatabase.addTalismanSets(Arrays.asList(talisman1, talisman2));

        assertThrows(
                TalismanExhaustedException.class,
                () -> mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_DEVICE));
    }

    @Test
    public void getTalismanSet_type_mismatch() throws Exception {
        TalismanSetWithKey talisman1 =
                new TalismanSetWithKey(
                        buildKey("somePublicKey", "someSecretKey"),
                        talismanSetBuilder("somePublicKey", "someTalisman").build());
        mDatabase.addTalismanSets(Arrays.asList(talisman1));

        // Requesting a different type
        assertThrows(
                TalismanExhaustedException.class,
                () -> mDatabase.getTalismanSet(TalismanSet.TYPE_VERIFIED_IDENTITIES));
    }

    private TalismanKey buildKey(String publicKey, String privateKey) {
        return new TalismanKey(publicKey.getBytes(), privateKey.getBytes());
    }

    private TalismanSet.Builder talismanSetBuilder(String publicKey, String talisman) {
        Instant now = Instant.ofEpochMilli(mClock.currentTimeMillis());
        return new TalismanSet.Builder()
                .setType(TalismanSet.TYPE_VERIFIED_DEVICE)
                .setPublicKey(publicKey.getBytes())
                .setTalismanSet(talisman.getBytes())
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
