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

package com.android.internal.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import android.platform.test.flag.junit.CheckFlagsRule;

import android.os.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StringCacheTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int CACHE_SIZE = 256;
    private StringCache mCache;

    @Before
    public void setUp() {
        mCache = new StringCache(CACHE_SIZE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testCacheHit() {
        String s1 = new String("hello");
        String s2 = new String("hello");

        String cachedS1 = mCache.cache(s1);
        String cachedS2 = mCache.cache(s2);

        assertSame(s1, cachedS1);
        assertSame(s1, cachedS2); // Should be the same instance
        assertEquals(1, mCache.getMissCount());
        assertEquals(1, mCache.getHitCount());
        assertEquals(1, mCache.size());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testCacheMiss() {
        String s1 = mCache.cache("one");
        String s2 = mCache.cache("two");

        assertEquals("one", s1);
        assertEquals("two", s2);
        assertEquals(2, mCache.getMissCount());
        assertEquals(0, mCache.getHitCount());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testEviction() {
        // In this test we allow at most 3 strings in the cache to exercise eviction.
        final int cacheCapacity = 3;
        mCache = new StringCache(cacheCapacity);

        // Fill the cache.
        int i = 0;
        while (mCache.size() < cacheCapacity) {
            // Generate a unique string each iteration.
            // We don't know at what bucket it'll land in, but we should eventually fill all buckets
            // this way.
            mCache.cache(Integer.toString(++i));
        }

        long expectedHits = mCache.getHitCount();
        long expectedMisses = mCache.getMissCount();
        long expectedEvictions = mCache.getEvictCount();

        // Cache a new string. It should be a miss, and cause an eviction.
        mCache.cache("Hello world!");
        ++expectedMisses;
        ++expectedEvictions;
        assertEquals(expectedHits, mCache.getHitCount());
        assertEquals(expectedMisses, mCache.getMissCount());
        assertEquals(expectedEvictions, mCache.getEvictCount());
        assertEquals(cacheCapacity, mCache.size());

        // Retrieve the same string. It should be a hit, and cause no evictions.
        mCache.cache("Hello world!");
        ++expectedHits;
        assertEquals(expectedHits, mCache.getHitCount());
        assertEquals(expectedMisses, mCache.getMissCount());
        assertEquals(expectedEvictions, mCache.getEvictCount());
        assertEquals(cacheCapacity, mCache.size());
    }

    /** Null strings are not cached. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testNullString() {
        String result = mCache.cache(null);
        assertSame(null, result);
        assertEquals(0, mCache.size());
        assertEquals(0, mCache.getHitCount() + mCache.getMissCount());
        assertEquals(0, mCache.getRejectCount());
    }

    /** Long strings are not cached. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testStringTooLong() {
        // Create a string longer than the max length (256)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("a");
        }
        String longString = sb.toString();
        String result = mCache.cache(longString);

        assertSame(longString, result);
        assertEquals(0, mCache.size());
        assertEquals(0, mCache.getHitCount() + mCache.getMissCount());
        assertEquals(1, mCache.getRejectCount());
    }

    /** The cache can be used safely from multiple threads. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testMultiThreaded() throws InterruptedException {
        final int numThreads = 10;
        final int iterations = 1000;
        final String[] strings = {"a", "b", "c", "d", "e"};

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] =
                    new Thread(
                            () -> {
                                for (int j = 0; j < iterations; j++) {
                                    mCache.cache(strings[j % strings.length]);
                                }
                            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertEquals(numThreads * iterations, mCache.getHitCount() + mCache.getMissCount());
        assertTrue(mCache.size() <= CACHE_SIZE);
    }

    /** The cache can be used safely from multiple threads under high contention. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testMultiThreadedStress() throws InterruptedException {
        final int numThreads = 50;
        final int iterations = 2000;
        final String[] strings = new String[100];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = "s" + i;
        }

        // The cache size is small, so there will be a lot of evictions.
        final StringCache stressCache = new StringCache(10);

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] =
                    new Thread(
                            () -> {
                                for (int j = 0; j < iterations; j++) {
                                    stressCache.cache(strings[j % strings.length]);
                                }
                            });
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        // Validate cache size.
        assertTrue("Cache size should be less than or equal to max size", stressCache.size() <= 10);

        // Validate stats.
        long totalOps = stressCache.getHitCount() + stressCache.getMissCount();
        assertEquals(numThreads * iterations, totalOps);

        // With so many threads and a small cache, there should be evictions.
        assertTrue("Should have evictions", stressCache.getEvictCount() > 0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testClear() {
        mCache.cache("a");
        mCache.cache("b");
        mCache.cache("a"); // Hit

        assertEquals(2, mCache.size());
        assertEquals(1, mCache.getHitCount());
        assertEquals(2, mCache.getMissCount());

        mCache.clear();

        assertEquals(0, mCache.size());
        // Clearing preserves stats.
        assertEquals(1, mCache.getHitCount());
        assertEquals(2, mCache.getMissCount());

        // "b" should be a miss after clear.
        mCache.cache("b");
        assertEquals(1, mCache.size());
        assertEquals(1, mCache.getHitCount());
        assertEquals(3, mCache.getMissCount());

        // Ensure that we can still hit.
        mCache.cache("b");
        assertEquals(1, mCache.size());
        assertEquals(2, mCache.getHitCount());
        assertEquals(3, mCache.getMissCount());
    }

    /** When the flag is disabled, the cache should be a no-op. */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_PARCEL_STRING_CACHE_ENABLED)
    public void testFlagDisabled() {
        // Use the singleton cache, which should be disabled by flag.
        mCache = StringCache.INSTANCE;

        String s1 = new String("hello");
        String s2 = new String("hello");

        String cachedS1 = StringCache.INSTANCE.cache(s1);
        String cachedS2 = StringCache.INSTANCE.cache(s2);

        assertSame(s1, cachedS1);
        assertSame(s2, cachedS2);

        assertEquals(0, mCache.getHitCount());
        assertEquals(0, mCache.getMissCount());
        assertEquals(0, mCache.size());
    }
}
