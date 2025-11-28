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
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.datastore.and
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.apifirst.preconditions.Allowed
import com.android.settingslib.metadata.apifirst.preconditions.ApiFirstPreconditions
import com.android.settingslib.metadata.apifirst.types.ApiFirstType

/** Configuration of the [ApiFirstPreference] permissions. */
data class PermissionsConfig(
    val permissions: List<String>,
)

/** Configuration of the [ApiFirstPreference] preconditions. */
data class PreconditionsConfig(
    val preconditions: (Context) -> ApiFirstPreconditions,
)

/** Configuration of the [ApiFirstPreference] get. */
data class GetConfig<V : Any>(
    val permissions: List<String>? = null,
    val preconditions: ((Context) -> ApiFirstPreconditions)? = null,
    val execute: (Context) -> V
)

/** Configuration of the [ApiFirstPreference] set. */
data class SetConfig<V : Any>(
    val permissions: List<String>? = null,
    val preconditions: ((Context) -> ApiFirstPreconditions)? = null,
    val valuePreconditions: ((Context, V) -> ApiFirstPreconditions)? = null,
    val execute: (Context, V) -> Unit
)

/**
 * TODO: Remove the typing from the preference and infer it based on the passed type
 *
 * A preference abstraction to describe the ability of getting and (optionally) setting a value
 * specific to that preference, without relying on binding to an actual UI widget. This class is
 * produced when an Engineer in a partner team migrates their preference to Catalyst using the 2026
 * "Lightweight" way. It sets suitable defaults, and converts between the code we are asking
 * partner teams to write and the methods which Catalyst expects.
 */
abstract class ApiFirstPreference<V : Any>() : PersistentPreference<V> {
    // TODO: Maybe refactor, use common function for both get/setPermissions
    private fun getPermissions(): Permissions {
        val permissionsList = mutableListOf<String>()
        screenPermissions?.let { permissionsList.addAll(it) }
        commonPermissions?.let { permissionsList.addAll(it.permissions) }
        get.permissions?.let { permissionsList.addAll(it) }

        var perms = Permissions.EMPTY
        for (perm in permissionsList) {
            perms = perms and perm
        }

        return perms
    }

    private fun setPermissions(): Permissions {
        val permissionsList = mutableListOf<String>()
        screenPermissions?.let { permissionsList.addAll(it) }
        commonPermissions?.let { permissionsList.addAll(it.permissions) }
        set?.permissions?.let { permissionsList.addAll(it) }

        var perms = Permissions.EMPTY
        for (perm in permissionsList) {
            perms = perms and perm
        }

        return perms
    }

    // TODO: Maybe refactor, use common function for both get/setPreconditions
    private fun getPreconditions(context: Context): ApiFirstPreconditions {
        screenPreconditions?.invoke(context)?.let {
            if (it != Allowed) return it
        }

        commonPreconditions?.preconditions?.invoke(context)?.let {
            if (it != Allowed) return it
        }

        get.preconditions?.invoke(context)?.let {
            if (it != Allowed) return it
        }

        return Allowed
    }

    private fun setPreconditions(context: Context): ApiFirstPreconditions {
        screenPreconditions?.invoke(context)?.let {
            if (it != Allowed) return it
        }

        commonPreconditions?.preconditions?.invoke(context)?.let {
            if (it != Allowed) return it
        }

        set?.preconditions?.invoke(context)?.let {
            if (it != Allowed) return it
        }

        return Allowed
    }

    override fun getReadPermissions(context: Context) = getPermissions()
    override fun getWritePermissions(context: Context) = setPermissions()

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        when (getPreconditions(context)) {
            Allowed -> ReadWritePermit.ALLOW
            else -> ReadWritePermit.DISALLOW
        }

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        when (setPreconditions(context)) {
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
                if (storeKey == key) {
                    // This cast is safe because the framework ensures `value` is of type `V`.
                    set?.execute(context, value as V)
                }
            }
        }

    var screenPermissions: List<String>? = null
    // TODO: Also consider the preconditions description
    var screenPreconditions: ((Context) -> ApiFirstPreconditions)? = null

    /** Permissions of the preference's value. */
    abstract val commonPermissions: PermissionsConfig?

    /** Preconditions of the preference's value. */
    abstract val commonPreconditions: PreconditionsConfig?

    /** Get block with logic for retrieving the preference's value. */
    abstract val get: GetConfig<V>

    /** Set block with logic for changing a preference's value. */
    abstract val set: SetConfig<V>?
}

