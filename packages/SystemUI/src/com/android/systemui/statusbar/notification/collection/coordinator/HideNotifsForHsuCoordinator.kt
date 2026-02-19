/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.systemui.statusbar.notification.collection.coordinator

import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import javax.inject.Inject

/** A coordinator that filters out notifications for users who are not the headless system user. */
@CoordinatorScope
class HideNotifsForHsuCoordinator
@Inject
constructor(private val selectedUserInteractor: SelectedUserInteractor) : Coordinator {

    override fun attach(pipeline: NotifPipeline) {
        pipeline.addPreGroupFilter(notifFilter)
    }

    private val notifFilter: NotifFilter =
        object : NotifFilter(TAG) {
            override fun shouldFilterOut(entry: NotificationEntry, now: Long): Boolean =
                selectedUserInteractor.isCurrentUserHeadlessSystemUser.value
        }

    companion object {
        private const val TAG = "HideNotifsForHsuCoordinator"
    }
}
