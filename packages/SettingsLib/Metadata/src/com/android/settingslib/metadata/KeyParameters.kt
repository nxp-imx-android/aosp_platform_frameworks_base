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

/**
 * Holds a validated set of key-value parameters based on a [KeyParametersSchema].
 *
 * This class is a simple data container. An instance of this class is guaranteed to be valid
 * because it can only be created by the [KeyParametersSchema.prepare] method, which performs
 * all necessary validation.
 *
 * @property schema The schema that this set of parameters conforms to.
 * @property values The validated map of parameter names to their string values.
 */
@ConsistentCopyVisibility
data class KeyParameters internal constructor(
    private val schema: KeyParametersSchema,
    internal val values: Map<String, String>
) {
    /**
     * Returns `true` if this [KeyParameters] object contains no parameters.
     */
    val isEmpty = values.isEmpty()

    /**
     * Retrieves the value for a given parameter key.
     *
     * @param key The name of the parameter to retrieve.
     * @return The string value of the parameter, or `null` if the parameter is optional and was
     * not provided.
     * @throws IllegalArgumentException if the key is not defined in the schema.
     */
    operator fun get(key: String): String? {
        if (!schema.containsKey(key)) {
            throw IllegalArgumentException("Parameter '$key' is not defined in the schema.")
        }
        return values[key]
    }

    /**
     * Retrieves the value for a parameter that is explicitly defined as **required** in the schema.
     *
     * This method enforces that the parameter was declared with `required = true` in the
     * [KeyParametersSchema]. It is a stricter version of [get] and should be used when the schema
     * itself guarantees the presence of a value.
     *
     * @param key The name of the required parameter to retrieve.
     * @return The name of the parameter.
     * @throws IllegalArgumentException if the key is not defined in the schema, or if the parameter
     * is not marked as `required` in the schema.
     * @throws IllegalStateException if the value is unexpectedly null, which would indicate a bug
     * in the validation logic.
     */
    fun getRequired(key: String): String {
        if (!schema.containsKey(key)) {
            throw IllegalArgumentException("Parameter '$key' is not defined in the schema.")
        }

        if (!schema.isRequiredParameter(key)) {
            throw IllegalArgumentException("Parameter '$key' is not defined as required in the schema.")
        }

        return values[key] ?: error("Value for required parameter '$key' was null.")
    }

    /**
     * Converts this [KeyParameters] instance into an Android [Bundle].
     *
     * @return A new [Bundle] containing all the key-value pairs stored in this object.
     */
    fun toBundle(): Bundle {
        val bundle = Bundle()
        values.forEach { (k, v) -> bundle.putString(k, v) }
        return bundle
    }

    /**
     * Converts the key-value parameters into a string format suitable for persistence and parsing.
     * The format is `[key1=value1,key2=value2,...]`.
     */
    fun toParametersString(): String {
        return values.entries.joinToString(separator = ",", prefix = "[", postfix = "]") {
            (key, value) -> "$key=$value"
        }
    }
}

/**
 * Defines the schema for a set of parameters, including their names, descriptions, and validation rules.
 * This class acts as a factory for creating validated [KeyParameters] instances.
 *
 * Use the DSL function `KeyParametersSchema { ... }` to create an instance.
 *
 * @param schema A map from a parameter name to its definition.
 */
