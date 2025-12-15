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

package com.android.settingslib.metadata.preferencesapi

import android.content.Context
import androidx.annotation.StringRes
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.NoOpKeyedObservable
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.and
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.preferencesapi.ExceptionMessagesFormatter.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.preferencesapi.ExceptionMessagesFormatter.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.ApiPreconditions
import com.android.settingslib.metadata.preferencesapi.preconditions.Disallowed
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import kotlinx.coroutines.runBlocking

/**
 * The context for an API-first operation, providing access to the application [Context] and
 * any necessary environmental information for executing preference
 * operations, ensuring that each operation has a consistent and secure context to run in.
 *
 * @property context The application context, used for accessing system services and resources.
 * @property parameters A map of validated key-value pairs that can be used to parameterize
 *   the behavior of the preference. These parameters are derived from the preference screen's
 *   configuration and are guaranteed to be valid.
 */
class ApiOperationContext(
    val context: Context,
    val parameters: ValidatedKeyParameters
)

/** Configuration of the [ApiPreference] flag. */
class FlagConfig(val check: () -> Boolean)

/** Configuration of the [ApiPreference] permissions. */
class PermissionsConfig(incomingPermissions: List<String>) {
    // Create a new, immutable list from the incoming one, avoiding unforeseen changes to the list
    val permissions: List<String> = incomingPermissions.toList()
}

/** Configuration of the [ApiPreference] preconditions. */
class PreconditionsConfig(
    @StringRes val description: Int,
    val check: suspend ApiOperationContext.() -> ApiPreconditions,
)

/** Configuration of the [ApiPreference] get. */
class GetConfig<V : Any>(
    val permissions: PermissionsConfig? = null,
    val preconditions: PreconditionsConfig? = null,
    val execute: suspend ApiOperationContext.() -> V
)

/** Configuration of the [ApiPreference] value preconditions. */
class ValuePreconditionsConfig<V : Any>(
    @StringRes val description: Int,
    val check: suspend (ApiOperationContext.(V) -> ApiPreconditions),
)

/** Configuration of the [ApiPreference] set. */
class SetConfig<V : Any>(
    val permissions: PermissionsConfig? = null,
    val preconditions: PreconditionsConfig? = null,
    val valuePreconditions: ValuePreconditionsConfig<V>? = null,
    val execute: (suspend ApiOperationContext.(V) -> Unit)
)

/**
 * A preference abstraction to describe the ability of getting and (optionally) setting a value
 * specific to that preference, without relying on binding to an actual UI widget. This class is
 * produced when an Engineer in a partner team migrates their preference to Catalyst using the 2026
 * "Lightweight" way. It sets suitable defaults, and converts between the code we are asking
 * partner teams to write and the methods which Catalyst expects.
 */
abstract class ApiPreference<V : Any>(val isFlagEnabled: Boolean) : PersistentPreference<V> {
    companion object {
        private const val VALUE_TYPE_MISMATCH_ERROR = "Value type mismatch. Expected %s, got %s"

        private fun buildValueTypeMismatchError(expected: Class<*>, actual: Class<*>) =
            String.format(VALUE_TYPE_MISMATCH_ERROR, expected.name, actual.name)
    }

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
     * Creates and returns an [ApiOperationContext] for the current preference.
     *
     * This method encapsulates the creation of the operation context, ensuring that it is
     * consistently initialized with the necessary application [Context] and validated key
     * parameters from the preference screen. If no screen parameters are available, it defaults
     * to an empty set of parameters.
     *
     * @param context The application context to be used in the operation context.
     * @return An initialized [ApiOperationContext] instance.
     */
    private fun getApiOperationContext(context: Context): ApiOperationContext {
        val keyParameters = screenParameters ?: ValidatedKeyParameters.EMPTY
        return ApiOperationContext(context, keyParameters)
    }

    /**
     * Evaluates preconditions in order: screen-level, common, and operation-specific.
     * Returns the first precondition that is not [Allowed], or [Allowed] if all preconditions
     * are met.
     */
    private suspend fun evaluatePreconditions(
        context: Context,
        operationPreconditions: PreconditionsConfig?
    ): ApiPreconditions {
        val operationContext = getApiOperationContext(context)
        screenPreconditions?.check(operationContext)?.let {
            if (it != Allowed) return it
        }

        preconditions?.check(operationContext)?.let {
            if (it != Allowed) return it
        }

        operationPreconditions?.check(operationContext)?.let {
            if (it != Allowed) return it
        }

        return Allowed
    }

    override fun getReadPermissions(context: Context) = buildPermissions(get.permissions)
    override fun getWritePermissions(context: Context) = buildPermissions(set?.permissions)

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        // TODO(b/469317113): This should run asynchronously
        runBlocking {
            when (evaluatePreconditions(context, get.preconditions)) {
                Allowed -> ReadWritePermit.ALLOW
                else -> ReadWritePermit.DISALLOW
            }
        }

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        // TODO(b/469317113): This should run asynchronously
        runBlocking {
            when (evaluatePreconditions(context, set?.preconditions)) {
                Allowed -> ReadWritePermit.ALLOW
                else -> ReadWritePermit.DISALLOW
            }
        }

