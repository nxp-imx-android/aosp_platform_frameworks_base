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
import android.content.pm.PackageManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@SysUISingleton
class InstalledAppsRepositoryImpl
@Inject
constructor(
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val packageManager: PackageManager,
) : InstalledAppsRepository {

    @SuppressLint("QueryPermissionsNeeded")
    override suspend fun fetchInstalledApps(): List<AppModel> {
        return withContext(backgroundDispatcher) {
            packageManager.getInstalledApplications(0).map {
                AppModel(
                    uid = it.uid,
                    label = it.loadLabel(packageManager).toString(),
                    // TODO: b/478225883 - Scale icon down.
                    icon = it.loadIcon(packageManager),
                    packageName = it.packageName,
                )
            }
        }
    }
}
