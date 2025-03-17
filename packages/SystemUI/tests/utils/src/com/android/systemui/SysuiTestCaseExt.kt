/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useStandardTestDispatcher

fun SysuiTestCase.testKosmos(): Kosmos = Kosmos().apply { testCase = this@testKosmos }

/**
 * This should not be called directly. Instead, you can use:
 * - testKosmos() to use the default dispatcher (which will soon be unconfined, see go/thetiger)
 * - testKosmos().useStandardTestDispatcher() to explicitly choose the standard dispatcher
 * - testKosmos().useUnconfinedTestDispatcher() to explicitly choose the unconfined dispatcher
 *
 * For details, see go/thetiger
 */
@Deprecated("Do not call this directly.  Use testKosmos() with dispatcher functions if needed.")
fun SysuiTestCase.testKosmosLegacy(): Kosmos =
    Kosmos().useStandardTestDispatcher().apply { testCase = this@testKosmosLegacy }

/** Run [f] on the main thread and return its result once completed. */
fun <T : Any> SysuiTestCase.runOnMainThreadAndWaitForIdleSync(f: () -> T): T {
    lateinit var result: T
    context.mainExecutor.execute { result = f() }
    waitForIdleSync()
    return result
}
