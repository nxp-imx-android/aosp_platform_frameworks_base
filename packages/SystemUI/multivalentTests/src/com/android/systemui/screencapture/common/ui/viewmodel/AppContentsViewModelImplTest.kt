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

package com.android.systemui.screencapture.common.ui.viewmodel

import android.content.ComponentName
import android.media.projection.MediaProjectionAppContent
import android.os.UserHandle
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureAppContentRepository
import com.android.systemui.screencapture.common.data.repository.fakeScreenCaptureRecentTaskRepository
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureAppContentInteractor
import com.android.systemui.screencapture.common.domain.interactor.screenCaptureRecentTaskInteractor
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppContentsViewModelImplTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    @Test
    fun getAppContents_returnsAppContentsFromRepository() =
        kosmos.runTest {
            // Arrange
            val viewModel =
                AppContentsViewModelImpl(
                    appContentInteractor = screenCaptureAppContentInteractor,
                    recentTaskInteractor = screenCaptureRecentTaskInteractor,
                    thumbnailWidthPx = 200,
                    thumbnailHeightPx = 100,
                )
            viewModel.activateIn(testScope)
            val fakeAppContent = MediaProjectionAppContent(createBitmap(200, 100), "TestLabel", 456)

            // Act
            val result = viewModel.targets
            fakeScreenCaptureRecentTaskRepository.setRecentTasks(
                RecentTask(
                    taskId = 1,
                    displayId = 2,
                    userId = 3,
                    topActivityComponent = ComponentName("FakeTopPackage", "FakeTopClass"),
                    baseIntentComponent = ComponentName("FakeBasePackage", "FakeBaseClass"),
                    colorBackground = 0x12345699,
                    isForegroundTask = true,
                    userType = RecentTask.UserType.STANDARD,
                    splitBounds = null,
                )
            )
            fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = "FakeBasePackage",
                user = UserHandle.CURRENT,
                fakeAppContent,
            )

            // Assert
            assertThat(result.value)
                .containsExactly(
                    ScreenCaptureAppContent(
                        packageName = "FakeBasePackage",
                        appContent = fakeAppContent,
                    )
                )
        }
}
