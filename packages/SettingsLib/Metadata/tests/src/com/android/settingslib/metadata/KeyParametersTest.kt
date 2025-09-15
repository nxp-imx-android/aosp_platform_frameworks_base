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

package com.android.settingslib.metadata

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyParametersTest {

    private val testSchema = KeyParametersSchema {
        parameter("required_param", "A required parameter", required = true)
        parameter("optional_param", "An optional parameter")
    }

    private val optionalSchema = KeyParametersSchema {
        parameter("optional_param", "An optional parameter")
    }

    @Test
    fun schemaBuilder_duplicateParameter_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            KeyParametersSchema {
                parameter("param1", "description1")
                parameter("param1", "description2")
            }
        }
        assertThat(exception.message).isEqualTo("Parameter 'param1' is already defined.")
    }

    @Test
    fun schemaBuilder_noParameters() {
        val emptySchema = KeyParametersSchema {}
        assertThat(emptySchema.toString()).isEqualTo("KeyParametersSchema(schema=[])")
    }

    @Test
    fun prepare_validParameters_succeeds() {
        val params = testSchema.prepare(
            mapOf(
                "required_param" to "value1",
                "optional_param" to "value2"
            )
        )
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("value2")
    }

    @Test
    fun prepare_onlyRequiredParameters_succeeds() {
        val params = testSchema.prepare(
            mapOf("required_param" to "value1")
        )
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepare_missingRequiredParameter_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare(mapOf("optional_param" to "value2"))
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepare_unknownParameter_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare(
                mapOf(
                    "required_param" to "value1",
                    "unknown_param" to "value3"
                )
            )
        }
        assertThat(exception.message).isEqualTo("Unknown parameter 'unknown_param' provided.")
    }

    @Test
    fun prepare_varargs_succeeds() {
        val params = testSchema.prepare(
            "required_param" to "value1",
            "optional_param" to "value2"
        )
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("value2")
    }

    @Test
    fun prepareFromBundle_validBundle_succeeds() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
            putString("optional_param", "bundle_value2")
        }
        val params = testSchema.prepare(bundle)
        assertThat(params.get("required_param")).isEqualTo("bundle_value1")
        assertThat(params.get("optional_param")).isEqualTo("bundle_value2")
    }

    @Test
    fun prepareFromBundle_onlyRequired_succeeds() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
        }
        val params = testSchema.prepare(bundle)
        assertThat(params.get("required_param")).isEqualTo("bundle_value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepareFromBundle_missingRequired_throwsException() {
        val bundle = Bundle().apply {
            putString("optional_param", "bundle_value2")
        }
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare(bundle)
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepareFromBundle_unknownParameter_throwsException() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
            putString("unknown_param", "unknown")
        }
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare(bundle)
        }
        assertThat(exception.message).isEqualTo("Unknown parameter 'unknown_param' provided.")
    }

    @Test
    fun prepareFromBundle_nullValueInBundle_isSkipped() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
            putString("optional_param", null) // This should be skipped
        }
        val params = testSchema.prepare(bundle)
        assertThat(params.get("required_param")).isEqualTo("bundle_value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepareFromBundle_emptyBundle_throwsForRequired() {
        val bundle = Bundle()
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare(bundle)
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepareFromBundle_emptyBundle_succeedsForOptional() {
        val bundle = Bundle()
        val params = optionalSchema.prepare(bundle)
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun get_existingKey_returnsValue() {
        val params = testSchema.prepare("required_param" to "value1")
        assertThat(params.get("required_param")).isEqualTo("value1")
    }

    @Test
    fun get_optionalKeyNotProvided_returnsNull() {
        val params = testSchema.prepare("required_param" to "value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun get_unknownKey_throwsException() {
        val params = testSchema.prepare("required_param" to "value1")
        val exception = assertFailsWith<IllegalArgumentException> {
            params.get("unknown_key")
        }
        assertThat(exception.message).isEqualTo("Parameter 'unknown_key' is not defined in the schema.")
    }

    @Test
    fun getRequired_existingRequiredKey_returnsValue() {
        val params = testSchema.prepare("required_param" to "value1")
        assertThat(params.getRequired("required_param")).isEqualTo("value1")
    }

    @Test
    fun getRequired_unknownKey_throwsException() {
        val params = testSchema.prepare("required_param" to "value1")
        val exception = assertFailsWith<IllegalArgumentException> {
            params.getRequired("unknown_key")
        }
        assertThat(exception.message).isEqualTo("Parameter 'unknown_key' is not defined in the schema.")
    }

    @Test
    fun getRequired_optionalKey_throwsException() {
        val params = testSchema.prepare(
            "required_param" to "value1",
            "optional_param" to "value2"
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            params.getRequired("optional_param")
        }
        assertThat(exception.message).isEqualTo("Parameter 'optional_param' is not defined as required in the schema.")
    }

    @Test
    fun toParametersString_multipleParams() {
        val params = testSchema.prepare(
            "required_param" to "val1",
            "optional_param" to "val2"
        )
        assertThat(params.toParametersString()).isEqualTo("[required_param=val1,optional_param=val2]")
    }

    @Test
    fun toParametersString_onlyRequired() {
        val params = testSchema.prepare("required_param" to "val1")
        assertThat(params.toParametersString()).isEqualTo("[required_param=val1]")
    }

    @Test
    fun toParametersString_noParamsInOptionalSchema() {
        val params = optionalSchema.prepare(emptyMap())
        assertThat(params.toParametersString()).isEqualTo("[]")
    }

     @Test
    fun toParametersString_specialChars() {
        val params = testSchema.prepare(
            "required_param" to "value with spaces, and=equals",
            "optional_param" to "another"
        )
        assertThat(params.toParametersString()).isEqualTo("[required_param=value with spaces, and=equals,optional_param=another]")
    }

    @Test
    fun toBundle_returnsCorrectBundle() {
        val map = mapOf(
            "required_param" to "value1",
            "optional_param" to "value2"
        )
        val params = testSchema.prepare(map)
        val bundle = params.toBundle()
        assertThat(bundle.getString("required_param")).isEqualTo("value1")
        assertThat(bundle.getString("optional_param")).isEqualTo("value2")
        assertThat(bundle.size()).isEqualTo(2)
    }

    @Test
    fun toBundle_onlyRequired() {
        val params = testSchema.prepare("required_param" to "value1")
        val bundle = params.toBundle()
        assertThat(bundle.getString("required_param")).isEqualTo("value1")
        assertThat(bundle.containsKey("optional_param")).isFalse()
        assertThat(bundle.size()).isEqualTo(1)
    }

    @Test
    fun schema_isRequiredParameter_nonExistentKey() {
        assertThat(testSchema.isRequiredParameter("non_existent")).isFalse()
    }

    @Test
    fun schema_containsKey_nonExistentKey() {
        assertThat(testSchema.containsKey("non_existent")).isFalse()
    }

    @Test
    fun schema_containsKey_existingKey() {
        assertThat(testSchema.containsKey("required_param")).isTrue()
        assertThat(testSchema.containsKey("optional_param")).isTrue()
    }

    @Test
    fun prepareFromString_validString_succeeds() {
        val params = testSchema.prepare("[required_param=value1,optional_param=123]")
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("123")
    }

    @Test
    fun prepareFromString_validStringWithWhitespace_succeeds() {
        val params = testSchema.prepare("[ required_param = value1 ,  optional_param = 123 ]")
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("123")
    }

    @Test
    fun prepareFromString_valueContainingEquals_succeeds() {
        val schema = KeyParametersSchema { parameter("url", "some url", required = true) }
        val params = schema.prepare("[url=http://example.com?a=1&b=2]")
        assertThat(params.get("url")).isEqualTo("http://example.com?a=1&b=2")
    }

    @Test
    fun prepareFromString_emptyContent_failsWithRequiredParam() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare("[]")
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepareFromString_emptyContent_succeedsWithOptionalSchema() {
        val params = optionalSchema.prepare("[]")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepareFromString_missingOpeningBracket_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare("required_param=value1]")
        }
        assertThat(exception.message).isEqualTo("String must be enclosed in brackets [].")
    }

    @Test
    fun prepareFromString_missingClosingBracket_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare("[required_param=value1")
        }
        assertThat(exception.message).isEqualTo("String must be enclosed in brackets [].")
    }

    @Test
    fun prepareFromString_malformedPair_noEquals_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare("[required_param:value1]")
        }
        assertThat(exception.message).isEqualTo("Malformed key-value pair: 'required_param:value1'")
    }

    @Test
    fun prepareFromString_emptyKey_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            testSchema.prepare("[=value,required_param=app]")
        }
        assertThat(exception.message).isEqualTo("Key cannot be empty in pair: '=value'")
    }
}
