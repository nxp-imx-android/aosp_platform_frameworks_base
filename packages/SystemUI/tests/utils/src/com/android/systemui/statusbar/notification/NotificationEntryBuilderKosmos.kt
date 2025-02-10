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

package com.android.systemui.statusbar.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.content.applicationContext
import android.graphics.drawable.Icon
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.promoted.setPromotedContent
import org.mockito.kotlin.mock

fun Kosmos.setIconPackWithMockIconViews(entry: NotificationEntry) {
    entry.icons =
        IconPack.buildPack(
            /* statusBarIcon = */ mock(),
            /* statusBarChipIcon = */ mock(),
            /* shelfIcon = */ mock(),
            /* aodIcon = */ mock(),
            /* source = */ null,
        )
}

fun Kosmos.buildOngoingCallEntry(
    promoted: Boolean = false,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry =
    buildNotificationEntry(
        tag = "call",
        promoted = promoted,
        style = makeOngoingCallStyle(),
        block = block,
    )

fun Kosmos.buildPromotedOngoingEntry(
    block: NotificationEntryBuilder.() -> Unit = {}
): NotificationEntry =
    buildNotificationEntry(tag = "ron", promoted = true, style = null, block = block)

fun Kosmos.buildNotificationEntry(
    tag: String? = null,
    promoted: Boolean = false,
    style: Notification.Style? = null,
    block: NotificationEntryBuilder.() -> Unit = {},
): NotificationEntry =
    NotificationEntryBuilder()
        .apply {
            setTag(tag)
            setFlag(applicationContext, Notification.FLAG_PROMOTED_ONGOING, promoted)
            modifyNotification(applicationContext)
                .setSmallIcon(Icon.createWithContentUri("content://null"))
                .setStyle(style)
        }
        .apply(block)
        .build()
        .also {
            setIconPackWithMockIconViews(it)
            if (promoted) setPromotedContent(it)
        }

private fun Kosmos.makeOngoingCallStyle(): Notification.CallStyle {
    val pendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent("action"),
            PendingIntent.FLAG_IMMUTABLE,
        )
    val person = Person.Builder().setName("person").build()
    return Notification.CallStyle.forOngoingCall(person, pendingIntent)
}