    override fun storage(context: Context): KeyValueStore =
        object : NoOpKeyedObservable<String>(), KeyValueStore {
            private val operationContext = getApiOperationContext(context)

            override fun contains(storeKey: String): Boolean = storeKey == key

            override fun <T : Any> getValue(storeKey: String, valueType: Class<T>): T? =
                // TODO(b/469317113): This should run asynchronously
                runBlocking {
                    when {
                        storeKey == key -> {
                            get.execute(operationContext) as T?
                        }

                        else -> null
                    }
                }

            override fun <T : Any> setValue(storeKey: String, valueType: Class<T>, value: T?) =
                // TODO(b/469317113): This should run asynchronously
                runBlocking {
                // Catalyst's KeyValueStore is designed to handle arbitrary key/value pairs.
                // However, the API-first approach dictates that each [ApiPreference] instance
                // is responsible for a single, specific key. Thus, ignoring calls for other keys.
                if (storeKey != key) {
                    return@runBlocking
                }

                // If value type is not of the preference valueType (V), throw an exception
                if (value != null && !this@ApiPreference.valueType.isInstance(value)) {
                    throw IllegalArgumentException(
                        buildValueTypeMismatchError(
                            this@ApiPreference.valueType,
                            value.javaClass
                        )
                    )
                }

                // This cast is safe because we already checked the `value` is of type `V`
                val valueV = value as V
                val valuePreconditionsCheck =
                    set?.valuePreconditions?.check?.invoke(operationContext, valueV) ?: Allowed
                when (valuePreconditionsCheck) {
                    Allowed -> set?.execute(operationContext, valueV)
                    is Disallowed -> error(
                        context.getString(
                            valuePreconditionsCheck.reason
                        )
                    )
                }
            }
        }

