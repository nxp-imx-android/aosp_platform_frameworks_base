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

package com.android.server.am;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.platform.test.annotations.Presubmit;

import com.android.internal.os.BackgroundThread;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.quality.Strictness;

import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;

/**
 * Test class for {@link BoundServiceSession}.
 *
 * Build/Install/Run:
 *  atest FrameworksMockingServicesTests:BoundServiceSessionTests
 */
@Presubmit
public class BoundServiceSessionTests {
    private static final String TEST_DEBUG_NAME = "test_bound_service_session";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .setStrictness(Strictness.WARN)
            .mockStatic(BackgroundThread.class)
            .build();

    private final ConnectionRecord mMockConnectionRecord = mock(ConnectionRecord.class);
    private final BiConsumer<ConnectionRecord, Boolean> mMockConsumer = mock(BiConsumer.class);

    @Before
    public void stubHandler() {
        final Handler syncHandler = mock(Handler.class);
        when(syncHandler.post(any(Runnable.class))).thenAnswer(inv -> {
            final Runnable r = inv.getArgument(0);
            r.run();
            return true;
        });
        doReturn(syncHandler).when(() -> BackgroundThread.getHandler());
    }

    private BoundServiceSession getNewBoundServiceSessionForTest() {
        return new BoundServiceSession(mMockConsumer, new WeakReference<>(mMockConnectionRecord),
                TEST_DEBUG_NAME);
    }

    private static void assertSessionReset(BoundServiceSession session) {
        assertEquals(0, session.mTotal);
        assertEquals(0, session.mCountsByTag.size());
    }

    @Test
    public void startingState() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        assertEquals(0, session.mTotal);
        assertNull(session.mCountsByTag);
    }

    @Test
    public void resetOnUndercount() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final String[] testTags = {"test0", "test1"};

        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[0]);

        final long token1 = session.binderTransactionStarting(testTags[1]);

        assertEquals(4, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(1, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(5, session.mTotal);

        session.binderTransactionCompleted(token1);

        assertEquals(4, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(0, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(4, session.mTotal);

        session.binderTransactionCompleted(token1);
        assertSessionReset(session);
    }

    @Test
    public void resetOnInvalidToken() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final String testTag = "test";

        final long validToken = session.binderTransactionStarting(testTag);
        session.binderTransactionStarting(testTag);
        session.binderTransactionStarting(testTag);

        assertEquals(3, session.mTotal);
        assertEquals(3, (int) session.mCountsByTag.get(testTag));

        session.binderTransactionCompleted(validToken + 1);
        assertSessionReset(session);

        session.binderTransactionStarting(testTag);
        session.binderTransactionStarting(testTag);

        assertEquals(2, session.mTotal);
        assertEquals(2, (int) session.mCountsByTag.get(testTag));

        session.binderTransactionCompleted(-1);
        assertSessionReset(session);
    }

    @Test
    public void tokenConsistency() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final String[] testTags = {"test0", "test1", "test2"};

        final long token0 = session.binderTransactionStarting(testTags[0]);
        final long token1 = session.binderTransactionStarting(testTags[1]);
        final long token2 = session.binderTransactionStarting(testTags[2]);

        assertEquals(token0, session.binderTransactionStarting(testTags[0]));
        assertEquals(token1, session.binderTransactionStarting(testTags[1]));
        assertEquals(token2, session.binderTransactionStarting(testTags[2]));
    }

    @Test
    public void callsConsumerOnChangeFromZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        assertEquals(0, session.mTotal);

        session.binderTransactionStarting("test");
        assertEquals(1, session.mTotal);
        verify(mMockConsumer).accept(mMockConnectionRecord, true);

        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");

        assertEquals(4, session.mTotal);
        verifyNoMoreInteractions(mMockConsumer);
    }

    @Test
    public void callsConsumerOnChangeFromOneToZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final long token = session.binderTransactionStarting("test");
        assertEquals(1, session.mTotal);
        clearInvocations(mMockConsumer);

        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");
        assertEquals(3, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());

        session.binderTransactionCompleted(token);
        session.binderTransactionCompleted(token);
        assertEquals(1, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());

        session.binderTransactionCompleted(token);
        assertEquals(0, session.mTotal);
        verify(mMockConsumer).accept(mMockConnectionRecord, false);
    }

    @Test
    public void callsConsumerOnChangeFromManyToZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        session.binderTransactionStarting("test");
        clearInvocations(mMockConsumer);

        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");
        assertEquals(3, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());

        session.binderTransactionCompleted(-1);
        assertEquals(0, session.mTotal);
        verify(mMockConsumer).accept(mMockConnectionRecord, false);
    }

    @Test
    public void noCallToConsumerOnChangeFromZeroToZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final long token = session.binderTransactionStarting("test");
        session.binderTransactionCompleted(token);

        assertEquals(0, session.mTotal);
        clearInvocations(mMockConsumer);

        session.binderTransactionCompleted(-1);
        assertEquals(0, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());
    }

    @Test
    public void countKeeping() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final String[] testTags = {"test0", "test1", "test2"};

        final long token0 = session.binderTransactionStarting(testTags[0]);
        final long token1 = session.binderTransactionStarting(testTags[1]);
        final long token2 = session.binderTransactionStarting(testTags[2]);

        session.binderTransactionStarting(testTags[1]);
        session.binderTransactionStarting(testTags[2]);
        session.binderTransactionStarting(testTags[2]);

        assertEquals(1, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(2, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(3, (int) session.mCountsByTag.get(testTags[2]));
        assertEquals(6, session.mTotal);

        session.binderTransactionCompleted(token0);
        session.binderTransactionCompleted(token1);
        session.binderTransactionCompleted(token2);

        assertEquals(0, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(1, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(2, (int) session.mCountsByTag.get(testTags[2]));
        assertEquals(3, session.mTotal);

        session.binderTransactionCompleted(token1);
        session.binderTransactionCompleted(token2);

        assertEquals(0, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(0, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(1, (int) session.mCountsByTag.get(testTags[2]));
        assertEquals(1, session.mTotal);

        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[1]);

        assertEquals(1, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(1, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(1, (int) session.mCountsByTag.get(testTags[2]));
        assertEquals(3, session.mTotal);

        session.binderTransactionCompleted(token0);
        session.binderTransactionCompleted(token1);
        session.binderTransactionCompleted(token2);

        assertEquals(0, (int) session.mCountsByTag.get(testTags[0]));
        assertEquals(0, (int) session.mCountsByTag.get(testTags[1]));
        assertEquals(0, (int) session.mCountsByTag.get(testTags[2]));
        assertEquals(0, session.mTotal);
    }
}
