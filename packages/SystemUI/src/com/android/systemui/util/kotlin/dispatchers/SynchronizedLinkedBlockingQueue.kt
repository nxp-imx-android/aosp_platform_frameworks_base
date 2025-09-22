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

package com.android.systemui.util.kotlin.dispatchers

import java.io.Closeable
import java.util.AbstractQueue
import java.util.ArrayDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A custom implementation of a blocking queue that uses separate locks for
 * producers and consumers, and a dedicated, non-blocking notifier thread.
 * This is designed to make producer operations (like on the main thread)
 * extremely fast and free of contention with consumer threads.
 */
class SynchronizedLinkedBlockingQueue : AbstractQueue<Runnable>(), BlockingQueue<Runnable>, Closeable {

    private val queue = ArrayDeque<Runnable>()

    /** The lock for all producer operations (`offer`, `put`). */
    private val putLock = Object()

    /** The lock for all consumer operations (`take`, `poll`). */
    private val takeLock = Object()

    /** The current size of the queue. */
    private val count = AtomicInteger(0)

    /**
     * A dedicated thread to send notifications, so the producer thread
     * (e.g., main thread) never has to acquire the consumer's `takeLock`.
     */
    private val notifierExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "IntrinsicQueueNotifier").also { it.isDaemon = true }
    }

    /**
     * A gate to ensure we only have one notification task pending at a time.
     * This "batches" notifications.
     */
    private val notificationPending = AtomicBoolean(false)

    private val signalConsumersRunnable = Runnable {
        // We are on the notifier thread.
        // Open the gate so new offers can schedule a notification.
        notificationPending.set(false)

        // Acquire the lock and notify all waiting consumers.
        synchronized(takeLock) {
            takeLock.notifyAll()
        }
    }

    override val size: Int
        get() = count.get()

    override fun offer(e: Runnable): Boolean {
        synchronized(putLock) {
            queue.addLast(e)
            count.incrementAndGet()
        }

        // If a notification isn't already pending, schedule one.
        // This CAS is very fast and non-blocking.
        if (notificationPending.compareAndSet(false, true)) {
            notifierExecutor.execute(signalConsumersRunnable)
        }
        return true
    }

    override fun offer(e: Runnable, timeout: Long, unit: TimeUnit): Boolean {
        return offer(e)
    }

    override fun put(e: Runnable) {
        offer(e)
    }

    override fun take(): Runnable {
        try {
            synchronized(takeLock) {
                while (count.get() == 0) {
                    takeLock.wait()
                }
                val ret = queue.removeFirst()
                count.decrementAndGet()
                return ret
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    override fun poll(timeout: Long, unit: TimeUnit): Runnable? {
        var nanos = unit.toNanos(timeout)
        val deadline = System.nanoTime() + nanos

        try {
            synchronized(takeLock) {
                while (count.get() == 0) {
                    if (nanos <= 0) {
                        return null
                    }
                    val millis = nanos / 1_000_000
                    val nanosPart = (nanos % 1_000_000).toInt()
                    takeLock.wait(millis, nanosPart)
                    nanos = deadline - System.nanoTime()
                }

                val item = queue.removeFirst()
                count.decrementAndGet()
                return item
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    override fun poll(): Runnable? {
        synchronized(takeLock) {
            val ret = queue.pollFirst()
            if (ret != null) {
                count.decrementAndGet()
            }
            return ret
        }
    }

    override fun peek(): Runnable? {
        synchronized(takeLock) {
            return queue.peekFirst()
        }
    }

    /**
     * Shuts down the internal notifier thread.
     */
    override fun close() {
        notifierExecutor.shutdown()
    }


    // Not required by ThreadPoolExecutor
    override fun iterator(): MutableIterator<Runnable> {
        throw UnsupportedOperationException("iterator() not supported by SynchronizedLinkedBlockingQueue")
    }

    // Not required by ThreadPoolExecutor
    override fun drainTo(c: MutableCollection<in Runnable>): Int {
        throw UnsupportedOperationException("drainTo() not supported by SynchronizedLinkedBlockingQueue")
    }

    // Not required by ThreadPoolExecutor
    override fun drainTo(c: MutableCollection<in Runnable>, maxElements: Int): Int {
        throw UnsupportedOperationException("drainTo() not supported by SynchronizedLinkedBlockingQueue")
    }

    override fun remainingCapacity(): Int {
        return Int.MAX_VALUE // Unbounded
    }
}