@DslMarker
internal annotation class ApiFirstPreferenceDsl

@ApiFirstPreferenceDsl
class PermissionsConfigBuilder(perms: List<String>) {
    private var permissionsList: List<String> = perms

    internal fun build(): PermissionsConfig {
        return PermissionsConfig(
            permissions = permissionsList,
        )
    }
}

@ApiFirstPreferenceDsl
class PreconditionsConfigBuilder(description: String, lambda: (Context) -> ApiFirstPreconditions) {
    private var preconditionsFun: (Context) -> ApiFirstPreconditions = lambda
    private var preconditionsDescription: String = description // TODO: Make use of this, too

    internal fun build(): PreconditionsConfig {
        return PreconditionsConfig(
            preconditions = preconditionsFun,
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
    private var permissionsRequired: List<String>? = null
    private var preconditionsFun: ((Context) -> ApiFirstPreconditions)? = null
    private var preconditionDescription: String? = null
    private var executeFun: ((Context) -> V)? = null

    /** Sets permissions for the get. */
    fun permissions(permissionsList: List<String>) {
        permissionsRequired = permissionsList
    }

    /** Defines a precondition check that must pass for the get to be executed. */
    fun preconditions(description: String, lambda: (Context) -> ApiFirstPreconditions) {
        preconditionDescription = description
        preconditionsFun = lambda
    }

    /*
     * TODO: When we add other blocks, error if they're done out-of-order. Make sure they are
     *       called only once.
     */
    /** Declare the execute block of the get. */
    fun execute(lambda: (Context) -> V) {
        executeFun = lambda
    }

    internal fun build(): GetConfig<V> {
        return GetConfig(
            permissions = permissionsRequired,
            preconditions = preconditionsFun,
            execute = executeFun
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
    private var permissionsRequired: List<String>? = null
    private var preconditionsFun: ((Context) -> ApiFirstPreconditions)? = null
    private var preconditionDescription: String? = null
    private var valuePreconditionsFun: ((Context, V) -> ApiFirstPreconditions)? = null
    private var valuePreconditionDescription: String? = null
    private var executeFun: ((Context, V) -> Unit)? = null

    /** Sets permissions for the set. */
    fun permissions(permissionsList: List<String>) {
        permissionsRequired = permissionsList
    }

    /** Defines a precondition check that must pass for the set to be executed. */
    fun preconditions(description: String, lambda: (Context) -> ApiFirstPreconditions) {
        preconditionDescription = description
        preconditionsFun = lambda
    }

    /** Defines a value precondition check that must pass for the set to be executed. */
    fun valuePreconditions(description: String, lambda: (Context, V) -> ApiFirstPreconditions) {
        valuePreconditionDescription = description
        valuePreconditionsFun = lambda
    }

    /*
     * TODO: When we add other blocks, error if they're done out-of-order. Make sure they are
     *       called only once.
     */
    /** Declare the execute block of the set. */
    fun execute(lambda: (Context, V) -> Unit) {
        executeFun = lambda
    }

    internal fun build(): SetConfig<V> {
        return SetConfig(
            permissions = permissionsRequired,
            preconditions = preconditionsFun,
            valuePreconditions = valuePreconditionsFun,
            execute = executeFun
                ?: throw IllegalStateException("Set 'execute' block is required")
        )
    }
}

/** Configuration builder for an [ApiFirstPreference]. */
@ApiFirstPreferenceDsl
class ApiFirstPreferenceConfigBuilder<V : Any>(val preferenceValueType: Class<V>) {
    lateinit var key: String
    lateinit var type: ApiFirstType
    var purpose: Int = 0
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
    fun build(): ApiFirstPreference<V> {
        if (!this::key.isInitialized) {
            throw IllegalStateException("'key' is required")
        }

        if (!this::type.isInitialized) {
            throw IllegalStateException("'type' is required")
        }

        // keep a copy of the preference key and purpose
        val preferenceKey = key
        val purpose = purpose

        return object : ApiFirstPreference<V>() {
            override val commonPermissions: PermissionsConfig? = permissionsConfig
            override val commonPreconditions: PreconditionsConfig? = preconditionsConfig
            override val get: GetConfig<V> =
                getConfig ?: throw IllegalStateException("'get' block is required")
            override val set: SetConfig<V>? = setConfig
            override val valueType: Class<V> = preferenceValueType // TODO: Use the passed `type`
            override val key: String = preferenceKey
            override val purpose: Int = purpose
        }
    }
}
