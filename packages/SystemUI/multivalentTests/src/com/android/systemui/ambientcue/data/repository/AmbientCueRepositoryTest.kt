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

package com.android.systemui.ambientcue.data.repository

import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.app.smartspace.SmartspaceTarget
import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.AmbientCueRepositoryImpl.Companion.AMBIENT_CUE_SURFACE
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.advanceUntilIdle
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class AmbientCueRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val smartSpaceSession = mock<SmartspaceSession>()
    private val smartSpaceManager =
        mock<SmartspaceManager>() {
            on { createSmartspaceSession(any()) } doReturn smartSpaceSession
        }
    val onTargetsAvailableListenerCaptor = argumentCaptor<OnTargetsAvailableListener>()
    private val underTest =
        AmbientCueRepositoryImpl(
            backgroundScope = kosmos.backgroundScope,
            smartSpaceManager = smartSpaceManager,
            executor = kosmos.fakeExecutor,
            applicationContext = kosmos.testableContext,
        )

    @Test
    fun isVisible_whenHasActions_true() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val isVisible by collectLastValue(underTest.isVisible)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(allTargets)
            advanceUntilIdle()
            assertThat(isVisible).isTrue()
        }

    @Test
    fun isVisible_whenNoActions_false() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val isVisible by collectLastValue(underTest.isVisible)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(
                listOf(invalidTarget1)
            )
            advanceUntilIdle()
            assertThat(isVisible).isFalse()
        }

    @Test
    fun actions_whenHasSmartSpaceAction() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            runCurrent()
            verify(smartSpaceSession)
                .addOnTargetsAvailableListener(any(), onTargetsAvailableListenerCaptor.capture())
            onTargetsAvailableListenerCaptor.firstValue.onTargetsAvailable(allTargets)
            runCurrent()

            actions.let {
                requireNotNull(it)
                assertThat(it.size).isEqualTo(2)

                val firstAction = it.first()
                assertThat(firstAction.label).isEqualTo(TITLE_1)
                assertThat(firstAction.attribution).isEqualTo(SUBTITLE_1)

                val lastAction = it.last()
                assertThat(lastAction.label).isEqualTo(TITLE_2)
                assertThat(lastAction.attribution).isEqualTo(SUBTITLE_2)
            }
        }

    companion object {

        private const val TITLE_1 = "title 1"
        private const val TITLE_2 = "title 2"
        private const val SUBTITLE_1 = "subtitle 1"
        private const val SUBTITLE_2 = "subtitle 2"
        private val validTarget =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn AMBIENT_CUE_SURFACE
                on { actionChips } doReturn
                    listOf(
                        SmartspaceAction.Builder("action1-id", "title 1")
                            .setSubtitle("subtitle 1")
                            .build(),
                        SmartspaceAction.Builder("action2-id", "title 2")
                            .setSubtitle("subtitle 2")
                            .build(),
                    )
            }

        private val invalidTarget1 =
            mock<SmartspaceTarget> {
                on { smartspaceTargetId } doReturn "home"
                on { actionChips } doReturn
                    listOf(SmartspaceAction.Builder("id", "title").setSubtitle("subtitle").build())
            }

        private val allTargets = listOf(validTarget, invalidTarget1)
    }
}
