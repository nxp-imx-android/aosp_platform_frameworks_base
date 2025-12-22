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

package com.android.systemui.statusbar.notification.row.icon

import android.content.Context
import android.content.applicationContext
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toDrawable
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.util.time.fakeSystemClock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

val Kosmos.mockAppIconProvider by
    Kosmos.Fixture {
        mock<AppIconProvider> {
            on { getOrFetchAppIcon(any(), any(), any()) } doReturn Color.RED.toDrawable()
            on { getOrFetchSkeletonAppIcon(any(), any()) } doReturn Color.BLUE.toDrawable()
        }
    }

public data class FakeAppIcons(
    @DrawableRes val colorAppIconRes: Int,
    @DrawableRes val skeletonAppIconRes: Int,
)

/** Sets up an [AppIconProvider] that returns the given fake app icons for all packages. */
fun Kosmos.setupFakeAppIcons(context: Context, fakeAppIcons: FakeAppIcons) {
    appIconProvider =
        object : AppIconProvider {
            override fun getOrFetchAppIcon(
                packageName: String,
                userHandle: UserHandle,
                instanceKey: String,
            ): Drawable = context.getDrawable(fakeAppIcons.colorAppIconRes)!!

            override fun getOrFetchSkeletonAppIcon(
                packageName: String,
                userHandle: UserHandle,
            ): Drawable = context.getDrawable(fakeAppIcons.skeletonAppIconRes)!!

            override fun purgeCache(wantedPackages: Collection<String>) {}
        }
    notificationIconStyleProvider = alwaysShowNotificationIconStyleProvider
}

var Kosmos.realAppIconProvider: AppIconProviderImpl by
    Kosmos.Fixture { AppIconProviderImpl(applicationContext, dumpManager, fakeSystemClock) }

class FallbackAppIconProvider(
    private val realProvider: AppIconProvider,
    private val fallbackProvider: AppIconProvider,
) : AppIconProvider {
    override fun getOrFetchAppIcon(
        packageName: String,
        userHandle: UserHandle,
        instanceKey: String,
    ): Drawable =
        try {
            requireNotNull(realProvider.getOrFetchAppIcon(packageName, userHandle, instanceKey))
        } catch (e: Exception) {
            Log.d("FallbackAppIconProvider", "Error for ${userHandle.identifier}|$packageName: $e")
            fallbackProvider.getOrFetchAppIcon(packageName, userHandle, instanceKey)
        }

    override fun getOrFetchSkeletonAppIcon(packageName: String, userHandle: UserHandle): Drawable =
        try {
            requireNotNull(realProvider.getOrFetchSkeletonAppIcon(packageName, userHandle))
        } catch (e: Exception) {
            Log.d("FallbackAppIconProvider", "Error for ${userHandle.identifier}|$packageName: $e")
            fallbackProvider.getOrFetchSkeletonAppIcon(packageName, userHandle)
        }

    override fun purgeCache(wantedPackages: Collection<String>) {
        realProvider.purgeCache(wantedPackages)
        fallbackProvider.purgeCache(wantedPackages)
    }
}

var Kosmos.appIconProvider: AppIconProvider by
    Kosmos.Fixture {
        // Real AppIconProvider doesn't work in Robolectric (b/415767135)
        FallbackAppIconProvider(realAppIconProvider, mockAppIconProvider)
    }
