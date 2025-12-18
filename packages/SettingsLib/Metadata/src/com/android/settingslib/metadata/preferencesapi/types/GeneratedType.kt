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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import androidx.annotation.StringRes


/**
 * The context for a GeneratedType, providing access to the [Context] and any necessary
 * environmental information for executing the lambda.
 *
 * @property context The application context, used for accessing system services and resources.
 */
class GeneratedTypeContext(val context: Context)

/**
 * Represents a single possible value for a [GeneratedType].
 *
 * Each result type provides a specific value and a human-readable description,
 * which can be used by clients to better understand the value.
 *
 * For example, if the value represents the UUID of an app, then the description should be the
 * the app package name or the app name.
 *
 * @param T The underlying data type of the value.
 * @property description A human-readable description of this specific value.
 * @property value The actual value.
 */
class GeneratedValue<T>(
    val description: String,
    val value: T,
)

class GeneratedType<T: Any>(
    @field:StringRes val description: Int,
    val lambda: GeneratedTypeContext.() -> Collection<GeneratedValue<T>>
) : ApiType<GeneratedValue<T>>


typealias GeneratedParameterType = GeneratedType<String>