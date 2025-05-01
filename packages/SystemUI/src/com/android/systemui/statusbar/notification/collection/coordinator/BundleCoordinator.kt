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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.NotificationChannel.NEWS_ID
import android.app.NotificationChannel.PROMOTIONS_ID
import android.app.NotificationChannel.RECS_ID
import android.app.NotificationChannel.SOCIAL_MEDIA_ID
import android.os.Build
import android.os.SystemProperties
import androidx.annotation.VisibleForTesting
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.collection.GroupEntry
import com.android.systemui.statusbar.notification.collection.ListEntry
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.PipelineEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.OnBeforeRenderListListener
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifBundler
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.render.BundleBarn
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.statusbar.notification.dagger.NewsHeader
import com.android.systemui.statusbar.notification.dagger.PromoHeader
import com.android.systemui.statusbar.notification.dagger.RecsHeader
import com.android.systemui.statusbar.notification.dagger.SocialHeader
import com.android.systemui.statusbar.notification.row.data.model.AppData
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.statusbar.notification.stack.BUCKET_NEWS
import com.android.systemui.statusbar.notification.stack.BUCKET_PROMO
import com.android.systemui.statusbar.notification.stack.BUCKET_RECS
import com.android.systemui.statusbar.notification.stack.BUCKET_SOCIAL
import javax.inject.Inject

