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

package com.android.server.companion.datatransfer.continuity.tasks;

import static com.google.common.truth.Truth.assertThat;

import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskListenerHolderTest {

    private final static class FakeRemoteTaskListener extends IRemoteTaskListener.Stub {
        List<List<RemoteTask>> remoteTasksReportedToListener = new ArrayList<>();

        @Override
        public void onRemoteTasksChanged(List<RemoteTask> remoteTasks) {
            remoteTasksReportedToListener.add(remoteTasks);
        }

        private void verifyListenerEvents(List<RemoteTask>... expectedEvents) {
            assertThat(remoteTasksReportedToListener).hasSize(expectedEvents.length);
            for (int i = 0; i < expectedEvents.length; i++) {
                assertThat(remoteTasksReportedToListener.get(i))
                        .containsExactlyElementsIn(expectedEvents[i]);
            }
        }
    }

    private RemoteTaskListenerHolder mRemoteTaskListenerHolder = new RemoteTaskListenerHolder();

    @Test
    public void addListener_notifiesWithEmptyListIfNoTasks() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener);
        listener.verifyListenerEvents(List.of());
    }

    @Test
    public void addListener_notifiesWithLastBroadcastedTasks() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1));
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.notifyListeners(tasks);
        mRemoteTaskListenerHolder.addListener(listener);
        listener.verifyListenerEvents(tasks);
    }

    @Test
    public void notifyListeners_notifiesAllListeners() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1));
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.notifyListeners(tasks);
        listener1.verifyListenerEvents(List.of(), tasks);
        listener2.verifyListenerEvents(List.of(), tasks);
    }

    @Test
    public void removeListener_doesNotNotifyRemovedListener() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1));
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.removeListener(listener1);
        mRemoteTaskListenerHolder.notifyListeners(tasks);
        listener1.verifyListenerEvents(List.of());
        listener2.verifyListenerEvents(List.of(), List.of(createRemoteTask(1)));
    }

    private RemoteTask createRemoteTask(int id) {
        return new RemoteTask.Builder(id).setDeviceId(100).build();
    }
}
