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

package com.android.systemui.screencapture.common.shared.model

import android.os.UserHandle
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion as LargeScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType

sealed interface ScreenCaptureUiParameters {

    val screenCaptureType: ScreenCaptureType

    /** Record screen content to the local device. */
    data class Record(val largeScreenParameters: LargeScreenCaptureUiParameters? = null) :
        ScreenCaptureUiParameters {

        override val screenCaptureType: ScreenCaptureType = ScreenCaptureType.RECORD

        data class LargeScreenCaptureUiParameters(
            val defaultCaptureType: LargeScreenCaptureType? = null,
            val defaultCaptureRegion: LargeScreenCaptureRegion? = null,
        )
    }

    /** Cast screen content to a remote device. */
    data object Cast : ScreenCaptureUiParameters {

        override val screenCaptureType: ScreenCaptureType = ScreenCaptureType.CAST
    }

    /** Share screen content to a local app. */
    data class ShareScreen(val hostAppUserHandle: UserHandle) : ScreenCaptureUiParameters {

        override val screenCaptureType: ScreenCaptureType = ScreenCaptureType.SHARE_SCREEN
    }
}
