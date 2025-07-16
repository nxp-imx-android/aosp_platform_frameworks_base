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

package com.android.systemui.screencapture.ui

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureActivityIntentParameters
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import dagger.Lazy
import javax.inject.Inject

class ScreenCaptureActivity
@Inject
constructor(
    private val componentBuilders:
        Map<
            @JvmSuppressWildcards
            ScreenCaptureType,
            @JvmSuppressWildcards
            ScreenCaptureComponent.Builder,
        >,
    private val defaultBuilder: Lazy<ScreenCaptureComponent.Builder>,
) : ComponentActivity() {

    private var component: ScreenCaptureComponent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.updateAttributes { type = WindowManager.LayoutParams.TYPE_SCREENSHOT }
        val intentParameters = ScreenCaptureActivityIntentParameters.fromIntent(intent)

        val builder: ScreenCaptureComponent.Builder =
            componentBuilders[intentParameters.screenCaptureType] ?: defaultBuilder.get()

        component =
            builder.setParameters(intentParameters).setScope(lifecycleScope).build().also {
                setContent { PlatformTheme { it.screenCaptureContent.Content() } }
            }
    }

    override fun onDestroy() {
        component = null
        super.onDestroy()
    }
}

private fun Window.updateAttributes(update: WindowManager.LayoutParams.() -> Unit) {
    attributes = attributes.apply(update)
}
