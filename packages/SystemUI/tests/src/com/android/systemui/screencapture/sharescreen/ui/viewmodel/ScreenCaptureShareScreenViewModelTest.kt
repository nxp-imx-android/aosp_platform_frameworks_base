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

package com.android.systemui.screencapture.sharescreen.ui.viewmodel

import android.app.ActivityManager
import android.app.WaitResult
import android.content.ComponentName
import android.content.Intent
import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.app.activityTaskManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.ui.viewmodel.recentTaskViewModelFactory
import com.android.systemui.screencapture.common.ui.viewmodel.recentTasksViewModel
import com.android.systemui.screencapture.sharescreen.domain.interactor.ShareScreenUiInteractor
import com.android.systemui.screencapture.sharescreen.domain.interactor.mockAsyncActivityLauncher
import com.android.systemui.screencapture.sharescreen.domain.interactor.shareScreenUiInteractor
import com.android.systemui.statusbar.featurepods.sharescreen.domain.interactor.shareScreenPrivacyIndicatorInteractor
import com.android.systemui.testKosmosNew
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureShareScreenViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            testableContext.orCreateTestableResources.addOverride(
                R.bool.config_largeScreenPrivacyIndicator,
                true,
            )
        }

    private val viewModel: ScreenCaptureShareScreenViewModel by lazy {
        kosmos.screenCaptureShareScreenViewModel
    }

    @Before
    fun setUp() {
        viewModel.activateIn(kosmos.testScope)

        // Setup the interactor for all tests.
        kosmos.shareScreenUiInteractor.initialize(
            mediaProjection = mock(),
            reviewGrantedConsentRequired = false,
            hostUserHandle = mock(),
        )
    }

    @Test
    fun initialState() =
        kosmos.runTest {
            // Assert that the initial values are as expected upon creation and activation.
            assertThat(viewModel.selectedScreenCaptureTarget)
                .isEqualTo(ScreenCaptureTarget.AppContent(contentId = 0))

            assertThat(viewModel.isUiVisible).isTrue()
        }

    @Test
    fun onShareClicked_hidesUiAndShowsChipAndCallsInteractor() =
        kosmos.runTest {
            val isChipVisible by
                collectLastValue(kosmos.shareScreenPrivacyIndicatorInteractor.isChipVisible)
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            assertThat(isChipVisible).isFalse()
            val target = ScreenCaptureTarget.App(displayId = 1, taskId = 42)
            val fakeRecentTask =
                RecentTask(
                    taskId = target.taskId,
                    displayId = target.displayId,
                    userId = 3,
                    topActivityComponent = ComponentName("FakeTopPackage", "FakeTopClass"),
                    baseIntentComponent = ComponentName("FakeBasePackage", "FakeBaseClass"),
                    colorBackground = 0x12345699,
                    isForegroundTask = true,
                    userType = RecentTask.UserType.STANDARD,
                    splitBounds = null,
                )
            val fakeRecentTaskViewModel =
                kosmos.recentTaskViewModelFactory.create(ScreenCaptureRecentTask(fakeRecentTask))

            // Setup the interactor's dependencies for this specific test.
            whenever(kosmos.activityTaskManager.getTasks(any())).thenAnswer {
                listOf(
                    ActivityManager.RunningTaskInfo().apply {
                        taskId = target.taskId
                        baseIntent = Intent()
                    }
                )
            }
            whenever(
                    kosmos.mockAsyncActivityLauncher.startActivityAsUser(any(), any(), any(), any())
                )
                .thenAnswer {
                    // Immediately invoke the callback to simulate activity start.
                    it.getArgument<(WaitResult) -> Unit>(3).invoke(WaitResult())
                    true
                }

            kosmos.recentTasksViewModel.setSelectedTarget(fakeRecentTaskViewModel)
            viewModel.selectedScreenCaptureTarget = target

            viewModel.onShareClicked()

            // Verify UI is hidden
            assertThat(viewModel.isUiVisible).isFalse()
            // Verify chip is shown
            assertThat(isChipVisible).isTrue()
            // Verify the sharing state is updated to [Approved].
            assertThat(sharingState).isEqualTo(ShareScreenUiInteractor.SharingState.Approved)
        }

    @Test
    fun onCloseClicked_callsInteractor() =
        kosmos.runTest {
            val sharingState by collectLastValue(kosmos.shareScreenUiInteractor.sharingState)
            viewModel.onCloseClicked()
            assertThat(sharingState).isEqualTo(ShareScreenUiInteractor.SharingState.Denied)
        }
}