    /** The validated key-value parameters from the preference screen this preference belongs to. */
    abstract val screenParameters: ValidatedKeyParameters?

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
internal annotation class ApiPreferenceDsl

@ApiPreferenceDsl
class FlagConfigBuilder(val lambda: () -> Boolean) {
    internal fun build(): FlagConfig {
        return FlagConfig(check = lambda)
    }
}

@ApiPreferenceDsl
class PermissionsConfigBuilder(val permissions: List<String>) {
    internal fun build(): PermissionsConfig {
        return PermissionsConfig(
            incomingPermissions = permissions,
        )
    }
}

@ApiPreferenceDsl
class PreconditionsConfigBuilder(
    @StringRes val description: Int,
    val lambda: suspend ApiOperationContext.() -> ApiPreconditions
) {
    internal fun build(): PreconditionsConfig {
        return PreconditionsConfig(
            description = description,
            check = lambda,
        )
    }
}

/**
 * Get configuration builder for an [ApiPreference].
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
@ApiPreferenceDsl
class GetConfigBuilder<V : Any> {
    private var permissionsConfig: PermissionsConfig? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var executeBlock: (suspend ApiOperationContext.() -> V)? = null

    /** Sets permissions for the get. */
    fun permissions(permissionsList: List<String>) {
        if (permissionsConfig != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preconditionsConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        permissionsConfig = PermissionsConfig(permissionsList)
    }

    /** Defines a precondition check that must pass for the get to be executed. */
    fun preconditions(@StringRes description: Int, lambda: suspend ApiOperationContext.() -> ApiPreconditions) {
        if (preconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (executeBlock != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = PreconditionsConfig(description, lambda)
    }

    /** Declare the execute block of the get. */
    fun execute(lambda: suspend ApiOperationContext.() -> V) {
        if (executeBlock != null) {
            error(getExceptionMessageMultipleDefines("execute"))
        }

        executeBlock = lambda
    }

    internal fun build(): GetConfig<V> {
        return GetConfig(
            permissions = permissionsConfig,
            preconditions = preconditionsConfig,
            execute = executeBlock ?: error("get 'execute' block is required")
        )
    }
}

/**
 * Set configuration builder for an [ApiPreference].
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
@ApiPreferenceDsl
class SetConfigBuilder<V : Any> {
    private var permissionsConfig: PermissionsConfig? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var valuePreconditionsConfig: ValuePreconditionsConfig<V>? = null
    private var executeBlock: (suspend ApiOperationContext.(V) -> Unit)? = null

    /** Sets permissions for the set. */
    fun permissions(permissionsList: List<String>) {
        if (permissionsConfig != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preconditionsConfig != null || valuePreconditionsConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        permissionsConfig = PermissionsConfig(permissionsList)
    }

    /** Defines a precondition check that must pass for the set to be executed. */
    fun preconditions(@StringRes description: Int, lambda: ApiOperationContext.() -> ApiPreconditions) {
        if (preconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (valuePreconditionsConfig != null || executeBlock != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = PreconditionsConfig(description, lambda)
    }

    /** Defines a value precondition check that must pass for the set to be executed. */
    fun valuePreconditions(@StringRes description: Int, lambda: suspend ApiOperationContext.(V) -> ApiPreconditions) {
        if (valuePreconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("valuePreconditions"))
        }

        if (executeBlock != null) {
            error(getExceptionMessageWrongOrder("valuePreconditions"))
        }

        valuePreconditionsConfig = ValuePreconditionsConfig(description, lambda)
    }

    /** Declare the execute block of the set. */
    fun execute(lambda: suspend ApiOperationContext.(V) -> Unit) {
        if (executeBlock != null) {
            error(getExceptionMessageMultipleDefines("execute"))
        }

        executeBlock = lambda
    }

    internal fun build(): SetConfig<V> {
        return SetConfig(
            permissions = permissionsConfig,
            preconditions = preconditionsConfig,
            valuePreconditions = valuePreconditionsConfig,
            execute = executeBlock ?: error("Set 'execute' block is required")
        )
    }
}

/** Configuration builder for an [ApiPreference]. */
@ApiPreferenceDsl
class ApiPreferenceConfigBuilder<V : Any>(
    val key: String,
    @StringRes val purpose: Int,
    val type: ApiType<V>,
    val valueType: Class<V>,
    val screenPermissions: PermissionsConfig?,
    val screenPreconditions: PreconditionsConfig?,
    val screenParameters: ValidatedKeyParameters?
) {
    private var flagConfig: FlagConfig? = null
    private var permissionsConfig: PermissionsConfig? = null
    private var preconditionsConfig: PreconditionsConfig? = null
    private var getConfig: GetConfig<V>? = null
    private var setConfig: SetConfig<V>? = null

    /**
     * Build the [FlagConfig] from the given [FlagConfigBuilder] block.
     */
    fun flag(lambda: () -> Boolean) {
        if (flagConfig != null) {
            error(getExceptionMessageMultipleDefines("flag"))
        }

        if (permissionsConfig != null || preconditionsConfig != null || getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("flag"))
        }

        flagConfig = FlagConfigBuilder(lambda).build()
    }

    /**
     * Build the [PermissionsConfig] from the given [PermissionsConfigBuilder] block.
     */
    fun permissions(permissionsList: List<String>) {
        if (permissionsConfig != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preconditionsConfig != null || getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        permissionsConfig = PermissionsConfigBuilder(permissionsList).build()
    }

    /**
     * Build the [PreconditionsConfig] from the given [PreconditionsConfigBuilder] block.
     */
    fun preconditions(@StringRes description: Int, lambda: ApiOperationContext.() -> ApiPreconditions) {
        if (preconditionsConfig != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (getConfig != null || setConfig != null) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        preconditionsConfig = PreconditionsConfigBuilder(description, lambda).build()
    }

    /**
     * Build the [GetConfig] from the given [GetConfigBuilder] block.
     */
    fun get(lambda: GetConfigBuilder<V>.() -> Unit) {
        if (getConfig != null) {
            error(getExceptionMessageMultipleDefines("get"))
        }

        if (setConfig != null) {
            error(getExceptionMessageWrongOrder("get"))
        }

        val builder = GetConfigBuilder<V>()
        builder.lambda()
        getConfig = builder.build()
    }

    /**
     * Build the [SetConfig] from the given [SetConfigBuilder] block.
     */
    fun set(lambda: SetConfigBuilder<V>.() -> Unit) {
        if (setConfig != null) {
            error(getExceptionMessageMultipleDefines("set"))
        }

        val builder = SetConfigBuilder<V>()
        builder.lambda()
        setConfig = builder.build()
    }

    /** Create an instance of [ApiPreference] from its configuration. */
    fun build() = object : ApiPreference<V>(flagConfig?.check() ?: true) {
        override val screenPermissions = this@ApiPreferenceConfigBuilder.screenPermissions
        override val screenPreconditions = this@ApiPreferenceConfigBuilder.screenPreconditions
        override val permissions: PermissionsConfig? = permissionsConfig
        override val preconditions: PreconditionsConfig? = preconditionsConfig
        override val get: GetConfig<V> = getConfig ?: error("'get' block is required")
        override val set: SetConfig<V>? = setConfig
        override val valueType: Class<V> = this@ApiPreferenceConfigBuilder.valueType
        override val key: String = this@ApiPreferenceConfigBuilder.key
        override val purpose: Int = this@ApiPreferenceConfigBuilder.purpose
        override val screenParameters: ValidatedKeyParameters? = this@ApiPreferenceConfigBuilder.screenParameters
    }
}
