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

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fake implementation of ScheduledExecutorService for use in testing. Implementation only covers
 * the subset of features that are relevant to the LSKF reset services. All commands will be run
 * synchronously when time is advanced.
 */
class FakeScheduledExecutorService implements ScheduledExecutorService {
    private long mElapsedMillis = 0;
    private final DelayQueue<FakeScheduledFuture<?>> mFutures = new DelayQueue<>();

    // The thread that can run fastForwardMillis.
    private final Thread mExecutionThread;

    /** Assert that the currently executing thread is the execution thread. */
    private void assertExecutionThread() {
        Preconditions.checkState(Thread.currentThread() == mExecutionThread);
    }

    /** Assert that the currently executing thread is not the execution thread. */
    private void assertNotExecutionThread() {
        Preconditions.checkState(Thread.currentThread() != mExecutionThread);
    }

    /**
     * Construct a new fake implementation of ScheduledExecutorService.
     *
     * @param executionThread The thread that is allowed to call fastForwardMillis. It is an error
     *     to fast forward time from any other thread. It is also an error to call any operation
     *     that would wait/block on the execution of a task from this thread, as that would lead to
     *     a deadlock.
     */
    FakeScheduledExecutorService(Thread executionThread) {
        mExecutionThread = executionThread;
    }

    /**
     * Reports the number of currently queued tasks.
     *
     * @return The number of tasks currently awaiting execution.
     */
    int numTasks() {
        return mFutures.size();
    }

    /**
     * Advances the scheduler and executes any tasks whose delay has expired.
     *
     * @param millis The number of milliseconds to advance the time forward.
     * @return The number of tasks that were executed.
     */
    int fastForwardMillis(long millis) {
        assertExecutionThread();
        mElapsedMillis += millis;
        List<FakeScheduledFuture<?>> readyFutures = new ArrayList<>();
        mFutures.drainTo(readyFutures);
        for (FakeScheduledFuture<?> future : readyFutures) {
            future.execute();
        }
        return readyFutures.size();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        FakeScheduledFuture<?> future = new FakeScheduledFuture<>(command, unit.toMillis(delay));
        mFutures.put(future);
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        FakeScheduledFuture<V> future = new FakeScheduledFuture<>(callable, unit.toMillis(delay));
        mFutures.put(future);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return schedule(
                () -> {
                    task.run();
                    return result;
                },
                0,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws ExecutionException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.MILLISECONDS);
    }

    private class FakeScheduledFuture<V> implements ScheduledFuture<V> {
        // The underlying object to be called when the scheduled time arrives. Exactly one of these
        // will be non-null.
        private final Runnable mRunnable;
        private final Callable<V> mCallable;

        // The result of the callable, if this future is for a callable. After execution if the
        // callable threw an exception then mResultException will be non-null; otherwise it returned
        // mResult. Note that callables can return null and so mResult==null does not necessarily
        // indicate that the callable was not executed.
        private V mResult = null;
        private ExecutionException mResultException = null;

        private final long mTimeToExecuteAt;
        private final CountDownLatch mCompletionLatch = new CountDownLatch(1);
        private boolean mCancelled = false;

        private FakeScheduledFuture(Runnable runnable, long delay) {
            mRunnable = runnable;
            mCallable = null;
            mTimeToExecuteAt = mElapsedMillis + delay;
        }

        private FakeScheduledFuture(Callable<V> callable, long delay) {
            mRunnable = null;
            mCallable = callable;
            mTimeToExecuteAt = mElapsedMillis + delay;
        }

        private void execute() {
            try {
                if (mRunnable != null) {
                    mRunnable.run();
                } else {
                    try {
                        mResult = mCallable.call();
                    } catch (Exception e) {
                        mResultException = new ExecutionException(e);
                    }
                }
            } finally {
                mCompletionLatch.countDown();
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delayInMs = mTimeToExecuteAt - mElapsedMillis;
            return unit.convert(delayInMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long lhs = getDelay(TimeUnit.NANOSECONDS);
            long rhs = o.getDelay(TimeUnit.NANOSECONDS);
            return Long.compare(lhs, rhs);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            mCancelled = true;
            return mFutures.remove(this);
        }

        @Override
        public boolean isCancelled() {
            return mCancelled;
        }

        @Override
        public boolean isDone() {
            return !mFutures.contains(this);
        }

        @Override
        public V get() throws ExecutionException, InterruptedException {
            assertNotExecutionThread();
            mCompletionLatch.await();
            if (mResultException != null) {
                throw mResultException;
            } else {
                return mResult;
            }
        }

        @Override
        public V get(long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            throw new UnsupportedOperationException();
        }
    }
}
