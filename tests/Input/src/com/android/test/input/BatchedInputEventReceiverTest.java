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

package com.android.test.input;

import static com.android.cts.input.inputeventmatchers.InputEventMatchersKt.withMotionAction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.os.HandlerThread;
import android.os.Looper;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.cts.input.BlockingQueueEventVerifier;
import com.android.cts.input.MotionEventBuilder;
import com.android.cts.input.PointerBuilder;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.LinkedBlockingQueue;

public class BatchedInputEventReceiverTest {
    private static final String TAG = "BatchedInputEventReceiverTest";

    private static class TestBatchedInputEventReceiver extends BatchedInputEventReceiver {
        // If set, event receiver will disable, and then immediately enable batching after handling
        // next pending batched input event - i.e. after scheduling batched input, but before the
        // scheduled batched input is actually consumed.
        boolean mResetBatchingAfterNextPendingEvent = false;

        // Keeps track of received input events.
        private final LinkedBlockingQueue<InputEvent> mInputEvents = new LinkedBlockingQueue<>();
        private final BlockingQueueEventVerifier mVerifier;

        TestBatchedInputEventReceiver(InputChannel channel, Looper looper,
                                      Choreographer choreographer) {
            super(channel, looper, choreographer);
            mVerifier = new BlockingQueueEventVerifier(mInputEvents);
        }

        @Override
        public void onBatchedInputEventPending(int source) {
            super.onBatchedInputEventPending(source);

            if (mResetBatchingAfterNextPendingEvent) {
                mResetBatchingAfterNextPendingEvent = false;
                setBatchingEnabled(false);
                setBatchingEnabled(true);
            }
        }

        @Override
        public void onInputEvent(InputEvent event) {
            if (event instanceof MotionEvent motionEvent) {
                mInputEvents.offer(MotionEvent.obtain(motionEvent));
            } else {
                throw new RuntimeException("Received " + event + " is not a motion");
            }
            finishInputEvent(event, true /* handled */);
        }

        void assertReceivedMotion(Matcher<MotionEvent> matcher) {
            mVerifier.assertReceivedMotion(matcher);
        }
    }

    @Rule
    public MockitoRule mMockitoJUnitRule = MockitoJUnit.rule();

    private final InputChannel[] mChannels = InputChannel.openInputChannelPair("TestChannel");

    // Use the custom class that exposes the Handler for posting Runnables
    private final HandlerThread mHandlerThread = new HandlerThread("Process input events");
    private SpyInputEventSender mSender;
    private TestBatchedInputEventReceiver mBatchedReceiver;

    @Mock
    private Choreographer mMockChoreographer;

    // Changed to a public field to match Kotlin's 'var' behavior for easy access/modification
    public long lastChoreographerFrameTimeMs = 995;

    private MotionEvent getTestMouseMotionEvent(int action, long eventTime) {
        // The MotionEventBuilder and PointerBuilder classes are assumed to exist
        return new MotionEventBuilder(action, InputDevice.SOURCE_MOUSE)
                .downTime(eventTime)
                .eventTime(eventTime)
                .pointer(new PointerBuilder(/* id= */ 0, MotionEvent.TOOL_TYPE_MOUSE).x(0f).y(0f))
                .build();
    }

    @Before
    public void setUp() {
        // Mocking Choreographer to run the posted callback immediately on the HandlerThread.
        doAnswer(invocation -> {
            mHandlerThread.getThreadHandler().post((Runnable) invocation.getArgument(1));
            return null;
        }).when(mMockChoreographer).postCallback(anyInt(), any(), any());

        doAnswer(invocation -> {
            mHandlerThread.getThreadHandler().removeCallbacks((Runnable) invocation.getArgument(1));
            return null;
        }).when(mMockChoreographer).removeCallbacks(anyInt(), any(), any());

        // Set up frame times in the choreographer to increment on each request.
        // NOTE: These timestamps are used to determine whether batched events need to be processed.
        // Time stamps for events sent by tests need to be tweaked to account for choreographer time
        // frames.
        when(mMockChoreographer.getFrameTimeNanos()).thenAnswer(invocation -> {
            lastChoreographerFrameTimeMs += 5;
            return lastChoreographerFrameTimeMs * 1000000L;
        });

        mHandlerThread.start();

        Looper looper = mHandlerThread.getLooper();
        mSender = new SpyInputEventSender(mChannels[0], looper);
        mBatchedReceiver = new TestBatchedInputEventReceiver(
                mChannels[1],
                looper,
                mMockChoreographer
        );
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
    }

    // Consumption of batched input events should continue if batching is disabled, and then enabled
    // again before running the task to consume pending events in response to batching getting
    // disabled.
    @Test
    public void testKeepConsumingEventsAfterBatchingRestart() {
        int seq = 12;
        // Send ACTION_DOWN, and verify it gets handled by the receiver without batching.
        mSender.sendInputEvent(seq, getTestMouseMotionEvent(MotionEvent.ACTION_DOWN, 900L));
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN));
        mSender.assertReceivedFinishedSignal(seq, true);

        mBatchedReceiver.mResetBatchingAfterNextPendingEvent = true;

        // Send hover move events, which will get batched.
        // The receiver resets batching after receiving batched input notification for this event.
        mSender.sendInputEvent(seq + 1, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 930L));
        mSender.sendInputEvent(seq + 2, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 1010L));

        // Verify that the receiver consumed batched hover move event, even though batching was
        // restarted upon receiving first pending batched input events notification.
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE));
        mSender.assertReceivedFinishedSignal(seq + 1, true);
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE));
        mSender.assertReceivedFinishedSignal(seq + 2, true);

        // Send another hover move event, and verify it gets consumed in case where batching is not
        // restarted.
        mSender.sendInputEvent(seq + 3, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 1060L));
        mBatchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE));

        mSender.assertReceivedFinishedSignal(seq + 3, true);

        mSender.dispose();
    }
}
