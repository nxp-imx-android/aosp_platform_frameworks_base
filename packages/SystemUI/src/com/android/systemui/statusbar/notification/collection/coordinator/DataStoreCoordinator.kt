/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.coordinator

import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.ListEntry
import com.android.systemui.statusbar.notification.collection.NotifLiveDataStoreImpl
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.PipelineDumper
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.render.requireSummary
import javax.inject.Inject

/**
 * A small coordinator which updates the notif stack (the view layer which holds notifications) with
 * high-level data after the stack is populated with the final entries.
 */
@CoordinatorScope
class DataStoreCoordinator
@Inject
internal constructor(private val notifLiveDataStoreImpl: NotifLiveDataStoreImpl) : CoreCoordinator {

    override fun attach(pipeline: NotifPipeline) {
        pipeline.addOnAfterRenderListListener { entries -> onAfterRenderList(entries) }
    }

    override fun dumpPipeline(d: PipelineDumper) {
        d.dump("notifLiveDataStoreImpl", notifLiveDataStoreImpl)
    }

    private fun onAfterRenderList(entries: List<PipelineEntry>) {
        val flatEntryList = flattenEntrySequence(entries).toList()
        notifLiveDataStoreImpl.setActiveNotifList(flatEntryList)
    }

    private fun flattenEntrySequence(entries: List<PipelineEntry>): Sequence<NotificationEntry> =
        sequence {
            entries.forEach { entry ->
                when (entry) {
                    is BundleEntry -> {
                        yieldAll(flattenEntrySequence(entry.children))
                    }
                    is ListEntry -> {
                        yieldAll(flattenEntrySequence(entry))
                    }
                }
            }
        }

    private fun flattenEntrySequence(entry: ListEntry): Sequence<NotificationEntry> = sequence {
        when (entry) {
            is NotificationEntry -> yield(entry)
            is GroupEntry -> {
                yield(entry.requireSummary)
                yieldAll(entry.children)
            }
        }
    }
}
