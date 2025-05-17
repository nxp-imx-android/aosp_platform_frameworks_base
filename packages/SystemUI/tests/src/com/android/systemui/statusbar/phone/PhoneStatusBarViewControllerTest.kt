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

package com.android.systemui.statusbar.phone

import android.app.StatusBarManager.WINDOW_STATE_HIDDEN
import android.app.StatusBarManager.WINDOW_STATE_HIDING
import android.app.StatusBarManager.WINDOW_STATE_SHOWING
import android.app.StatusBarManager.WINDOW_STATUS_BAR
import android.graphics.Insets
import android.hardware.display.DisplayManagerGlobal
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.view.Display
import android.view.DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS
import android.view.DisplayInfo
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.systemui.SysuiTestCase
import com.android.systemui.SysuiTestableContext
import com.android.systemui.battery.BatteryMeterView
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.shade.ShadeControllerImpl
import com.android.systemui.shade.ShadeLogger
import com.android.systemui.shade.ShadeViewController
import com.android.systemui.shade.StatusBarLongPressGestureDetector
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.shade.data.repository.defaultShadeDisplayPolicy
import com.android.systemui.shade.display.StatusBarTouchShadeDisplayPolicy
import com.android.systemui.shade.domain.interactor.PanelExpansionInteractor
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.data.repository.FakeStatusBarConfigurationControllerStore
import com.android.systemui.statusbar.data.repository.fakeStatusBarContentInsetsProviderStore
import com.android.systemui.statusbar.phone.PhoneStatusBarViewController.PhoneStatusBarViewInteractionsGate
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import com.android.systemui.testKosmos
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider
import com.android.systemui.user.ui.viewmodel.StatusBarUserChipViewModel
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.view.ViewUtil
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class PhoneStatusBarViewControllerTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos()
    private val statusBarContentInsetsProviderStore = kosmos.fakeStatusBarContentInsetsProviderStore
    private val statusBarContentInsetsProvider = statusBarContentInsetsProviderStore.defaultDisplay
    private val statusBarContentInsetsProviderForSecondaryDisplay =
        statusBarContentInsetsProviderStore.forDisplay(SECONDARY_DISPLAY_ID)
    private val windowRootView = mock<WindowRootView>()

    private val fakeDarkIconDispatcher = kosmos.fakeDarkIconDispatcher
    @Mock private lateinit var shadeViewController: ShadeViewController
    @Mock private lateinit var panelExpansionInteractor: PanelExpansionInteractor
    @Mock private lateinit var progressProvider: ScopedUnfoldTransitionProgressProvider
    @Mock private lateinit var mStatusOverlayHoverListenerFactory: StatusOverlayHoverListenerFactory
    @Mock private lateinit var mStatusOverlayHoverListener: StatusOverlayHoverListener
    @Mock private lateinit var userChipViewModel: StatusBarUserChipViewModel
    @Mock private lateinit var centralSurfacesImpl: CentralSurfacesImpl
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var shadeControllerImpl: ShadeControllerImpl
    @Mock private lateinit var shadeLogger: ShadeLogger
    @Mock private lateinit var viewUtil: ViewUtil
    @Mock private lateinit var mStatusBarLongPressGestureDetector: StatusBarLongPressGestureDetector
    @Mock private lateinit var statusBarTouchShadeDisplayPolicy: StatusBarTouchShadeDisplayPolicy
    @Mock private lateinit var shadeDisplayRepository: ShadeDisplaysRepository
    private lateinit var statusBarWindowStateController: StatusBarWindowStateController
    private val fakeConfigurationControllerStore = FakeStatusBarConfigurationControllerStore()
    private lateinit var configurationController: ConfigurationController

    private lateinit var view: PhoneStatusBarView
    private lateinit var controller: PhoneStatusBarViewController

    private lateinit var viewForSecondaryDisplay: PhoneStatusBarView

    private val clockView: Clock
        get() = view.requireViewById(R.id.clock)

    private val batteryView: BatteryMeterView
        get() = view.requireViewById(R.id.battery)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        statusBarWindowStateController = StatusBarWindowStateController(DISPLAY_ID, commandQueue)
        configurationController =
            fakeConfigurationControllerStore.forDisplay(Display.DEFAULT_DISPLAY)

        whenever(statusBarContentInsetsProvider.getStatusBarContentInsetsForCurrentRotation())
            .thenReturn(Insets.NONE)
        whenever(mStatusOverlayHoverListenerFactory.createDarkAwareListener(any()))
            .thenReturn(mStatusOverlayHoverListener)

        // create the view and controller on main thread as it requires main looper
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(mContext) // add parent to keep layout params
            view =
                LayoutInflater.from(mContext).inflate(R.layout.status_bar, parent, false)
                    as PhoneStatusBarView
            controller = createAndInitController(view)
        }

        whenever(
                statusBarContentInsetsProviderForSecondaryDisplay
                    .getStatusBarContentInsetsForCurrentRotation()
            )
            .thenReturn(Insets.NONE)

        val contextForSecondaryDisplay =
            SysuiTestableContext(
                mContext.createDisplayContext(
                    Display(
                        DisplayManagerGlobal.getInstance(),
                        SECONDARY_DISPLAY_ID,
                        DisplayInfo(),
                        DEFAULT_DISPLAY_ADJUSTMENTS,
                    )
                )
            )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(contextForSecondaryDisplay) // add parent to keep layout params
            viewForSecondaryDisplay =
                LayoutInflater.from(contextForSecondaryDisplay)
                    .inflate(R.layout.status_bar, parent, false) as PhoneStatusBarView
            createAndInitController(viewForSecondaryDisplay)
        }
    }

    @Test
    fun onViewAttachedAndDrawn_startListeningConfigurationControllerCallback() {
        val view = createViewMock(view)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        verify(configurationController).addCallback(any())
    }

    @Test
    fun onViewAttachedAndDrawn_darkReceiversRegistered() {
        val view = createViewMock(view)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        assertThat(fakeDarkIconDispatcher.receivers.size).isEqualTo(2)
        assertThat(fakeDarkIconDispatcher.receivers).contains(clockView)
        assertThat(fakeDarkIconDispatcher.receivers).contains(batteryView)
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun onViewAttachedAndDrawn_connectedDisplaysFlagOff_doesNotSetInteractionGate() {
        val view = createViewMock(view)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        verify(view, never()).setInteractionGate(any())
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun onViewAttachedAndDrawn_connectedDisplaysFlagOn_defaultDisplay_doesNotSetInteractionGate() {
        val view = createViewMock(view)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        verify(view, never()).setInteractionGate(any())
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun onViewAttachedAndDrawn_connectedDisplaysFlagOn_secondaryDisplay_setsInteractionGate() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }

        verify(viewForSecondaryDisplay).setInteractionGate(any())
    }

    @Test
    fun onViewAttached_containersInteractive() {
        val view = createViewMock(view)
        val endSideContainer = spy(view.requireViewById<View>(R.id.system_icons))
        whenever(view.requireViewById<View>(R.id.system_icons)).thenReturn(endSideContainer)
        val statusContainer = spy(view.requireViewById<View>(R.id.status_bar_start_side_content))
        whenever(view.requireViewById<View>(R.id.status_bar_start_side_content))
            .thenReturn(statusContainer)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        verify(endSideContainer).setOnHoverListener(any())
        verify(statusContainer).setOnTouchListener(any())
    }

    @Test
    fun onViewDetached_darkReceiversUnregistered() {
        val view = createViewMock(view)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        assertThat(fakeDarkIconDispatcher.receivers).isNotEmpty()

        controller.onViewDetached()

        assertThat(fakeDarkIconDispatcher.receivers).isEmpty()
    }

    @Test
    fun handleTouchEventFromStatusBar_panelsNotEnabled_returnsFalseAndNoViewEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(false)
        val returnVal =
            view.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0))
        assertThat(returnVal).isFalse()
        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    fun handleTouchEventFromStatusBar_viewNotEnabled_returnsTrueAndNoViewEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(false)
        val returnVal =
            view.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0))
        assertThat(returnVal).isTrue()
        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    fun handleTouchEventFromStatusBar_viewNotEnabledButIsMoveEvent_viewReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(false)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 0f, 2f, 0)

        view.onTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    fun handleTouchEventFromStatusBar_panelAndViewEnabled_viewReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        view.onTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    fun handleTouchEventFromStatusBar_topEdgeTouch_viewNeverReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(panelExpansionInteractor.isFullyCollapsed).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        view.onTouchEvent(event)

        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    fun handleTouchEventFromStatusBar_touchOnPrimaryDisplay_shadeReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        view.dispatchTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun handleTouchEventFromStatusBar_touchOnSecondaryDisplay_interactionsAllowed_shadeReceivesEvent() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        // Ensure test is set up with an interaction gate that allows interactions.
        whenever(shadeDisplayRepository.currentPolicy).thenReturn(statusBarTouchShadeDisplayPolicy)
        val argumentCaptor = ArgumentCaptor.forClass(PhoneStatusBarViewInteractionsGate::class.java)
        verify(viewForSecondaryDisplay).setInteractionGate(argumentCaptor.capture())
        assertThat(argumentCaptor.value.shouldAllowInteractions()).isTrue()
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        viewForSecondaryDisplay.dispatchTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun handleTouchEventFromStatusBar_touchOnSecondaryDisplay_interactionsNotAllowed_shadeDoesNotReceiveEvent() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        // Ensure test is set up with an interaction gate that does not allow interactions.
        whenever(shadeDisplayRepository.currentPolicy).thenReturn(kosmos.defaultShadeDisplayPolicy)
        val argumentCaptor = ArgumentCaptor.forClass(PhoneStatusBarViewInteractionsGate::class.java)
        verify(viewForSecondaryDisplay).setInteractionGate(argumentCaptor.capture())
        assertThat(argumentCaptor.value.shouldAllowInteractions()).isFalse()
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        viewForSecondaryDisplay.dispatchTouchEvent(event)

        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP)
    fun handleInterceptTouchEventFromStatusBar_shadeReturnsFalse_flagOff_viewReturnsFalse() {
        whenever(shadeViewController.handleExternalInterceptTouch(any())).thenReturn(false)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        val returnVal = view.onInterceptTouchEvent(event)

        assertThat(returnVal).isFalse()
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP)
    fun handleInterceptTouchEventFromStatusBar_shadeReturnsFalse_flagOn_viewReturnsFalse() {
        whenever(shadeViewController.handleExternalInterceptTouch(any())).thenReturn(false)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        val returnVal = view.onInterceptTouchEvent(event)

        assertThat(returnVal).isFalse()
    }

    @Test
    @DisableFlags(com.android.systemui.Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP)
    fun handleInterceptTouchEventFromStatusBar_shadeReturnsTrue_flagOff_viewReturnsFalse() {
        whenever(shadeViewController.handleExternalInterceptTouch(any())).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        val returnVal = view.onInterceptTouchEvent(event)

        assertThat(returnVal).isFalse()
    }

    @Test
    @EnableFlags(com.android.systemui.Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP)
    fun handleInterceptTouchEventFromStatusBar_shadeReturnsTrue_flagOn_viewReturnsTrue() {
        whenever(shadeViewController.handleExternalInterceptTouch(any())).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        val returnVal = view.onInterceptTouchEvent(event)

        assertThat(returnVal).isTrue()
    }

    @Test
    fun onTouch_windowHidden_centralSurfacesNotNotified() {
        val callback = getCommandQueueCallback()
        callback.setWindowState(DISPLAY_ID, WINDOW_STATUS_BAR, WINDOW_STATE_HIDDEN)

        controller.onTouch(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0))

        verify(centralSurfacesImpl, never()).setInteracting(any(), any())
    }

    @Test
    fun onTouch_windowHiding_centralSurfacesNotNotified() {
        val callback = getCommandQueueCallback()
        callback.setWindowState(DISPLAY_ID, WINDOW_STATUS_BAR, WINDOW_STATE_HIDING)

        controller.onTouch(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0))

        verify(centralSurfacesImpl, never()).setInteracting(any(), any())
    }

    @Test
    fun onTouch_windowShowing_centralSurfacesNotified() {
        val callback = getCommandQueueCallback()
        callback.setWindowState(DISPLAY_ID, WINDOW_STATUS_BAR, WINDOW_STATE_SHOWING)

        controller.onTouch(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0))

        verify(centralSurfacesImpl).setInteracting(any(), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onInterceptTouchEvent_actionDown_propagatesToDisplayPolicy() {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        view.onInterceptTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy).onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onInterceptTouchEvent_actionUp_notPropagatesToDisplayPolicy() {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 0f, 0f, 0)

        view.onInterceptTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy, never()).onStatusBarOrLauncherTouched(any(), any())
    }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onInterceptTouchEvent_shadeWindowGoesAroundDisabled_notPropagatesToDisplayPolicy() {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        view.onInterceptTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy, never())
            .onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onTouch_withMouseOnEndSideIcons_flagOn_propagatedToShadeDisplayPolicy() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }
        val event = getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)

        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        statusContainer.dispatchTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy).onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onTouch_withMouseOnStartSideIcons_flagOn_propagatedToShadeDisplayPolicy() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }
        val event = getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)

        val statusContainer = view.requireViewById<View>(R.id.status_bar_start_side_content)
        statusContainer.dispatchTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy).onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onTouch_withMouseOnSystemIcons_flagOff_notPropagatedToShadeDisplayPolicy() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }
        val event = getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)

        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        statusContainer.dispatchTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy, never())
            .onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_shadeGoesAroundFlagOff_returnsFalse() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }
        val argumentCaptor = ArgumentCaptor.forClass(PhoneStatusBarViewInteractionsGate::class.java)
        verify(viewForSecondaryDisplay).setInteractionGate(argumentCaptor.capture())

        assertThat(argumentCaptor.value.shouldAllowInteractions()).isFalse()
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_defaultShadeDisplayPolicy_returnsFalse() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }
        val argumentCaptor = ArgumentCaptor.forClass(PhoneStatusBarViewInteractionsGate::class.java)
        verify(viewForSecondaryDisplay).setInteractionGate(argumentCaptor.capture())

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(kosmos.defaultShadeDisplayPolicy)
        assertThat(argumentCaptor.value.shouldAllowInteractions()).isFalse()
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_statusBarTouchShadeDisplayPolicy_returnsTrue() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }
        val argumentCaptor = ArgumentCaptor.forClass(PhoneStatusBarViewInteractionsGate::class.java)
        verify(viewForSecondaryDisplay).setInteractionGate(argumentCaptor.capture())

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(statusBarTouchShadeDisplayPolicy)
        assertThat(argumentCaptor.value.shouldAllowInteractions()).isTrue()
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_shadePolicyChanges_updatesReturnValue() {
        val viewForSecondaryDisplay = createViewMock(viewForSecondaryDisplay)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(viewForSecondaryDisplay)
        }
        val argumentCaptor = ArgumentCaptor.forClass(PhoneStatusBarViewInteractionsGate::class.java)
        verify(viewForSecondaryDisplay).setInteractionGate(argumentCaptor.capture())

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(kosmos.defaultShadeDisplayPolicy)
        assertThat(argumentCaptor.value.shouldAllowInteractions()).isFalse()

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(statusBarTouchShadeDisplayPolicy)
        assertThat(argumentCaptor.value.shouldAllowInteractions()).isTrue()
    }

    @Test
    @EnableSceneContainer
    fun dualShade_qsIsExpandedOnEndSideContentMouseClick() =
        kosmos.runTest {
            kosmos.enableDualShade(wideLayout = true)

            val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

            val view = createViewMock(view)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller = createAndInitController(view)
            }
            val endSideContainer = view.requireViewById<View>(R.id.system_icons)
            endSideContainer.dispatchTouchEvent(
                getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)
            )

            verify(shadeControllerImpl).animateExpandQs()
            verify(shadeControllerImpl, never()).animateExpandShade()
        }

    @Test
    fun shadeIsExpandedOnEndSideContentMouseClick() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }
        val endSideContainer = view.requireViewById<View>(R.id.system_icons)
        endSideContainer.dispatchTouchEvent(getActionUpEventFromSource(InputDevice.SOURCE_MOUSE))

        verify(shadeControllerImpl).animateExpandShade()
        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    @Test
    fun shadeIsExpandedOnStartSideContentMouseClick() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }

        val startSideContainer = view.requireViewById<View>(R.id.status_bar_start_side_content)
        startSideContainer.dispatchTouchEvent(getActionUpEventFromSource(InputDevice.SOURCE_MOUSE))

        verify(shadeControllerImpl).animateExpandShade()
        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    @Test
    fun statusIconContainerIsNotHandlingTouchScreenTouches() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }
        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        val handled =
            statusContainer.dispatchTouchEvent(
                getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
            )
        assertThat(handled).isFalse()
    }

    private fun getActionUpEventFromSource(source: Int): MotionEvent {
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        ev.source = source
        return ev
    }

    @Test
    fun shadeIsNotExpandedOnStatusBarGeneralClick() {
        val view = createViewMock(view)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = createAndInitController(view)
        }
        view.performClick()
        verify(shadeControllerImpl, never()).animateExpandShade()
    }

    private fun getCommandQueueCallback(): CommandQueue.Callbacks {
        val captor = argumentCaptor<CommandQueue.Callbacks>()
        verify(commandQueue).addCallback(captor.capture())
        return captor.value!!
    }

    private fun createViewMock(view: PhoneStatusBarView): PhoneStatusBarView {
        val mView = spy(view)
        val viewTreeObserver = mock(ViewTreeObserver::class.java)
        whenever(mView.viewTreeObserver).thenReturn(viewTreeObserver)
        whenever(mView.isAttachedToWindow).thenReturn(true)
        return mView
    }

    private fun createAndInitController(view: PhoneStatusBarView): PhoneStatusBarViewController {
        return PhoneStatusBarViewController.Factory(
                Optional.of(progressProvider),
                userChipViewModel,
                centralSurfacesImpl,
                statusBarWindowStateController,
                shadeControllerImpl,
                shadeViewController,
                kosmos.shadeModeInteractor,
                panelExpansionInteractor,
                { mStatusBarLongPressGestureDetector },
                { windowRootView },
                shadeLogger,
                viewUtil,
                fakeConfigurationControllerStore,
                configurationController,
                mStatusOverlayHoverListenerFactory,
                fakeDarkIconDispatcher,
                statusBarContentInsetsProviderStore,
                { statusBarTouchShadeDisplayPolicy },
                { shadeDisplayRepository },
            )
            .create(view)
            .also { it.init() }
    }

    private companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }

        const val DISPLAY_ID = 0
        const val SECONDARY_DISPLAY_ID = 2
    }
}