class KeyParametersSchema private constructor(
    private val schema: Map<String, ParameterDefinition>
) {
    /**
     * Holds the definition and rules for a single parameter within the schema.
     *
     * @property name The unique name of the parameter.
     * @property description A human-readable description of what the parameter is for.
     * @property required If `true`, this parameter must be provided when creating [KeyParameters].
     */
    data class ParameterDefinition(
        val name: String,
        val description: String,
        val required: Boolean
    ) {
        fun toParameterSchemaString(): String {
            val escapedDescription = description.replace("\"", "\\\"")
            return "{\"description\":\"$escapedDescription\",\"required\":$required}"
        }
    }

    /**
     * A builder for constructing a [KeyParametersSchema] using a DSL-style syntax.
     */
    class Builder {
        private val parameters = mutableMapOf<String, ParameterDefinition>()

        /**
         * Defines a parameter and adds it to the schema.
         *
         * @param name The unique name for the parameter.
         * @param description A human-readable description of the parameter.
         * @param required Whether this parameter must be provided. Defaults to `false`.
         * @throws IllegalArgumentException if a parameter with the same name is already defined.
         */
        fun parameter(name: String, description: String, required: Boolean = false) {
            if (parameters.containsKey(name)) {
                throw IllegalArgumentException("Parameter '$name' is already defined.")
            }
            parameters[name] = ParameterDefinition(name, description, required)
        }

        /**
         * Builds and returns the final [KeyParametersSchema] instance. For internal use by the DSL.
         */
        internal fun build(): KeyParametersSchema = KeyParametersSchema(parameters.toMap())
    }

    /**
     * Creates a validated [KeyParameters] instance from a map of provided values.
     *
     * This method checks the provided values against the schema rules, ensuring that all required
     * parameters are present and that no unknown parameters are included.
     *
     * @param providedValues A map of parameter names to their string values.
     * @return A validated [KeyParameters] instance.
     * @throws IllegalArgumentException if a required parameter is missing or an unknown parameter
     * is provided.
     */
    fun prepare(providedValues: Map<String, String>): KeyParameters {
        val finalValues = mutableMapOf<String, String>()

        // Validate provided values and check for required parameters
        schema.forEach { (name, def) ->
            val value = providedValues[name]
            if (value != null) {
                finalValues[name] = value
            } else if (def.required) {
                throw IllegalArgumentException("Required parameter '$name' is missing.")
            }
        }
        return KeyParameters(this, finalValues)
    }

    /**
     * A convenience method to create a validated [KeyParameters] instance from a `vararg` of [Pair]s.
     *
     * @see prepare(providedValues: Map<String, String>)
     */
    fun prepare(vararg values: Pair<String, String>): KeyParameters = prepare(values.toMap())

    /**
     * A convenience method to create a validated [KeyParameters] instance from a [Bundle].
     *
     * @see prepare(providedValues: Map<String, String>)
     *
     * TODO (b/442385176): remove this method once the catalyst client stops passing the arguments as a bundle.
     */
    fun prepare(bundle: Bundle): KeyParameters {
        val providedValues = bundle.keySet().mapNotNull { key ->
            bundle.getString(key)?.let { value -> key to value }
        }.toMap()
        return prepare(providedValues)
    }

    /**
     * Creates a new [KeyParameters] instance by updating an existing one with new values.
     *
     * This method takes an existing, valid [KeyParameters] object, merges it with the
     * new values, and then re-validates the entire set to produce a new, immutable object.
     *
     * @param existing The original [KeyParameters] object.
     * @param newValues A map of the new key-value pairs to apply.
     * @return A new, validated [KeyParameters] instance.
     * @throws IllegalArgumentException if validation fails.
     */
    fun prepareWith(existing: KeyParameters?, newValues: Map<String, String>): KeyParameters {
        val currentValues = existing?.values?.toMutableMap() ?: mutableMapOf()

        // TODO (b/452555836): remove this "schemaDevtool" devtool testing
        currentValues.remove("schemaDevtool")

        currentValues.putAll(newValues)

        return prepare(currentValues)
    }

    /**
     * A convenience overload for updating with a single key-value pair.
     */
    fun prepareWith(existing: KeyParameters?, vararg newValues: Pair<String, String>): KeyParameters {
        return prepareWith(existing, newValues.toMap())
    }

    /**
     * Creates a validated [KeyParameters] instance from a string representation.
     *
     * The expected format is `[key1=value1,key2=value2,...]`. This method parses the string
     * and then validates the resulting key-value pairs against the schema.
     *
     * @param parametersString The string representation of the parameters.
     * @return A validated [KeyParameters] instance.
     * @throws IllegalArgumentException if the string is malformed or if validation against
     * the schema fails.
     */
    fun prepare(parametersString: String): KeyParameters {
        if (!parametersString.startsWith("[") || !parametersString.endsWith("]")) {
            throw IllegalArgumentException("String must be enclosed in brackets [].")
        }

        val content = parametersString.substring(1, parametersString.length - 1)
        if (content.isEmpty()) {
            return prepare(emptyMap())
        }

        val providedValues = content.split(',').associate { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Malformed key-value pair: '$pair'")
            }
            val key = parts[0].trim()
            val value = parts[1].trim()
            if (key.isEmpty()) {
                throw IllegalArgumentException("Key cannot be empty in pair: '$pair'")
            }
            key to value
        }

        return prepare(providedValues)
    }

    /**
     * Creates an empty [KeyParameters] instance.
     *
     * This method is primarily used for backward compatibility when a preference screen
     * transitions from being non-parameterized to parameterized. In such migration scenarios,
     * the parameterized screen is expected to gracefully accept and handle empty [KeyParameters]
     * to ensure compatibility with older configurations or entry points.
     */
    fun prepareEmpty() = prepare(emptyMap())

    /**
     * Checks if a parameter key is defined in this schema.
     */
    internal fun containsKey(key: String): Boolean = schema.containsKey(key)

    /**
     * Checks if a parameter is defined as required in the schema.
     *
     * @param key The name of the parameter to check.
     * @return `true` if the parameter exists in the schema and is marked as required,
     * `false` otherwise.
     */
    internal fun isRequiredParameter(key: String): Boolean {
        return schema[key]?.required ?: false
    }

    fun toParametersSchemaString(): String {
        return schema.entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
            "\"${it.key}\":${it.value.toParameterSchemaString()}"
        }
    }

    override fun toString() = "KeyParametersSchema(schema=${toParametersSchemaString()})"

    companion object {
        /**
         * An empty [KeyParametersSchema] instance.
         *
         * TODO (b/457182494): This should be removed once all the parameterized screen have been migrated.
         */
        @JvmStatic
        val EMPTY = KeyParametersSchema { }
    }
}

/**
 * A DSL entry point for creating a [KeyParametersSchema] in a clean and readable way.
 *
 * Example:
 * ```
 * val mySchema = KeyParametersSchema {
 *     parameter("pkg", "The package name", required = true)
 * }
 * ```
 */
fun KeyParametersSchema(block: KeyParametersSchema.Builder.() -> Unit): KeyParametersSchema {
    val builder = KeyParametersSchema.Builder()
    block(builder)
    return builder.build()
}

// Should we have here a unified place to store the keys?
const val KEY_PACKAGE_NAME = "pkg"

/**
 * Adds the app package name parameter to the schema.
 * @param required Whether this parameter must be provided. Defaults to `true`.
 */
fun KeyParametersSchema.Builder.withAppPackageName() {
    parameter(KEY_PACKAGE_NAME, "The package name of the app", required = true)
}

/**
 * Convenience method to prepare KeyParameters with a single application package name.
 * Assumes the schema includes the KEY_PACKAGE_NAME parameter.
 *
 * @param packageName The package name value.
 * @return Validated KeyParameters.
 */
fun KeyParametersSchema.prepareForApp(packageName: String): KeyParameters {
    return prepare(KEY_PACKAGE_NAME to packageName)
}

/**
 * Convenience method to retrieve the package name from a KeyParameters.
 */
val KeyParameters.packageName: String
    get() = getRequired(KEY_PACKAGE_NAME)