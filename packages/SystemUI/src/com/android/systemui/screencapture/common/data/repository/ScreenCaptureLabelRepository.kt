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

package com.android.systemui.screencapture.common.data.repository

import android.annotation.UserIdInt
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screencapture.common.ScreenCapture
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Repository for fetching app labels */
interface ScreenCaptureLabelRepository {

    /** Returns a flow that fetches an app label. */
    fun labelFor(
        component: ComponentName,
        @UserIdInt userId: Int,
        badged: Boolean = true,
    ): StateFlow<Result<CharSequence>?>

    /** Fetch app label on background dispatcher. */
    suspend fun loadLabel(
        component: ComponentName,
        @UserIdInt userId: Int,
        badged: Boolean = true,
    ): CharSequence?
}

/** Default implementation of [ScreenCaptureLabelRepository]. */
class ScreenCaptureLabelRepositoryImpl
@Inject
constructor(
    @ScreenCapture private val scope: CoroutineScope,
    @Background private val bgContext: CoroutineContext,
    private val packageManager: PackageManager,
) : ScreenCaptureLabelRepository {

    override fun labelFor(
        component: ComponentName,
        userId: Int,
        badged: Boolean,
    ): StateFlow<Result<CharSequence>?> =
        flow {
                val label = loadLabel(component, userId, badged)
                if (label == null) {
                    emit(
                        Result.failure(
                            Exception(
                                "Could not find label for ${component.flattenToString()}, user $userId"
                            )
                        )
                    )
                } else {
                    emit(Result.success(label))
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun loadLabel(
        component: ComponentName,
        @UserIdInt userId: Int,
        badged: Boolean,
    ): CharSequence? =
        withContext(bgContext) {
            try {
                val label =
                    packageManager
                        .getApplicationInfoAsUser(
                            component.packageName,
                            PackageManager.ApplicationInfoFlags.of(0),
                            userId,
                        )
                        .let { appInfo -> packageManager.getApplicationLabel(appInfo) }
                if (badged) {
                    packageManager.getUserBadgedLabel(label, UserHandle(userId))
                } else {
                    label
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Unable to get application info", e)
                null
            }
        }
}

private const val TAG = "ScreenCaptureLabelRepository"
