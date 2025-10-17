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

import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_CATEGORY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_NAME
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_VERSION
import android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME
import android.app.appsearch.GenericDocument
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AppFunctionMetadataTest {

    @Test
    fun testEqualsAndHashcode() {
        val metadata1 =
            AppFunctionMetadata.create(
                TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                TEST_AF_RUNTIME_METADATA_GD_BUILDER.build(),
                TEST_PACKAGE_METADATA
            )
        val metadata2 =
            AppFunctionMetadata.create(
                TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                TEST_AF_RUNTIME_METADATA_GD_BUILDER.build(),
                TEST_PACKAGE_METADATA
            )
        val metadata3 =
            AppFunctionMetadata.create(
                TEST_AF_STATIC_METADATA_GD_BUILDER.setId("testFunctionId2").build(),
                TEST_AF_RUNTIME_METADATA_GD_BUILDER.build(),
                TEST_PACKAGE_METADATA
            )

        assertThat(metadata1).isEqualTo(metadata2)
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode())
        assertThat(metadata1).isNotEqualTo(metadata3)
        assertThat(metadata1.hashCode()).isNotEqualTo(metadata3.hashCode())
    }

    @Test
    fun testGenericDocumentConstructor() {
        val appFunctionMetadata =
            AppFunctionMetadata.create(
                TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                TEST_AF_RUNTIME_METADATA_GD_BUILDER.build(),
                TEST_PACKAGE_METADATA
            )

        assertThat(appFunctionMetadata.name).isEqualTo(
            AppFunctionName(
                "testPackage",
                "testFunctionId"
            )
        )
        assertThat(appFunctionMetadata.schemaMetadata).isEqualTo(
            AppFunctionSchemaMetadata(
                "testCategory",
                "testName",
                1L
            )
        )
        assertThat(appFunctionMetadata.packageMetadata).isEqualTo(
            TEST_PACKAGE_METADATA
        )
        assertThat(appFunctionMetadata.metadataDocument).isEqualTo(
            TEST_AF_STATIC_METADATA_GD_BUILDER.build()
        )
        assertThat(appFunctionMetadata.isEnabled).isTrue()
    }

    private companion object {
        val TEST_AF_STATIC_METADATA_GD_BUILDER =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                "",
                "testPackage/testFunctionId",
                ""
            )
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true
                )
        val TEST_AF_RUNTIME_METADATA_GD_BUILDER =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyLong(
                    AppFunctionRuntimeMetadata.PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong()
                )
        val TEST_PACKAGE_METADATA =
            AppFunctionPackageMetadata.create(
                "testPackage",
                listOf(
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyString("exampleProperty", "exampleValue")
                        .build()
                )
            )
    }
}
