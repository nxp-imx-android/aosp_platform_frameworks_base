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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.compose.theme.colorAttr
import com.android.settingslib.Utils
import com.android.systemui.clock.ui.composable.ClockLegacy
import com.android.systemui.clock.ui.viewmodel.AmPmStyle
import com.android.systemui.clock.ui.viewmodel.ClockViewModel
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.res.R
import com.android.systemui.shade.ui.composable.ShadeHighlightChip
import com.android.systemui.shade.ui.composable.VariableDayDate
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChips
import com.android.systemui.statusbar.featurepods.popups.StatusBarPopupChips
import com.android.systemui.statusbar.featurepods.popups.ui.compose.StatusBarPopupChipsContainer
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.pipeline.battery.ui.composable.UnifiedBattery
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModel
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIcons
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIconsLegacy

object DesktopStatusBar {
    object Dimensions {
        val ElementSpacing = 8.dp
        val ChipInternalSpacing = 6.dp
    }
}

// TODO(b/343358983): Add support for color themes in this composable.
/** Top level composable responsible for all UI shown for the Status Bar for DesktopMode. */
@Composable
fun DesktopStatusBar(
    viewModel: HomeStatusBarViewModel,
    clockViewModelFactory: ClockViewModel.Factory,
    statusBarIconController: StatusBarIconController,
    iconManagerFactory: TintedIconManager.Factory,
    mediaHierarchyManager: MediaHierarchyManager,
    mediaHost: MediaHost,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
) {
    // TODO(433589833): Update padding values to match UX specs.
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp)) {
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(DesktopStatusBar.Dimensions.ElementSpacing, Alignment.Start),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            ClockLegacy(textColor = Color.White, onClick = null)

            val clockViewModel =
                rememberViewModel("HomeStatusBar.Clock") {
                    clockViewModelFactory.create(AmPmStyle.Gone)
                }
            VariableDayDate(
                longerDateText = clockViewModel.longerDateText,
                shorterDateText = clockViewModel.shorterDateText,
                textColor = colorAttr(R.attr.wallpaperTextColor),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(DesktopStatusBar.Dimensions.ElementSpacing, Alignment.End)
        ) {
            val chipsVisibilityModel = viewModel.ongoingActivityChips
            if (chipsVisibilityModel.areChipsAllowed) {
                OngoingActivityChips(
                    chips = chipsVisibilityModel.chips,
                    iconViewStore = iconViewStore,
                    onChipBoundsChanged = viewModel::onChipBoundsChanged,
                    modifier = Modifier.sysUiResTagContainer(),
                )
            }

            if (StatusBarPopupChips.isEnabled) {
                StatusBarPopupChipsContainer(
                    chips = viewModel.popupChips,
                    mediaHost = mediaHost,
                    onMediaControlPopupVisibilityChanged = { popupShowing ->
                        mediaHierarchyManager.isMediaControlPopupShowing = popupShowing
                    },
                )
            }

            NotificationsChip(viewModel = viewModel)

            QuickSettingsChip(
                viewModel = viewModel,
                statusBarIconController = statusBarIconController,
                iconManagerFactory = iconManagerFactory,
            )
        }
    }
}

@Composable
private fun NotificationsChip(viewModel: HomeStatusBarViewModel) {
    ShadeHighlightChip(
        onClick = { viewModel.onNotificationIconChipClicked() },
        // TODO(433589833): Add support for ChipHighlight when Notifications Panel is visible.
        backgroundColor = Color.Transparent,
        horizontalArrangement =
            Arrangement.spacedBy(DesktopStatusBar.Dimensions.ChipInternalSpacing, Alignment.Start),
    ) {
        // TODO(433589833): Add new icon resources for the notification chip icon.
        Icon(
            icon = Icon.Resource(res = R.drawable.ic_volume_ringer, contentDescription = null),
            tint = Color.White,
        )
    }
}

@Composable
private fun QuickSettingsChip(
    viewModel: HomeStatusBarViewModel,
    statusBarIconController: StatusBarIconController,
    iconManagerFactory: TintedIconManager.Factory,
    modifier: Modifier = Modifier,
) {

    ShadeHighlightChip(
        onClick = { viewModel.onQuickSettingsChipClicked() },
        // TODO(433589833): Add support for ChipHighlight when QS Panel is visible.
        backgroundColor = Color.Transparent,
        horizontalArrangement =
            Arrangement.spacedBy(DesktopStatusBar.Dimensions.ChipInternalSpacing, Alignment.Start),
    ) {
        if (SystemStatusIconsInCompose.isEnabled) {
            SystemStatusIcons(
                viewModelFactory = viewModel.systemStatusIconsViewModelFactory,
                tint = Color.White,
                modifier = modifier,
            )
        } else {
            val localContext = LocalContext.current
            val themedContext = ContextThemeWrapper(localContext, R.style.Theme_SystemUI)
            val foregroundColor =
                Utils.getColorAttrDefaultColor(themedContext, android.R.attr.textColorPrimary)
            val backgroundColor =
                Utils.getColorAttrDefaultColor(
                    themedContext,
                    android.R.attr.textColorPrimaryInverse,
                )

            val iconContainer =
                remember(localContext, iconManagerFactory) {
                    StatusIconContainer(
                        ContextThemeWrapper(localContext, R.style.Theme_SystemUI),
                        null,
                    )
                }
            val iconManager =
                remember(iconContainer) {
                    iconManagerFactory.create(iconContainer, StatusBarLocation.HOME)
                }

            SystemStatusIconsLegacy(
                statusBarIconController = statusBarIconController,
                iconContainer = iconContainer,
                iconManager = iconManager,
                useExpandedFormat = true,
                foregroundColor = foregroundColor,
                backgroundColor = backgroundColor,
                isSingleCarrier = true,
                isMicCameraIndicationEnabled = true,
                isPrivacyChipEnabled = true,
                isTransitioning = false,
                isLocationIndicationEnabled = true,
            )
        }

        val batteryHeight =
            with(LocalDensity.current) {
                BatteryViewModel.getStatusBarBatteryHeight(LocalContext.current).toDp()
            }
        UnifiedBattery(
            viewModel =
                rememberViewModel("DesktopStatusBar.BatteryViewModel") {
                    viewModel.unifiedBatteryViewModel.create()
                },
            isDarkProvider = { viewModel.areaDark },
            modifier = Modifier.height(batteryHeight),
        )
    }
}
