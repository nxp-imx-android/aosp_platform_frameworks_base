/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.util.kotlin.FlowDumperImpl
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@SysUISingleton
class AODPromotedNotificationInteractor
@Inject
constructor(
    promotedNotificationsInteractor: PromotedNotificationsInteractor,
    dumpManager: DumpManager,
) : FlowDumperImpl(dumpManager) {
    /** The content to show as the promoted notification on AOD */
    val content: Flow<PromotedNotificationContentModel?> =
        promotedNotificationsInteractor.aodPromotedNotification
            .map {
                // TODO(b/400991304): show the private version when unlocked
                it?.publicVersion
            }
            .distinctUntilNewInstance()

    val isPresent: Flow<Boolean> = content.map { it != null }.dumpWhileCollecting("isPresent")

    /**
     * Returns flow where all subsequent repetitions of the same object instance are filtered out.
     */
    private fun <T> Flow<T>.distinctUntilNewInstance() = distinctUntilChanged { a, b -> a === b }
}
