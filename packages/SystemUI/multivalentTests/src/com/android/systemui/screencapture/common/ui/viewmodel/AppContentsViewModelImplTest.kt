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
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
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

    private val Kosmos.viewModel by
        Kosmos.Fixture {
            AppContentsViewModelImpl(
                appContentInteractor = screenCaptureAppContentInteractor,
                recentTaskInteractor = screenCaptureRecentTaskInteractor,
                appContentViewModelFactory = appContentViewModelFactory,
                drawableLoaderViewModel = drawableLoaderViewModel,
                audioSwitchViewModel = audioSwitchViewModel,
                thumbnailWidthPx = 200,
                thumbnailHeightPx = 100,
            )
        }

    private val Kosmos.fakeThumbnail by Kosmos.Fixture { createBitmap(200, 100) }
    private val Kosmos.fakeRecentTask by
        Kosmos.Fixture {
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
        }
    private val Kosmos.fakeMediaProjectionAppContent by
        Kosmos.Fixture { MediaProjectionAppContent(fakeThumbnail, "FakeLabel", 123) }
    private val Kosmos.fakeAppContent by
        Kosmos.Fixture { ScreenCaptureAppContent("FakeBasePackage", fakeMediaProjectionAppContent) }
    private val Kosmos.fakeAppContentViewModel by
        Kosmos.Fixture { appContentViewModelFactory.create(fakeAppContent) }

    @Test
    fun targets_returnsAppContentsFromRepository() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)

            // Act
            val result = viewModel.targets
            fakeScreenCaptureRecentTaskRepository.setRecentTasks(fakeRecentTask)
            fakeScreenCaptureAppContentRepository.setAppContentSuccess(
                packageName = "FakeBasePackage",
                user = UserHandle.CURRENT,
                fakeMediaProjectionAppContent,
            )

            // Assert
            assertThat(result.value).containsExactly(fakeAppContent)
        }

    @Test
    fun selectedTarget_returnsSelectedTarget() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)
            val result by viewModel.selectedTarget
            assertThat(result).isNull()

            // Act
            viewModel.setSelectedTarget(fakeAppContentViewModel)

            // Assert
            assertThat(result).isSameInstanceAs(fakeAppContentViewModel)
        }

    @Test
    fun createViewModelFor_returnsViewModelForRecentTask() =
        kosmos.runTest {
            // Arrange
            viewModel.activateIn(testScope)

            // Act
            val result =
                viewModel.createViewModelFor(fakeAppContent).apply { activateIn(testScope) }

            // Assert
            with(result) {
                assertThat(model).isSameInstanceAs(fakeAppContent)
                assertThat(icon?.isFailure).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail?.getOrNull()?.sameAs(fakeThumbnail)).isTrue()
                assertThat(backgroundColorOpaque).isEqualTo(Color.Black)
            }
        }
}
