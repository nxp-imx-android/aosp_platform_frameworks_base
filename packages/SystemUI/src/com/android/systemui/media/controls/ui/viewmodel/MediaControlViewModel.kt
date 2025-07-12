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

package com.android.systemui.media.controls.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSession.Token
import android.media.session.PlaybackState
import android.util.Log
import androidx.constraintlayout.widget.ConstraintSet
import com.android.internal.logging.InstanceId
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState
import com.android.systemui.Flags.enableSuggestedDeviceUi
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaControlInteractor
import com.android.systemui.media.controls.shared.model.MediaAction
import com.android.systemui.media.controls.shared.model.MediaButton
import com.android.systemui.media.controls.shared.model.MediaControlModel
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.controller.MediaLocation
import com.android.systemui.media.controls.util.MediaUiEventLogger
import com.android.systemui.res.R
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Models UI state and handles user input for a media control. */
data class MediaControlViewModel(
    @Application private val applicationContext: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Background private val backgroundExecutor: Executor,
    private val interactor: MediaControlInteractor,
    private val logger: MediaUiEventLogger,
    val instanceId: InstanceId,
    val onAdded: (MediaControlViewModel) -> Unit,
    val onRemoved: (Boolean) -> Unit,
    val onUpdated: (MediaControlViewModel) -> Unit,
    val updateTime: Long = 0,
) {
    val player: Flow<MediaPlayerViewModel?> =
        interactor.mediaControl
            .map { mediaControl -> mediaControl?.let { toViewModel(it) } }
            .distinctUntilChanged { old, new ->
                (new == null && old == null) || new?.contentEquals(old) ?: false
            }
            .flowOn(backgroundDispatcher)

    private var isPlaying = false
    private var isAnyButtonClicked = false
    @MediaLocation private var location = MediaHierarchyManager.LOCATION_UNKNOWN
    private var playerViewModel: MediaPlayerViewModel? = null
    private var allowPlayerUpdate: Boolean = false

    fun setPlayer(viewModel: MediaPlayerViewModel): Boolean {
        val tempViewModel = playerViewModel
        playerViewModel = viewModel
        return allowPlayerUpdate || !(tempViewModel?.contentEquals(viewModel) ?: false)
    }

    fun onMediaConfigChanged() {
        allowPlayerUpdate = true
    }

    fun onMediaControlIsBound(artistName: CharSequence, titleName: CharSequence) {
        interactor.logMediaControlIsBound(artistName, titleName)
        allowPlayerUpdate = false
    }

    private fun onDismissMediaData(
        token: Token?,
        uid: Int,
        packageName: String,
        instanceId: InstanceId,
    ) {
        logger.logLongPressDismiss(uid, packageName, instanceId)
        interactor.removeMediaControl(token, instanceId, MEDIA_PLAYER_ANIMATION_DELAY)
    }

    private fun toViewModel(model: MediaControlModel): MediaPlayerViewModel {
        val mediaController = model.token?.let { MediaController(applicationContext, it) }
        val gutsViewModel = toGutsViewModel(model)

        // Set playing state
        val wasPlaying = isPlaying
        isPlaying =
            mediaController?.playbackState?.let { it.state == PlaybackState.STATE_PLAYING } ?: false

        // Resetting button clicks state.
        val wasButtonClicked = isAnyButtonClicked
        isAnyButtonClicked = false

        return MediaPlayerViewModel(
            contentDescription = { gutsVisible ->
                if (gutsVisible) {
                    gutsViewModel.gutsText
                } else {
                    applicationContext.getString(
                        R.string.controls_media_playing_item_description,
                        model.songName,
                        model.artistName,
                        model.appName,
                    )
                }
            },
            backgroundCover = model.artwork,
            appIcon = model.appIcon,
            launcherIcon = getIconFromApp(model.packageName),
            useGrayColorFilter = model.appIcon == null || model.isResume,
            artistName = model.artistName ?: "",
            titleName = model.songName ?: "",
            isExplicitVisible = model.showExplicit,
            canShowTime = canShowScrubbingTimeViews(model.semanticActionButtons),
            playTurbulenceNoise = isPlaying && !wasPlaying && wasButtonClicked,
            useSemanticActions = model.semanticActionButtons != null,
            actionButtons = toActionViewModels(model),
            outputSwitcher = toOutputSwitcherViewModel(model),
            deviceSuggestion = toSuggestionViewModel(model),
            gutsMenu = gutsViewModel,
            onClicked = { expandable ->
                model.clickIntent?.let { clickIntent ->
                    logger.logTapContentView(model.uid, model.packageName, model.instanceId)
                    interactor.startClickIntent(expandable, clickIntent)
                }
            },
            onLongClicked = {
                logger.logLongPressOpen(model.uid, model.packageName, model.instanceId)
            },
            onSeek = { logger.logSeek(model.uid, model.packageName, model.instanceId) },
            onBindSeekbar = { seekBarViewModel ->
                if (model.isResume && model.resumeProgress != null) {
                    seekBarViewModel.updateStaticProgress(model.resumeProgress)
                } else {
                    backgroundExecutor.execute {
                        seekBarViewModel.updateController(mediaController)
                    }
                }
            },
            onLocationChanged = { location = it },
        )
    }

    private fun toOutputSwitcherViewModel(model: MediaControlModel): MediaOutputSwitcherViewModel {
        val device = model.deviceData
        val isDisabled = device?.enabled == false || model.isResume
        val deviceString =
            device?.name ?: applicationContext.getString(R.string.media_seamless_other_device)
        return MediaOutputSwitcherViewModel(
            isTapEnabled = !isDisabled,
            deviceString = deviceString,
            deviceIcon =
                device?.icon?.let { Icon.Loaded(it, null) }
                    ?: Icon.Resource(R.drawable.ic_media_home_devices, null),
            isIntentValid = device?.intent != null,
            alpha =
                if (isDisabled) {
                    DISABLED_ALPHA
                } else {
                    1.0f
                },
            onClicked = { expandable ->
                logger.logOpenOutputSwitcher(model.uid, model.packageName, model.instanceId)
                device?.intent?.let { interactor.startDeviceIntent(it) }
                    ?: interactor.startMediaOutputDialog(expandable, model.packageName, model.token)
            },
        )
    }

    private fun toSuggestionViewModel(model: MediaControlModel): MediaSuggestionViewModel {
        if (!enableSuggestedDeviceUi()) {
            return MediaSuggestionViewModel(isValidSuggestion = false)
        }
        val suggestionData =
            model.suggestionData ?: return MediaSuggestionViewModel(isValidSuggestion = false)
        suggestionData.onSuggestionSpaceVisible.run()
        val suggestedDeviceData =
            suggestionData.suggestedMediaDeviceData
                ?: return MediaSuggestionViewModel(isValidSuggestion = false)
        with(suggestedDeviceData) {
            // Don't show the device as suggested if we're already connected to it
            if (
                !(connectionState == MediaDeviceState.STATE_DISCONNECTED ||
                    connectionState == MediaDeviceState.STATE_CONNECTING ||
                    connectionState == MediaDeviceState.STATE_GROUPING ||
                    connectionState == MediaDeviceState.STATE_CONNECTING_FAILED)
            ) {
                return MediaSuggestionViewModel(isValidSuggestion = false)
            }
            val onClick =
                if (
                    connectionState == MediaDeviceState.STATE_DISCONNECTED ||
                        connectionState == MediaDeviceState.STATE_CONNECTING_FAILED
                )
                    ({ connect() })
                else null
            val buttonText =
                when (connectionState) {
                    MediaDeviceState.STATE_DISCONNECTED,
                    MediaDeviceState.STATE_CONNECTING,
                    MediaDeviceState.STATE_GROUPING ->
                        applicationContext.getString(
                            R.string.media_suggestion_disconnected_text,
                            name,
                        )
                    MediaDeviceState.STATE_CONNECTING_FAILED ->
                        applicationContext.getString(R.string.media_suggestion_failure_text)
                    else -> {
                        Log.wtf(TAG, "Invalid media device state for suggestion: $connectionState")
                        null
                    }
                }
            val isConnecting =
                connectionState == MediaDeviceState.STATE_CONNECTING ||
                    connectionState == MediaDeviceState.STATE_GROUPING
            return MediaSuggestionViewModel(
                isValidSuggestion = true,
                onClicked = onClick,
                buttonText = buttonText,
                isConnecting = isConnecting,
                icon = icon?.asIcon(),
            )
        }
    }

    private fun toGutsViewModel(model: MediaControlModel): GutsViewModel {
        return GutsViewModel(
            gutsText =
                if (model.isDismissible) {
                    applicationContext.getString(
                        R.string.controls_media_close_session,
                        model.appName,
                    )
                } else {
                    applicationContext.getString(R.string.controls_media_active_session)
                },
            isDismissEnabled = model.isDismissible,
            onDismissClicked = {
                onDismissMediaData(model.token, model.uid, model.packageName, model.instanceId)
            },
            cancelTextBackground =
                if (model.isDismissible) {
                    applicationContext.getDrawable(R.drawable.qs_media_outline_button)
                } else {
                    applicationContext.getDrawable(R.drawable.qs_media_solid_button)
                },
            onSettingsClicked = {
                logger.logLongPressSettings(model.uid, model.packageName, model.instanceId)
                interactor.startSettings()
            },
        )
    }

    private fun toActionViewModels(model: MediaControlModel): List<MediaActionViewModel> {
        val semanticActionButtons =
            model.semanticActionButtons?.let { mediaButton ->
                val isScrubbingTimeEnabled = canShowScrubbingTimeViews(mediaButton)
                SEMANTIC_ACTIONS_ALL.map { buttonId ->
                    toSemanticActionViewModel(
                        model,
                        mediaButton.getActionById(buttonId),
                        buttonId,
                        isScrubbingTimeEnabled,
                    )
                }
            }
        val notifActionButtons =
            model.notificationActionButtons.mapIndexed { index, mediaAction ->
                toNotifActionViewModel(model, mediaAction, index)
            }
        return semanticActionButtons ?: notifActionButtons
    }

    private fun toSemanticActionViewModel(
        model: MediaControlModel,
        mediaAction: MediaAction?,
        buttonId: Int,
        canShowScrubbingTimeViews: Boolean,
    ): MediaActionViewModel {
        val showInCollapsed = SEMANTIC_ACTIONS_COMPACT.contains(buttonId)
        val hideWhenScrubbing = SEMANTIC_ACTIONS_HIDE_WHEN_SCRUBBING.contains(buttonId)
        val shouldHideWhenScrubbing = canShowScrubbingTimeViews && hideWhenScrubbing
        return MediaActionViewModel(
            icon = mediaAction?.icon,
            contentDescription = mediaAction?.contentDescription,
            background = mediaAction?.background,
            isVisibleWhenScrubbing = !shouldHideWhenScrubbing,
            notVisibleValue =
                if (
                    !shouldHideWhenScrubbing &&
                        ((buttonId == R.id.actionPrev &&
                            model.semanticActionButtons!!.reservePrev) ||
                            (buttonId == R.id.actionNext &&
                                model.semanticActionButtons!!.reserveNext))
                ) {
                    ConstraintSet.INVISIBLE
                } else {
                    ConstraintSet.GONE
                },
            showInCollapsed = showInCollapsed,
            rebindId = mediaAction?.rebindId,
            buttonId = buttonId,
            isEnabled = mediaAction?.action != null,
            onClicked = { id ->
                mediaAction?.action?.let {
                    onButtonClicked(id, model.uid, model.packageName, model.instanceId, it)
                }
            },
        )
    }

    private fun toNotifActionViewModel(
        model: MediaControlModel,
        mediaAction: MediaAction,
        index: Int,
    ): MediaActionViewModel {
        return MediaActionViewModel(
            icon = mediaAction.icon,
            contentDescription = mediaAction.contentDescription,
            background = mediaAction.background,
            showInCollapsed = model.actionsToShowInCollapsed.contains(index),
            rebindId = mediaAction.rebindId,
            isEnabled = mediaAction.action != null,
            onClicked = { id ->
                mediaAction.action?.let {
                    onButtonClicked(id, model.uid, model.packageName, model.instanceId, it)
                }
            },
        )
    }

    private fun onButtonClicked(
        id: Int,
        uid: Int,
        packageName: String,
        instanceId: InstanceId,
        action: Runnable,
    ) {
        logger.logTapAction(id, uid, packageName, instanceId)
        isAnyButtonClicked = true
        action.run()
    }

    private fun getIconFromApp(packageName: String): Icon {
        return try {
            Icon.Loaded(applicationContext.packageManager.getApplicationIcon(packageName), null)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Cannot find icon for package $packageName", e)
            Icon.Resource(R.drawable.ic_music_note, null)
        }
    }

    private fun canShowScrubbingTimeViews(semanticActions: MediaButton?): Boolean {
        // The scrubbing time views replace the SEMANTIC_ACTIONS_HIDE_WHEN_SCRUBBING action views,
        // so we should only allow scrubbing times to be shown if those action views are present.
        return semanticActions?.let {
            SEMANTIC_ACTIONS_HIDE_WHEN_SCRUBBING.stream().allMatch { id: Int ->
                semanticActions.getActionById(id) != null ||
                    (id == R.id.actionPrev && semanticActions.reservePrev ||
                        id == R.id.actionNext && semanticActions.reserveNext)
            }
        } ?: false
    }

    companion object {
        private const val TAG = "MediaControlViewModel"
        private const val MEDIA_PLAYER_ANIMATION_DELAY = 334L
        private const val DISABLED_ALPHA = 0.38f

        /** Buttons to show in small player when using semantic actions */
        val SEMANTIC_ACTIONS_COMPACT =
            listOf(R.id.actionPlayPause, R.id.actionPrev, R.id.actionNext)

        /**
         * Buttons that should get hidden when we are scrubbing (they will be replaced with the
         * views showing scrubbing time)
         */
        val SEMANTIC_ACTIONS_HIDE_WHEN_SCRUBBING = listOf(R.id.actionPrev, R.id.actionNext)

        /** Buttons to show in player when using semantic actions. */
        val SEMANTIC_ACTIONS_ALL =
            listOf(
                R.id.actionPlayPause,
                R.id.actionPrev,
                R.id.actionNext,
                R.id.action0,
                R.id.action1,
            )

        const val TURBULENCE_NOISE_PLAY_MS_DURATION = 7500L
        const val MEDIA_PLAYER_SCRIM_START_ALPHA = 0.65f
        const val MEDIA_PLAYER_SCRIM_END_ALPHA = 0.75f
    }
}
