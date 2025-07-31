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

package com.android.systemui.statusbar.systemstatusicons.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager

@Composable
fun SystemStatusIconsLegacy(
    statusBarIconController: StatusBarIconController,
    iconContainer: StatusIconContainer,
    iconManager: TintedIconManager,
    useExpandedFormat: Boolean,
    isTransitioning: Boolean,
    foregroundColor: Int,
    backgroundColor: Int,
    isSingleCarrier: Boolean,
    isMicCameraIndicationEnabled: Boolean,
    isPrivacyChipEnabled: Boolean,
    isLocationIndicationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val carrierIconSlots =
        listOf(
            stringResource(id = com.android.internal.R.string.status_bar_mobile),
            stringResource(id = com.android.internal.R.string.status_bar_stacked_mobile),
        )
    val cameraSlot = stringResource(id = com.android.internal.R.string.status_bar_camera)
    val micSlot = stringResource(id = com.android.internal.R.string.status_bar_microphone)
    val locationSlot = stringResource(id = com.android.internal.R.string.status_bar_location)

    AndroidView(
        factory = {
            statusBarIconController.addIconGroup(iconManager)
            iconContainer
        },
        onRelease = { statusBarIconController.removeIconGroup(iconManager) },
        update = { container ->
            container.setQsExpansionTransitioning(isTransitioning)

            if (isSingleCarrier || !useExpandedFormat) {
                container.removeIgnoredSlots(carrierIconSlots)
            } else {
                container.addIgnoredSlots(carrierIconSlots)
            }

            if (isPrivacyChipEnabled) {
                if (isMicCameraIndicationEnabled) {
                    container.addIgnoredSlot(cameraSlot)
                    container.addIgnoredSlot(micSlot)
                } else {
                    container.removeIgnoredSlot(cameraSlot)
                    container.removeIgnoredSlot(micSlot)
                }
                if (isLocationIndicationEnabled) {
                    container.addIgnoredSlot(locationSlot)
                } else {
                    container.removeIgnoredSlot(locationSlot)
                }
            } else {
                container.removeIgnoredSlot(cameraSlot)
                container.removeIgnoredSlot(micSlot)
                container.removeIgnoredSlot(locationSlot)
            }

            iconManager.setTint(foregroundColor, backgroundColor)
        },
        modifier = modifier,
    )
}
