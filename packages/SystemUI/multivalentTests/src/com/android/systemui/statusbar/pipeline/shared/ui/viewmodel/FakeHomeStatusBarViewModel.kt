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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.statusbar.chips.mediaprojection.domain.model.MediaProjectionStopDialogModel
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState.Idle
import com.android.systemui.statusbar.layout.ui.viewmodel.AppHandlesViewModel
import com.android.systemui.statusbar.layout.ui.viewmodel.StatusBarBoundsViewModel
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.ChipsVisibilityModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.SystemInfoCombinedVisibilityModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel
import com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityState
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.statusbar.quickactions.ui.viewmodel.QuickActionChipUiState
import com.android.systemui.statusbar.systemstatusicons.ui.viewmodel.SystemStatusIconsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.Mockito.mock

class FakeHomeStatusBarViewModel(
    override val operatorNameViewModel: StatusBarOperatorNameViewModel
) : HomeStatusBarViewModel, HydratedActivatable() {
    private val hydrator = Hydrator("FakeHomeStatusBarViewModel.hydrator")

    override val areNotificationsLightsOut = MutableStateFlow(false)

    override val isTransitioningFromLockscreenToOccluded = MutableStateFlow(false)

    override val transitionFromLockscreenToDreamStartedEvent = MutableSharedFlow<Unit>()

    override val ongoingActivityChips =
        ChipsVisibilityModel(MultipleOngoingActivityChipsModel(), areChipsAllowed = false)

    override fun onChipBoundsChanged(key: String, bounds: RectF) {}

    override fun onStatusBarTap(eventX: Float) {}

    override fun onStatusBarLongPressed() {}

    override fun onQuickSettingsChipClicked() {}

    override fun onNotificationIconChipClicked() {}

    override fun onShadeExpansionIntent(eventX: Float, statusBarWidth: Int) {}

    override val popupChips = emptyList<QuickActionChipUiState.PopupChip>()

    override val mediaProjectionStopDialogDueToCallEndedState =
        MutableStateFlow(MediaProjectionStopDialogModel.Hidden)

    override val isHomeStatusBarAllowed = MutableStateFlow(false)

    override val canShowOngoingActivityChips: Flow<Boolean> = MutableStateFlow(false)

    override val unifiedBatteryViewModel: BatteryViewModel.BasedOnUserSetting.Factory =
        BatteryViewModel.BasedOnUserSetting.Factory {
            mock(BatteryViewModel.BasedOnUserSetting::class.java)
        }

    override val systemStatusIconsViewModelFactory: SystemStatusIconsViewModel.Factory =
        object : SystemStatusIconsViewModel.Factory {
            override fun create(context: Context): SystemStatusIconsViewModel =
                mock(SystemStatusIconsViewModel::class.java)
        }

    override val statusBarBoundsViewModelFactory: StatusBarBoundsViewModel.Factory =
        object : StatusBarBoundsViewModel.Factory {
            override fun create(
                startSideContainerView: View,
                clockView: Clock,
            ): StatusBarBoundsViewModel = mock(StatusBarBoundsViewModel::class.java)
        }

    override val appHandlesViewModelFactory: AppHandlesViewModel.Factory =
        object : AppHandlesViewModel.Factory {
            override fun create(displayId: Int): AppHandlesViewModel =
                mock(AppHandlesViewModel::class.java)
        }

    private val shouldShowOperatorNameViewSource = MutableStateFlow(false)
    override val shouldShowOperatorNameView: Boolean by
        shouldShowOperatorNameViewSource.hydratedStateOf()

    private val isClockVisibleSource =
        MutableStateFlow(
            VisibilityModel(visibility = VisibilityState.GONE, shouldAnimateChange = false)
        )
    override val isClockVisible by isClockVisibleSource.hydratedStateOf()

    private val isNotificationIconContainerVisibleSource =
        MutableStateFlow(
            VisibilityModel(visibility = VisibilityState.GONE, shouldAnimateChange = false)
        )
    override val isNotificationIconContainerVisible: VisibilityModel by
        isNotificationIconContainerVisibleSource.hydratedStateOf()

    private val systemInfoCombinedVisSource =
        MutableStateFlow(
            SystemInfoCombinedVisibilityModel(
                VisibilityModel(visibility = VisibilityState.GONE, shouldAnimateChange = false),
                Idle,
            )
        )
    override val systemInfoCombinedVis: SystemInfoCombinedVisibilityModel by
        systemInfoCombinedVisSource.hydratedStateOf()

    override val iconBlockList: MutableStateFlow<List<String>> = MutableStateFlow(listOf())

    override val contentArea = MutableStateFlow(Rect(0, 0, 1, 1))

    val darkRegions = mutableListOf<Rect>()

    var darkIconTint = Color.BLACK
    var lightIconTint = Color.WHITE
    var darkIntensity = 0f

    override val areaTint: Flow<StatusBarTintColor> =
        MutableStateFlow(
            StatusBarTintColor { viewBounds ->
                if (DarkIconDispatcher.isInAreas(darkRegions, viewBounds)) {
                    lightIconTint
                } else {
                    darkIconTint
                }
            }
        )

    val isAreaDarkSource =
        MutableStateFlow(
            IsAreaDark { viewBounds ->
                if (DarkIconDispatcher.isInAreas(darkRegions, viewBounds)) {
                    darkIntensity < 0.5f
                } else {
                    false
                }
            }
        )

    override val areaDark: IsAreaDark by
        hydrator.hydratedStateOf(traceName = "areaDark", source = isAreaDarkSource)

    val desktopStatusBarEnabledSource = MutableStateFlow(false)

    override val useDesktopStatusBar: Boolean by
        hydrator.hydratedStateOf(
            traceName = "areaDark",
            source = desktopStatusBarEnabledSource,
            initialValue = false,
        )

    val hasStatusBarNotificationsSource = MutableStateFlow(false)

    override val hasStatusBarNotifications: Boolean by
        hydrator.hydratedStateOf(
            traceName = "hasStatusBarNotifications",
            source = hasStatusBarNotificationsSource,
            initialValue = false,
        )

    val isQuickSettingsChipHighlightedSource = mutableStateOf(false)
    override val isQuickSettingsChipHighlighted: Boolean by isQuickSettingsChipHighlightedSource

    val isNotificationsChipHighlightedSource = mutableStateOf(false)
    override val isNotificationsChipHighlighted: Boolean by isNotificationsChipHighlightedSource

    val isNotificationsChipClickableSource = mutableStateOf(false)
    override val isNotificationsChipClickable: Boolean by isNotificationsChipClickableSource

    val isQuickSettingsChipClickableSource = mutableStateOf(false)
    override val isQuickSettingsChipClickable: Boolean by isQuickSettingsChipClickableSource

    override val isSignOutButtonVisible: Boolean = false

    override fun onSignOut() {}

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }
}
