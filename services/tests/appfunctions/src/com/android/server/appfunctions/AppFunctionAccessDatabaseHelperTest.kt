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

package com.android.server.appfunctions

import android.app.appfunctions.AppFunctionAttribution
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionAidlRequest
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFunctionAccessDatabaseHelperTest {
    private lateinit var context: Context
    private lateinit var dbHelper: AppFunctionAccessDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        dbHelper = AppFunctionAccessDatabaseHelper(context)
    }

    @After
    fun tearDown() {
        dbHelper.deleteAll()
        dbHelper.close()
    }

    @Test
    fun queryAppFunctionAccessHistory_shouldReturnInsertedHistories_withoutAttribution() {
        val requestTime = System.currentTimeMillis()
        val duration = 100L
        val request = createTestExecuteAppFunctionAidlRequest(requestTime = requestTime)

        val rowId = dbHelper.insertAppFunctionAccessHistory(request, duration)

        assertThat(rowId).isNotEqualTo(-1)
        dbHelper.queryAppFunctionAccessHistory(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_AGENT_PACKAGE_NAME)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_INTERACTION_TYPE
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_CUSTOM_INTERACTION_TYPE
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_INTERACTION_URI
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_THREAD_ID
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_ACCESS_TIME
                        )
                    )
                )
                .isEqualTo(requestTime)
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_DURATION
                        )
                    )
                )
                .isEqualTo(duration)
        }
    }

    @Test
    fun queryAppFunctionAccessHistory_shouldReturnInsertedHistories_withMinimalAttribution() {
        val requestTime = System.currentTimeMillis()
        val duration = 100L
        val attribution =
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY)
                .build()
        val request =
            createTestExecuteAppFunctionAidlRequest(
                attribution = attribution,
                requestTime = requestTime,
            )

        val rowId = dbHelper.insertAppFunctionAccessHistory(request, duration)

        assertThat(rowId).isNotEqualTo(-1)
        dbHelper.queryAppFunctionAccessHistory(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_AGENT_PACKAGE_NAME)

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_INTERACTION_TYPE
                        )
                    )
                )
                .isEqualTo(AppFunctionAttribution.INTERACTION_TYPE_USER_QUERY)
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_CUSTOM_INTERACTION_TYPE
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_INTERACTION_URI
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_THREAD_ID
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_ACCESS_TIME
                        )
                    )
                )
                .isEqualTo(requestTime)
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_DURATION
                        )
                    )
                )
                .isEqualTo(duration)
        }
    }

    @Test
    fun queryAppFunctionAccessHistory_shouldReturnInsertedHistories_withFullAttribution() {
        val requestTime = System.currentTimeMillis()
        val duration = 100L
        val attribution =
            AppFunctionAttribution.Builder(AppFunctionAttribution.INTERACTION_TYPE_OTHER)
                .setCustomInteractionType(TEST_CUSTOM_INTERACTION_TYPE)
                .setThreadId(TEST_THREAD_ID)
                .setInteractionUri(TEST_INTERACTION_URI)
                .build()
        val request =
            createTestExecuteAppFunctionAidlRequest(
                attribution = attribution,
                requestTime = requestTime,
            )

        val rowId = dbHelper.insertAppFunctionAccessHistory(request, duration)

        assertThat(rowId).isNotEqualTo(-1)
        dbHelper.queryAppFunctionAccessHistory(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_AGENT_PACKAGE_NAME)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_INTERACTION_TYPE
                        )
                    )
                )
                .isEqualTo(AppFunctionAttribution.INTERACTION_TYPE_OTHER)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_CUSTOM_INTERACTION_TYPE
                        )
                    )
                )
                .isEqualTo(TEST_CUSTOM_INTERACTION_TYPE)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_INTERACTION_URI
                        )
                    )
                )
                .isEqualTo(TEST_INTERACTION_URI.toString())
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_THREAD_ID
                        )
                    )
                )
                .isEqualTo(TEST_THREAD_ID)
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_ACCESS_TIME
                        )
                    )
                )
                .isEqualTo(requestTime)
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_DURATION
                        )
                    )
                )
                .isEqualTo(duration)
        }
    }

    @Test
    fun deleteExpiredAppFunctionAccessHistories_shouldClearExpiredHistories() {
        val retentionPeriodMillis = 2000L
        val oldRequestTime = System.currentTimeMillis() - retentionPeriodMillis - 500
        val newRequestTime = System.currentTimeMillis()
        val oldRequest = createTestExecuteAppFunctionAidlRequest(requestTime = oldRequestTime)
        dbHelper.insertAppFunctionAccessHistory(oldRequest, 100L)
        val newRequest = createTestExecuteAppFunctionAidlRequest(requestTime = newRequestTime)
        dbHelper.insertAppFunctionAccessHistory(newRequest, 100L)

        dbHelper.deleteExpiredAppFunctionAccessHistories(retentionPeriodMillis)

        dbHelper.queryAppFunctionAccessHistory(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_ACCESS_TIME
                        )
                    )
                )
                .isEqualTo(newRequestTime)
        }
    }

    @Test
    fun deleteAppFunctionAccessHistories_shouldClearAllHistoryAssociatedWithThePackage() {
        val otherPackageName = "com.android.test.other"
        val agentAsCallerRequest =
            createTestExecuteAppFunctionAidlRequest(
                callingPackage = TEST_AGENT_PACKAGE_NAME,
                targetPackageName = TEST_TARGET_PACKAGE_NAME,
            )
        dbHelper.insertAppFunctionAccessHistory(agentAsCallerRequest, 100L)
        val agentAsTargetRequest =
            createTestExecuteAppFunctionAidlRequest(
                callingPackage = TEST_TARGET_PACKAGE_NAME,
                targetPackageName = TEST_AGENT_PACKAGE_NAME,
            )
        dbHelper.insertAppFunctionAccessHistory(agentAsTargetRequest, 100L)
        val unrelatedRequest =
            createTestExecuteAppFunctionAidlRequest(
                callingPackage = TEST_TARGET_PACKAGE_NAME,
                targetPackageName = otherPackageName,
            )
        dbHelper.insertAppFunctionAccessHistory(unrelatedRequest, 100L)

        dbHelper.deleteAppFunctionAccessHistories(TEST_AGENT_PACKAGE_NAME)

        dbHelper.queryAppFunctionAccessHistory(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppFunctionManager.AccessHistory.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(otherPackageName)
        }
    }

    private fun createTestExecuteAppFunctionAidlRequest(
        attribution: AppFunctionAttribution? = null,
        requestTime: Long = System.currentTimeMillis(),
        callingPackage: String = TEST_AGENT_PACKAGE_NAME,
        targetPackageName: String = TEST_TARGET_PACKAGE_NAME,
    ): ExecuteAppFunctionAidlRequest {
        val clientRequestBuilder =
            ExecuteAppFunctionRequest.Builder(targetPackageName, TEST_FUNCTION_ID)
        if (attribution != null) {
            clientRequestBuilder.setAttribution(attribution)
        }
        return ExecuteAppFunctionAidlRequest(
            clientRequestBuilder.build(),
            context.user,
            callingPackage,
            requestTime,
        )
    }

    companion object {
        private const val TEST_AGENT_PACKAGE_NAME = "com.android.test.agent"
        private const val TEST_TARGET_PACKAGE_NAME = "com.android.test.target"
        private const val TEST_FUNCTION_ID = "test_function_id"
        private const val TEST_CUSTOM_INTERACTION_TYPE = "MAINTENANCE"
        private const val TEST_THREAD_ID = "test_thread_id"
        private val TEST_INTERACTION_URI: Uri = Uri.parse("content://test/interaction")
    }
}