/** Coordinator for sections derived from NotificationAssistantService classification. */
@CoordinatorScope
class BundleCoordinator
@Inject
constructor(
    @NewsHeader private val newsHeaderController: NodeController,
    @SocialHeader private val socialHeaderController: NodeController,
    @RecsHeader private val recsHeaderController: NodeController,
    @PromoHeader private val promoHeaderController: NodeController,
    private val bundleBarn: BundleBarn,
) : Coordinator {

    val newsSectioner =
        object : NotifSectioner("News", BUCKET_NEWS) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == NEWS_ID
            }

            override fun getHeaderNodeController(): NodeController {
                return newsHeaderController
            }
        }

    val socialSectioner =
        object : NotifSectioner("Social", BUCKET_SOCIAL) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == SOCIAL_MEDIA_ID
            }

            override fun getHeaderNodeController(): NodeController {
                return socialHeaderController
            }
        }

    val recsSectioner =
        object : NotifSectioner("Recommendations", BUCKET_RECS) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == RECS_ID
            }

            override fun getHeaderNodeController(): NodeController {
                return recsHeaderController
            }
        }

    val promoSectioner =
        object : NotifSectioner("Promotions", BUCKET_PROMO) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == PROMOTIONS_ID
            }

            override fun getHeaderNodeController(): NodeController {
                return promoHeaderController
            }
        }

    val bundler =
        object : NotifBundler("NotifBundler") {
            // Use list instead of set to keep fixed order
            override val bundleSpecs: List<BundleSpec> = buildList {
                add(BundleSpec.NEWS)
                add(BundleSpec.SOCIAL_MEDIA)
                add(BundleSpec.PROMOTIONS)
                add(BundleSpec.RECOMMENDED)
            }

            private val bundleIds = this.bundleSpecs.map { it.key }

            /**
             * Return the id string of the bundle this ListEntry belongs in Or null if this
             * ListEntry should not be bundled
             */
            override fun getBundleIdOrNull(entry: ListEntry): String? {
                if (isFromDebugApp(entry)) {
                    return BundleSpec.RECOMMENDED.key
                }
                if (entry is GroupEntry) {
                    if (entry.children.isEmpty()) return null
                    val summary = entry.summary ?: return null
                    // When the model classifies notifications from the same group into
                    // different bundles, system_server creates new group summaries that we can
                    // check for classification here.
                    return getBundleIdForNotifEntry(summary)
                }
                return getBundleIdForNotifEntry(entry as NotificationEntry)
            }

            private fun isFromDebugApp(entry: ListEntry): Boolean {
                return !debugBundleAppName.isNullOrEmpty() && entry.key.contains(debugBundleAppName)
            }

            private fun getBundleIdForNotifEntry(notifEntry: NotificationEntry): String? {
                return notifEntry.representativeEntry?.channel?.id?.takeIf { it in this.bundleIds }
            }
        }

    /** Recursively check parents until finding bundle or null */
    private fun PipelineEntry.getBundleOrNull(): BundleEntry? =
        when (this) {
            is BundleEntry -> this
            is ListEntry -> parent?.getBundleOrNull()
        }

    private fun inflateAllBundleEntries(entries: List<PipelineEntry>) {
        for (entry in entries) {
            if (entry is BundleEntry) {
                bundleBarn.inflateBundleEntry(entry)
            }
        }
    }

    private val bundleFilter: NotifFilter =
        object : NotifFilter("BundleInflateFilter") {
            override fun shouldFilterOut(entry: NotificationEntry, now: Long): Boolean {
                // TODO(b/399736937) Do not hide notifications if we have a bug that means the
                //  bundle isn't inflated yet. It's better that we just show those notifications in
                //  the silent section than fail to show them to the user at all
                val bundle = entry.getBundleOrNull()
                if (bundle == null) {
                    debugBundleLog(TAG, { "$name bundle null for notifEntry:${entry.key}" })
                    return false
                }
                val isInflated = bundleBarn.isInflated(bundle)
                debugBundleLog(TAG, { "$name isInflated:$isInflated bundle:${bundle.key}" })
                return !isInflated
            }
        }

    private val bundleCountUpdater = OnBeforeRenderListListener { entries ->
        for (entry in entries) {
            if (entry is BundleEntry) {
                entry.updateTotalCount()
            }
        }
    }

    /**
     * For each BundleEntry, populate its bundleRepository.appDataList with AppData (package name,
     * UserHandle) from its notif children
     */
    @get:VisibleForTesting
    val bundleAppDataUpdater = OnBeforeRenderListListener { entries ->
        for (entry in entries) {
            if (entry !is BundleEntry) continue
            val newAppDataList: List<AppData> =
                entry.children.flatMap { listEntry ->
                    when (listEntry) {
                        is NotificationEntry -> {
                            listOf(AppData(listEntry.sbn.packageName, listEntry.sbn.user))
                        }

                        is GroupEntry -> {
                            val summary = listEntry.representativeEntry
                            if (summary != null) {
                                listOf(AppData(summary.sbn.packageName, summary.sbn.user))
                            } else {
                                error(
                                    "BundleEntry (key: ${entry.key}) contains GroupEntry " +
                                        "(key: ${listEntry.key}) with no summary."
                                )
                            }
                        }

                        else -> error("Unexpected ListEntry type: ${listEntry::class.simpleName}")
                    }
                }
            entry.bundleRepository.appDataList = newAppDataList
        }
    }

    override fun attach(pipeline: NotifPipeline) {
        if (NotificationBundleUi.isEnabled) {
            pipeline.setNotifBundler(bundler)
            pipeline.addOnBeforeFinalizeFilterListener(this::inflateAllBundleEntries)
            pipeline.addFinalizeFilter(bundleFilter)
            pipeline.addOnBeforeRenderListListener(bundleCountUpdater)
            pipeline.addOnBeforeRenderListListener(bundleAppDataUpdater)
        }
    }

    companion object {
        @JvmField val TAG: String = "BundleCoordinator"

        @JvmField var debugBundleLogs: Boolean = false

        /**
         * All notifications that contain this String in the key are bundled into the recommended
         * bundle such that bundle code can be easily and deterministically tested.
         *
         * E.g. use this command to bundle all notifications from notify: `adb shell setprop
         * persist.debug.notification_bundle_ui_debug_app_name com.google.cinek.notify && adb
         * reboot`
         */
        val debugBundleAppName: String? =
            if (Build.IS_USERDEBUG)
                SystemProperties.get("persist.debug.notification_bundle_ui_debug_app_name")
            else null

        @JvmStatic
        fun debugBundleLog(tag: String, stringLambda: () -> String) {
            if (debugBundleLogs) {
                android.util.Log.d(tag, stringLambda())
            }
        }
    }
}
