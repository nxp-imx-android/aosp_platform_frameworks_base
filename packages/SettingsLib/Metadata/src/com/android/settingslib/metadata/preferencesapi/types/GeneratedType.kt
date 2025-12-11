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

typealias GeneratedParameterType = GeneratedType<ResultValue>

/**
 * The context for a GeneratedType, providing access to the [Context] and any necessary
 * environmental information for executing the lambda.
 *
 * @property context The application context, used for accessing system services and resources.
 */
class GeneratedTypeContext(val context: Context)

class GeneratedType<V : Any>(
    @StringRes val description: Int,
    val lambda: GeneratedTypeContext.() -> Collection<V>
) : ApiType<V>
