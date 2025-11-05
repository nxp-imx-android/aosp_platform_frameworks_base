/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.testutils;


import static android.util.ExceptionUtils.appendCause;
import static android.util.ExceptionUtils.propagate;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * A test {@link Handler} that stores incoming {@link Message}s and {@link Runnable callbacks}
 * in a {@link PriorityQueue} based on time, to be manually processed later in a correct order
 * either all together with {@link #flush}, or only those due at the current time with
 * {@link #timeAdvance}.
 *
 * For the latter use case this also supports providing a custom clock (in a format of a
 * milliseconds-returning {@link LongSupplier}), that will be used for storing the messages'
 * timestamps to be posted at, and checked against during {@link #timeAdvance}.
 *
 * This allows to test code that uses {@link Handler}'s delayed invocation capabilities, such as
 * {@link Handler#sendMessageDelayed} or {@link Handler#postDelayed} without resorting to
 * synchronously {@link Thread#sleep}ing in your test.
 *
 * Note, this class works by enqueuing messages infinitely in the future to correctly test message
 * removal on the handler. When doing so, there is a design choice: either remove these sentinel
 * messages when the message is manually dispatched, or retain the message. In the former case,
 * duplicate messages (at the level of msg/obj or runnable identity), won't be handled correctly,
 * since handler removal operates at this level of granularity. In the latter case, handler
 * introspection (i.e. checking if messages exist), won't be handled correctly, as they will always
 * show as present even if within the test logic, the message already "ran". As such, this behavior
 * can be parametrized, defaulting to the former.
 *
 * Enqueuing messages should be synchronized against ungating their dispatch.
 * @see OffsettableClock for a useful custom clock implementation to use with this handler
 */
public class TestHandler extends Handler {
    private static final LongSupplier DEFAULT_CLOCK = SystemClock::uptimeMillis;
    private static final String TAG = "TestHandler";
    private static final boolean DEBUG = false;

    private final PriorityQueue<MsgInfo> mMessages = new PriorityQueue<>();
    private final boolean mRemoveMessages;

    private final LongSupplier mClock;
    private int  mMessageCount = 0;

    public TestHandler(Callback callback) {
        this(callback, DEFAULT_CLOCK);
    }

    public TestHandler(Callback callback, LongSupplier clock) {
        this(Looper.getMainLooper(), callback, clock);
    }

    public TestHandler(Looper looper, Callback callback, LongSupplier clock) {
        this(looper, callback, clock, false);
    }

    public TestHandler(
            Looper looper, Callback callback, LongSupplier clock, boolean removeMessagesOnRun) {
        super(looper, callback);
        mClock = clock;
        mRemoveMessages = removeMessagesOnRun;
    }

    @Override
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        ++mMessageCount;

        // uptimeMillis is an absolute time obtained as SystemClock.uptimeMillis() + offsetMillis
        // if custom clock is given, recalculate the time with regards to it
        if (mClock != DEFAULT_CLOCK) {
            uptimeMillis = uptimeMillis - SystemClock.uptimeMillis() + mClock.getAsLong();
        }

        MsgInfo m = new MsgInfo(Message.obtain(msg), uptimeMillis, mMessageCount);
        if (DEBUG) {
            Log.d(TAG, "Enqueue message: " + m);
        }
        // post a sentinel queue entry to keep track of message removal
        return super.sendMessageAtTime(msg, Long.MAX_VALUE)
                && mMessages.add(m);
    }

    /** @see TestHandler */
    public void timeAdvance() {
        long now = mClock.getAsLong();
        if (DEBUG) {
            Log.d(TAG, "Advancing time: " + now);
        }
        while (!mMessages.isEmpty() && mMessages.peek().sendTime <= now) {
            dispatch(mMessages.poll());
        }
    }

    /**
     * Dispatch all messages in order. Dispatches pending messages in the "future" with respect to
     * the clock.
     *
     * @see TestHandler
     */
    public void flush() {
        MsgInfo msg;
        while ((msg = mMessages.poll()) != null) {
            dispatch(msg);
        }
    }

    /**
     * Deletes all messages in queue.
     */
    public void clear() {
        mMessages.clear();
    }

    public PriorityQueue<MsgInfo> getPendingMessages() {
        return new PriorityQueue<>(mMessages);
    }

    /**
     * Removes messages matching the predicate
     */
    public void removeIf(Predicate<? super MsgInfo> predicate) {
        mMessages.removeIf(predicate);
    }

    /**
     * Optionally-overridable to allow deciphering message types
     *
     * @see android.util.DebugUtils#valueToString - a handy utility to use when overriding this
     */
    protected String messageToString(Message message) {
        return message.toString();
    }

    private void dispatch(MsgInfo msg) {
        int msgId = msg.message.what;
        Object obj = msg.message.obj;
        Runnable cb = msg.message.getCallback();

        if (cb != null && hasCallbacks(cb)) {
            if (mRemoveMessages) {
                removeCallbacks(cb, obj);
            }
        } else if (hasMessages(msgId, obj)) {
            if (mRemoveMessages) {
                removeMessages(msgId, msg.message.obj);
            }
        } else {
            if (DEBUG) {
                Log.d(TAG, "Dispatch message skipped due to message removal: " + msg);
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Dispatch message: " + msg);
        }
        try {
            dispatchMessage(msg.message);
        } catch (Throwable t) {
            // Append stack trace of this message being posted as a cause for a helpful
            // test error message
            throw propagate(appendCause(t, msg.postPoint));
        } finally {
            msg.message.recycle();
        }
    }

    public class MsgInfo implements Comparable<MsgInfo> {
        public final Message message;
        public final long sendTime;
        public final int mMessageOrder;
        public final RuntimeException postPoint;

        private MsgInfo(Message message, long sendTime, int messageOrder) {
            this.message = message;
            this.sendTime = sendTime;
            this.postPoint = new RuntimeException("Message originated from here:");
            mMessageOrder = messageOrder;
        }

        @Override
        public int compareTo(MsgInfo o) {
            final int result = Long.compare(sendTime, o.sendTime);
            return result != 0 ? result : Integer.compare(mMessageOrder, o.mMessageOrder);
        }

        @Override
        public String toString() {
            return "MsgInfo{" +
                    "message =" + messageToString(message)
                    + ", sendTime =" + sendTime
                    + ", mMessageOrder =" + mMessageOrder
                    + '}';
        }
    }
}
