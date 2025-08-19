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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.PolicyProcessor
import com.google.common.base.Charsets
import com.google.common.io.ByteSource
import com.google.common.io.Resources
import com.google.testing.compile.Compilation
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import java.io.IOException
import javax.tools.StandardLocation
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail

import org.junit.Test

class PolicyProcessorTest {
    private val mCompiler = Compiler.javac().withProcessors(PolicyProcessor())

    private companion object {
        const val POLICIES_JSON_LOCATION = "android/processor/devicepolicy/policies.json"

        const val POLICY_IDENTIFIER = "android/processor/devicepolicy/test/PolicyIdentifier"
        const val POLICY_IDENTIFIER_JAVA = "$POLICY_IDENTIFIER.java"
        const val POLICY_IDENTIFIER_JSON = "$POLICY_IDENTIFIER.json"

        fun loadTextResource(path: String): String {
            try {
                val url = Resources.getResource(path)
                assertNotNull(String.format("Resource file not found: %s", path), url)
                return Resources.toString(url, Charsets.UTF_8)
            } catch (e: IOException) {
                fail(e.message)
                return ""
            }
        }
    }

    @Test
    fun test_PolicyIdendifierFake_generates() {
        val expectedOutput = loadTextResource(POLICY_IDENTIFIER_JSON)

        val compilation: Compilation =
            mCompiler.compile(JavaFileObjects.forResource(POLICY_IDENTIFIER_JAVA))
        assertThat(compilation).succeeded()
        assertThat(compilation).generatedFile(
            StandardLocation.SOURCE_OUTPUT, POLICIES_JSON_LOCATION
        ).hasContents(ByteSource.wrap(expectedOutput.toByteArray()))
    }

    @Test
    fun test_other_class_failsToCompile() {
        val compilation: Compilation =
            mCompiler.compile(
                JavaFileObjects.forResource("android/processor/devicepolicy/test/OtherClass.java"),
                JavaFileObjects.forResource(POLICY_IDENTIFIER_JAVA)
            )
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("@PolicyDefinition can only be applied to fields in android.app.admin.PolicyIdentifier")
    }
}