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

package com.android.systemui.statusbar.notification.collection

import android.app.NotificationChannel
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.internal.R

data class BundleSpec(
    val key: String,
    @StringRes val titleTextResId: Int,
    @DrawableRes val icon: Int,
) {
    companion object {
        val PROMOTIONS =
            BundleSpec(
                key = NotificationChannel.PROMOTIONS_ID,
                titleTextResId = R.string.promotional_notification_channel_label,
                icon = com.android.settingslib.R.drawable.ic_promotions,
            )
        val SOCIAL_MEDIA =
            BundleSpec(
                key = NotificationChannel.SOCIAL_MEDIA_ID,
                titleTextResId = R.string.social_notification_channel_label,
                icon = com.android.settingslib.R.drawable.ic_social,
            )
        val NEWS =
            BundleSpec(
                key = NotificationChannel.NEWS_ID,
                titleTextResId = R.string.news_notification_channel_label,
                icon = com.android.settingslib.R.drawable.ic_news,
            )
        val RECOMMENDED =
            BundleSpec(
                key = NotificationChannel.RECS_ID,
                titleTextResId = R.string.recs_notification_channel_label,
                icon = com.android.settingslib.R.drawable.ic_recs,
            )
        val DEBUG =
            BundleSpec(
                key = "debug_bundle",
                titleTextResId = R.string.notification_channel_developer,
                icon = com.android.systemui.res.R.drawable.ic_person,
            )
    }
}
