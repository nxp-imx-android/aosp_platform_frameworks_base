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

package android.processor.devicepolicy

import com.android.json.stream.JsonWriter

class EnumEntryMetadata(val name: String, val value: Int, val documentation: String) {
    fun dump(writer: JsonWriter) {
        writer.beginObject()

        writer.name("name")
        writer.value(name)

        writer.name("value")
        writer.value(value.toLong())

        writer.name("documentation")
        writer.value(documentation)

        writer.endObject()
    }
}

class EnumPolicyMetadata(
    val defaultValue: Int,
    val enum: String,
    val enumDocumentation: String,
    val entries: List<EnumEntryMetadata>
) : PolicyMetadata() {
    override fun dump(writer: JsonWriter) {
        writer.name("default")
        writer.value(defaultValue.toLong())

        writer.name("enum")
        writer.value(enum)

        writer.name("enumDocumentation")
        writer.value(enumDocumentation)

        writer.name("options")
        writer.beginArray()

        entries.forEach { it.dump(writer) }

        writer.endArray()
    }
}