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

package com.android.systemui.scene.ui.composable.transitions

import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.notifications.ui.composable.NotificationsShade

fun TransitionBuilder.goneToShadeOverlayTransition() {
    sharedElement(Notifications.Elements.HeadsUpNotificationPlaceholder)

    // HUN over Gone source Y is above target Y in Panel
    // so we start translating Panel at [the distance that HUN will move down] above target Y
    anchoredTranslate(
        NotificationsShade.Elements.Panel,
        Notifications.Elements.HeadsUpNotificationPlaceholder)

    // Start scaling Panel height up from 60% to allow enough space for expanded HUN height
    scaleSize(NotificationsShade.Elements.Panel, height = 0.6f)
}
