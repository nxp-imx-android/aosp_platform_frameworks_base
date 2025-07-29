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

package com.android.compose.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

// This deprecated-error function shadows the varargs overload so that the varargs version
// is not used without key parameters.
@Deprecated(LaunchedEffectNoParamError, level = DeprecationLevel.ERROR)
@Composable
fun LaunchedEffectWithLifecycle(block: suspend CoroutineScope.() -> Unit) {
    error(LaunchedEffectNoParamError)
}

private const val LaunchedEffectNoParamError =
    "LaunchedEffectWithLifecycle must provide one or more 'key' parameters that define the " +
        "identity of the LaunchedEffect and determine when its previous effect coroutine should " +
        "be cancelled and a new effect launched for the new key."

/**
 * A [LaunchedEffect] that is lifecycle-aware.
 *
 * This effect is triggered every time the [lifecycle] reaches the [minActiveState], and will be
 * **re-launched** whenever [LaunchedEffectWithLifecycle] is recomposed with a different [key1],
 * [lifecycle] or [minActiveState].
 *
 * @see androidx.compose.runtime.LaunchedEffect
 */
@Composable
@NonRestartableComposable
fun LaunchedEffectWithLifecycle(
    key1: Any?,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(key1, lifecycle, minActiveState) {
        lifecycle.repeatOnLifecycle(minActiveState, block)
    }
}

/**
 * A [LaunchedEffect] that is lifecycle-aware.
 *
 * This effect is triggered every time the [lifecycle] reaches the [minActiveState], and will be
 * **re-launched** whenever [LaunchedEffectWithLifecycle] is recomposed with a different [key1],
 * [key2], [lifecycle] or [minActiveState].
 *
 * @see androidx.compose.runtime.LaunchedEffect
 */
@Composable
@NonRestartableComposable
fun LaunchedEffectWithLifecycle(
    key1: Any?,
    key2: Any?,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(key1, key2, lifecycle, minActiveState) {
        lifecycle.repeatOnLifecycle(minActiveState, block)
    }
}

/**
 * A [LaunchedEffect] that is lifecycle-aware.
 *
 * This effect is triggered every time the [lifecycle] reaches the [minActiveState], and will be
 * **re-launched** whenever [LaunchedEffectWithLifecycle] is recomposed with a different [key1],
 * [key2], [lifecycle] or [minActiveState].
 *
 * @see androidx.compose.runtime.LaunchedEffect
 */
@Composable
@NonRestartableComposable
fun LaunchedEffectWithLifecycle(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(key1, key2, key3, lifecycle, minActiveState) {
        lifecycle.repeatOnLifecycle(minActiveState, block)
    }
}

/**
 * A [LaunchedEffect] that is lifecycle-aware.
 *
 * This effect is triggered every time the [lifecycle] reaches the [minActiveState], and will be
 * **re-launched** whenever [LaunchedEffectWithLifecycle] is recomposed with a different [key1],
 * [key2], [key3], [lifecycle] or [minActiveState].
 *
 * @see androidx.compose.runtime.LaunchedEffect
 */
@Composable
@NonRestartableComposable
fun LaunchedEffectWithLifecycle(
    vararg keys: Any?,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(keys, lifecycle, minActiveState) {
        lifecycle.repeatOnLifecycle(minActiveState, block)
    }
}
