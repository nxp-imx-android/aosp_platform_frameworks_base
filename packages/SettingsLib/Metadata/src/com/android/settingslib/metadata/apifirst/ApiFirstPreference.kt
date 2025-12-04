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
import androidx.annotation.StringRes
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.NoOpKeyedObservable
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.and
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.apifirst.preconditions.Allowed
import com.android.settingslib.metadata.apifirst.preconditions.ApiFirstPreconditions
import com.android.settingslib.metadata.apifirst.preconditions.Disallowed
import com.android.settingslib.metadata.apifirst.types.ApiFirstType

/** Configuration of the [ApiFirstPreference] permissions. */
class PermissionsConfig(incomingPermissions: List<String>) {
    // Create a new, immutable list from the incoming one, avoiding unforeseen changes to the list
    val permissions: List<String> = incomingPermissions.toList()
}

/** Configuration of the [ApiFirstPreference] preconditions. */
class PreconditionsConfig(
    val description: String,
    val check: (Context) -> ApiFirstPreconditions,
)

/** Configuration of the [ApiFirstPreference] get. */
class GetConfig<V : Any>(
    val permissions: PermissionsConfig? = null,
    val preconditions: PreconditionsConfig? = null,
    val execute: (Context) -> V
)

/** Configuration of the [ApiFirstPreference] value preconditions. */
class ValuePreconditionsConfig<V : Any>(
    val description: String,
    val check: ((Context, V) -> ApiFirstPreconditions),
)

/** Configuration of the [ApiFirstPreference] set. */
class SetConfig<V : Any>(
    val permissions: PermissionsConfig? = null,
    val preconditions: PreconditionsConfig? = null,
    val valuePreconditions: ValuePreconditionsConfig<V>? = null,
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
    /**
     * Builds the final [Permissions] object by combining screen-level, common, and
     * operation-specific permissions.
     */
    private fun buildPermissions(operationPermissions: PermissionsConfig?): Permissions {
        val permissionsList = mutableListOf<String>()
        screenPermissions?.let { permissionsList.addAll(it.permissions) }
        permissions?.let { permissionsList.addAll(it.permissions) }
        operationPermissions?.let { permissionsList.addAll(it.permissions) }

        return permissionsList.fold(Permissions.EMPTY) { acc, perm -> acc and perm }
    }

    /**
     * Evaluates preconditions in order: screen-level, common, and operation-specific.
     * Returns the first precondition that is not [Allowed], or [Allowed] if all preconditions
     * are met.
     */
    private fun evaluatePreconditions(
        context: Context,
        operationPreconditions: PreconditionsConfig?
    ): ApiFirstPreconditions {
        screenPreconditions?.check(context)?.let {
            if (it != Allowed) return it
        }

        preconditions?.check(context)?.let {
            if (it != Allowed) return it
        }

        operationPreconditions?.check(context)?.let {
            if (it != Allowed) return it
        }

        return Allowed
    }

    override fun getReadPermissions(context: Context) = buildPermissions(get.permissions)
    override fun getWritePermissions(context: Context) = buildPermissions(set?.permissions)

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        when (evaluatePreconditions(context, get.preconditions)) {
            Allowed -> ReadWritePermit.ALLOW
            else -> ReadWritePermit.DISALLOW
        }

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        when (evaluatePreconditions(context, set?.preconditions)) {
            Allowed -> ReadWritePermit.ALLOW
            else -> ReadWritePermit.DISALLOW
        }

    override fun storage(context: Context): KeyValueStore =
        object : NoOpKeyedObservable<String>(), KeyValueStore {
            override fun contains(storeKey: String): Boolean = storeKey == key

            override fun <T : Any> getValue(storeKey: String, valueType: Class<T>): T? =
                when {
                    storeKey == key -> {
                        get.execute(context) as T?
                    }

                    else -> null
                }

            override fun <T : Any> setValue(storeKey: String, valueType: Class<T>, value: T?) {
                // Catalyst's KeyValueStore is designed to handle arbitrary key/value pairs.
                // However, the api-first approach dictates that each ApiFirstPreference instance
                // is responsible for a single, specific key. Thus, ignoring calls for other keys.
                if (storeKey != key) {
                    return
                }

                // If value type is not of the preference valueType (V), throw an exception
                if (!this@ApiFirstPreference.valueType.isInstance(value)) {
                    throw IllegalArgumentException("Value type mismatch")
                }

                // This cast is safe because we already checked the `value` is of type `V`
                val valueV = value as V
                val valuePreconditionsCheck =
                    set?.valuePreconditions?.check?.invoke(context, valueV) ?: Allowed
                when (valuePreconditionsCheck) {
                    Allowed -> set?.execute(context, valueV)
                    is Disallowed -> throw IllegalStateException(valuePreconditionsCheck.reason)
                }
            }
        }

    /** Preference's permission on the screen level. */
    abstract val screenPermissions: PermissionsConfig?

    /** Preference's preconditions on the screen level. */
    abstract val screenPreconditions: PreconditionsConfig?

    /** Preference's permission. */
    abstract val permissions: PermissionsConfig?

    /** Preference's preconditions. */
    abstract val preconditions: PreconditionsConfig?

    /** Get block with logic for retrieving the preference's value. */
    abstract val get: GetConfig<V>

    /** Set block with logic for changing a preference's value. */
    abstract val set: SetConfig<V>?
}

