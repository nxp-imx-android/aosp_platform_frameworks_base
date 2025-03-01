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

package com.android.systemui.media.remedia.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.media.remedia.domain.interactor.MediaInteractor
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.res.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.roundToLong
import kotlinx.coroutines.awaitCancellation

/** Models UI state for a media element. */
class MediaViewModel
@AssistedInject
constructor(
    private val interactor: MediaInteractor,
    @Assisted private val context: Context,
    @Assisted private val carouselVisibility: MediaCarouselVisibility,
) : ExclusiveActivatable() {

    /** Whether the user is actively moving the thumb of the seek bar. */
    private var isScrubbing: Boolean by mutableStateOf(false)
    /** The position of the thumb of the seek bar as the user is scrubbing it. */
    private var seekProgress: Float by mutableFloatStateOf(0f)
    /** Whether the internal "guts" are visible. */
    private var isGutsVisible: Boolean by mutableStateOf(false)
    /** The index of the currently-selected card. */
    private var selectedCardIndex: Int by mutableIntStateOf(0)
        private set

    /** The current list of cards to show in the UI. */
    val cards: List<MediaCardViewModel> by derivedStateOf {
        interactor.sessions.mapIndexed { sessionIndex, session ->
            val isCurrentSessionAndScrubbing = isScrubbing && sessionIndex == selectedCardIndex
            object : MediaCardViewModel {
                override val key = session.key
                override val icon = session.appIcon
                override val artLoader = suspend { interactor.loadArt(session.key).asImageBitmap() }
                override val title = session.title
                override val subtitle = session.subtitle
                override val actionButtonLayout = session.actionButtonLayout
                override val playPauseAction =
                    session.playPauseAction.toPlayPauseActionViewModel(session.state)
                override val additionalActions: List<MediaSecondaryActionViewModel>
                    get() {
                        return session.additionalActions.map { action ->
                            action.toSecondaryActionViewModel()
                        }
                    }

                override val navigation: MediaNavigationViewModel
                    get() {
                        return if (session.canBeScrubbed) {
                            MediaNavigationViewModel.Showing(
                                progress =
                                    if (!isCurrentSessionAndScrubbing) {
                                        session.positionMs.toFloat() / session.durationMs
                                    } else {
                                        seekProgress
                                    },
                                left = session.leftAction.toSecondaryActionViewModel(),
                                right = session.rightAction.toSecondaryActionViewModel(),
                                isSquiggly =
                                    session.state != MediaSessionState.Paused &&
                                        !isCurrentSessionAndScrubbing,
                                isScrubbing = isCurrentSessionAndScrubbing,
                                onScrubChange = { progress ->
                                    check(selectedCardIndex == sessionIndex) {
                                        "Can't seek on a card that's not the selected card!"
                                    }
                                    isScrubbing = true
                                    seekProgress = progress
                                },
                                onScrubFinished = {
                                    interactor.seek(
                                        sessionKey = session.key,
                                        to = (seekProgress * session.durationMs).roundToLong(),
                                    )
                                    isScrubbing = false
                                },
                            )
                        } else {
                            MediaNavigationViewModel.Hidden
                        }
                    }

                override val guts: MediaCardGutsViewModel
                    get() {
                        return MediaCardGutsViewModel(
                            isVisible = isGutsVisible,
                            text =
                                if (session.canBeHidden) {
                                    context.getString(
                                        R.string.controls_media_close_session,
                                        session.appName,
                                    )
                                } else {
                                    context.getString(R.string.controls_media_active_session)
                                },
                            primaryAction =
                                if (session.canBeHidden) {
                                    MediaGutsButtonViewModel(
                                        text =
                                            context.getString(
                                                R.string.controls_media_dismiss_button
                                            ),
                                        onClick = {
                                            interactor.hide(session.key)
                                            isGutsVisible = false
                                        },
                                    )
                                } else {
                                    MediaGutsButtonViewModel(
                                        text = context.getString(R.string.cancel),
                                        onClick = { isGutsVisible = false },
                                    )
                                },
                            secondaryAction =
                                MediaGutsButtonViewModel(
                                        text = context.getString(R.string.cancel),
                                        onClick = { isGutsVisible = false },
                                    )
                                    .takeIf { session.canBeHidden },
                            settingsButton =
                                MediaGutsSettingsButtonViewModel(
                                    icon =
                                        Icon.Resource(
                                            res = R.drawable.ic_settings,
                                            contentDescription =
                                                ContentDescription.Resource(
                                                    res = R.string.controls_media_settings_button
                                                ),
                                        ),
                                    onClick = { interactor.openMediaSettings() },
                                ),
                            onLongClick = { isGutsVisible = false },
                        )
                    }

                override val outputSwitcherChips: List<MediaOutputSwitcherChipViewModel>
                    get() {
                        return listOf(
                            MediaOutputSwitcherChipViewModel(
                                icon = session.outputDevice.icon,
                                text = session.outputDevice.name,
                                onClick = {
                                    // TODO(b/397989775): tell the UI to show the output switcher.
                                },
                            )
                        )
                    }

                override val outputSwitcherChipButton: MediaSecondaryActionViewModel.Action
                    get() {
                        return MediaSecondaryActionViewModel.Action(
                            icon = session.outputDevice.icon,
                            onClick = {
                                // TODO(b/397989775): tell the UI to show the output switcher.
                            },
                        )
                    }

                override val onClick = session.onClick
                override val onClickLabel =
                    context.getString(R.string.controls_media_playing_item_description)
                override val onLongClick = { isGutsVisible = true }
            }
        }
    }

    /** Whether the carousel should be visible. */
    val isCarouselVisible: Boolean
        get() =
            when (carouselVisibility) {
                MediaCarouselVisibility.WhenNotEmpty -> interactor.sessions.isNotEmpty()

                MediaCarouselVisibility.WhenAnyCardIsActive ->
                    interactor.sessions.any { session -> session.isActive }
            }

    /** Notifies that the card at [cardIndex] has been selected in the UI. */
    fun onCardSelected(cardIndex: Int) {
        check(cardIndex >= 0 && cardIndex < cards.size)
        selectedCardIndex = cardIndex
    }

    override suspend fun onActivated(): Nothing {
        awaitCancellation()
    }

    private fun MediaActionModel.toPlayPauseActionViewModel(
        mediaSessionState: MediaSessionState
    ): MediaPlayPauseActionViewModel? {
        return when (this) {
            is MediaActionModel.Action ->
                MediaPlayPauseActionViewModel(
                    state = mediaSessionState,
                    icon = icon,
                    onClick = onClick ?: {},
                )
            is MediaActionModel.None,
            is MediaActionModel.ReserveSpace -> null
        }
    }

    private fun MediaActionModel.toSecondaryActionViewModel(): MediaSecondaryActionViewModel {
        return when (this) {
            is MediaActionModel.Action ->
                MediaSecondaryActionViewModel.Action(icon = icon, onClick = onClick)
            is MediaActionModel.ReserveSpace -> MediaSecondaryActionViewModel.ReserveSpace
            is MediaActionModel.None -> MediaSecondaryActionViewModel.None
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context, carouselVisibility: MediaCarouselVisibility): MediaViewModel
    }
}
