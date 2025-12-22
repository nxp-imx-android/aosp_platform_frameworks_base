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
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.preferencesapi.category.Category
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.Utils.EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleParametersDefined
import com.android.settingslib.metadata.preferencesapi.preconditions.ApiPreconditions
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import com.android.settingslib.metadata.preferencesapi.types.GeneratedParameterType
import com.android.settingslib.metadata.preferencesapi.types.GeneratedTypeContext
import com.android.settingslib.metadata.preferenceHierarchy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

/**
 * Interface for preference screens that provide parameters in a non-static method.
 */
interface ProvidesParametersNonStatically {
    fun getAllPossibleParameters(context: Context): Flow<ValidatedKeyParameters>
}

/**
 * Scope for parameterization-related declarations.
 */
class ParameterizationConfig {
    internal class ApiParameterDefinition(
        val name: String,
        @StringRes val purpose: Int,
        val required: Boolean,
        val type: GeneratedParameterType
    )

    internal val parameters = mutableMapOf<String, ApiParameterDefinition>()
    internal var prepareScreenExtras: ((ValidatedKeyParameters, Bundle) -> Unit)? = null

    /**
     * Defines a parameter and adds it to the schema.
     *
     * @param name The unique name for the parameter.
     * @param purpose A human-readable purpose of the parameter.
     * @param required Whether this parameter must be provided. Defaults to `false`.
     * @param type The type of the parameter, used to generate its possible values.
     *
     * @throws IllegalArgumentException if a parameter with the same name is already defined.
     */
    fun parameter(
        name: String,
        @StringRes purpose: Int,
        required: Boolean = false,
        type: GeneratedParameterType
    ) {
        if (parameters.containsKey(name)) {
            throw IllegalArgumentException("Parameter '$name' is already defined.")
        }
        parameters[name] = ApiParameterDefinition(name, purpose, required, type)
    }

    /**
     * Declares how to convert the API-First parameters into the `Bundle` of extras required
     * to launch the fragment for this screen.
     */
    fun prepareScreenExtras(lambda: (ValidatedKeyParameters, Bundle) -> Unit) {
        if (prepareScreenExtras != null) {
            throw IllegalStateException(getExceptionMessageMultipleDefines("prepareScreenExtras"))
        }
        prepareScreenExtras = lambda
    }

    /**
     * Builds and returns the final [KeyParametersSchema] instance. For internal use by the DSL.
     */
    internal fun buildSchema(): KeyParametersSchema = KeyParametersSchema {
        parameters.values.map {
            parameter(name = it.name, purpose = it.purpose, required = it.required)
        }
    }
}

/**
 * Container for all information and preferences on a Settings screen which is intended to be
 * exposed via API using 2026 "Lightweight" way.
 */
