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

package com.android.settingslib.metadata.apifirst

import android.content.Context
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.NoOpKeyedObservable
import com.android.settingslib.metadata.PersistentPreference

/** Configuration of the [ApiFirstPreference] getter. */
data class GetterConfig<V : Any>(
    // TODO: add other lambdas for Get API
    val execute: (Context) -> V
)

/** Configuration of the [ApiFirstPreference] setter. */
data class SetterConfig<V : Any>(
    // TODO: add other lambdas for Set API
    val execute: (Context, V) -> Unit
)

/**
 * A preference abstraction to describe the ability of getting and (optionally) setting a value
 * specific to that preference, without relying on binding to an actual UI widget. This class is
 * produced when an Engineer in a partner team migrates their preference to Catalyst using the 2026
 * "Lightweight" way. It sets suitable defaults, and converts between the code we are asking
 * partner teams to write and the methods which Catalyst expects.
 */
abstract class ApiFirstPreference<V : Any>() : PersistentPreference<V> {
    override fun storage(context: Context): KeyValueStore =
        object : NoOpKeyedObservable<String>(), KeyValueStore {
            override fun contains(storeKey: String): Boolean = storeKey == key

            override fun <T : Any> getValue(storeKey: String, valueType: Class<T>): T? =
                when {
                    storeKey == key -> {
                        // TODO: Maybe add permission checks here
                        getter.execute(context) as T?
                    }

                    else -> null
                }

            override fun <T : Any> setValue(storeKey: String, valueType: Class<T>, value: T?) {
                if (storeKey == key) {
                    // This cast is safe because the framework ensures `value` is of type `V`.
                    setter?.execute(context, value as V)
                }
            }
        }

    /** Getter block with logic for retrieving the preference's value. */
    abstract val getter: GetterConfig<V>

    /** Setter block with logic for changing a preference's value. */
    abstract val setter: SetterConfig<V>?
}

@DslMarker
internal annotation class ApiFirstPreferenceDsl

/**
 * Getter configuration builder for an [ApiFirstPreference].
 *
 * ```
 * getter {
 *     execute { context ->
 *         // Get the value
 *         Foo(context)
 *     }
 * }
 * ```
 */
@ApiFirstPreferenceDsl
class GetterConfigBuilder<V : Any> {
    private var executeFun: ((Context) -> V)? = null

    // TODO: When we add other blocks, error if they're done out-of-order
    /** Declare the execute block of the getter. */
    fun execute(lambda: (Context) -> V) {
        executeFun = lambda
    }

    internal fun build(): GetterConfig<V> {
        return GetterConfig(
            execute = executeFun
                ?: throw IllegalStateException("Getter 'execute' block is required")
        )
    }
}

/**
 * Setter configuration builder for an [ApiFirstPreference].
 *
 * ```
 * setter {
 *     execute { context, value ->
 *         // Set the value
 *         Bar(context, value)
 *     }
 * }
 * ```
 */
@ApiFirstPreferenceDsl
class SetterConfigBuilder<V : Any> {
    private var executeFun: ((Context, V) -> Unit)? = null

    // TODO: When we add other blocks, error if they're done out-of-order
    /** Declare the execute block of the setter. */
    fun execute(lambda: (Context, V) -> Unit) {
        executeFun = lambda
    }

    internal fun build(): SetterConfig<V> {
        return SetterConfig(
            execute = executeFun
                ?: throw IllegalStateException("Setter 'execute' block is required")
        )
    }
}

/** Configuration builder for an [ApiFirstPreference]. */
@ApiFirstPreferenceDsl
class ApiFirstPreferenceConfigBuilder<V : Any>(val type: Class<V>) {
    /** Preference key. */
    lateinit var key: String
    private var getterConfig: GetterConfig<V>? = null
    private var setterConfig: SetterConfig<V>? = null

    /**
     * Build the [GetterConfig] from the given [GetterConfigBuilder] block.
     */
    fun getter(lambda: GetterConfigBuilder<V>.() -> Unit) {
        val builder = GetterConfigBuilder<V>()
        builder.lambda()
        getterConfig = builder.build()
    }

    /**
     * Build the [SetterConfig] from the given [SetterConfigBuilder] block.
     */
    fun setter(lambda: SetterConfigBuilder<V>.() -> Unit) {
        val builder = SetterConfigBuilder<V>()
        builder.lambda()
        setterConfig = builder.build()
    }

    /** Create an instance of [ApiFirstPreference] from its configuration. */
    fun build(): ApiFirstPreference<V> {
        if (!this::key.isInitialized) {
            throw IllegalStateException("'key' is required")
        }

        // keep a copy of the preference key
        val preferenceKey = key

        return object : ApiFirstPreference<V>() {
            override val getter: GetterConfig<V> =
                getterConfig ?: throw IllegalStateException("'getter' block is required")
            override val setter: SetterConfig<V>? = setterConfig
            override val valueType: Class<V> = type
            override val key: String = preferenceKey
        }
    }
}

/**
 * A factory function to create an instance of [ApiFirstPreference].
 * This is a convenient way to instantiate a preference without creating a new concrete class.
 *
 * ```
 * createPreference {
 *     key = "PREFERENCE_KEY"
 *
 *     getter {
 *         execute { context ->
 *             // Get the value
 *             Foo(context)
 *         }
 *     }
 *
 *     setter {
 *         execute { context, value ->
 *             // Set the value
 *             Bar(context, value)
 *         }
 *     }
 * }
 * ```
 */
inline fun <reified V : Any> createPreference(
    lambda: ApiFirstPreferenceConfigBuilder<V>.() -> Unit
): ApiFirstPreference<V> {
    val builder = ApiFirstPreferenceConfigBuilder(V::class.java)
    builder.lambda()
    return builder.build()
}


