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

package com.android.systemui.statusbar.chips.call.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.view.View
import com.android.internal.jank.Cuj
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.call.domain.interactor.CallChipInteractor
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the ongoing phone call chip shown in the status bar. */
@SysUISingleton
open class CallChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    interactor: CallChipInteractor,
    systemClock: SystemClock,
    private val activityStarter: ActivityStarter,
    @StatusBarChipsLog private val logger: LogBuffer,
) : OngoingActivityChipViewModel {
    private val chipWithReturnAnimation: StateFlow<OngoingActivityChipModel> =
        if (StatusBarChipsReturnAnimations.isEnabled) {
            interactor.ongoingCallState
                .map { state ->
                    when (state) {
                        is OngoingCallModel.NoCall -> OngoingActivityChipModel.Inactive()
                        is OngoingCallModel.InCall ->
                            prepareChip(state, systemClock, isHidden = state.isAppVisible)
                    }
                }
                .stateIn(
                    scope,
                    SharingStarted.WhileSubscribed(),
                    OngoingActivityChipModel.Inactive(),
                )
        } else {
            MutableStateFlow(OngoingActivityChipModel.Inactive()).asStateFlow()
        }

    private val chipLegacy: StateFlow<OngoingActivityChipModel> =
        if (!StatusBarChipsReturnAnimations.isEnabled) {
            interactor.ongoingCallState
                .map { state ->
                    when (state) {
                        is OngoingCallModel.NoCall -> OngoingActivityChipModel.Inactive()
                        is OngoingCallModel.InCall ->
                            if (state.isAppVisible) {
                                OngoingActivityChipModel.Inactive()
                            } else {
                                prepareChip(state, systemClock, isHidden = false)
                            }
                    }
                }
                .stateIn(
                    scope,
                    SharingStarted.WhileSubscribed(),
                    OngoingActivityChipModel.Inactive(),
                )
        } else {
            MutableStateFlow(OngoingActivityChipModel.Inactive()).asStateFlow()
        }

    override val chip: StateFlow<OngoingActivityChipModel> =
        if (StatusBarChipsReturnAnimations.isEnabled) {
            chipWithReturnAnimation
        } else {
            chipLegacy
        }

    /** Builds an [OngoingActivityChipModel.Active] from all the relevant information. */
    private fun prepareChip(
        state: OngoingCallModel.InCall,
        systemClock: SystemClock,
        isHidden: Boolean,
    ): OngoingActivityChipModel.Active {
        val key = state.notificationKey
        val contentDescription = getContentDescription(state.appName)
        val icon =
            if (state.notificationIconView != null) {
                StatusBarConnectedDisplays.assertInLegacyMode()
                OngoingActivityChipModel.ChipIcon.StatusBarView(
                    state.notificationIconView,
                    contentDescription,
                )
            } else if (StatusBarConnectedDisplays.isEnabled) {
                OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                    state.notificationKey,
                    contentDescription,
                )
            } else {
                OngoingActivityChipModel.ChipIcon.SingleColorIcon(phoneIcon)
            }

        val colors = ColorsModel.AccentThemed

        // This block mimics OngoingCallController#updateChip.
        if (state.startTimeMs <= 0L) {
            // If the start time is invalid, don't show a timer and show just an icon.
            // See b/192379214.
            return OngoingActivityChipModel.Active.IconOnly(
                key = key,
                icon = icon,
                colors = colors,
                onClickListenerLegacy = getOnClickListener(state.intent),
                clickBehavior = getClickBehavior(state.intent),
                isHidden = isHidden,
            )
        } else {
            val startTimeInElapsedRealtime =
                state.startTimeMs - systemClock.currentTimeMillis() + systemClock.elapsedRealtime()
            return OngoingActivityChipModel.Active.Timer(
                key = key,
                icon = icon,
                colors = colors,
                startTimeMs = startTimeInElapsedRealtime,
                onClickListenerLegacy = getOnClickListener(state.intent),
                clickBehavior = getClickBehavior(state.intent),
                isHidden = isHidden,
            )
        }
    }

    private fun getOnClickListener(intent: PendingIntent?): View.OnClickListener? {
        if (intent == null) return null
        return View.OnClickListener { view ->
            StatusBarChipsModernization.assertInLegacyMode()
            logger.log(TAG, LogLevel.INFO, {}, { "Chip clicked" })
            val backgroundView =
                view.requireViewById<ChipBackgroundContainer>(R.id.ongoing_activity_chip_background)
            // This mimics OngoingCallController#updateChipClickListener.
            activityStarter.postStartActivityDismissingKeyguard(
                intent,
                ActivityTransitionAnimator.Controller.fromView(
                    backgroundView,
                    Cuj.CUJ_STATUS_BAR_APP_LAUNCH_FROM_CALL_CHIP,
                ),
            )
        }
    }

    private fun getClickBehavior(intent: PendingIntent?): OngoingActivityChipModel.ClickBehavior =
        if (intent == null) {
            OngoingActivityChipModel.ClickBehavior.None
        } else {
            OngoingActivityChipModel.ClickBehavior.ExpandAction(
                onClick = { expandable ->
                    StatusBarChipsModernization.unsafeAssertInNewMode()
                    val animationController =
                        expandable.activityTransitionController(
                            Cuj.CUJ_STATUS_BAR_APP_LAUNCH_FROM_CALL_CHIP
                        )
                    activityStarter.postStartActivityDismissingKeyguard(intent, animationController)
                }
            )
        }

    private fun getContentDescription(appName: String): ContentDescription {
        val ongoingCallDescription = context.getString(R.string.ongoing_call_content_description)
        return ContentDescription.Loaded(
            context.getString(
                R.string.accessibility_desc_notification_icon,
                appName,
                ongoingCallDescription,
            )
        )
    }

    companion object {
        private val phoneIcon =
            Icon.Resource(
                com.android.internal.R.drawable.ic_phone,
                ContentDescription.Resource(R.string.ongoing_call_content_description),
            )
        private val TAG = "CallVM".pad()
    }
}
