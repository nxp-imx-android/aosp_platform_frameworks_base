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

package com.android.systemui.activity.data.repository

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
import com.android.systemui.activity.data.model.AppVisibilityModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.core.Logger
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan

/** Repository for interfacing with [ActivityManager]. */
interface ActivityManagerRepository {

    /**
     * Given a UID, creates a flow that emits details about when the process with the given UID was
     * and is visible to the user.
     *
     * @param identifyingLogTag a tag identifying who created this flow, used for logging.
     */
    fun createAppVisibilityFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
    ): Flow<AppVisibilityModel>

    /**
     * Given a UID, creates a flow that emits true when the process with the given UID is visible to
     * the user and false otherwise.
     *
     * @param identifyingLogTag a tag identifying who created this flow, used for logging.
     */
    fun createIsAppVisibleFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
    ): Flow<Boolean>

    /**
     * Given a UID, creates a flow that emits true when the process with the given UID is dead
     * (IMPORTANCE_GONE) and false otherwise.
     *
     * @param identifyingLogTag a tag identifying who created this flow, used for logging.
     */
    fun createIsAppDeadFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
    ): Flow<Boolean>
}

@SysUISingleton
class ActivityManagerRepositoryImpl
@Inject
constructor(
    @Background private val backgroundContext: CoroutineContext,
    private val systemClock: SystemClock,
    private val activityManager: ActivityManager,
) : ActivityManagerRepository {

    override fun createAppVisibilityFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
    ): Flow<AppVisibilityModel> {
        return createIsAppVisibleFlow(creationUid, logger, identifyingLogTag)
            .distinctUntilChanged()
            .scan(initial = AppVisibilityModel()) {
                oldState: AppVisibilityModel,
                newIsVisible: Boolean ->
                if (newIsVisible) {
                    val lastAppVisibleTime = systemClock.currentTimeMillis()
                    logger.d({ "$str1: Setting lastAppVisibleTime=$long1" }) {
                        str1 = identifyingLogTag
                        long1 = lastAppVisibleTime
                    }
                    AppVisibilityModel(
                        isAppCurrentlyVisible = true,
                        lastAppVisibleTime = lastAppVisibleTime,
                    )
                } else {
                    // Reset the current status while maintaining the lastAppVisibleTime
                    oldState.copy(isAppCurrentlyVisible = false)
                }
            }
            .distinctUntilChanged()
    }

    override fun createIsAppVisibleFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
    ): Flow<Boolean> {
        return createUidImportanceFlow(
            creationUid,
            logger,
            identifyingLogTag,
            importanceCutpoint = IMPORTANCE_FOREGROUND,
        ) { importance ->
            importance <= IMPORTANCE_FOREGROUND
        }
    }

    override fun createIsAppDeadFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
    ): Flow<Boolean> {
        return createUidImportanceFlow(
            creationUid,
            logger,
            identifyingLogTag,
            importanceCutpoint = IMPORTANCE_GONE,
        ) { importance ->
            importance == IMPORTANCE_GONE
        }
    }

    private fun createUidImportanceFlow(
        creationUid: Int,
        logger: Logger,
        identifyingLogTag: String,
        importanceCutpoint: Int,
        mapper: (Int) -> Boolean,
    ): Flow<Boolean> {
        return conflatedCallbackFlow {
                val listener =
                    object : ActivityManager.OnUidImportanceListener {
                        override fun onUidImportance(uid: Int, importance: Int) {
                            if (uid != creationUid) {
                                return
                            }
                            val mappedValue = mapper(importance)
                            logger.d({
                                "$str1: #onUidImportance. importance=$int1, mappedValue=$bool1"
                            }) {
                                str1 = identifyingLogTag
                                int1 = importance
                                bool1 = mappedValue
                            }
                            trySend(mappedValue)
                        }
                    }
                try {
                    // TODO(b/286258140): Replace this with the #addOnUidImportanceListener
                    //  overload that filters to certain UIDs.
                    activityManager.addOnUidImportanceListener(listener, importanceCutpoint)
                } catch (e: SecurityException) {
                    logger.e({ "$str1: Security exception on #addOnUidImportanceListener" }, e) {
                        str1 = identifyingLogTag
                    }
                }

                awaitClose { activityManager.removeOnUidImportanceListener(listener) }
            }
            .distinctUntilChanged()
            .onStart {
                try {
                    val importanceOnStart = activityManager.getUidImportance(creationUid)
                    val mappedValueOnStart = mapper(importanceOnStart)
                    logger.d({ "$str1: Starting UID observation. mappedValue=$bool1" }) {
                        str1 = identifyingLogTag
                        bool1 = mappedValueOnStart
                    }
                    emit(mappedValueOnStart)
                } catch (e: SecurityException) {
                    logger.e({ "$str1: Security exception on #getUidImportance" }, e) {
                        str1 = identifyingLogTag
                    }
                    emit(false)
                }
            }
            .flowOn(backgroundContext)
    }
}
