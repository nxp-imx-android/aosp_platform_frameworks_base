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

package com.android.wm.shell.bubbles;

import android.app.ActivityManager;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.taskview.TaskViewRepository;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.Transitions;

public class BubbleTaskViewTransitions extends TaskViewTransitions {

    public BubbleTaskViewTransitions(Transitions transitions, TaskViewRepository repository,
            ShellTaskOrganizer taskOrganizer, SyncTransactionQueue syncQueue) {
        super(transitions, repository, taskOrganizer, syncQueue);
    }

    @Override
    public void prepareOpenAnimation(TaskViewTaskController taskView, boolean newTask,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction, ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash, WindowContainerTransaction wct) {
        if (!taskInfo.getConfiguration().windowConfiguration.isAlwaysOnTop()) {
            wct.setAlwaysOnTop(taskInfo.token, true /* alwaysOnTop */);
        }
        super.prepareOpenAnimation(taskView, newTask, startTransaction, finishTransaction, taskInfo,
                leash, wct);
    }
}
