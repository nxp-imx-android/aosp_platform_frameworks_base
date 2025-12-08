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
import androidx.fragment.app.Fragment
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.apifirst.ExceptionMessagesFormatter.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.apifirst.ExceptionMessagesFormatter.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.apifirst.category.Category
import com.android.settingslib.metadata.apifirst.preconditions.ApiFirstPreconditions
import com.android.settingslib.metadata.apifirst.types.ApiFirstType
import com.android.settingslib.metadata.preferenceHierarchy
import kotlinx.coroutines.CoroutineScope
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

/**
 * Container for all information and preferences on a Settings screen which is intended to be
 * exposed via API using 2026 "Lightweight" way.
 */
abstract class ApiFirstPreferenceScreen(
    override val key: String,
    val topLevelSettingsCategory: Category,
    val fragment: KClass<out Fragment>,
    override val purpose: Int,
    val alreadyPartiallyMigrated: KClass<*>? = null,
) : PreferenceScreenMetadata {
    override fun fragmentClass(): Class<out Fragment>? = fragment.java

    override fun getPreferenceHierarchy(
        context: Context,
        coroutineScope: CoroutineScope,
    ): PreferenceHierarchy =
        preferenceHierarchy(context) {
            for (preference in preferences) {
                +preference
            }
        }

    var parametersSchema: KeyParametersSchema? = null
    var screenPermissions: PermissionsConfig? = null
    var screenPreconditions: PreconditionsConfig? = null

    val preferences = mutableListOf<ApiFirstPreference<*>>()

    /**
     * A factory function to create an instance of [ApiFirstPreference].
     * This is a convenient way to instantiate a preference without creating a new concrete class.
     *
     * ```
     * preference(
     *     key = "PREFERENCE_KEY",
     *     purpose = R.string.my_preference_purpose,
     *     type = AnyString
     * ) {
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
        type: ApiFirstType<V>,
        lambda: ApiFirstPreferenceConfigBuilder<V>.() -> Unit
    ) {
        val builder = ApiFirstPreferenceConfigBuilder(
            key,
            purpose,
            type,
            V::class.java,
            screenPermissions,
            screenPreconditions
        )
        builder.lambda()
        preferences.add(builder.build())
    }

    protected fun parameters(lambda: KeyParametersSchema.Builder.() -> Unit) {
        if (parametersSchema != null) {
            error(getExceptionMessageMultipleDefines("parameters"))
        }

        if (preferences.isNotEmpty() || screenPermissions != null || screenPreconditions != null) {
            error(getExceptionMessageWrongOrder("parameters"))
        }

        parametersSchema = KeyParametersSchema(lambda)
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
        lambda: (Context) -> ApiFirstPreconditions
    ) {
        if (screenPreconditions != null) {
            error(getExceptionMessageMultipleDefines("preconditions"))
        }

        if (preferences.isNotEmpty()) {
            error(getExceptionMessageWrongOrder("preconditions"))
        }

        screenPreconditions = PreconditionsConfig(description, lambda)
    }
}
