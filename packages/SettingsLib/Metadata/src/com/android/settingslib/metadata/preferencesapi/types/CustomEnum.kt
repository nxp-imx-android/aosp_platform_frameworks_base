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

import kotlin.reflect.KClass

/** An entry from the enum. */
class CustomEnum<T, E>(enumClass: KClass<E>) : ApiType<E> where E : Enum<E>, E : EnumApi<T> {

    @Suppress("UNCHECKED_CAST")
    private val entries: Array<E> = enumClass.java.enumConstants ?: (emptyArray<Any>() as Array<E>)

    private val valueMap: Map<T, E> = entries.associateBy { it.asApiValue }

    /** Finds the Enum constant associated with the given API value. */
    fun fromApiValue(value: T): E? = valueMap[value]

    /** Returns all entries. */
    fun getEntries(): List<E> = entries.toList()
}

/**
 * Defines a contract for Enums that require mapping to a specific API value type and providing a
 * human-readable purpose.
 *
 * This interface allows generic wrappers to handle serialization and UI display logic uniformly
 * across different Enum types.
 *
 * @param T The type of the value used by the API (e.g., [Int], [String]).
 *
 * Example usage:
 * ```
 * enum class ConnectionState(override val asApiValue: Int, override val purpose: Int) :
 *     EnumApi<Int> {
 *     CONNECTED(1, R.string.connected),
 *     DISCONNECTED(0, R.string.disconnected),
 * }
 * ```
 */
interface EnumApi<T> {
    val asApiValue: T
    val purpose: Int
}

