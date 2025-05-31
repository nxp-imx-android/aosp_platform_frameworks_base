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

package com.android.systemui.ambientcue.data.repository

import android.app.ActivityManager.RunningTaskInfo
import android.app.PendingIntent
import android.app.assist.ActivityId
import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.app.smartspace.SmartspaceTarget
import android.content.Intent
import android.content.testableContext
import android.os.Binder
import android.os.Bundle
import android.view.WindowManagerPolicyConstants
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.LauncherProxyService
import com.android.systemui.LauncherProxyService.LauncherProxyListener
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.AmbientCueRepositoryImpl.Companion.AMBIENT_CUE_SURFACE
import com.android.systemui.ambientcue.data.repository.AmbientCueRepositoryImpl.Companion.DEBOUNCE_DELAY_MS
import com.android.systemui.ambientcue.data.repository.AmbientCueRepositoryImpl.Companion.EXTRA_ACTIVITY_ID
import com.android.systemui.ambientcue.data.repository.AmbientCueRepositoryImpl.Companion.EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT
import com.android.systemui.ambientcue.data.repository.AmbientCueRepositoryImpl.Companion.EXTRA_AUTOFILL_ID
import com.android.systemui.ambientcue.shared.model.ActionModel
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.advanceUntilIdle
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.navigationbar.NavigationModeController.ModeChangedListener
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.update
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val smartSpaceSession = mock<SmartspaceSession>()
    private val autofillManager = mock<AutofillManager>()
    private val activityStarter = mock<ActivityStarter>()
    private val launcherProxyService = mock<LauncherProxyService>()
    private val navigationModeController = mock<NavigationModeController>()
    private val smartSpaceManager =
        mock<SmartspaceManager>() {
            on { createSmartspaceSession(any()) } doReturn smartSpaceSession
        }
    val onTargetsAvailableListenerCaptor = argumentCaptor<OnTargetsAvailableListener>()
    val navigationModeChangeListenerCaptor = argumentCaptor<ModeChangedListener>()
    val launcherProxyListenerCaptor = argumentCaptor<LauncherProxyListener>()
    private val underTest =
        AmbientCueRepositoryImpl(
            backgroundScope = kosmos.backgroundScope,
            smartSpaceManager = smartSpaceManager,
            autofillManager = autofillManager,
            activityStarter = activityStarter,
            launcherProxyService = launcherProxyService,
            navigationModeController = navigationModeController,
            executor = kosmos.fakeExecutor,
            applicationContext = kosmos.testableContext,
            focusdDisplayRepository = kosmos.fakeFocusedDisplayRepository,
        )

    @Test
    fun isRootViewAttached_whenHasActionsAndNotDeactivatedAndTaskIdMatch_true() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val isRootViewAttached by collectLastValue(underTest.isRootViewAttached)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())

            fakeFocusedDisplayRepository.setGlobalTask(RunningTaskInfo().apply { taskId = TASK_ID })
            advanceTimeBy(DEBOUNCE_DELAY_MS)
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(autofillTarget))
            advanceUntilIdle()

            assertThat(isRootViewAttached).isTrue()
        }

    @Test
    fun isRootViewAttached_whenNoActions_false() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val isRootViewAttached by collectLastValue(underTest.isRootViewAttached)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())

            fakeFocusedDisplayRepository.setGlobalTask(RunningTaskInfo().apply { taskId = TASK_ID })
            advanceTimeBy(DEBOUNCE_DELAY_MS)
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(invalidTarget1))
            advanceUntilIdle()

            assertThat(isRootViewAttached).isFalse()
        }

    @Test
    fun isRootViewAttached_deactivated_false() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val isRootViewAttached by collectLastValue(underTest.isRootViewAttached)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            fakeFocusedDisplayRepository.setGlobalTask(RunningTaskInfo().apply { taskId = TASK_ID })
            advanceTimeBy(DEBOUNCE_DELAY_MS)
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(autofillTarget))
            advanceUntilIdle()

            runCurrent()
            underTest.isDeactivated.update { true }
            runCurrent()

            assertThat(isRootViewAttached).isFalse()
        }

    @Test
    fun isRootViewAttached_taskIdNotMatch_false() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val isRootViewAttached by collectLastValue(underTest.isRootViewAttached)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(autofillTarget))
            advanceUntilIdle()

            fakeFocusedDisplayRepository.setGlobalTask(
                RunningTaskInfo().apply { taskId = TASK_ID_2 }
            )
            advanceTimeBy(DEBOUNCE_DELAY_MS)

            assertThat(isRootViewAttached).isFalse()
        }

    @Test
    fun actions_whenHasSmartSpaceAction() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(allTargets)
            runCurrent()

            actions.let {
                requireNotNull(it)
                assertThat(it.size).isEqualTo(2)

                val firstAction = it.first()
                assertThat(firstAction.label).isEqualTo(TITLE_1)
                assertThat(firstAction.attribution).isEqualTo(SUBTITLE_1)

                val lastAction = it.last()
                assertThat(lastAction.label).isEqualTo(TITLE_2)
                assertThat(lastAction.attribution).isEqualTo(SUBTITLE_2)
            }
        }

    @Test
    fun globallyFocusedTaskId_whenFocusedTaskChange_taskIdUpdated() =
        kosmos.runTest {
            val globallyFocusedTaskId by collectLastValue(underTest.globallyFocusedTaskId)

            fakeFocusedDisplayRepository.setGlobalTask(RunningTaskInfo().apply { taskId = TASK_ID })
            advanceTimeBy(DEBOUNCE_DELAY_MS)

            assertThat(globallyFocusedTaskId).isEqualTo(TASK_ID)
        }

    @Test
    fun action_performAutofill() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(autofillTarget))

            val action: ActionModel = actions!!.first()
            action.onPerformAction()
            runCurrent()
            verify(autofillManager)
                .autofillRemoteApp(autofillId, action.label, activityId.token!!, activityId.taskId)
        }

    @Test
    fun action_performStartActivity() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(intentTarget))

            val action: ActionModel = actions!!.first()
            action.onPerformAction()
            runCurrent()
            verify(activityStarter).startActivity(launchIntent, false)
        }

    @Test
    fun action_performPendingIntent() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(
                listOf(pendingIntentTarget)
            )

            val action: ActionModel = actions!!.first()
            action.onPerformAction()
            runCurrent()
            verify(pendingIntent).send(any<Bundle>())
        }

    @Test
    fun action_performLongClick_pendingIntentSent() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(
                listOf(attributionDialogPendingIntentTarget)
            )

            val action: ActionModel = actions!!.first()
            action.onPerformLongClick()
            runCurrent()
            verify(attributionDialogPendingIntent).send(any<Bundle>())
        }

    @Test
    fun targetTaskId_updatedWithAction() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val targetTaskId by collectLastValue(underTest.targetTaskId)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(listOf(autofillTarget))

            runCurrent()
            assertThat(targetTaskId).isEqualTo(TASK_ID)
        }

    fun isGestureNav_propagatesFromNavigationModeController() =
        kosmos.runTest {
            val isGestureNav by collectLastValue(underTest.isGestureNav)
            runCurrent()
            verify(navigationModeController)
                .addListener(navigationModeChangeListenerCaptor.capture())

            navigationModeChangeListenerCaptor.firstValue.onNavigationModeChanged(
                WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL
            )
            assertThat(isGestureNav).isTrue()

            navigationModeChangeListenerCaptor.firstValue.onNavigationModeChanged(
                WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON
            )
            assertThat(isGestureNav).isFalse()
        }

    @Test
    fun isTaskBarVisible_propagatesFromLauncherProxyService() =
        kosmos.runTest {
            val isTaskBarVisible by collectLastValue(underTest.isTaskBarVisible)
            runCurrent()
            verify(launcherProxyService).addCallback(launcherProxyListenerCaptor.capture())

            launcherProxyListenerCaptor.firstValue.onTaskbarStatusUpdated(false, false)
            runCurrent()
            assertThat(isTaskBarVisible).isFalse()

            launcherProxyListenerCaptor.firstValue.onTaskbarStatusUpdated(true, false)
            runCurrent()
            assertThat(isTaskBarVisible).isTrue()

            launcherProxyListenerCaptor.firstValue.onTaskbarStatusUpdated(true, true)
            runCurrent()
            assertThat(isTaskBarVisible).isFalse()
        }

    companion object {

        private const val TITLE_1 = "title 1"
        private const val TITLE_2 = "title 2"
        private const val SUBTITLE_1 = "subtitle 1"
        private const val SUBTITLE_2 = "subtitle 2"
        private const val TASK_ID = 1
        private const val TASK_ID_2 = 2
        private val validTarget =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn AMBIENT_CUE_SURFACE
                on { actionChips } doReturn
                    listOf(
                        SmartspaceAction.Builder("action1-id", "title 1")
                            .setSubtitle("subtitle 1")
                            .build(),
                        SmartspaceAction.Builder("action2-id", "title 2")
                            .setSubtitle("subtitle 2")
                            .build(),
                    )
            }

        private val autofillId = AutofillId(2)
        private val activityId = ActivityId(TASK_ID, Binder())
        private val autofillTarget =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn AMBIENT_CUE_SURFACE
                on { actionChips } doReturn
                    listOf(
                        SmartspaceAction.Builder("action1-id", "title 1")
                            .setSubtitle("subtitle 1")
                            .setExtras(
                                Bundle().apply {
                                    putParcelable(EXTRA_ACTIVITY_ID, activityId)
                                    putParcelable(EXTRA_AUTOFILL_ID, autofillId)
                                }
                            )
                            .build()
                    )
            }

        private val launchIntent = Intent()
        private val intentTarget =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn AMBIENT_CUE_SURFACE
                on { actionChips } doReturn
                    listOf(
                        SmartspaceAction.Builder("action1-id", "title 1")
                            .setSubtitle("subtitle 1")
                            .setIntent(launchIntent)
                            .build()
                    )
            }

        private val pendingIntent = mock<PendingIntent>()
        private val pendingIntentTarget =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn AMBIENT_CUE_SURFACE
                on { actionChips } doReturn
                    listOf(
                        SmartspaceAction.Builder("action1-id", "title 1")
                            .setSubtitle("subtitle 1")
                            .setPendingIntent(pendingIntent)
                            .build()
                    )
            }

        private val attributionDialogPendingIntent = mock<PendingIntent>()
        private val attributionDialogPendingIntentTarget =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn AMBIENT_CUE_SURFACE
                on { actionChips } doReturn
                    listOf(
                        SmartspaceAction.Builder("action1-id", "title 1")
                            .setSubtitle("subtitle 1")
                            .setExtras(
                                Bundle().apply {
                                    putParcelable(
                                        EXTRA_ATTRIBUTION_DIALOG_PENDING_INTENT,
                                        attributionDialogPendingIntent,
                                    )
                                }
                            )
                            .build()
                    )
            }

        private val invalidTarget1 =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn "home"
                on { actionChips } doReturn
                    listOf(SmartspaceAction.Builder("id", "title").setSubtitle("subtitle").build())
            }

        private val allTargets = listOf(validTarget, invalidTarget1)
    }
}
