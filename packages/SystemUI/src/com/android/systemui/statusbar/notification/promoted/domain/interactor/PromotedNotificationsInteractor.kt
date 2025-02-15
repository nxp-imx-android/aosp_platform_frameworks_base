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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.chips.call.domain.interactor.CallChipInteractor
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * An interactor that provides details about promoted notification precedence, based on the
 * presented order of current notification status bar chips.
 */
@SysUISingleton
class PromotedNotificationsInteractor
@Inject
constructor(
    activeNotificationsInteractor: ActiveNotificationsInteractor,
    callChipInteractor: CallChipInteractor,
    notifChipsInteractor: StatusBarNotificationChipsInteractor,
    @Background backgroundDispatcher: CoroutineDispatcher,
) {
    /**
     * This is the ordered list of notifications (and the promoted content) represented as chips in
     * the status bar.
     */
    private val orderedChipNotifications: Flow<List<NotifAndPromotedContent>> =
        combine(callChipInteractor.ongoingCallState, notifChipsInteractor.allNotificationChips) {
            callState,
            notifChips ->
            buildList {
                val callData = callState.getNotifData()?.also { add(it) }
                addAll(
                    notifChips.mapNotNull {
                        when (it.key) {
                            callData?.key -> null // do not re-add the same call
                            else -> NotifAndPromotedContent(it.key, it.promotedContent)
                        }
                    }
                )
            }
        }

    private fun OngoingCallModel.getNotifData(): NotifAndPromotedContent? =
        when (this) {
            is OngoingCallModel.InCall -> NotifAndPromotedContent(notificationKey, promotedContent)
            is OngoingCallModel.InCallWithVisibleApp ->
                // TODO(b/395989259): support InCallWithVisibleApp when it has notif data
                null
            is OngoingCallModel.NoCall -> null
        }

    /**
     * The top promoted notification represented by a chip, with the order determined by the order
     * of the chips, not the notifications.
     */
    private val topPromotedChipNotification: Flow<PromotedNotificationContentModel?> =
        orderedChipNotifications
            .map { list -> list.firstNotNullOfOrNull { it.promotedContent } }
            .distinctUntilNewInstance()

    /** This is the top-most promoted notification, which should avoid regular changing. */
    val topPromotedNotificationContent: Flow<PromotedNotificationContentModel?> =
        combine(
                topPromotedChipNotification,
                activeNotificationsInteractor.topLevelRepresentativeNotifications,
            ) { topChipNotif, topLevelNotifs ->
                topChipNotif ?: topLevelNotifs.firstNotNullOfOrNull { it.promotedContent }
            }
            // #equals() can be a bit expensive on this object, but this flow will regularly try to
            // emit the same immutable instance over and over, so just prevent that.
            .distinctUntilNewInstance()

    /**
     * This is the ordered list of notifications (and the promoted content) represented as chips in
     * the status bar. Flows on the background context.
     */
    val orderedChipNotificationKeys: Flow<List<String>> =
        orderedChipNotifications
            .map { list -> list.map { it.key } }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)

    /**
     * Returns flow where all subsequent repetitions of the same object instance are filtered out.
     */
    private fun <T> Flow<T>.distinctUntilNewInstance() = distinctUntilChanged { a, b -> a === b }

    /**
     * A custom pair, but providing clearer semantic names, and implementing equality as being the
     * same instance of the promoted content model, which allows us to use distinctUntilChanged() on
     * flows containing this without doing pixel comparisons on the Bitmaps inside Icon objects
     * provided by the Notification.
     */
    private data class NotifAndPromotedContent(
        val key: String,
        val promotedContent: PromotedNotificationContentModel?,
    ) {
        /**
         * Define the equals of this object to only check the reference equality of the promoted
         * content so that we can mark.
         */
        override fun equals(other: Any?): Boolean {
            return when {
                other == null -> false
                other === this -> true
                other !is NotifAndPromotedContent -> return false
                else -> key == other.key && promotedContent === other.promotedContent
            }
        }

        /** Define the hashCode to be very quick, even if it increases collisions. */
        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + (promotedContent?.identity?.hashCode() ?: 0)
            return result
        }
    }
}
