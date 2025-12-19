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
package android.app.appfunctions

import android.app.appfunctions.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionSearchSpecTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testEqualsAndHashcode() {
        val testSearchSpec1 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        val testSearchSpec2 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        val testSearchSpec3 =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(listOf(AppFunctionName("testPackage1", "id3")))
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec1).isEqualTo(testSearchSpec2)
        assertThat(testSearchSpec1.hashCode()).isEqualTo(testSearchSpec2.hashCode())
        assertThat(testSearchSpec1).isNotEqualTo(testSearchSpec3)
        assertThat(testSearchSpec1.hashCode()).isNotEqualTo(testSearchSpec3.hashCode())
    }

    @Test
    fun testBuilder_emptyPackageNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionSearchSpec.Builder().setPackageNames(emptyList())
        }
    }

    @Test
    fun testBuilder_emptyFunctionNames_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            AppFunctionSearchSpec.Builder().setFunctionNames(emptyList())
        }
    }

    @Test
    fun testGetStaticMetadataAppSearchQuery() {
        assertThat(SEARCH_SPEC_WITH_ALL_PROPERTIES.getStaticMetadataAppSearchQuery())
            .isEqualTo(
                "packageName:(\"testPackage1\" OR \"testPackage2\") " +
                    "schemaCategory:\"testCategory\" " +
                    "schemaName:\"testName\" " +
                    "schemaVersion>=1"
            )
    }

    @Test
    fun testGetStaticMetadataAppSearchQuery_nullPackageNames_skipsPackageNamesFilter() {
        assertThat(SEARCH_SPEC_WITHOUT_PACKAGE_NAMES.getStaticMetadataAppSearchQuery())
            .isEqualTo(
                "schemaCategory:\"testCategory\" " + "schemaName:\"testName\" " + "schemaVersion>=1"
            )
    }

    @Test
    fun testGetStaticMetadataAppSearchQuery_schemaVersionZero_noVersionFilter() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(0L)
                .build()

        assertThat(testSearchSpec.getStaticMetadataAppSearchQuery())
            .isEqualTo(
                "packageName:(\"testPackage1\" OR \"testPackage2\") " +
                    "schemaCategory:\"testCategory\" " +
                    "schemaName:\"testName\""
            )
    }

    @Test
    fun testGetQualifiedIdsFilter() {
        assertThat(SEARCH_SPEC_WITH_ALL_PROPERTIES.getQualifiedIdsFilter())
            .isEqualTo(listOf("testPackage1/id1", "testPackage2/id2"))
    }

    @Test
    fun testGetQualifiedIdsFilter_nullAppFunctionNames_returnsEmptyString() {
        assertThat(SEARCH_SPEC_WITHOUT_FUNCTION_NAMES.getQualifiedIdsFilter())
            .isEqualTo(emptyList<String>())
    }

    @Test
    fun testGetObservedPackageNames_packageAndFunctionFiltersSet_returnsIntersectingPackageNames() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedPackageNames).containsExactly("testPackage1")
    }

    @Test
    fun testGetObservedPackageNames_packageAndFunctionFiltersSet_noIntersection_returnsEmptySet() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage2", "id1"),
                        AppFunctionName("testPackage3", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedPackageNames).isEmpty()
    }

    @Test
    fun testGetObservedPackageNames_onlyFunctionFilterSet_returnsAllFunctionNamePackages() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(null)
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedPackageNames)
            .containsExactly("testPackage1", "testPackage2")
    }

    @Test
    fun testGetObservedPackageNames_onlyPackageFilterSet_returnsAllPackageNames() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedPackageNames)
            .containsExactly("testPackage1", "testPackage2")
    }

    @Test
    fun testGetObservedAppFunctions_packageAndFunctionFiltersSet_returnsAppFunctionsOfIntersectingPackages() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedAppFunctions)
            .containsExactly(AppFunctionName("testPackage1", "id1"))
    }

    @Test
    fun testGetObservedAppFunctions_packageAndFunctionFiltersSet_noIntersection_returnsEmptySet() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage2", "id1"),
                        AppFunctionName("testPackage3", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedAppFunctions).isEmpty()
    }

    @Test
    fun testGetObservedAppFunctions_onlyFunctionFilterSet_returnsAllAppFunctions() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(null)
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedAppFunctions)
            .containsExactly(
                AppFunctionName("testPackage1", "id1"),
                AppFunctionName("testPackage2", "id2"),
            )
    }

    @Test
    fun testGetObservedAppFunctions_onlyPackageFilterSet_returnsNull() {
        val testSearchSpec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        assertThat(testSearchSpec.observedAppFunctions).isNull()
    }

    companion object {
        val SEARCH_SPEC_WITH_ALL_PROPERTIES =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        val SEARCH_SPEC_WITHOUT_PACKAGE_NAMES =
            AppFunctionSearchSpec.Builder()
                .setFunctionNames(
                    listOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        val SEARCH_SPEC_WITHOUT_FUNCTION_NAMES =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(listOf("testPackage1", "testPackage2"))
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()
    }
}
