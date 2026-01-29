/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app

import android.content.Context
import android.content.applicationContext
import android.graphics.Bitmap
import android.graphics.Canvas
import com.android.internal.R as internalR
import com.android.launcher3.util.UserIconInfo
import com.android.systemui.kosmos.Kosmos

@UserIconInfo.UserType
var Kosmos.fakeUserType: Int by Kosmos.Fixture { UserIconInfo.TYPE_MAIN }

var Kosmos.userProfileBadgeProvider: Notification.UserProfileBadgeProvider? by
    Kosmos.Fixture { null }

fun Kosmos.setupFakeUserProfileBadgeProvider() {
    userProfileBadgeProvider = FakeUserProfileBadgeProvider(applicationContext, fakeUserType)
}

class FakeUserProfileBadgeProvider(
    private val sysuiContext: Context,
    @UserIconInfo.UserType private val userType: Int,
) : Notification.UserProfileBadgeProvider {
    override fun getProfileBadge(context: Context): Bitmap? {
        val badgeDrawableRes =
            when (userType) {
                UserIconInfo.TYPE_MAIN -> return null
                UserIconInfo.TYPE_WORK -> internalR.drawable.ic_corp_badge
                UserIconInfo.TYPE_CLONED -> internalR.drawable.ic_clone_badge
                UserIconInfo.TYPE_PRIVATE -> internalR.drawable.ic_private_profile_badge
                else -> error("Unsupported user type $userType")
            }
        val badgeDrawable =
            sysuiContext.getDrawable(badgeDrawableRes)
                ?: error("Null drawable for user type $userType")
        val tintColorRes =
            when (userType) {
                UserIconInfo.TYPE_WORK -> internalR.color.semanticBlueOnSurfaceVariant
                else -> internalR.color.materialColorOnSurface
            }
        val tintColor = sysuiContext.getColor(tintColorRes)
        badgeDrawable.setTint(tintColor)
        val size =
            sysuiContext
                .getResources()
                .getDimensionPixelSize(internalR.dimen.notification_2025_badge_size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        badgeDrawable.setBounds(0, 0, size, size)
        badgeDrawable.draw(canvas)
        return bitmap
    }

    override fun getProfileAccessibilityString(context: Context): String? =
        when (userType) {
            UserIconInfo.TYPE_WORK -> "Work Profile"
            UserIconInfo.TYPE_CLONED -> "Cloned Profile"
            UserIconInfo.TYPE_PRIVATE -> "Private Space"
            else -> "Unknown Profile"
        }
}
