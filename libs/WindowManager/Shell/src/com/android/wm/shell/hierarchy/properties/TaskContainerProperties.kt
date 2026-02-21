/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.wm.shell.hierarchy.properties

import android.app.ActivityManager.RunningTaskInfo
import android.window.TransitionInfo
import com.android.wm.shell.common.ComponentUtils
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty

/**
 * Properties for a container that is associated with a task in the WindowManager hierarchy.
 */
class TaskContainerProperties(
    @WmSyncedProperty var taskInfo: RunningTaskInfo,
) : ContainerProperties(taskInfo.userId) {
    // Convenience property
    val taskId: Int
        get() = taskInfo.taskId

    /** @see ContainerProperties.updateFromWindowChange */
    override fun updateFromWindowChange(change: TransitionInfo.Change) {
        super.updateFromWindowChange(change)
        if (change.taskInfo == null) {
            return
        }
        taskInfo = change.taskInfo!!
        config.windowConfiguration.windowingMode = change.taskInfo!!.windowingMode
    }

    /** @see ContainerProperties.copy */
    override fun copy(): TaskContainerProperties {
        return TaskContainerProperties(RunningTaskInfo(taskInfo)).apply {
            copyFrom(this@TaskContainerProperties)
        }
    }

    /** @see ContainerProperties.propsToString */
    override fun propsToString(): String {
        return "#$taskId pkg=${ComponentUtils.getPackageName(taskInfo)} " + super.propsToString()
    }

    /** @see ContainerProperties.getTypeName */
    override fun getTypeName(): String {
        return "Task#$taskId"
    }
}