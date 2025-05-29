/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License athasEqualMessages
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Message;
import android.os.MessageHeap;

import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MessageHeapTest {
    private static final String TAG = "MessageHeapTest";

    private final AtomicLong mNextInsertSeq = new AtomicLong(1);
    private final AtomicLong mNextFrontInsertSeq = new AtomicLong(-1);

    private final Random mRand = new Random(8675309);

    private void insertMessage(MessageHeap heap, long when) {
        long seq = when != 0 ? mNextInsertSeq.incrementAndGet()
                : mNextFrontInsertSeq.decrementAndGet();

        Message m = new Message();
        m.when = when;
        m.insertSeq = seq;
        heap.add(m);
    }

    private static boolean popAndVerifyUntilEmpty(MessageHeap heap) {
        Message last = null;
        Message m;

        while (!heap.isEmpty()) {
            m = heap.poll();
            if (last != null && m.when < last.when) {
                Log.e(TAG, "popAndVerifyUntilEmpty: heap property broken last: " + last.when
                        + " popped: " + m.when);
                return false;
            }
            last = m;
        }
        return true;
    }

    private static boolean verify(MessageHeap heap) {
        if (!heap.verify()) {
            return false;
        }

        if (!popAndVerifyUntilEmpty(heap)) {
            return false;
        }
        return true;
    }

    private long getPositiveRandLong() {
        try {
            return Math.absExact(mRand.nextLong());
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    private MessageHeap fillWithRandomValues(int numValues) {
        MessageHeap heap = new MessageHeap();
        for (int i = 0; i < numValues; i++) {
            insertMessage(heap, getPositiveRandLong());
        }
        return heap;
    }

    @Test
    public void fillSequentially() {
        MessageHeap heap = new MessageHeap();
        for (int i = 0; i < 4_000; i++) {
            insertMessage(heap, i);
        }
        assertTrue(verify(heap));
    }

    @Test
    public void reverseOrderTest() {
        MessageHeap heap = new MessageHeap();
        for (int i = 99; i >= 90; i--) {
            insertMessage(heap, i);
        }
        assertTrue(verify(heap));
    }

    @Test
    public void zerosTest() {
        MessageHeap heap = new MessageHeap();
        for (int i = 0; i < 10; i++) {
            insertMessage(heap, 0);
        }
        assertTrue(verify(heap));
    }

    @Test
    public void randomValuesTest() {
        final int numValues = 4_000;
        MessageHeap heap = new MessageHeap();
        for (int i = 0; i < numValues; i++) {
            insertMessage(heap, getPositiveRandLong());
        }
        assertTrue(verify(heap));
    }

    @Test
    public void fillWithRandomValuesAndZeros() {
        final int numValues = 4_000;
        MessageHeap heap = fillWithRandomValues(numValues);
        for (int i = 0; i < numValues; i++) {
            long when = getPositiveRandLong();
            if ((when % 4) == 0) {
                when = 0;
            }
            insertMessage(heap, when);
        }
        assertTrue(verify(heap));
    }

    @Test
    public void randomRemoveTest() {
        MessageHeap heap = fillWithRandomValues(4_000);
        for (int i = 0; i < heap.size(); i++) {
            if (mRand.nextBoolean()) {
                heap.remove(i);
            }
        }
        heap.maybeShrink();
        assertTrue(verify(heap));
    }

    @Test
    public void growTest() {
        MessageHeap heap = fillWithRandomValues(4_000);
        assertTrue(verify(heap));
    }

    @Test
    public void shrinkSlightly() {
        MessageHeap heap = fillWithRandomValues(4_000);
        for (int i = 0; i < 1_000; i++) {
            heap.remove(i);
        }
        heap.maybeShrink();
        assertTrue(verify(heap));
    }

    @Test
    public void shrinkCompletely() {
        MessageHeap heap = fillWithRandomValues(4_000);
        heap.removeAll();
        assertTrue(verify(heap));
    }

    @Test
    public void removeBoundsTest() {
        MessageHeap heap = new MessageHeap();
        for (int i = 0; i < 10; i++) {
            insertMessage(heap, 0);
        }
        try {
            heap.remove(heap.size());
            heap.remove(-1);
            fail("Expected IllegalArgumentException for out of bounds test");
        } catch (IllegalArgumentException e) {

        }
        assertTrue(verify(heap));
    }
}
