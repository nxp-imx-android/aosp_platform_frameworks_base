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

package com.android.systemui.statusbar.phone.ongoingcall.shared.model

import android.app.PendingIntent
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.shared.CallType
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.data.repository.ongoingCallRepository
import org.mockito.kotlin.mock

/** Helper for building [OngoingCallModel.InCall] instances in tests. */
fun inCallModel(
    startTimeMs: Long,
    notificationIcon: StatusBarIconView? = null,
    intent: PendingIntent? = null,
    notificationKey: String = "test",
    appName: String = "",
    promotedContent: PromotedNotificationContentModel? = null,
) =
    OngoingCallModel.InCall(
        startTimeMs,
        notificationIcon,
        intent,
        notificationKey,
        appName,
        promotedContent,
    )

object OngoingCallTestHelper {
    /**
     * Sets the call state to be no call, and does it correctly based on whether
     * [StatusBarChipsModernization] is enabled or not.
     */
    fun setNoCallState(kosmos: Kosmos) {
        if (StatusBarChipsModernization.isEnabled) {
            // TODO(b/372657935): Maybe don't clear *all* notifications
            kosmos.activeNotificationListRepository.activeNotifications.value =
                ActiveNotificationsStore()
        } else {
            kosmos.ongoingCallRepository.setOngoingCallState(OngoingCallModel.NoCall)
        }
    }

    /**
     * Sets the ongoing call state correctly based on whether [StatusBarChipsModernization] is
     * enabled or not.
     */
    fun setOngoingCallState(
        kosmos: Kosmos,
        startTimeMs: Long = 1000L,
        key: String = "notif",
        statusBarChipIconView: StatusBarIconView? = createStatusBarIconViewOrNull(),
        promotedContent: PromotedNotificationContentModel? = null,
        contentIntent: PendingIntent? = null,
        uid: Int = DEFAULT_UID,
    ) {
        if (StatusBarChipsModernization.isEnabled) {
            kosmos.activeNotificationListRepository.activeNotifications.value =
                ActiveNotificationsStore.Builder()
                    .apply {
                        addIndividualNotif(
                            activeNotificationModel(
                                key = key,
                                whenTime = startTimeMs,
                                callType = CallType.Ongoing,
                                statusBarChipIcon = statusBarChipIconView,
                                contentIntent = contentIntent,
                                promotedContent = promotedContent,
                                uid = uid,
                            )
                        )
                    }
                    .build()
        } else {
            kosmos.ongoingCallRepository.setOngoingCallState(
                inCallModel(
                    startTimeMs = startTimeMs,
                    notificationIcon = statusBarChipIconView,
                    intent = contentIntent,
                    notificationKey = key,
                    promotedContent = promotedContent,
                )
            )
        }
    }

    private fun createStatusBarIconViewOrNull(): StatusBarIconView? =
        if (StatusBarConnectedDisplays.isEnabled) {
            null
        } else {
            mock<StatusBarIconView>()
        }

    private const val DEFAULT_UID = 886
}
