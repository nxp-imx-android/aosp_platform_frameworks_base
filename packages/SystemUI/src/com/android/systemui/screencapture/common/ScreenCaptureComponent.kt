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

package com.android.systemui.screencapture.common

import com.android.systemui.screencapture.common.shared.model.ScreenCaptureActivityIntentParameters
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import dagger.BindsInstance
import dagger.Subcomponent
import kotlinx.coroutines.CoroutineScope

/**
 * Dagger Subcomponent interface for Screen Capture.
 *
 * Actual Subcomponents should extend this interface and be listed as a subcomponent in
 * [ScreenCaptureModule].
 */
@ScreenCaptureScope
@Subcomponent(modules = [CommonModule::class, FallbackModule::class])
interface ScreenCaptureComponent {

    /**
     * Dagger Subcomponent Builder for [ScreenCaptureComponent].
     *
     * Actual Subcomponent Builders should extend this interface and override [build] to return the
     * actual subcomponent type.
     */
    @Subcomponent.Builder
    interface Builder {

        /** The [CoroutineScope] to use coroutines limited to Screen Capture sessions. */
        @BindsInstance @ScreenCapture fun setScope(scope: CoroutineScope): Builder

        /** [ScreenCaptureActivityIntentParameters] that has been used to start capture flow. */
        @BindsInstance
        @ScreenCapture
        fun setParameters(parameters: ScreenCaptureActivityIntentParameters): Builder

        /**
         * Builds this [ScreenCaptureComponent]. Actual Subcomponent Builders should override this
         * method with their own version that returns the actual subcomponent type.
         */
        fun build(): ScreenCaptureComponent
    }

    val screenCaptureContent: ScreenCaptureContent
}
