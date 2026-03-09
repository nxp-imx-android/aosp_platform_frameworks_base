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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.user.data.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class InstalledAppsRepositoryImpl
@Inject
constructor(
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val packageManager: PackageManager,
    private val appIconProvider: AppIconProvider,
    private val userRepository: UserRepository,
) : InstalledAppsRepository {

    @SuppressLint("QueryPermissionsNeeded")
    override suspend fun fetchInstalledApps(context: Context): List<AppModel> {
        return withContext(backgroundDispatcher) {
            val currentUser = userRepository.selectedUser.value.userInfo
            packageManager.getInstalledApplicationsAsUser(0, currentUser.id).map {
                AppModel(
                    uid = it.uid,
                    label = it.loadLabel(packageManager).toString(),
                    icon = fetchAppIcon(it.packageName, currentUser.userHandle, context),
                    packageName = it.packageName,
                )
            }
        }
    }

    private fun fetchAppIcon(
        packageName: String,
        userHandle: UserHandle,
        context: Context,
    ): Drawable {
        return try {
            appIconProvider.getOrFetchAppIcon(
                packageName = packageName,
                userHandle = userHandle,
                instanceKey = "notificationRule",
            )
        } catch (_: NameNotFoundException) {
            // TODO: b/478225883 - Log error.
            context.getDrawable(android.R.drawable.sym_def_app_icon)!!
        }
    }
}