@DslMarker
internal annotation class ApiFirstPreferenceDsl

@ApiFirstPreferenceDsl
class PermissionsConfigBuilder(val permissions: List<String>) {
    internal fun build(): PermissionsConfig {
        return PermissionsConfig(
            incomingPermissions = permissions,
        )
    }
}

@ApiFirstPreferenceDsl
class PreconditionsConfigBuilder(
    val description: String,
    val lambda: (Context) -> ApiFirstPreconditions
) {
    internal fun build(): PreconditionsConfig {
        return PreconditionsConfig(
            description = description,
            check = lambda,
        )
    }
}

/**
 * Get configuration builder for an [ApiFirstPreference].
 *
 * ```
 * get {
 *     execute { context ->
 *         // Get the value
 *         Foo(context)
 *     }
 * }
 * ```
 */
@ApiFirstPreferenceDsl
class GetConfigBuilder<V : Any> {
    private var permissionsConfig: PermissionsConfig? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var executeBlock: ((Context) -> V)? = null

    /** Sets permissions for the get. */
    fun permissions(permissionsList: List<String>) {
        permissionsConfig = PermissionsConfig(permissionsList)
    }

    /** Defines a precondition check that must pass for the get to be executed. */
    fun preconditions(description: String, lambda: (Context) -> ApiFirstPreconditions) {
        preconditionsConfig = PreconditionsConfig(description, lambda)
    }

    /*
     * TODO: When we add other blocks, error if they're done out-of-order. Make sure they are
     *       called only once.
     */
    /** Declare the execute block of the get. */
    fun execute(lambda: (Context) -> V) {
        executeBlock = lambda
    }

    internal fun build(): GetConfig<V> {
        return GetConfig(
            permissions = permissionsConfig,
            preconditions = preconditionsConfig,
            execute = executeBlock
                ?: throw IllegalStateException("get 'execute' block is required")
        )
    }
}

/**
 * Set configuration builder for an [ApiFirstPreference].
 *
 * ```
 * set {
 *     execute { context, value ->
 *         // Set the value
 *         Bar(context, value)
 *     }
 * }
 * ```
 */
@ApiFirstPreferenceDsl
class SetConfigBuilder<V : Any> {
    private var permissionsConfig: PermissionsConfig? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var valuePreconditionsConfig: ValuePreconditionsConfig<V>? = null
    private var executeBlock: ((Context, V) -> Unit)? = null

