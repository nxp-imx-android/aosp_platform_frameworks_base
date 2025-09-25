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
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.statusbar.RankingBuilder
import com.android.systemui.statusbar.notification.NmSummarizationAllFlag
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.buildNotificationEntry
import com.android.systemui.statusbar.notification.collection.makeEntryOfPeopleType
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class SummarizationCoordinatorTest : SysuiTestCase() {
    private lateinit var coordinator: SummarizationCoordinator
    private lateinit var notifCollectionListener: NotifCollectionListener

    @Mock private lateinit var pipeline: NotifPipeline
    private val kosmos = testKosmos()

    @Before
    fun setUp() {
        initMocks(this)
        coordinator = SummarizationCoordinator(mContext, kosmos.applicationCoroutineScope, mock())
        coordinator.attach(pipeline)
        notifCollectionListener = withArgCaptor {
            verify(pipeline).addCollectionListener(capture())
        }
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithSummarization() {
        val summarization = "hello"
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(summarization).build())

        notifCollectionListener.onEntryAdded(entry)

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
        assertThat(processedSummary.toString()).isEqualTo("   $summarization")

        val checkSpans = SpannableStringBuilder(processedSummary)
        assertThat(
                checkSpans.getSpans(
                    /* queryStart = */ 0,
                    /* queryEnd = */ 2,
                    /* kind = */ ImageSpan::class.java,
                )
            )
            .isNotNull()
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleUpdateSummarizationToNull() {
        val entry = kosmos.makeEntryOfPeopleType()
        entry.setRanking(RankingBuilder(entry.ranking).setSummarization("hello").build())
        notifCollectionListener.onEntryAdded(entry)
        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT))
            .isNotNull()

        entry.setRanking(RankingBuilder(entry.ranking).setSummarization(null).build())
        notifCollectionListener.onEntryUpdated(entry)
        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_noStyle_appProvidedSummarization() {
        val summarization = "hello"
        val entry =
            kosmos.buildNotificationEntry {
                modifyNotification(context).setSummarizedContent(summarization)
            }

        notifCollectionListener.onEntryAdded(entry)

        val processedSummary =
            entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)
        assertThat(processedSummary.toString()).isEqualTo("   $summarization")
        val checkSpans = SpannableStringBuilder(processedSummary)
        assertThat(
            checkSpans.getSpans(
                /* queryStart = */ 0,
                /* queryEnd = */ 2,
                /* kind = */ ImageSpan::class.java,
            )
        ).isNotNull()
    }

    @Test
    @EnableFlags(NmSummarizationAllFlag.FLAG_NAME)
    fun onBeforeRenderList_messagingStyleWithoutSummarization() {
        val entry = kosmos.makeEntryOfPeopleType()
        notifCollectionListener.onEntryAdded(entry)

        assertThat(entry.sbn.notification.extras.getCharSequence(EXTRA_SUMMARIZED_CONTENT)).isNull()
    }
}
