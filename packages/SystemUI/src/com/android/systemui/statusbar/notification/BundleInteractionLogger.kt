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

package com.android.systemui.statusbar.notification

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger
import com.android.internal.util.FrameworkStatsLog
import com.android.systemui.statusbar.notification.collection.BundleEntry
import javax.inject.Inject

enum class BundleInteractedEvent(private val _id: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "User dismissed a bundle") NOTIF_BUNDLE_DISMISSED(2264);

    override fun getId() = _id
}

class BundleInteractionLogger @Inject constructor() {

    fun logBundleDismissed(bundle: BundleEntry) {
        FrameworkStatsLog.write(
            FrameworkStatsLog.NOTIFICATION_BUNDLE_INTERACTED,
            /* optional int32 event_id */ BundleInteractedEvent.NOTIF_BUNDLE_DISMISSED.id,
            /* optional int32 type */ bundle.bundleRepository.bundleType,
            // TODO: b/415113012 - correctly reflect whether the bundle was ever expanded
            /* optional bool contents_shown */ false,
        )
    }
}
