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

package com.android.systemui.screencapture.record.smallscreen.player.ui.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class VideoPlayerViewModel
@AssistedInject
constructor(
    @Assisted val uri: Uri,
    @Application private val context: Context,
    @Background private val backgroundContext: CoroutineContext,
) : HydratedActivatable() {

    var player: MediaPlayer? by mutableStateOf(null)
        private set

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("VideoPlayerViewModel#createPlayer") { player = createPlayer() }
        }
    }

    override suspend fun onDeactivated() {
        player?.let {
            player = null
            it.release()
        }
    }

    private suspend fun createPlayer(): MediaPlayer =
        withContext(backgroundContext) {
            MediaPlayer(context).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                setVolume(1.0f)
                seekTo(0)
            }
        }

    @AssistedFactory
    interface Factory {
        fun create(uri: Uri): VideoPlayerViewModel
    }
}
