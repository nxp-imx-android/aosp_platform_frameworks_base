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

import android.app.Activity
import com.android.systemui.screencapture.cast.ScreenCaptureCastComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.record.ScreenCaptureRecordComponent
import com.android.systemui.screencapture.record.smallscreen.ui.SmallScreenPostRecordingActivity
import com.android.systemui.screencapture.sharescreen.ScreenCaptureShareScreenComponent
import com.android.systemui.screencapture.ui.ScreenCaptureActivity
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/**
 * Top level Dagger Module for Screen Capture.
 *
 * Injects Screen Capture Subcomponents into the System UI dagger graph via
 * [SystemUIModule][com.android.systemui.dagger.SystemUIModule].
 */
@Module(
    subcomponents =
        [
            ScreenCaptureCastComponent::class,
            ScreenCaptureComponent::class,
            ScreenCaptureRecordComponent::class,
            ScreenCaptureShareScreenComponent::class,
        ]
)
interface ScreenCaptureModule {
    @Binds
    @IntoMap
    @ScreenCaptureTypeKey(ScreenCaptureType.CAST)
    fun bindCastComponentBuilder(
        impl: ScreenCaptureCastComponent.Builder
    ): ScreenCaptureComponent.Builder

    @Binds
    @IntoMap
    @ScreenCaptureTypeKey(ScreenCaptureType.RECORD)
    fun bindRecordComponentBuilder(
        impl: ScreenCaptureRecordComponent.Builder
    ): ScreenCaptureComponent.Builder

    @Binds
    @IntoMap
    @ScreenCaptureTypeKey(ScreenCaptureType.SHARE_SCREEN)
    fun bindShareScreenComponentBuilder(
        impl: ScreenCaptureShareScreenComponent.Builder
    ): ScreenCaptureComponent.Builder

    @Binds
    @IntoMap
    @ClassKey(ScreenCaptureActivity::class)
    fun provideScreenCaptureActivity(activity: ScreenCaptureActivity): Activity

    @Binds
    @IntoMap
    @ClassKey(SmallScreenPostRecordingActivity::class)
    fun provideSmallScreenPostRecordingActivity(
        activity: SmallScreenPostRecordingActivity
    ): Activity
}
