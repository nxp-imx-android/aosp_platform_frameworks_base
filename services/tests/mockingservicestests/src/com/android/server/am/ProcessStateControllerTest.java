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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.app.IServiceConnection;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.TestLooperManager;
import android.platform.test.annotations.Presubmit;

import com.android.server.wm.ActivityServiceConnectionsHolder;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

@Presubmit
public class ProcessStateControllerTest {

    private Handler mManagedHandler;
    private TestLooperManager mTestLooperManager;

    private static ProcessStateController createProcessStateController() {
        final ActivityManagerService ams = mock(ActivityManagerService.class);
        ams.mAppProfiler = mock(AppProfiler.class);
        ams.mConstants = mock(ActivityManagerConstants.class);
        final ActiveUids au = new ActiveUids(null);
        return new ProcessStateController.Builder(ams, ams.mProcessList, au).build();
    }

    private static ConnectionRecord createConnectionRecord(long flags) {
        final ProcessRecord pr = mock(ProcessRecord.class);
        final ServiceRecord sr = mock(ServiceRecord.class);
        final IntentBindRecord ibr = mock(IntentBindRecord.class);
        final AppBindRecord abr = new AppBindRecord(sr, ibr, pr, pr);
        return new ConnectionRecord(abr, mock(ActivityServiceConnectionsHolder.class),
                mock(IServiceConnection.class), flags,
                0, null, 0, null, null, null);

    }

    @Before
    public void setup() {
        HandlerThread t = new HandlerThread("ManagedThread");
        t.start();
        mManagedHandler = new Handler(t.getLooper());
        mTestLooperManager = new TestLooperManager(t.getLooper());
    }

    @Test
    public void bindAllowFreezeReturnsBoundServiceSession() {
        final ProcessStateController psc = createProcessStateController();
        final ConnectionRecord cr = createConnectionRecord(Context.BIND_ALLOW_FREEZE);
        assertThat(psc.getBoundServiceSessionFor(cr)).isNotNull();
    }

    @Test
    public void bindSimulateAllowFreezeReturnsBoundServiceSession() {
        final ProcessStateController psc = createProcessStateController();
        final ConnectionRecord cr = createConnectionRecord(Context.BIND_SIMULATE_ALLOW_FREEZE);
        assertThat(psc.getBoundServiceSessionFor(cr)).isNotNull();
    }

    @Test
    public void noAllowFreezeReturnsNullBoundServiceSession() {
        final ProcessStateController psc = createProcessStateController();
        final ConnectionRecord cr = createConnectionRecord(0);
        assertThat(psc.getBoundServiceSessionFor(cr)).isNull();
    }