    /** Sets permissions for the set. */
    fun permissions(permissionsList: List<String>) {
        permissionsConfig = PermissionsConfig(permissionsList)
    }

    /** Defines a precondition check that must pass for the set to be executed. */
    fun preconditions(description: String, lambda: (Context) -> ApiFirstPreconditions) {
        preconditionsConfig = PreconditionsConfig(description, lambda)
    }

    /** Defines a value precondition check that must pass for the set to be executed. */
    fun valuePreconditions(description: String, lambda: (Context, V) -> ApiFirstPreconditions) {
        valuePreconditionsConfig = ValuePreconditionsConfig<V>(description, lambda)
    }

    /*
     * TODO: When we add other blocks, error if they're done out-of-order. Make sure they are
     *       called only once.
     */
    /** Declare the execute block of the set. */
    fun execute(lambda: (Context, V) -> Unit) {
        executeBlock = lambda
    }

    internal fun build(): SetConfig<V> {
        return SetConfig(
            permissions = permissionsConfig,
            preconditions = preconditionsConfig,
            valuePreconditions = valuePreconditionsConfig,
            execute = executeBlock
                ?: throw IllegalStateException("Set 'execute' block is required")
        )
    }
}

/** Configuration builder for an [ApiFirstPreference]. */
@ApiFirstPreferenceDsl
class ApiFirstPreferenceConfigBuilder<V : Any>(val key: String,
                                               @StringRes val purpose: Int,
                                               val type: ApiFirstType<V>,
                                               val valueType: Class<V>,
                                               val screenPermissions: PermissionsConfig?,
                                               val screenPreconditions: PreconditionsConfig?)
{
    private var permissionsConfig: PermissionsConfig? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var getConfig: GetConfig<V>? = null
    private var setConfig: SetConfig<V>? = null

    /**
     * Build the [PermissionsConfig] from the given [PermissionsConfigBuilder] block.
     */
    fun permissions(permissionsList: List<String>) {
        val builder = PermissionsConfigBuilder(permissionsList)
        permissionsConfig = builder.build()
    }

    /**
     * Build the [PreconditionsConfig] from the given [PreconditionsConfigBuilder] block.
     */
    fun preconditions(description: String, lambda: (Context) -> ApiFirstPreconditions) {
        val builder = PreconditionsConfigBuilder(description, lambda)
        preconditionsConfig = builder.build()
    }

    /**
     * Build the [GetConfig] from the given [GetConfigBuilder] block.
     */
    fun get(lambda: GetConfigBuilder<V>.() -> Unit) {
        val builder = GetConfigBuilder<V>()
        builder.lambda()
        getConfig = builder.build()
    }

    /**
     * Build the [SetConfig] from the given [SetConfigBuilder] block.
     */
    fun set(lambda: SetConfigBuilder<V>.() -> Unit) {
        val builder = SetConfigBuilder<V>()
        builder.lambda()
        setConfig = builder.build()
    }

    /** Create an instance of [ApiFirstPreference] from its configuration. */
    fun build() = object : ApiFirstPreference<V>() {
        override val screenPermissions = this@ApiFirstPreferenceConfigBuilder.screenPermissions
        override val screenPreconditions = this@ApiFirstPreferenceConfigBuilder.screenPreconditions
        override val permissions: PermissionsConfig? = permissionsConfig
        override val preconditions: PreconditionsConfig? = preconditionsConfig
        override val get: GetConfig<V> =
            getConfig ?: throw IllegalStateException("'get' block is required")
        override val set: SetConfig<V>? = setConfig
        override val valueType: Class<V> = this@ApiFirstPreferenceConfigBuilder.valueType
        override val key: String = this@ApiFirstPreferenceConfigBuilder.key
        override val purpose: Int = this@ApiFirstPreferenceConfigBuilder.purpose
    }
}
