/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.systemui.dreams.touch

import android.content.ComponentName
import android.content.pm.UserInfo
import android.content.res.mainResources
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.dreams.DreamOverlayContainerView
import com.android.systemui.dreams.data.repository.dreamRepository
import com.android.systemui.dreams.data.repository.fake
import com.android.systemui.dreams.domain.interactor.DreamInteractor
import com.android.systemui.dreams.domain.interactor.dreamInteractor
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.haptics.fake
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LongPressTouchHandlerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.session: TouchHandler.TouchSession by Kosmos.Fixture { mock() }

    private val Kosmos.gestureListenerCaptor by
        Kosmos.Fixture { argumentCaptor<GestureDetector.OnGestureListener>() }

    private val Kosmos.lifecycleOwner: TestLifecycleOwner by
        Kosmos.Fixture {
            TestLifecycleOwner(
                initialState = Lifecycle.State.CREATED,
                coroutineDispatcher = testDispatcher,
            )
        }

    private val Kosmos.containerView: DreamOverlayContainerView by
        Kosmos.Fixture { DreamOverlayContainerView(context, null) }

    private val Kosmos.underTest: LongPressTouchHandler by
        Kosmos.Fixture {
            LongPressTouchHandler(
                vibratorHelper,
                containerView,
                dreamInteractor,
                lifecycleOwner.lifecycle,
                mainResources,
            )
        }

    @Before
    fun setUp() {
        runBlocking {
            kosmos.setupUser()
            // Make sure the user is set up before we advance the lifecycle
            kosmos.lifecycleOwner.currentState = Lifecycle.State.RESUMED
        }
    }

    @Test
    fun onSessionStart_whenSwitchingDisabled_doesNotRegisterListener() =
        kosmos.runTest {
            // GIVEN switching is disabled
            setCanSwitchDreams(false)

            // WHEN a session starts
            underTest.onSessionStart(session)

            // THEN no listener is registered
            verify(session, never()).registerGestureListener(gestureListenerCaptor.capture())
        }

    @Test
    fun onSessionStart_whenSwitchingEnabled_registersListener() =
        kosmos.runTest {
            // GIVEN switching is enabled
            setCanSwitchDreams(true)

            // WHEN a new session starts
            underTest.onSessionStart(session)

            // THEN a listener is registered
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            assertThat(gestureListenerCaptor.firstValue).isNotNull()
        }

    @Test
    fun onLongPress_whenSwitchingEnabled_showsSwitcher() =
        kosmos.runTest {
            val switcherRequest by collectLastValue(dreamInteractor.switcherRequests)

            // GIVEN switching is enabled
            setCanSwitchDreams(true)
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // WHEN a long press occurs
            listener.onLongPress(mock())

            // THEN the switcher is shown
            assertThat(switcherRequest)
                .isInstanceOf(DreamInteractor.SwitcherRequest.Show::class.java)
        }

    @Test
    fun onLongPress_whenSwitchingEnabled_vibrates() =
        kosmos.runTest {
            // GIVEN switching is enabled
            setCanSwitchDreams(true)
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // WHEN a long press occurs
            listener.onLongPress(mock())

            // THEN the device vibrates
            assertThat(
                    vibratorHelper.fake.timesVibratedWithHapticFeedbackConstant(
                        HapticFeedbackConstants.LONG_PRESS
                    )
                )
                .isEqualTo(1)
        }

    @Test
    fun onLongPress_whenSwitchingDisabled_doesNothing() =
        kosmos.runTest {
            val switcherRequest by collectLastValue(dreamInteractor.switcherRequests)

            // GIVEN switching is enabled, and a listener is registered
            setCanSwitchDreams(true)
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // WHEN switching is disabled
            setCanSwitchDreams(false)

            // and a long press occurs
            listener.onLongPress(mock())

            // THEN nothing happens
            assertThat(switcherRequest)
                .isNotInstanceOf(DreamInteractor.SwitcherRequest.Show::class.java)
            assertThat(vibratorHelper.fake.totalVibrations).isEqualTo(0)
        }

    @Test
    fun onDestroy_unsubscribesFromDreamRepository() =
        kosmos.runTest {
            // Ensure the handler is created
            underTest

            val collectors by collectLastValue(dreamRepository.fake.activeDreamStateCollectors)
            assertThat(collectors).isEqualTo(1)

            lifecycleOwner.currentState = Lifecycle.State.DESTROYED

            assertThat(collectors).isEqualTo(0)
        }

    @Test
    fun onDestroy_removesAccessibilityAction() =
        kosmos.runTest {
            // Ensure the handler is created
            underTest

            // GIVEN switching is enabled and an accessibility action has been added
            setCanSwitchDreams(true)
            assertThat(getAccessibilityActions(containerView)).isNotEmpty()

            // WHEN the handler is destroyed
            underTest.onDestroy()

            // THEN the accessibility action is removed
            assertThat(getAccessibilityActions(containerView)).isEmpty()
        }

    @Test
    fun canSwitchDreams_emitsTrue_accessibilityActionAdded() =
        kosmos.runTest {
            // Ensure the handler is created
            underTest

            // GIVEN no accessibility actions are present
            assertThat(getAccessibilityActions(containerView)).isEmpty()

            // WHEN canSwitchDreams emits true
            setCanSwitchDreams(true)

            // THEN the accessibility action is added
            assertThat(getAccessibilityActions(containerView)).hasSize(1)
            assertThat(getAccessibilityActions(containerView).first().label)
                .isEqualTo(context.getString(R.string.dreams_switcher_accessibility_action))
        }

    @Test
    fun canSwitchDreams_emitsFalse_accessibilityActionRemoved() =
        kosmos.runTest {
            // Ensure the handler is created
            underTest

            // GIVEN canSwitchDreams is true and the accessibility action has been added
            setCanSwitchDreams(true)
            assertThat(getAccessibilityActions(containerView)).isNotEmpty()

            // WHEN canSwitchDreams emits false
            setCanSwitchDreams(false)

            // THEN the accessibility action is removed
            assertThat(getAccessibilityActions(containerView)).isEmpty()
        }

    private suspend fun Kosmos.setupUser() {
        fakeUserRepository.setUserInfos(listOf(USER))
        fakeUserRepository.setSelectedUserInfo(USER)
        // Wait for the user to be set before continuing
        dreamInteractor.dreamState.first()
    }

    private fun Kosmos.setCanSwitchDreams(canSwitch: Boolean) {
        val dreams =
            if (canSwitch) {
                listOf(
                    DreamItemModel(ComponentName("d1", "d1")),
                    DreamItemModel(ComponentName("d2", "d2")),
                )
            } else {
                listOf(DreamItemModel(ComponentName("d1", "d1")))
            }
        dreamRepository.fake.setDreamState(USER.userHandle, DreamPlaylistModel(dreams, 0))
    }

    private fun getAccessibilityActions(
        view: DreamOverlayContainerView
    ): List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> {
        val nodeInfo = AccessibilityNodeInfoCompat.obtain()
        view.onInitializeAccessibilityNodeInfo(nodeInfo.unwrap())
        return nodeInfo.actionList
    }

    private companion object {
        val USER = UserInfo(0, "user", UserInfo.FLAG_MAIN)
    }
}
