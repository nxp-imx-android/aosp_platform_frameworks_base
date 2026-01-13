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

package android.libcore;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ArraysPerfTest {

    @Rule
    public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    // A volatile boolean to store the result of the Arrays.equals calls to avoid the compiler
    // optimizing it away.
    private volatile boolean mResult;

    private static final int LENGTH = 4096;
    private final int[] a;
    private final int[] aCopy;
    private final int[] aDiff;
    private final byte[] b;
    private final byte[] bCopy;
    private final byte[] bDiff;

    public ArraysPerfTest() {
        a = new int[LENGTH];
        Random random = new Random(0);
        for (int i = 0; i < LENGTH; i++) {
            a[i] = random.nextInt();
        }
        aCopy = Arrays.copyOf(a, LENGTH);
        aDiff = Arrays.copyOf(a, LENGTH);
        aDiff[LENGTH - 1] += 1; // Make it different at the end

        b = new byte[LENGTH];
        random.nextBytes(b);
        bCopy = Arrays.copyOf(b, LENGTH);
        bDiff = Arrays.copyOf(b, LENGTH);
        bDiff[LENGTH - 1] += 1; // Make it different at the end
    }

    @Test
    public void timeArraysEqualsIntTrue() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mResult = Arrays.equals(a, aCopy);
        }
    }

    @Test
    public void timeArraysEqualsIntFalse() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mResult = Arrays.equals(a, aDiff);
        }
    }

    @Test
    public void timeArraysEqualsByteTrue() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mResult = Arrays.equals(b, bCopy);
        }
    }

    @Test
    public void timeArraysEqualsByteFalse() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mResult = Arrays.equals(b, bDiff);
        }
    }
}
