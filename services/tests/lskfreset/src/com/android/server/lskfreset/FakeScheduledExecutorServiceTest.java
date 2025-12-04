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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class FakeScheduledExecutorServiceTest {
    private FakeScheduledExecutorService mExecutor;

    @Before
    public void setUp() {
        mExecutor = new FakeScheduledExecutorService(Thread.currentThread());
    }

    @Test
    public void testInitialState() {
        assertEquals(0, mExecutor.numTasks());
    }

    @Test
    public void testFastForwardEmptyExecutor() {
        assertEquals(0, mExecutor.fastForwardMillis(TimeUnit.DAYS.toMillis(1)));
    }

    @Test
    public void testFastForwardOnAnotherThreadFails() throws InterruptedException {
        AtomicBoolean threadCompleted = new AtomicBoolean(false);
        Thread otherThread =
                new Thread(
                        () -> {
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> mExecutor.fastForwardMillis(TimeUnit.DAYS.toMillis(1)));
                            threadCompleted.set(true);
                        });
        otherThread.start();
        otherThread.join();
        assertTrue(threadCompleted.get());
    }

    @Test
    public void testZeroDelayDoesNotRunImmediately() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        0,
                        TimeUnit.MILLISECONDS);
        assertEquals(0, executions.get());
        assertFalse(future.isDone());
    }

    @Test
    public void testFutureGetOnTestThreadFails() {
        ScheduledFuture<?> future = mExecutor.schedule(() -> {}, 1, TimeUnit.SECONDS);
        assertFalse(future.isDone());
        assertThrows(IllegalStateException.class, () -> future.get());
    }

    @Test
    public void testScheduleRunnable() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleRunnableReturnsNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        AtomicBoolean futureGetReturned = new AtomicBoolean(false);
        ScheduledFuture<?> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                future.get();
                                futureGetReturned.set(true);
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(futureGetReturned.get());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        futureGetThread.join();
        assertTrue(futureGetReturned.get());
    }

    @Test
    public void testScheduleCallableWithResult() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get().intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleCallableWithResultAndTimeout() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        },
                        2,
                        TimeUnit.SECONDS);
        Thread futureGetTimeoutThread =
                new Thread(
                        () -> {
                            assertThrows(
                                    TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS));
                        });
        Thread futureGetSuccessThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get(3, TimeUnit.SECONDS).intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetTimeoutThread.start();
        futureGetSuccessThread.start();
        mExecutor.waitForNumTimeoutWaiters(2);
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        futureGetTimeoutThread.join();
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetSuccessThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleCallableWithNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            return null;
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertNull(future.get());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testScheduleCallableWithException() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<Integer> future =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            throw new RuntimeException("testing");
                        },
                        1,
                        TimeUnit.SECONDS);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            assertThrows(ExecutionException.class, () -> future.get());
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testExecute() {
        AtomicInteger executions = new AtomicInteger(0);
        mExecutor.execute(() -> executions.incrementAndGet());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitRunnable() {
        AtomicInteger executions = new AtomicInteger(0);
        Future<?> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                        });
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitRunnableReturnsNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        AtomicBoolean futureGetReturned = new AtomicBoolean(false);
        Future<?> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                future.get();
                                futureGetReturned.set(true);
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(futureGetReturned.get());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        futureGetThread.join();
        assertTrue(futureGetReturned.get());
    }

    @Test
    public void testSubmitRunnableWithResultValue() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                        },
                        31415);
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get().intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitCallableWithResult() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                            return 31415;
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertEquals(31415, future.get().intValue());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitCallableWithNull() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                            return null;
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            try {
                                assertNull(future.get());
                            } catch (Exception e) {
                                fail("future.get() threw unexpected exception: " + e);
                            }
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testSubmitCallableWithException() throws InterruptedException {
        AtomicInteger executions = new AtomicInteger(0);
        Future<Integer> future =
                mExecutor.submit(
                        () -> {
                            executions.incrementAndGet();
                            throw new RuntimeException("testing");
                        });
        Thread futureGetThread =
                new Thread(
                        () -> {
                            assertThrows(ExecutionException.class, () -> future.get());
                        });
        futureGetThread.start();
        assertFalse(future.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
        futureGetThread.join();
        assertEquals(0, mExecutor.fastForwardMillis(0));
        assertTrue(future.isDone());
        assertEquals(1, executions.get());
    }

    @Test
    public void testRunSequentialTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        3,
                        TimeUnit.SECONDS);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
    }

    @Test
    public void testRunMultipleTasks() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(0, executions.get());
        assertEquals(2, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
    }

    @Test
    public void testScheduleNewTasksLater() {
        AtomicInteger executions = new AtomicInteger(0);
        ScheduledFuture<?> future1 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        1,
                        TimeUnit.SECONDS);
        ScheduledFuture<?> future2 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        4,
                        TimeUnit.SECONDS);
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        ScheduledFuture<?> future3 =
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                        },
                        2,
                        TimeUnit.SECONDS);
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertFalse(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(2, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
        assertEquals(3, executions.get());
    }

    @Test
    public void testTaskSchedulesTask() {
        AtomicInteger executions = new AtomicInteger(0);
        List<ScheduledFuture<?>> futures = new ArrayList<>();
        futures.add(
                mExecutor.schedule(
                        () -> {
                            executions.incrementAndGet();
                            futures.add(
                                    mExecutor.schedule(
                                            () -> {
                                                executions.incrementAndGet();
                                            },
                                            2,
                                            TimeUnit.SECONDS));
                        },
                        1,
                        TimeUnit.SECONDS));
        assertEquals(1, futures.size());
        assertFalse(futures.get(0).isDone());
        assertEquals(0, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertFalse(futures.get(1).isDone());
        assertEquals(1, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertFalse(futures.get(1).isDone());
        assertEquals(1, executions.get());
        assertEquals(1, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertTrue(futures.get(1).isDone());
        assertEquals(2, executions.get());
        assertEquals(0, mExecutor.fastForwardMillis(1000));
        assertEquals(2, futures.size());
        assertTrue(futures.get(0).isDone());
        assertTrue(futures.get(1).isDone());
        assertEquals(2, executions.get());
    }
}
