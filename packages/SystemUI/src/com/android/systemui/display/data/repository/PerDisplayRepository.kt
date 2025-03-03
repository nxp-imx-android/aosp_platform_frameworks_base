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

package com.android.systemui.display.data.repository

import android.util.Log
import android.view.Display
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.app.tracing.traceSection
import com.android.systemui.Dumpable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dump.DumpManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest

/**
 * Used to create instances of type `T` for a specific display.
 *
 * This is useful for resources or objects that need to be managed independently for each connected
 * display (e.g., UI state, rendering contexts, or display-specific configurations).
 *
 * Note that in most cases this can be implemented by a simple `@AssistedFactory` with `displayId`
 * parameter
 *
 * ```kotlin
 * class SomeType @AssistedInject constructor(@Assisted displayId: Int,..)
 *      @AssistedFactory
 *      interface Factory {
 *         fun create(displayId: Int): SomeType
 *      }
 *  }
 * ```
 *
 * Then it can be used to create a [PerDisplayRepository] as follows:
 * ```kotlin
 * // Injected:
 * val repositoryFactory: PerDisplayRepositoryImpl.Factory
 * val instanceFactory: PerDisplayRepositoryImpl.Factory
 * // repository creation:
 * repositoryFactory.create(instanceFactory::create)
 * ```
 *
 * @see PerDisplayRepository For how to retrieve and manage instances created by this factory.
 */
fun interface PerDisplayInstanceProvider<T> {
    /** Creates an instance for a display. */
    fun createInstance(displayId: Int): T?
}

/**
 * Extends [PerDisplayInstanceProvider], adding support for destroying the instance.
 *
 * This is useful for releasing resources associated with a display when it is disconnected or when
 * the per-display instance is no longer needed.
 */
interface PerDisplayInstanceProviderWithTeardown<T> : PerDisplayInstanceProvider<T> {
    /** Destroys a previously created instance of `T` forever. */
    fun destroyInstance(instance: T)
}

/**
 * Provides access to per-display instances of type `T`.
 *
 * Acts as a repository, managing the caching and retrieval of instances created by a
 * [PerDisplayInstanceProvider]. It ensures that only one instance of `T` exists per display ID.
 */
interface PerDisplayRepository<T> {
    /** Gets the cached instance or create a new one for a given display. */
    operator fun get(displayId: Int): T?

    /** List of display ids for which this repository has an instance. */
    val displayIds: Set<Int>

    /** Debug name for this repository, mainly for tracing and logging. */
    val debugName: String
}

/**
 * Default implementation of [PerDisplayRepository].
 *
 * This class manages a cache of per-display instances of type `T`, creating them using a provided
 * [PerDisplayInstanceProvider] and optionally tearing them down using a
 * [PerDisplayInstanceProviderWithTeardown] when displays are disconnected.
 *
 * It listens to the [DisplayRepository] to detect when displays are added or removed, and
 * automatically manages the lifecycle of the per-display instances.
 *
 * Note that this is a [PerDisplayStoreImpl] 2.0 that doesn't require [CoreStartable] bindings,
 * providing all args in the constructor.
 */
class PerDisplayInstanceRepositoryImpl<T>
@AssistedInject
constructor(
    @Assisted override val debugName: String,
    @Assisted private val instanceProvider: PerDisplayInstanceProvider<T>,
    @Background private val backgroundApplicationScope: CoroutineScope,
    private val displayRepository: DisplayRepository,
    private val dumpManager: DumpManager,
) : PerDisplayRepository<T>, Dumpable {

    private val perDisplayInstances = ConcurrentHashMap<Int, T?>()

    init {
        backgroundApplicationScope.launch("$debugName#start") { start() }
    }

    override val displayIds: Set<Int>
        get() = perDisplayInstances.keys

    private suspend fun start() {
        dumpManager.registerDumpable(this)
        displayRepository.displayIds.collectLatest { displayIds ->
            val toRemove = perDisplayInstances.keys - displayIds
            toRemove.forEach { displayId ->
                perDisplayInstances.remove(displayId)?.let { instance ->
                    (instanceProvider as? PerDisplayInstanceProviderWithTeardown)?.destroyInstance(
                        instance
                    )
                }
            }
        }
    }

    override fun get(displayId: Int): T? {
        if (displayRepository.getDisplay(displayId) == null) {
            Log.e(TAG, "<$debugName: Display with id $displayId doesn't exist.")
            return null
        }

        // If it doesn't exist, create it and put it in the map.
        return perDisplayInstances.computeIfAbsent(displayId) { key ->
            val instance =
                traceSection({ "creating instance of $debugName for displayId=$key" }) {
                    instanceProvider.createInstance(key)
                }
            if (instance == null) {
                Log.e(
                    TAG,
                    "<$debugName> returning null because createInstance($key) returned null.",
                )
            }
            instance
        }
    }

    @AssistedFactory
    interface Factory<T> {
        fun create(
            debugName: String,
            instanceProvider: PerDisplayInstanceProvider<T>,
        ): PerDisplayInstanceRepositoryImpl<T>
    }

    companion object {
        private const val TAG = "PerDisplayInstanceRepo"
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println(perDisplayInstances)
    }
}

/**
 * Provides an instance of a given class **only** for the default display, even if asked for another
 * display.
 *
 * This is useful in case of flag refactors: it can be provided instead of an instance of
 * [PerDisplayInstanceRepositoryImpl] when a flag related to multi display refactoring is off.
 */
class DefaultDisplayOnlyInstanceRepositoryImpl<T>(
    override val debugName: String,
    private val instanceProvider: PerDisplayInstanceProvider<T>,
) : PerDisplayRepository<T> {
    private val lazyDefaultDisplayInstance by lazy {
        instanceProvider.createInstance(Display.DEFAULT_DISPLAY)
    }
    override val displayIds: Set<Int> = setOf(Display.DEFAULT_DISPLAY)

    override fun get(displayId: Int): T? = lazyDefaultDisplayInstance
}
