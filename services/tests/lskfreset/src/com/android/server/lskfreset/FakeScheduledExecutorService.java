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

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
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
    private final List<FakeScheduledFuture<?>> mFutures = new ArrayList<>();

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
        mElapsedMillis += millis;
        // Make a copy of the futures for us to iterate over, and clear out the existing mFutures.
        // Otherwise if any tasks were to schedule new tasks you would end up modifying the list
        // of futures while we're iterating over it.
        ImmutableList<FakeScheduledFuture<?>> futuresCopy = ImmutableList.copyOf(mFutures);
        mFutures.clear();
        for (FakeScheduledFuture<?> future : futuresCopy) {
            if (future.getDelay(TimeUnit.MILLISECONDS) <= 0) {
                future.getRunnable().run();
            } else {
                mFutures.add(future);
            }
        }
        return futuresCopy.size() - mFutures.size();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        FakeScheduledFuture<?> future = new FakeScheduledFuture<>(command, unit.toMillis(delay));
        mFutures.add(future);
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable runnable) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    private class FakeScheduledFuture<V> implements ScheduledFuture<V> {
        private final Runnable mRunnable;
        private final long mTimeToExecuteAt;
        private boolean mCancelled = false;

        private FakeScheduledFuture(Runnable runnable, long delay) {
            mRunnable = runnable;
            mTimeToExecuteAt = mElapsedMillis + delay;
        }

        private Runnable getRunnable() {
            return mRunnable;
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
            return null;
        }

        @Override
        public V get(long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            return null;
        }
    }
}
