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

            override fun getHeaderNodeController(): NodeController? {
                return newsHeaderController
            }
        }

    val socialSectioner =
        object : NotifSectioner("Social", BUCKET_SOCIAL) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == SOCIAL_MEDIA_ID
            }

            override fun getHeaderNodeController(): NodeController? {
                return socialHeaderController
            }
        }

    val recsSectioner =
        object : NotifSectioner("Recommendations", BUCKET_RECS) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == RECS_ID
            }

            override fun getHeaderNodeController(): NodeController? {
                return recsHeaderController
            }
        }

    val promoSectioner =
        object : NotifSectioner("Promotions", BUCKET_PROMO) {
            override fun isInSection(entry: PipelineEntry): Boolean {
                return entry.asListEntry()?.representativeEntry?.channel?.id == PROMOTIONS_ID
            }

            override fun getHeaderNodeController(): NodeController? {
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
                if (debugBundleUi) add(BundleSpec.DEBUG)
            }

            private val bundleIds = this.bundleSpecs.map { it.key }

            /**
             * Return the id string of the bundle this ListEntry belongs in Or null if this
             * ListEntry should not be bundled
             */
            override fun getBundleIdOrNull(entry: ListEntry): String? {
                if (debugBundleUi && entry?.key?.contains("notify") == true) {
                    return BundleSpec.DEBUG.key
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

            private fun getBundleIdForNotifEntry(notifEntry: NotificationEntry): String? {
                return notifEntry.representativeEntry?.channel?.id?.takeIf { it in this.bundleIds }
            }
        }

    /** Recursively check parents until finding bundle or null */
    private fun PipelineEntry.getBundleOrNull(): BundleEntry? {
        return when (this) {
            is BundleEntry -> this
            is ListEntry -> parent?.getBundleOrNull()
        }
    }

    private fun inflateAllBundleEntries(list: List<PipelineEntry>) {
        list.filterIsInstance<BundleEntry>().forEach { bundleBarn.inflateBundleEntry(it) }
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
        entries.filterIsInstance<BundleEntry>()
            .forEach(BundleEntry::updateTotalCount)
    }

    override fun attach(pipeline: NotifPipeline) {
        if (NotificationBundleUi.isEnabled) {
            pipeline.setNotifBundler(bundler)
            pipeline.addOnBeforeFinalizeFilterListener(this::inflateAllBundleEntries)
            pipeline.addFinalizeFilter(bundleFilter)
            pipeline.addOnBeforeRenderListListener(bundleCountUpdater)
        }
    }

    companion object {
        @JvmField val TAG: String = "BundleCoordinator"
        @JvmField var debugBundleUi: Boolean = false
        @JvmStatic
        fun debugBundleLog(tag: String, stringLambda: () -> String) {
            if (debugBundleUi) {
                android.util.Log.d(tag, stringLambda())
            }
        }
    }
}
