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

package com.android.systemui.media.remedia.data.repository

import android.content.applicationContext
import androidx.appcompat.content.res.AppCompatResources
import com.android.internal.logging.InstanceId
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.MediaButton
import com.android.systemui.media.controls.util.fakeMediaControllerFactory
import com.android.systemui.media.remedia.data.model.MediaDataModel
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.provider.visualStabilityProvider
import com.android.systemui.util.settings.fakeSettings
import com.android.systemui.util.time.systemClock

val Kosmos.mediaRepository by
    Kosmos.Fixture {
        MediaRepositoryImpl(
            applicationContext = applicationContext,
            applicationScope = applicationCoroutineScope,
            backgroundDispatcher = testDispatcher,
            visualStabilityProvider = visualStabilityProvider,
            systemClock = systemClock,
            secureSettings = fakeSettings,
            mediaControllerFactory = fakeMediaControllerFactory,
        )
    }

val Kosmos.fakeMediaRepository by
    Kosmos.Fixture {
        FakeMediaRepository(
            applicationContext = applicationContext,
            applicationScope = applicationCoroutineScope,
            backgroundDispatcher = testDispatcher,
            fakeUserSettings = fakeSettings,
        )
    }

val Kosmos.fakeActiveMedia by
    Kosmos.Fixture {
        MediaDataModel(
            instanceId = InstanceId.fakeInstanceId(1),
            appUid = 1,
            packageName = "com.fake.music.app",
            appName = "Fake_Music_Player",
            appIcon = Icon.Resource(resId = R.drawable.ic_cake, contentDescription = null),
            background = null,
            title = "Fake title",
            subtitle = "Fake subtitle",
            colorScheme = null,
            notificationActions = emptyList(),
            notificationActionsCompressed = listOf(0),
            playbackStateActions =
                MediaButton(
                    playOrPause = mediaPauseActionButton,
                    nextOrCustom = mediaNextActionButton,
                    prevOrCustom = mediaPrevActionButton,
                ),
            outputDevice = null,
            clickIntent = null,
            state = MediaSessionState.Playing,
            durationMs = 60_000,
            positionMs = 20_000,
            canShowSeekbar = true,
            canBeScrubbed = true,
            canBeDismissed = false,
            isActive = true,
            isResume = false,
            resumeAction = null,
            isExplicit = true,
            suggestionData = null,
            token = null,
            needsImmediateRemoval = false,
        )
    }

val Kosmos.fakePausedMediaWithCustomActions by
    Kosmos.Fixture {
        MediaDataModel(
            instanceId = InstanceId.fakeInstanceId(2),
            appUid = 2,
            packageName = "com.fake.audio.app",
            appName = "Fake_Audio_Player",
            appIcon = Icon.Resource(resId = R.drawable.cactus1, contentDescription = null),
            background = null,
            title = "Fake title 1",
            subtitle = "Fake subtitle 1",
            colorScheme = null,
            notificationActions = emptyList(),
            notificationActionsCompressed = listOf(0),
            playbackStateActions =
                MediaButton(
                    playOrPause = mediaPlayActionButton,
                    nextOrCustom = mediaNextActionButton,
                    prevOrCustom = mediaPrevActionButton,
                    custom0 =
                        MediaAction(
                            icon =
                                AppCompatResources.getDrawable(
                                    this.applicationContext,
                                    R.drawable.cloud,
                                ),
                            action = null,
                            contentDescription = "Custom0",
                            background = null,
                            rebindId = 4,
                        ),
                    custom1 =
                        MediaAction(
                            icon =
                                AppCompatResources.getDrawable(
                                    this.applicationContext,
                                    R.drawable.hearing,
                                ),
                            action = null,
                            contentDescription = "Custom1",
                            background = null,
                            rebindId = 5,
                        ),
                ),
            outputDevice = null,
            clickIntent = null,
            state = MediaSessionState.Paused,
            durationMs = 60_000,
            positionMs = 20_000,
            canShowSeekbar = true,
            canBeScrubbed = true,
            canBeDismissed = true,
            isActive = true,
            isResume = false,
            resumeAction = null,
            isExplicit = true,
            suggestionData = null,
            token = null,
            needsImmediateRemoval = false,
        )
    }

val Kosmos.mediaPlayActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_play_button,
                ),
            action = null,
            contentDescription = "Play",
            background =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_play_button_container,
                ),
            rebindId = 1,
        )
val Kosmos.mediaPauseActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_pause_button,
                ),
            action = null,
            contentDescription = "Pause",
            background =
                AppCompatResources.getDrawable(
                    this.applicationContext,
                    R.drawable.ic_media_pause_button_container,
                ),
            rebindId = 1,
        )
val Kosmos.mediaNextActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(this.applicationContext, R.drawable.ic_media_next),
            action = null,
            contentDescription = "Next",
            background = null,
            rebindId = 2,
        )
val Kosmos.mediaPrevActionButton: MediaAction
    get() =
        MediaAction(
            icon =
                AppCompatResources.getDrawable(this.applicationContext, R.drawable.ic_media_prev),
            action = null,
            contentDescription = "Prev",
            background = null,
            rebindId = 3,
        )

fun Kosmos.setFakeCurrentMedia(mediaList: List<MediaDataModel>) {
    fakeMediaRepository.setFakeCurrentMedia(mediaList)
}
