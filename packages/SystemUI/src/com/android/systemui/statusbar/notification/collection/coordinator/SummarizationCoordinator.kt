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

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.Notification.EXTRA_SUMMARIZED_CONTENT
import android.content.Context
import android.text.TextUtils
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.NmSummarizationAllFlag
import com.android.systemui.statusbar.notification.OnboardingAffordanceManager
import com.android.systemui.statusbar.notification.Summarization
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.Invalidator
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Coordinator for notification summarization features. */
@CoordinatorScope
class SummarizationCoordinator
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    @Application private val scope: CoroutineScope,
    @Summarization private val onboardingAffordanceManager: OnboardingAffordanceManager,
    private val decorator: SummarizationDecorator,
) : Coordinator {
    override fun attach(pipeline: NotifPipeline) {
        bindOnboardingAffordanceInvalidator(pipeline)

        if (NmSummarizationAllFlag.isEnabled) {
            pipeline.addCollectionListener(
                object : NotifCollectionListener {
                    override fun onEntryAdded(entry: NotificationEntry) {
                        decorateSummarization(entry)
                    }

                    override fun onEntryUpdated(entry: NotificationEntry) {
                        decorateSummarization(entry)
                    }

                    override fun onRankingApplied() {
                        for (entry in pipeline.allNotifs) {
                            decorateSummarization(entry)
                        }
                    }
                }
            )
        }
    }

    private fun bindOnboardingAffordanceInvalidator(pipeline: NotifPipeline) {
        val invalidator = object : Invalidator("summarization onboarding") {}
        pipeline.addPreRenderInvalidator(invalidator)
        scope.launch {
            onboardingAffordanceManager.view.collect {
                invalidator.invalidateList("summarization onboarding view changed")
            }
        }
    }

    private fun decorateSummarization(entry: NotificationEntry) {
        if (!TextUtils.isEmpty(entry.summarization)) {
            decorator.decorateSummarization(entry)
        } else {
            entry.sbn.notification.extras.putCharSequence(EXTRA_SUMMARIZED_CONTENT, null)
        }
    }
}
