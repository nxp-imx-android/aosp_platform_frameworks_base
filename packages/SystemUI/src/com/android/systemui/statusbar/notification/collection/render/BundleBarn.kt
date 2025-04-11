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

package com.android.systemui.statusbar.notification.collection.render

import android.content.Context
import android.view.ViewGroup
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationPresenter
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.coordinator.BundleCoordinator.Companion.debugBundleLog
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.RowInflaterTask
import com.android.systemui.statusbar.notification.row.RowInflaterTaskLogger
import com.android.systemui.statusbar.notification.row.dagger.ExpandableNotificationRowComponent
import com.android.systemui.statusbar.notification.stack.NotificationListContainer
import com.android.systemui.util.time.SystemClock
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Provider

/**
 * Class that handles inflating BundleEntry view and controller, for use by NodeSpecBuilder.
 * TODO(b/402628023) Make this class dumpable and dump its map so that we can see the
 * "inflation pending" state per bundle
 */
@SysUISingleton
class BundleBarn @Inject constructor(
    private val rowComponent: ExpandableNotificationRowComponent.Builder,
    private val rowInflaterTaskProvider: Provider<RowInflaterTask>,
    private val listContainer: NotificationListContainer,
    val context: Context? = null,
    val systemClock: SystemClock,
    val logger: RowInflaterTaskLogger,
    val userTracker: UserTracker,
    private val presenterLazy: Lazy<NotificationPresenter?>? = null
) {
    /**
     * Map of [BundleEntry] key to [NodeController]:
     * no key -> not started
     * key maps to null -> inflating
     * key maps to controller -> inflated
     */
    private val keyToControllerMap = mutableMapOf<String, NotifViewController?>()

    fun debugLog(s: String) {
        debugBundleLog(TAG, { s })
    }

    /**
     * Build view and controller for BundleEntry.
     */
    fun inflateBundleEntry(bundleEntry: BundleEntry) {
        debugLog("inflateBundleEntry: ${bundleEntry.key}")
        if (keyToControllerMap.containsKey(bundleEntry.key)) {
            // Skip if bundle is inflating or inflated.
            debugLog("already in map: ${bundleEntry.key}")
            return
        }
        val parent: ViewGroup = listContainer.getViewParentForNotification()
        val inflationFinishedListener: (ExpandableNotificationRow) -> Unit = { row ->
            // A subset of NotificationRowBinderImpl.inflateViews
            debugLog("finished inflating: ${bundleEntry.key}")
            val component = rowComponent
                    .expandableNotificationRow(row)
                    .pipelineEntry(bundleEntry)
                    .onExpandClickListener(presenterLazy?.get())
                    .build()
            val controller =
                component.expandableNotificationRowController
            controller.init(bundleEntry)
            keyToControllerMap[bundleEntry.key] = controller
        }
        debugLog("calling inflate: ${bundleEntry.key}")
        keyToControllerMap[bundleEntry.key] = null
        rowInflaterTaskProvider.get().inflate(
            context, parent, bundleEntry, inflationFinishedListener
        )
    }

    /**
     * Return true if finished inflating.
     */
    fun isInflated(bundleEntry: BundleEntry): Boolean {
        return keyToControllerMap[bundleEntry.key] != null
    }

    /**
     * Return ExpandableNotificationRowController for BundleEntry.
     */
    fun requireNodeController(bundleEntry: BundleEntry): NodeController {
        debugLog("requireNodeController: ${bundleEntry.key}" +
                "controller: ${keyToControllerMap[bundleEntry.key]}")
        return keyToControllerMap[bundleEntry.key]
            ?: error("No view has been registered for bundle: ${bundleEntry.key}")
    }
}

private const val TAG = "BundleBarn"
