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

import android.app.appsearch.GenericDocument
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AppSearchDataYamlConverterTest {

    @Test
    fun convertGenericDocumentsToYaml_withScalarTypes_succeeds() {
        val doc = GenericDocument.Builder<GenericDocument.Builder<*>>("ns1", "doc1", "TestSchema")
          .setPropertyString("stringProp", "hello world")
          .setPropertyLong("longProp", 123L)
          .setPropertyDouble("doubleProp", 45.67)
          .setPropertyBoolean("boolProp", true)
          .setScore(10)
          .setCreationTimestampMillis(1000L)
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(doc),
          /* keepEmptyValues= */ true,
          /* keepNullValues= */ true,
          /* keepGenericDocumentProperties= */ true
        )

        val expectedYaml = """
                - id: doc1
                  namespace: ns1
                  schemaType: TestSchema
                  creationTimestampMillis: 1000
                  score: 10
                  longProp: 123
                  stringProp: hello world
                  doubleProp: 45.67
                  boolProp: true
            """.trimIndent()

        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withoutDefaultValues_filtersProperties() {
        val doc = GenericDocument.Builder<GenericDocument.Builder<*>>("ns2", "doc2", "FilterSchema")
          .setPropertyString("emptyString", "")
          .setPropertyString("realString", "value")
          .setPropertyLong("zeroLong", 0L)
          .setPropertyBoolean("falseBoolean", false)
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(doc),
          /* keepEmptyValues= */ false,
          /* keepNullValues= */ false,
          /* keepGenericDocumentProperties= */ false
        )

        val expectedYaml = """
              - falseBoolean: false
                realString: value
                zeroLong: 0
        """.trimIndent()
        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withoutDocProperties_filtersProperties() {
        val doc = GenericDocument.Builder<GenericDocument.Builder<*>>("ns3", "doc3", "NoDocProps")
          .setPropertyString("prop", "value")
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(doc),
          /* keepEmptyValues= */ true,
          /* keepNullValues= */ true,
          /* keepGenericDocumentProperties= */ false
        )

        val expectedYaml = "- prop: value"
        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withArrayTypes_succeeds() {
        val doc = GenericDocument.Builder<GenericDocument.Builder<*>>("ns1", "doc4", "ArraySchema")
          .setPropertyString("stringArr", "a", "b", "c")
          .setPropertyLong("longArr", 1L, 2L)
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(doc),
          /* keepEmptyValues= */ true,
          /* keepNullValues= */ true,
          /* keepGenericDocumentProperties= */ false
        )

        val expectedYaml = """
                - stringArr:
                    - a
                    - b
                    - c
                  longArr:
                    - 1
                    - 2
            """.trimIndent()
        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withNestedDocument_succeeds() {
        val nestedDoc = GenericDocument.Builder<GenericDocument.Builder<*>>("nestedNs", "nestedId", "Nested")
          .setPropertyString("nestedProp", "I am nested")
          .build()

        val mainDoc = GenericDocument.Builder<GenericDocument.Builder<*>>("mainNs", "mainId", "Main")
          .setPropertyDocument("nestedDoc", nestedDoc)
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(mainDoc),
          /* keepEmptyValues= */ true,
          /* keepNullValues= */ true,
          /* keepGenericDocumentProperties= */ false
        )

        val expectedYaml = """
                - nestedDoc:
                    nestedProp: I am nested
            """.trimIndent()
        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withArrayOfNestedDocuments_succeeds() {
        val nestedDoc1 = GenericDocument.Builder<GenericDocument.Builder<*>>("ns", "n1", "Nested")
          .setPropertyString("prop", "first")
          .setCreationTimestampMillis(0L)
          .build()
        val nestedDoc2 = GenericDocument.Builder<GenericDocument.Builder<*>>("ns", "n2", "Nested")
          .setPropertyString("prop", "second")
          .setCreationTimestampMillis(1L)
          .build()

        val mainDoc = GenericDocument.Builder<GenericDocument.Builder<*>>("ns", "main", "Main")
          .setPropertyDocument("docArray", nestedDoc1, nestedDoc2)
          .setCreationTimestampMillis(1000L)
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(mainDoc),
          /* keepEmptyValues= */ true,
          /* keepNullValues= */ true,
          /* keepGenericDocumentProperties= */ true
        )

      val expectedYaml = """
            - id: main
              namespace: ns
              schemaType: Main
              creationTimestampMillis: 1000
              score: 0
              docArray:
                - id: n1
                  namespace: ns
                  schemaType: Nested
                  creationTimestampMillis: 0
                  score: 0
                  prop: first
                - id: n2
                  namespace: ns
                  schemaType: Nested
                  creationTimestampMillis: 1
                  score: 0
                  prop: second
        """.trimIndent()
        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withMultipleDocuments_succeeds() {
        val doc1 = GenericDocument.Builder<GenericDocument.Builder<*>>("ns", "id1", "MySchema")
          .setPropertyString("prop", "val1")
          .setCreationTimestampMillis(0L)
          .build()
        val doc2 = GenericDocument.Builder<GenericDocument.Builder<*>>("ns", "id2", "MySchema")
          .setPropertyString("prop", "val2")
          .setCreationTimestampMillis(1L)
          .build()

        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(doc1, doc2),
          /* keepEmptyValues= */ false,
          /* keepNullValues= */ false,
          /* keepGenericDocumentProperties= */ true
        )

        val expectedYaml = """
                - id: id1
                  namespace: ns
                  schemaType: MySchema
                  creationTimestampMillis: 0
                  score: 0
                  prop: val1
                - id: id2
                  namespace: ns
                  schemaType: MySchema
                  creationTimestampMillis: 1
                  score: 0
                  prop: val2
            """.trimIndent()

        assertThat(yaml.trim()).isEqualTo(expectedYaml)
    }

    @Test
    fun convertGenericDocumentsToYaml_withEmptyArray_returnsEmptyList() {
        val yaml = AppSearchDataYamlConverter.convertGenericDocumentsToYaml(
          arrayOf(),
          /* keepEmptyValues= */ true,
          /* keepNullValues= */ true,
          /* keepGenericDocumentProperties= */ true
        )

        assertThat(yaml.trim()).isEqualTo("")
    }
}