abstract class PreferencesApiScreen(
    override val key: String,
    val topLevelSettingsCategory: Category,
    val fragment: KClass<out Fragment>,
    override val purpose: Int,
    val alreadyPartiallyMigrated: KClass<*>? = null,
) : PreferenceScreenMetadata, ProvidesParametersNonStatically {
    init {
        if (alreadyPartiallyMigrated != null) {
            require(key.startsWith(PARTIALLY_MIGRATED_PREFIX)) {
                "The key '$key' must start with '$PARTIALLY_MIGRATED_PREFIX' because it has an already migrated class."
            }
        }
    }

    override fun fragmentClass(): Class<out Fragment>? = fragment.java

    override fun isFlagEnabled(context: Context): Boolean =
        flag?.check() ?: super.isFlagEnabled(context)

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            for (preference in preferences) {
                if (preference.isFlagEnabled) {
                    +preference
                }
            }
        }

    var flag: FlagConfig? = null
    var parametersSchema: KeyParametersSchema? = null
    var screenPermissions: PermissionsConfig? = null
    var screenPreconditions: PreconditionsConfig? = null

    override val keyParameters: ValidatedKeyParameters?
        get() = if (::screenParameters.isInitialized) screenParameters else super.keyParameters

    override val launchScreenExtra: Bundle
        get() {
            val bundle = super.launchScreenExtra ?: Bundle()
            val keyParameters = keyParameters ?: return bundle

            prepareScreenExtras?.invoke(keyParameters, bundle)

            return bundle
        }

    private lateinit var screenParameters: ValidatedKeyParameters
    private var prepareScreenExtras: ((ValidatedKeyParameters, Bundle) -> Unit)? = null
    private var allPossibleParameters: ((Context) -> Collection<ValidatedKeyParameters>) = { emptyList() }

    val preferencesPermissions = mutableListOf<String>()

    val preferences = mutableListOf<ApiPreference<*>>()

    /**
     * A factory function to create an instance of [ApiPreference].
     * This is a convenient way to instantiate a preference without creating a new concrete class.
     *
     * ```
     * preference(
     *     key = "PREFERENCE_KEY",
     *     purpose = R.string.my_preference_purpose,
     *     type = AnyString
     * ) {
     *     flag { Flags.FooBarFlag() }
     *     permissions(listOf(Manifest.permission.PERMISSION))
     *     preconditions("My precondition description") { context ->
     *         if (conditionFoo(context)) {
     *             Allowed
     *         } else {
     *             HardwareUnsupported("Hardware Foo not connected")
     *         }
     *     }
     *
     *     get {
     *         permissions(listOf(Manifest.permission.PERMISSION_GET))
     *         preconditions("My get precondition description") { context ->
     *             if (conditionBar(context)) {
     *                 Allowed
     *             } else {
     *                 EnterpriseRestriction("Admin Bar restriction")
     *             }
     *         }
     *
     *         execute { context ->
     *             // Get the value
     *             Foo(context)
     *         }
     *     }
     *
     *     set {
     *         permissions(listOf(Manifest.permission.PERMISSION_SET))
     *         preconditions("My set precondition description") { context ->
     *             if (conditionFooBar(context)) {
     *                 Allowed
     *             } else {
     *                 Custom("condition FooBar not met")
     *             }
     *         }
     *         valuePreconditions("My value precondition description") { context, value ->
     *             if (conditionBarFoo(context, value)) {
     *                 Allowed
     *             } else {
     *                 Custom("Value not allowed to be set")
     *             }
     *         }
     *
     *         execute { context, value ->
     *             // Set the value
     *             Bar(context, value)
     *         }
     *     }
     * }
     * ```
     */
    protected inline fun <reified V : Any> preference(
        key: String,
        purpose: Int,
        type: ApiType<V>,
        lambda: ApiPreferenceConfigBuilder<V>.() -> Unit
    ) {
        val builder = ApiPreferenceConfigBuilder(
            key,
            purpose,
            type,
            V::class.java,
            screenPermissions,
            screenPreconditions,
            { keyParameters },
        )
        builder.lambda()
        preferences.add(builder.build())
    }

    /**
     * Initializes the [ValidatedKeyParameters] if this preference screen is parameterized.
     */
    fun initializeParameters(keyParameters: ValidatedKeyParameters) {
        screenParameters = keyParameters
    }

    /**
     * Returns a [Flow] of all possible [ValidatedKeyParameters] parameters if this preference
     * screen is parameterized, otherwise returns an empty flow.
     *
     * This method provides a stream of all valid combinations of parameters that can be used
     * to instantiate this preference screen.
     *
     * @param context The application context.
     * @return A [Flow] emitting all possible [ValidatedKeyParameters].
     */
    override fun getAllPossibleParameters(context: Context) = allPossibleParameters(context).asFlow()

    protected fun flag(lambda: () -> Boolean) {
        if (flag != null) {
            error(getExceptionMessageMultipleDefines("flag"))
        }

        if (parametersSchema != null || preferences.isNotEmpty() || screenPermissions != null || screenPreconditions != null) {
            error(getExceptionMessageWrongOrder("flag"))
        }

        flag = FlagConfig(lambda)
    }

    /**
     * Declares the parameterization for this preference screen.
     *
     * Example:
     * ```
     * parameters {
     *     parameter(
     *         name = "package",
     *         purpose = "The app package",
     *         required = true,
     *         type = GeneratedParameterType(...)
     *     )
     *
     *     prepareScreenExtras { parameters, extras ->
     *         // Convert from the specified parameters into the extras required
     *         // to launch the screen. Look at the fragment to see what extras
     *         // it depends on to figure out how to do this
     *         extras.putString("package", parameters["package"])
     *     }
     *
     * }
     * ```
     */
    protected fun parameters(lambda: ParameterizationConfig.() -> Unit) {
        if (parametersSchema != null) {
            throw IllegalStateException(getExceptionMessageMultipleDefines("parameters"))
        }

        if (preferences.isEmpty() && screenPermissions == null && screenPreconditions == null) {
            val scope = ParameterizationConfig()
            scope.lambda()
            parametersSchema = scope.buildSchema()
            prepareScreenExtras = scope.prepareScreenExtras

            val parametersSize = scope.parameters.size
            if (parametersSize == 0) {
                throw IllegalStateException(EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED)
            }
            if (parametersSize > 1) {
                throw IllegalStateException(
                    getExceptionMessageMultipleParametersDefined(parametersSize)
                )
            }

            val parameterToUse = scope.parameters.values.first()

            this@PreferencesApiScreen.allPossibleParameters = { context ->
                parameterToUse.type.lambda.invoke(GeneratedTypeContext(context)).map { parameterOption ->
                    this@PreferencesApiScreen.parametersSchema!!.prepare(
                        parameterToUse.name to parameterOption.value
                    )
                }
            }
        } else {
            throw IllegalStateException(getExceptionMessageWrongOrder("parameters"))
        }
    }

    protected fun permissions(permissions: List<String>) {
        if (screenPermissions != null) {
            error(getExceptionMessageMultipleDefines("permissions"))
        }

        if (preferences.isNotEmpty() || screenPreconditions != null) {
            error(getExceptionMessageWrongOrder("permissions"))
        }

        screenPermissions = PermissionsConfig(permissions)
    }

    protected fun preconditions(
        @StringRes description: Int,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    protected fun preconditions(
        description: String,
        lambda: suspend ApiOperationContext.() -> ApiPreconditions
    ) {
        setPreconditions(PreconditionsConfig(description, lambda))
    }

    private fun setPreconditions(config: PreconditionsConfig) {
        if (screenPreconditions != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (preferences.isNotEmpty()) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        screenPreconditions = config
    }

    companion object {
        const val PARTIALLY_MIGRATED_PREFIX = "api_"
    }
}