    @Test
    public void asyncBatchSession_enqueue() {
        ArrayList<String> list = new ArrayList<>();
        ProcessStateController.AsyncBatchSession session =
                new ProcessStateController.AsyncBatchSession(mManagedHandler, new Object(), null,
                        () -> list.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.enqueue(() -> list.add("A"));
        mManagedHandler.post(() -> list.add("X"));
        session.runUpdate();
        session.enqueue(() -> list.add("B"));

        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("A");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("A", "X");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("A", "X", "UPDATED");
        // Step through the looper one last time.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("A", "X", "UPDATED", "B");
    }

    @Test
    public void asyncBatchSession_enqueue_batched() {
        ArrayList<String> list = new ArrayList<>();
        ProcessStateController.AsyncBatchSession session =
                new ProcessStateController.AsyncBatchSession(mManagedHandler, new Object(), null,
                        () -> list.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.start();
        session.enqueue(() -> list.add("A"));
        mManagedHandler.post(() -> list.add("X"));
        session.runUpdate();
        session.enqueue(() -> list.add("B"));
        session.close();

        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("X");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list.get(0)).isEqualTo("X");
        assertThat(list.get(1)).isEqualTo("A");
        assertThat(list.get(2)).isEqualTo("B");
        assertThat(list.get(3)).isEqualTo("UPDATED");
    }

    @Test
    public void asyncBatchSession_enqueueNoUpdate_batched() {
        ArrayList<String> list = new ArrayList<>();
        ProcessStateController.AsyncBatchSession session =
                new ProcessStateController.AsyncBatchSession(mManagedHandler, new Object(), null,
                        () -> list.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.start();
        session.enqueue(() -> list.add("A"));
        mManagedHandler.post(() -> list.add("X"));
        session.enqueue(() -> list.add("B"));
        session.close();

        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("X");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("X", "A", "B");
    }

    @Test
    public void asyncBatchSession_enqueueBoostPriority_batched() {
        ArrayList<String> list = new ArrayList<>();
        ProcessStateController.AsyncBatchSession session =
                new ProcessStateController.AsyncBatchSession(mManagedHandler, new Object(), null,
                        () -> list.add("UPDATED"));

        // Enqueue some work , while batching is active and boost the priority of the session.
        session.start();
        session.enqueue(() -> list.add("A"));
        mManagedHandler.post(() -> list.add("X"));
        session.enqueue(() -> list.add("B"));
        session.postToHead();
        session.close();

        assertThat(list).isEmpty();
        // Step through the looper once.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("A", "B");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("A", "B", "X");
    }

    @Test
    public void asyncBatchSession_interlacedEnqueueAndStage() {
        ArrayList<String> list = new ArrayList<>();
        ConcurrentLinkedQueue<Runnable> stagingQueue = new ConcurrentLinkedQueue<>();
        ProcessStateController.AsyncBatchSession session =
                new ProcessStateController.AsyncBatchSession(mManagedHandler, new Object(),
                        stagingQueue, () -> list.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.stage(() -> list.add("1"));
        session.enqueue(() -> list.add("A"));
        session.runUpdate();
        session.stage(() -> list.add("2"));

        // Run the first staged runnable.
        stagingQueue.poll().run();
        assertThat(list).containsExactly("1");
        // Step through the looper one
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("1", "A");
        // Run the second staged runnable.
        stagingQueue.poll().run();
        assertThat(list).containsExactly("1", "A", "2");
        // Step through the looper once more.
        mTestLooperManager.execute(mTestLooperManager.next());
        assertThat(list).containsExactly("1", "A", "2", "UPDATED");
    }

    @Test
    public void asyncBatchSession_interlacedEnqueueAndStage_batched() {
        ArrayList<String> list = new ArrayList<>();
        ConcurrentLinkedQueue<Runnable> stagingQueue = new ConcurrentLinkedQueue<>();
        ProcessStateController.AsyncBatchSession session =
                new ProcessStateController.AsyncBatchSession(mManagedHandler, new Object(),
                        stagingQueue, () -> list.add("UPDATED"));

        // Enqueue some work and trigger an update mid way, while batching is active.
        session.start();
        session.stage(() -> list.add("1"));
        session.enqueue(() -> list.add("A"));
        session.stage(() -> list.add("2"));
        session.runUpdate();
        session.enqueue(() -> list.add("B"));
        session.stage(() -> list.add("3"));
        session.stage(() -> list.add("4"));
        session.close();

        // Run the first staged runnable.
        stagingQueue.poll().run();
        // Run the second staged runnable.
        stagingQueue.poll().run();
        // Run the third staged runnable.
        stagingQueue.poll().run();
        // Step through the looper once to run all batched enqueued work.
        mTestLooperManager.execute(mTestLooperManager.next());
        // Run the last staged runnable.
        stagingQueue.poll().run();

        assertThat(list.get(0)).isEqualTo("1");
        assertThat(list.get(1)).isEqualTo("2");
        assertThat(list.get(2)).isEqualTo("3");
        assertThat(list.get(3)).isEqualTo("A");
        assertThat(list.get(4)).isEqualTo("B");
        assertThat(list.get(5)).isEqualTo("UPDATED");
        assertThat(list.get(6)).isEqualTo("4");
    }
}
