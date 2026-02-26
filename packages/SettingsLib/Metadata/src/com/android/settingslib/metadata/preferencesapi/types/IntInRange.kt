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
import com.android.settingslib.metadata.R

/** Any int in the given range, along the given step. */
class IntInRange(val min: Int?, val max: Int?, val step: Int = 1): ApiType<Int> {

    override fun getType(): Class<Int> = Int::class.java

    init {
        require(min != null || max != null)
    }

    override fun getDescription(context: Context): String {
        return when {
            min != null && max != null ->
                context.getString(R.string.int_in_range_type_description_between, min, max, step)
            min != null -> context.getString(R.string.int_in_range_type_description_from, min, step)
            max != null -> context.getString(R.string.int_in_range_type_description_to, max, step)
            else -> error("There needs to be at least a minimum bound or a maximum bound in an IntInRange")
        }
    }

    override fun getKey(): String = "IntInRange:${min}:${max}:${step}"
}
