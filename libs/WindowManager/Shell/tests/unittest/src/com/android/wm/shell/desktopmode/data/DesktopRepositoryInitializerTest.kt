/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode.data

import android.graphics.Rect
import android.graphics.RectF
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE
import com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.android.window.flags.Flags.FLAG_ENABLE_REMEMBERED_BOUNDS
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.data.persistence.Desktop
import com.android.wm.shell.desktopmode.data.persistence.DesktopPersistentRepository
import com.android.wm.shell.desktopmode.data.persistence.DesktopRepositoryState
import com.android.wm.shell.desktopmode.data.persistence.DesktopTask
import com.android.wm.shell.desktopmode.data.persistence.DesktopTaskState
import com.android.wm.shell.desktopmode.data.persistence.DesktopTaskTilingState
import com.android.wm.shell.desktopmode.data.persistence.PackageState
import com.android.wm.shell.desktopmode.data.persistence.PreservedDisplay
import com.android.wm.shell.desktopmode.data.persistence.Rect as RectProto
import com.android.wm.shell.desktopmode.data.persistence.RectF as RectFProto
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class DesktopRepositoryInitializerTest : ShellTestCase() {

    private lateinit var repositoryInitializer: DesktopRepositoryInitializer
    private lateinit var shellInit: ShellInit
    private lateinit var datastoreScope: CoroutineScope
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig

    private lateinit var desktopUserRepositories: DesktopUserRepositories
    private val persistentRepository = mock<DesktopPersistentRepository>()
    private val userManager = mock<UserManager>()
    private val testExecutor = mock<ShellExecutor>()
    private val shellController = mock<ShellController>()
    private val displayController = mock<DisplayController>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
        shellInit = spy(ShellInit(testExecutor))
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        repositoryInitializer =
            DesktopRepositoryInitializerImpl(
                context,
                persistentRepository,
                datastoreScope,
                desktopConfig,
                desktopState,
                displayController,
            )
        desktopUserRepositories =
            DesktopUserRepositories(
                shellInit,
                shellController,
                persistentRepository,
                repositoryInitializer,
                datastoreScope,
                datastoreScope,
                userManager,
                desktopState,
                desktopConfig,
            )
    }

    @Test
    fun init_updatesFlow() =
        runTest(StandardTestDispatcher()) {
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(repositoryInitializer.isInitialized.value).isTrue()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_multipleUsers_addedCorrectly() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(
                    mapOf(
                        USER_ID_1 to desktopRepositoryState1,
                        USER_ID_2 to desktopRepositoryState2,
                    )
                )
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_2))
                .thenReturn(desktopRepositoryState2)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)
            whenever(persistentRepository.readDesktop(USER_ID_2, DESKTOP_ID_3)).thenReturn(desktop3)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(1, 3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4, 5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_2)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_3)
                )
                .containsExactly(7, 8)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_1)
                )
                .containsExactly(1)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_2)
                )
                .containsExactly(5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_2)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_3)
                )
                .containsExactly(7)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_2)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_3)
                )
                .containsExactly(8)
                .inOrder()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_singleUser_addedCorrectly() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(1, 3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4, 5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_1)
                )
                .containsExactly(1)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getExpandedTasksIdsInDeskOrdered(DESKTOP_ID_2)
                )
                .containsExactly(5)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_1)
                )
                .containsExactly(3)
                .inOrder()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getMinimizedTaskIdsInDesk(DESKTOP_ID_2)
                )
                .containsExactly(4)
                .inOrder()
            assertThat(desktopUserRepositories.getProfile(USER_ID_1).getLeftTiledTask(DESKTOP_ID_2))
                .isEqualTo(4)
            assertThat(
                    desktopUserRepositories.getProfile(USER_ID_1).getRightTiledTask(DESKTOP_ID_2)
                )
                .isEqualTo(5)
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_deskRecreationFailed_deskNotAdded() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_2)).thenReturn(desktop2)

            // Make [DESKTOP_ID_2] re-creation fail.
            repositoryInitializer.deskRecreationFactory =
                DesktopRepositoryInitializer.DeskRecreationFactory {
                    userId,
                    destinationDisplayId,
                    deskId ->
                    if (deskId == DESKTOP_ID_2) {
                        null
                    } else {
                        deskId
                    }
                }
            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(desktopUserRepositories.getProfile(USER_ID_1).getDeskIds(DEFAULT_DISPLAY))
                .containsExactly(DESKTOP_ID_1)
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_defaultDisplayDoesNotSupportDesks_deskNotAdded() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState1))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState1)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop1)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(desktopUserRepositories.getProfile(USER_ID_1).getDeskIds(DEFAULT_DISPLAY))
                .isEmpty()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_persistedExternalDisplay_addedCorrectly() =
        runTest(StandardTestDispatcher()) {
            val mockDisplay = mock<Display>()
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState3))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState3)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_4)).thenReturn(desktop4)
            whenever(displayController.getAllDisplaysByUniqueId())
                .thenReturn(mapOf(UNIQUE_DISPLAY_ID to SECOND_DISPLAY_ON_REBOOT))

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_4)
                )
                .containsExactly(7, 8)
                .inOrder()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initDisplayNotFound_defaultDisplayNotSupportDesktop_preservesTransientDesk() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState3))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState3)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_4)).thenReturn(desktop4)
            whenever(displayController.getAllDisplaysByUniqueId())
                .thenReturn(mapOf(UNIQUE_DISPLAY_ID2 to DEFAULT_DISPLAY))
            whenever(displayController.getDisplayIdByUniqueIdBlocking(UNIQUE_DISPLAY_ID))
                .thenReturn(INVALID_DISPLAY)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_4)
                )
                .isEmpty()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .removePreservedDisplay(UNIQUE_DISPLAY_ID)
                        ?.orderedDesks
                        ?.map { desk -> desk.deskId }
                )
                .containsExactly(DESKTOP_ID_4)
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initDisplayNotFound_defaultDisplaySupportsDesktop_preservesNonTransientDesk() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState3))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState3)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_4)).thenReturn(desktop4)
            whenever(displayController.getAllDisplaysByUniqueId())
                .thenReturn(mapOf(UNIQUE_DISPLAY_ID2 to DEFAULT_DISPLAY))
            whenever(displayController.getDisplayIdByUniqueIdBlocking(UNIQUE_DISPLAY_ID))
                .thenReturn(INVALID_DISPLAY)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_4)
                )
                .containsExactly(7, 8)
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .removePreservedDisplay(UNIQUE_DISPLAY_ID)
                        ?.orderedDesks
                        ?.map { desk -> desk.deskId }
                )
                .containsExactly(DESKTOP_ID_4)
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_preservedDisplayPresent_initializesAsDesk() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState4))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState4)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_4)).thenReturn(desktop4)
            whenever(displayController.getAllDisplaysByUniqueId())
                .thenReturn(mapOf(UNIQUE_DISPLAY_ID to SECOND_DISPLAY_ON_REBOOT))
            whenever(displayController.getDisplayIdByUniqueIdBlocking(UNIQUE_DISPLAY_ID))
                .thenReturn(SECOND_DISPLAY_ON_REBOOT)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_4)
                )
                .containsExactly(7, 8)
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .removePreservedDisplay(UNIQUE_DISPLAY_ID)
                )
                .isNull()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE, FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun initWithPersistence_preservedDisplayNotPresent_preservedAgain() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to desktopRepositoryState4))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(desktopRepositoryState4)
            whenever(displayController.getDisplayIdByUniqueIdBlocking(UNIQUE_DISPLAY_ID))
                .thenReturn(INVALID_DISPLAY)

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getActiveTaskIdsInDesk(DESKTOP_ID_4)
                )
                .isEmpty()
            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .removePreservedDisplay(UNIQUE_DISPLAY_ID)
                        ?.orderedDesks
                        ?.map { desk -> desk.deskId }
                )
                .containsExactly(DESKTOP_ID_4)
        }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    fun initWithPersistence_boundsBeforeSnapOrMaximize_areRestored() =
        runTest(StandardTestDispatcher()) {
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true

            // Task 1 with previous bounds
            val taskId1 = 1
            val bounds1 = Rect(10, 20, 110, 120)
            val rectProto1 =
                RectProto.newBuilder()
                    .setLeft(bounds1.left)
                    .setTop(bounds1.top)
                    .setRight(bounds1.right)
                    .setBottom(bounds1.bottom)
                    .build()
            val task1 =
                DesktopTask.newBuilder()
                    .setTaskId(taskId1)
                    .setDesktopTaskState(DesktopTaskState.VISIBLE)
                    .setBoundsBeforeSnapOrMaximize(rectProto1)
                    .build()

            // Task 2 with previous bounds
            val taskId2 = 2
            val bounds2 = Rect(30, 40, 130, 140)
            val rectProto2 =
                RectProto.newBuilder()
                    .setLeft(bounds2.left)
                    .setTop(bounds2.top)
                    .setRight(bounds2.right)
                    .setBottom(bounds2.bottom)
                    .build()
            val task2 =
                DesktopTask.newBuilder()
                    .setTaskId(taskId2)
                    .setDesktopTaskState(DesktopTaskState.VISIBLE)
                    .setBoundsBeforeSnapOrMaximize(rectProto2)
                    .build()

            // Task 3 without previous bounds
            val taskId3 = 3
            val bounds3 = Rect(50, 60, 150, 160)
            val rectProto3 =
                RectProto.newBuilder()
                    .setLeft(bounds3.left)
                    .setTop(bounds3.top)
                    .setRight(bounds3.right)
                    .setBottom(bounds3.bottom)
                    .build()
            val task3 =
                DesktopTask.newBuilder()
                    .setTaskId(taskId3)
                    .setDesktopTaskState(DesktopTaskState.VISIBLE)
                    .setTaskBounds(rectProto3)
                    .build()

            val desktop =
                Desktop.newBuilder()
                    .setDesktopId(DESKTOP_ID_1)
                    .addZOrderedTasks(taskId1)
                    .addZOrderedTasks(taskId2)
                    .addZOrderedTasks(taskId3)
                    .putTasksByTaskId(taskId1, task1)
                    .putTasksByTaskId(taskId2, task2)
                    .putTasksByTaskId(taskId3, task3)
                    .build()

            val repositoryState =
                DesktopRepositoryState.newBuilder().putDesktop(DESKTOP_ID_1, desktop).build()

            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to repositoryState))
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1))
                .thenReturn(repositoryState)
            whenever(persistentRepository.readDesktop(USER_ID_1, DESKTOP_ID_1)).thenReturn(desktop)

            repositoryInitializer.initialize(desktopUserRepositories)

            val repo = desktopUserRepositories.getProfile(USER_ID_1)

            val restoredBounds1 = repo.removeBoundsBeforeSnapOrMaximize(taskId1)
            assertThat(restoredBounds1).isEqualTo(bounds1)

            val restoredBounds2 = repo.removeBoundsBeforeSnapOrMaximize(taskId2)
            assertThat(restoredBounds2).isEqualTo(bounds2)

            // Also verify that a task without persisted previous bounds does not have any restored.
            val restoredBounds3 = repo.removeBoundsBeforeSnapOrMaximize(taskId3)
            assertThat(restoredBounds3).isNull()
        }

    @Test
    @EnableFlags(FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun initWithPersistence_restoresRememberedBoundsRatio() =
        runTest(StandardTestDispatcher()) {
            val packageName = "com.test.app"
            val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)
            val boundsRatioProto =
                RectFProto.newBuilder()
                    .setLeft(boundsRatio.left)
                    .setTop(boundsRatio.top)
                    .setRight(boundsRatio.right)
                    .setBottom(boundsRatio.bottom)
                    .build()
            val packageState =
                PackageState.newBuilder().setRememberedBoundsRatio(boundsRatioProto).build()
            val state =
                DesktopRepositoryState.newBuilder()
                    .putPackageStateByPackageName(packageName, packageState)
                    .build()
            whenever(persistentRepository.getDesktopRepositoryState(USER_ID_1)).thenReturn(state)
            whenever(persistentRepository.getUserDesktopRepositoryMap())
                .thenReturn(mapOf(USER_ID_1 to state))

            repositoryInitializer.initialize(desktopUserRepositories)

            assertThat(
                    desktopUserRepositories
                        .getProfile(USER_ID_1)
                        .getRememberedBoundsRatio(packageName)
                )
                .isEqualTo(boundsRatio)
        }

    @After
    fun tearDown() {
        datastoreScope.cancel()
    }

    private companion object {
        const val USER_ID_1 = 5
        const val USER_ID_2 = 6
        const val DESKTOP_ID_1 = 2
        const val DESKTOP_ID_2 = 3
        const val DESKTOP_ID_3 = 4
        const val DESKTOP_ID_4 = 5
        const val SECOND_DISPLAY = 5
        const val SECOND_DISPLAY_ON_REBOOT = 2
        const val UNIQUE_DISPLAY_ID = "unique_display_id"
        const val UNIQUE_DISPLAY_ID2 = "unique_display_id2"

        val freeformTasksInZOrder1 = listOf(1, 3)
        val desktop1: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_1)
                .addAllZOrderedTasks(freeformTasksInZOrder1)
                .putTasksByTaskId(
                    1,
                    DesktopTask.newBuilder()
                        .setTaskId(1)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .build(),
                )
                .putTasksByTaskId(
                    3,
                    DesktopTask.newBuilder()
                        .setTaskId(3)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .build(),
                )
                .build()

        val freeformTasksInZOrder2 = listOf(4, 5)
        val desktop2: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_2)
                .addAllZOrderedTasks(freeformTasksInZOrder2)
                .putTasksByTaskId(
                    4,
                    DesktopTask.newBuilder()
                        .setTaskId(4)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .setDesktopTaskTilingState(DesktopTaskTilingState.LEFT)
                        .build(),
                )
                .putTasksByTaskId(
                    5,
                    DesktopTask.newBuilder()
                        .setTaskId(5)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .setDesktopTaskTilingState(DesktopTaskTilingState.RIGHT)
                        .build(),
                )
                .build()

        val freeformTasksInZOrder3 = listOf(7, 8)
        val desktop3: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_3)
                .addAllZOrderedTasks(freeformTasksInZOrder3)
                .putTasksByTaskId(
                    7,
                    DesktopTask.newBuilder()
                        .setTaskId(7)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .build(),
                )
                .putTasksByTaskId(
                    8,
                    DesktopTask.newBuilder()
                        .setTaskId(8)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .build(),
                )
                .build()
        val desktop4: Desktop =
            Desktop.newBuilder()
                .setDesktopId(DESKTOP_ID_4)
                .setUniqueDisplayId(UNIQUE_DISPLAY_ID)
                .addAllZOrderedTasks(freeformTasksInZOrder3)
                .putTasksByTaskId(
                    7,
                    DesktopTask.newBuilder()
                        .setTaskId(7)
                        .setDesktopTaskState(DesktopTaskState.VISIBLE)
                        .build(),
                )
                .putTasksByTaskId(
                    8,
                    DesktopTask.newBuilder()
                        .setTaskId(8)
                        .setDesktopTaskState(DesktopTaskState.MINIMIZED)
                        .build(),
                )
                .build()
        val preservedDisplay1 =
            PreservedDisplay.newBuilder()
                .putPreservedDesktop(DESKTOP_ID_4, desktop4)
                .setActiveDeskId(DESKTOP_ID_4)
                .build()
        val desktopRepositoryState1: DesktopRepositoryState =
            DesktopRepositoryState.newBuilder()
                .putDesktop(DESKTOP_ID_1, desktop1)
                .putDesktop(DESKTOP_ID_2, desktop2)
                .build()
        val desktopRepositoryState2: DesktopRepositoryState =
            DesktopRepositoryState.newBuilder().putDesktop(DESKTOP_ID_3, desktop3).build()
        val desktopRepositoryState3: DesktopRepositoryState =
            DesktopRepositoryState.newBuilder().putDesktop(DESKTOP_ID_4, desktop4).build()
        val desktopRepositoryState4: DesktopRepositoryState =
            DesktopRepositoryState.newBuilder()
                .putPreservedDisplayByUniqueId(UNIQUE_DISPLAY_ID, preservedDisplay1)
                .build()
    }
}
