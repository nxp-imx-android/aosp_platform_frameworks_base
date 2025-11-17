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

package com.android.server.lskfreset;

import static org.junit.Assert.assertEquals;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FakeScheduledExecutorServiceTest {
    private FakeScheduledExecutorService mExecutor = new FakeScheduledExecutorService();

    @Test
    public void testInitialState() {
        assertEquals(0, mExecutor.numTasks());
    }

    @Test
    public void testFastForwardEmptyExecutor() {
        assertEquals(0, mExecutor.fastForwardMillis(TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    public void testZeroDelayDoesNotExecute() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                0,
                TimeUnit.MILLISECONDS);
        assertEquals(0, executions.get());
    }

    @Test
    public void testExecuteSingleTask() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                1,
                TimeUnit.SECONDS);
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(1, executions.get());
    }

    @Test
    public void testExecuteSequentialTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                1,
                TimeUnit.SECONDS);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                2,
                TimeUnit.SECONDS);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                3,
                TimeUnit.SECONDS);
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(3, executions.get());
    }

    @Test
    public void testExecuteMultipleTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                1,
                TimeUnit.SECONDS);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                1,
                TimeUnit.SECONDS);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                2,
                TimeUnit.SECONDS);
        assertEquals(0, executions.get());
        assertEquals(2, mExecutor.fastForwardMillis(1000));
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(3, executions.get());
    }

    @Test
    public void testScheduleNewTasksLater() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                1,
                TimeUnit.SECONDS);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                4,
                TimeUnit.SECONDS);
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                },
                2,
                TimeUnit.SECONDS);
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(3, executions.get());
    }

    @Test
    public void testExecuteTaskSchedulesTask() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.schedule(
                () -> {
                    executions.incrementAndGet();
                    mExecutor.schedule(
                            () -> {
                                executions.incrementAndGet();
                            },
                            2,
                            TimeUnit.SECONDS);
                },
                1,
                TimeUnit.SECONDS);
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(2, executions.get());
    }
}
