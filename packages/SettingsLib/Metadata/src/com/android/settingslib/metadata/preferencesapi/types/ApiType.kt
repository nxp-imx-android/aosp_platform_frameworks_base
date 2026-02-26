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
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.ValidatedKeyParameters

interface ApiType<V> {

    fun getParametersSchema(): KeyParametersSchema? = null
    fun getParameters(): ValidatedKeyParameters? = null

    fun getType(): Class<V>
    fun getDescription(context: Context): String

    /**
     * Returns a globally unique key which refers to this type. It must be stable through multiple
     * executions as long as we don't update the app.
     */
    fun getKey(): String
}
