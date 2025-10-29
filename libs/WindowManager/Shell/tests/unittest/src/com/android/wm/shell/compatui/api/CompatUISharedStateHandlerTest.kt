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

package com.android.wm.shell.compatui.api

import android.app.ActivityManager
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.impl.CompatUIHandlerRule
import kotlin.test.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [CompatUISharedStateHandler].
 *
 * Build/Install/Run: atest WMShellUnitTests:CompatUISharedStateHandlerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUISharedStateHandlerTest : ShellTestCase() {

    @JvmField @Rule val compatUIHandlerRule: CompatUIHandlerRule = CompatUIHandlerRule()

    lateinit var sharedStateRepository: CompatUISharedStateRepository
    lateinit var sharedStateHandler: CompatUISharedStateHandler

    @Before
    fun setUp() {
        sharedStateRepository = CompatUISharedStateRepository()
        sharedStateHandler = CompatUISharedStateHandler(sharedStateRepository)
    }

    @Test
    fun `when onCompatInfoChanged is invoked CompatUISharedStateRepository is updated`() {
        val compatUIInfo = testCompatUIInfo(taskId = 10)
        sharedStateHandler.onCompatInfoChanged(compatUIInfo)
        assertNotNull(sharedStateRepository.find(key = 10))
    }

    private fun testCompatUIInfo(taskId: Int = 1): CompatUIInfo {
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = taskId
        return CompatUIInfo(taskInfo, null)
    }
}
