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

import android.annotation.NonNull;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskStoreTest implements RemoteTaskStore.Listener {

    private RemoteTaskStore mRemoteTaskStore = new RemoteTaskStore();

    private List<List<RemoteTaskStore.Task>> mListenerEvents;

    @Before
    public void setUp() {
        mListenerEvents = new ArrayList<>();
        mRemoteTaskStore.addListener(this);
    }

    @Test
    public void setTasks_deviceExists_notifiesListener() {
        int associationId = 1;
        RemoteTaskInfo task = new RemoteTaskInfo(1, "label", 0, null, true);
        mRemoteTaskStore.setTasks(associationId, List.of(task));
        verifyListenerEvents(List.of(new RemoteTaskStore.Task(associationId, task)));
    }

    @Test
    public void upsertTask_taskAdded_notifiesListener() {
        int associationId = 1;
        RemoteTaskInfo task = new RemoteTaskInfo(1, "label", 0, null, true);
        mRemoteTaskStore.upsertTask(associationId, task);
        verifyListenerEvents(List.of(new RemoteTaskStore.Task(associationId, task)));
    }

    @Test
    public void upsertTask_taskUpdated_notifiesListener() {
        int associationId = 1;
        RemoteTaskInfo task = new RemoteTaskInfo(1, "label", 0, null, true);
        mRemoteTaskStore.upsertTask(associationId, task);
        RemoteTaskInfo updatedTask = new RemoteTaskInfo(1, "label", 1000, null, true);
        mRemoteTaskStore.upsertTask(associationId, updatedTask);
        verifyListenerEvents(
                List.of(new RemoteTaskStore.Task(associationId, task)),
                List.of(new RemoteTaskStore.Task(associationId, updatedTask)));
    }

    @Test
    public void upsertTask_taskUnchanged_doesNotNotifyListener() {
        int associationId = 1;
        RemoteTaskInfo task = new RemoteTaskInfo(1, "label", 0, null, true);
        mRemoteTaskStore.upsertTask(associationId, task);
        mRemoteTaskStore.upsertTask(associationId, task);
        verifyListenerEvents(List.of(new RemoteTaskStore.Task(associationId, task)));
    }

    @Test
    public void removeTask_deviceDoesNotExist_doesNotNotifyListener() {
        mRemoteTaskStore.removeTask(1, 1);
        verifyListenerEvents();
    }

    @Test
    public void removeTask_taskRemoved_notifiesListener() {
        int associationId = 1;
        RemoteTaskInfo task = new RemoteTaskInfo(1, "label", 0, null, true);
        mRemoteTaskStore.upsertTask(associationId, task);
        mRemoteTaskStore.removeTask(associationId, 1);
        verifyListenerEvents(List.of(new RemoteTaskStore.Task(associationId, task)), List.of());
    }

    @Test
    public void removeAssociation_deviceDoesNotExist_doesNotNotifyListener() {
        mRemoteTaskStore.removeAssociation(1);
        verifyListenerEvents();
    }

    @Test
    public void removeAssociation_deviceRemoved_notifiesListener() {
        int associationId = 1;
        RemoteTaskInfo task = new RemoteTaskInfo(1, "label", 0, null, true);
        mRemoteTaskStore.upsertTask(associationId, task);
        mRemoteTaskStore.removeAssociation(associationId);
        verifyListenerEvents(List.of(new RemoteTaskStore.Task(associationId, task)), List.of());
    }

    @Override
    public void onRemoteTasksChanged(@NonNull Collection<RemoteTaskStore.Task> tasks) {
        mListenerEvents.add(new ArrayList<RemoteTaskStore.Task>(tasks));
    }

    private void verifyListenerEvents(List<RemoteTaskStore.Task>... expectedEvents) {
        assertThat(mListenerEvents).hasSize(expectedEvents.length);
        for (int i = 0; i < expectedEvents.length; i++) {
            assertThat(mListenerEvents.get(i)).containsExactlyElementsIn(expectedEvents[i]);
        }
    }

    private RemoteTaskInfo createRemoteTaskInfo(int id) {
        return new RemoteTaskInfo(id, "", 0, null, true);
    }
}
