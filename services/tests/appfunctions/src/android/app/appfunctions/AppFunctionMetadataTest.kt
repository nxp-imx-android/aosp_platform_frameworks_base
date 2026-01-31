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
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GenericDocument
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionMetadataTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testEqualsAndHashcode() {
        val metadata1 =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()
        val metadata2 =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()
        val metadata3 =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER.setId("testPackage/testFunctionId2").build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()
        val metadata4 =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()

        assertThat(metadata1).isEqualTo(metadata2)
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode())
        assertThat(metadata1).isNotEqualTo(metadata3)
        assertThat(metadata1.hashCode()).isNotEqualTo(metadata3.hashCode())
        assertThat(metadata1).isNotEqualTo(metadata4)
        assertThat(metadata1.hashCode()).isNotEqualTo(metadata4.hashCode())
    }

    @Test
    fun testGenericDocumentConstructor() {
        val appFunctionMetadata =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()

        assertThat(appFunctionMetadata.name)
            .isEqualTo(AppFunctionName("testPackage", "testFunctionId"))
        assertThat(appFunctionMetadata.schemaMetadata)
            .isEqualTo(AppFunctionSchemaMetadata("testCategory", "testName", 1L))
        assertThat(appFunctionMetadata.packageMetadata).isEqualTo(TEST_PACKAGE_METADATA)
        assertThat(appFunctionMetadata.metadataDocument)
            .isEqualTo(TEST_AF_STATIC_METADATA_GD_BUILDER.build())
    }

    @Test
    fun testParcelAndUnparcel_allFieldsSet() {
        val originalMetadata =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER.build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()

        val restoredMetadata = parcelAndUnparcel(originalMetadata)

        assertThat(restoredMetadata.name).isEqualTo(originalMetadata.name)
        assertThat(restoredMetadata.schemaMetadata).isEqualTo(originalMetadata.schemaMetadata)
        assertThat(restoredMetadata.metadataDocument).isEqualTo(originalMetadata.metadataDocument)
        assertThat(restoredMetadata.packageMetadata).isEqualTo(originalMetadata.packageMetadata)
    }

    @Test
    fun testParcelAndUnparcel_nullSchema() {
        val originalMetadata =
            AppFunctionMetadata.Builder(
                    TEST_AF_STATIC_METADATA_GD_BUILDER_NO_SCHEMA.build(),
                    TEST_PACKAGE_METADATA,
                )
                .build()

        val restoredMetadata = parcelAndUnparcel(originalMetadata)

        assertThat(restoredMetadata.name).isEqualTo(originalMetadata.name)
        assertThat(restoredMetadata.schemaMetadata).isEqualTo(originalMetadata.schemaMetadata)
        assertThat(restoredMetadata.metadataDocument).isEqualTo(originalMetadata.metadataDocument)
        assertThat(restoredMetadata.packageMetadata).isEqualTo(originalMetadata.packageMetadata)
    }

    private companion object {
        val TEST_AF_STATIC_METADATA_GD_BUILDER =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testFunctionId",
                    "",
                )
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true,
                )
        val TEST_PACKAGE_METADATA =
            AppFunctionPackageMetadata.create(
                "testPackage",
                listOf(
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyString("exampleProperty", "exampleValue")
                        .build()
                ),
            )

        val TEST_AF_STATIC_METADATA_GD_BUILDER_NO_SCHEMA =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testFunctionId",
                    "",
                )
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true,
                )
    }

    private fun parcelAndUnparcel(original: AppFunctionMetadata): AppFunctionMetadata {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return AppFunctionMetadata.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
