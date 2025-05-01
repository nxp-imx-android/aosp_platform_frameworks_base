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

package com.android.systemui.statusbar.notification.row.domain.interactor

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.row.data.repository.BundleRepository
import com.android.systemui.statusbar.notification.row.icon.AppIconProvider
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val TAG = "BundleInteractor"

/** Provides functionality for UI to interact with a Notification Bundle. */
class BundleInteractor(
    private val repository: BundleRepository,
    private val appIconProvider: AppIconProvider,
    private val context: Context,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    @get:StringRes
    val titleText: Int
        get() = repository.titleText

    val numberOfChildren: Int?
        get() = repository.numberOfChildren

    @get:DrawableRes
    val bundleIcon: Int
        get() = repository.bundleIcon

    /**
     * A cold flow of app icon [Drawable]s fetched asynchronously based on changes to
     * `repository.appDataList` each time this flow is collected.
     */
    val previewIcons: Flow<List<Drawable>> =
        snapshotFlow { repository.appDataList }
            .mapLatestConflated { appList ->
                withContext(backgroundDispatcher) {
                    appList.asSequence().distinct().mapNotNull(::fetchAppIcon).take(3).toList()
                }
            }

    private fun fetchAppIcon(appData: AppData): Drawable? =
        try {
            appIconProvider.getOrFetchAppIcon(
                packageName = appData.packageName,
                // TODO(b/416126107) remove context and withWorkProfileBadge after we add them to
                //  AppIconProvider
                context = context,
                withWorkProfileBadge = false,
                themed = false,
            )
        } catch (e: NameNotFoundException) {
            Log.w(TAG, "Failed to load app icon for ${appData.packageName}", e)
            null
        }
}
