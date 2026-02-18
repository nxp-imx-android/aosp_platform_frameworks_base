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

import android.Manifest
import android.app.appfunctions.AppFunctionAidlSearchSpec
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.flags.Flags
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerInternal
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.testing.TestableContext
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class VisibilityHelperImplTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @get:Rule
    val testableContext = TestableContext(InstrumentationRegistry.getInstrumentation().context)

    private lateinit var mockPmInternal: PackageManagerInternal
    private lateinit var visibilityHelper: VisibilityHelperImpl

    @Before
    fun setup() {
        mockPmInternal = mock()
        visibilityHelper = VisibilityHelperImpl(testableContext, mockPmInternal)
    }

    @Test
    fun testApplyFilterWithoutFunctionNames_noExecutePermission_restrictsToCallingPackage() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, false)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder().build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        assertThat(result!!.packageNames).containsExactly(CALLING_PKG)
        assertThat(result.functionNames).isNull()
    }

    @Test
    fun testApplyFilterWithFunctionNames_noExecutePermission_restrictsToCallingPackage() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, false)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder()
                    .setFunctionNames(setOf(FUNC_A_1, FUNC_B_2, FUNC_HIDDEN_1))
                    .build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        // None of the search functionNames is visible
        assertThat(result).isNull()
    }

    @Test
    fun testApplyFilterWithoutFunctionNames_hasQueryAllPackages_returnsOriginalSpec() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, true)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, true)
        val requested = setOf(PKG_A, PKG_B, PKG_HIDDEN)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder().setPackageNames(requested).build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        assertThat(result!!.packageNames).containsExactlyElementsIn(requested)
        assertThat(result.functionNames).isNull()
    }

    @Test
    fun testApplyFilterWithFunctionNames_hasQueryAllPackages_returnsOriginalSpec() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, true)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, true)
        val requested = setOf(PKG_A, PKG_B, PKG_HIDDEN)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder()
                    .setPackageNames(requested)
                    .setFunctionNames(setOf(FUNC_A_1, FUNC_A_2, FUNC_B_1, FUNC_HIDDEN_1))
                    .build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        assertThat(result!!.packageNames).containsExactlyElementsIn(requested)
        assertThat(result.functionNames)
            .containsExactly(FUNC_A_1, FUNC_A_2, FUNC_B_1, FUNC_HIDDEN_1)
    }

    @Test
    fun testApplyFilterWithoutFunctionNames_filtersInvisiblePackages() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, true)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        mockVisiblePackages(PKG_A, PKG_B)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder().setPackageNames(setOf(PKG_A, PKG_HIDDEN)).build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        assertThat(result!!.packageNames).containsExactly(PKG_A)
        assertThat(result.functionNames).isNull()
    }

    @Test
    fun testApplyFilterWithFunctionNames_filtersInvisiblePackages() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, true)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        mockVisiblePackages(PKG_A, PKG_B)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder()
                    .setPackageNames(setOf(PKG_A, PKG_HIDDEN))
                    .setFunctionNames(setOf(FUNC_A_1, FUNC_A_2, FUNC_HIDDEN_1, FUNC_HIDDEN_2))
                    .build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        assertThat(result!!.packageNames).containsExactly(PKG_A)
        assertThat(result.functionNames).containsExactly(FUNC_A_1, FUNC_A_2)
    }

    @Test
    fun testApplyFilterWithFunctionNames_returnNull_whenNoOverlapBetweenVisiblePackageAndSearchFunctions() {
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, true)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        mockVisiblePackages(PKG_A, PKG_B)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder()
                    .setPackageNames(setOf(PKG_A, PKG_HIDDEN))
                    .setFunctionNames(setOf(FUNC_HIDDEN_1, FUNC_HIDDEN_2))
                    .build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        assertThat(result).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    fun testApplyFilter_withSystemPermission_filtersInvisiblePackages() {
        // With EXECUTE_APP_FUNCTIONS_SYSTEM, the caller can query for other packages.
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM, true)
        // Ensure other permissions that grant visibility are denied to isolate the test.
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, false)
        setTestPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS, false)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        mockVisiblePackages(PKG_A, PKG_B)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder().setPackageNames(setOf(PKG_A, PKG_HIDDEN)).build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        // The result should be filtered to only packages visible to the caller.
        assertThat(result!!.packageNames).containsExactly(PKG_A)
        assertThat(result.functionNames).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_FUNCTION_PERMISSION_V2)
    fun testApplyFilterWithFunctionNames_withSystemPermission_filtersInvisiblePackages() {
        // With EXECUTE_APP_FUNCTIONS_SYSTEM, the caller can query for other packages.
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS_SYSTEM, true)
        // Ensure other permissions that grant visibility are denied to isolate the test.
        setTestPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS, false)
        setTestPermission(Manifest.permission.DISCOVER_APP_FUNCTIONS, false)
        setTestPermission(Manifest.permission.QUERY_ALL_PACKAGES, false)
        mockVisiblePackages(PKG_A, PKG_B)
        val spec =
            AppFunctionAidlSearchSpec(
                CALLING_PKG,
                AppFunctionSearchSpec.Builder()
                    .setPackageNames(setOf(PKG_A, PKG_HIDDEN))
                    .setFunctionNames(setOf(FUNC_A_1, FUNC_A_2, FUNC_HIDDEN_1, FUNC_HIDDEN_2))
                    .build(),
                TARGET_USER_ID,
            )

        val result = visibilityHelper.applyVisiblePackageFilter(spec, TEST_UID, TEST_PID)

        // The result should be filtered to only packages and functions visible to the caller.
        assertThat(result!!.packageNames).containsExactly(PKG_A)
        assertThat(result.functionNames).containsExactly(FUNC_A_1, FUNC_A_2)
    }

    private fun setTestPermission(perm: String, granted: Boolean) {
        val result =
            if (granted) {
                PackageManager.PERMISSION_GRANTED
            } else {
                PackageManager.PERMISSION_DENIED
            }
        testableContext.testablePermissions.setPermission(perm, result)
    }

    private fun mockVisiblePackages(vararg packages: String) {
        val apps = ArrayList<ApplicationInfo>()

        for (pkg in packages) {
            val info = ApplicationInfo()
            info.packageName = pkg
            info.uid = 9999
            apps.add(info)
        }

        doReturn(apps).`when`(mockPmInternal).getInstalledApplications(any(), any(), eq(TEST_UID))
    }

    companion object {
        private const val TEST_UID = 10050
        private const val TEST_PID = 1234
        private const val TARGET_USER_ID = 10
        private const val CALLING_PKG = "com.android.caller"

        private const val PKG_A = "com.example.package.a"
        private val FUNC_A_1 = AppFunctionName(PKG_A, "ActivityA1")
        private val FUNC_A_2 = AppFunctionName(PKG_A, "ActivityA2")

        private const val PKG_B = "com.example.package.b"
        private val FUNC_B_1 = AppFunctionName(PKG_B, "ActivityB1")
        private val FUNC_B_2 = AppFunctionName(PKG_B, "ActivityB2")

        private const val PKG_HIDDEN = "com.example.hidden"
        private val FUNC_HIDDEN_1 = AppFunctionName(PKG_HIDDEN, "ActivityC1")
        private val FUNC_HIDDEN_2 = AppFunctionName(PKG_HIDDEN, "ActivityC2")
    }
}
