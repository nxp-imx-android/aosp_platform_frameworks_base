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
import androidx.fragment.app.Fragment
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.apifirst.preconditions.ApiFirstPreconditions
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
    val topLevelSettingsCategory: String,
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
            for (preference in preferencesList) {
                preference.apply {
                    screenPermissions = preferencesPermissions
                    screenPreconditions = preferencesPreconditionsFun
                }
                +preference
            }
        }

    var parametersSchema: KeyParametersSchema? = null
    val preferencesPermissions = mutableListOf<String>()

    // TODO: Wrap this in a single subtype
    var preferencesPreconditionsDescription: String? = null
    var preferencesPreconditionsFun: ((Context) -> ApiFirstPreconditions)? = null

    val preferencesList = mutableListOf<ApiFirstPreference<*>>()

    /**
     * TODO: Update comment example
     *
     * A factory function to create an instance of [ApiFirstPreference].
     * This is a convenient way to instantiate a preference without creating a new concrete class.
     *
     * ```
     * createPreference {
     *     key = "PREFERENCE_KEY"
     *
     *     get {
     *         execute { context ->
     *             // Get the value
     *             Foo(context)
     *         }
     *     }
     *
     *     set {
     *         execute { context, value ->
     *             // Set the value
     *             Bar(context, value)
     *         }
     *     }
     * }
     * ```
     */
    protected inline fun <reified V : Any> preference(
        lambda: ApiFirstPreferenceConfigBuilder<V>.() -> Unit
    ) {
        val builder = ApiFirstPreferenceConfigBuilder(V::class.java)
        builder.lambda()
        preferencesList.add(builder.build())
    }

    protected fun parameters(lambda: KeyParametersSchema.Builder.() -> Unit) {
        parametersSchema = KeyParametersSchema(lambda)
    }

    protected fun permissions(permissions: List<String>) {
        preferencesPermissions.addAll(permissions)
    }

    protected fun preconditions(
        description: String,
        lambda: (Context) -> ApiFirstPreconditions
    ) {
        preferencesPreconditionsDescription = description
        preferencesPreconditionsFun = lambda
    }
}
