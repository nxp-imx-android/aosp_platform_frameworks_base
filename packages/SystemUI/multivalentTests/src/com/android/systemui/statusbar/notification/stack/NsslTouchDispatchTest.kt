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
@file:Suppress("SameParameterValue")

package com.android.systemui.statusbar.notification.stack

import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.SystemClock
import android.testing.TestableLooper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.notifications.ui.YSpace
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.shade.ShadeController
import com.android.systemui.statusbar.NotificationShelf
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManager
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager
import com.android.systemui.statusbar.notification.collection.render.groupExpansionManager
import com.android.systemui.statusbar.notification.collection.render.groupMembershipManager
import com.android.systemui.statusbar.notification.row.ExpandableView
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.phone.ScreenOffAnimationController
import com.android.systemui.statusbar.phone.screenOffAnimationController
import com.android.systemui.testKosmos
import com.android.systemui.util.MotionEventSubject.Companion.assertThat
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import kotlin.properties.ReadOnlyProperty
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Validates [NotificationStackScrollLayout]'s touch dispatch logic while avoiding the use of mocks.
 * * Uses real instances of the NSSL, its controller and touch helpers.
 * * Generates valid valid, multi-pointer gesture streams. See [MotionEventBuilder].
 * * Simulates a view hierarchy where NSSL overlaps a sibling view that records dispatched touches:
 * ```
 * FrameLayout (parent)
 *  ├── TouchRecordingView (sibling underneath, capturing touches)
 *  └── NotificationStackScrollLayout (NSSL on top, ready to scroll)
 * ```
 */
@EnableSceneContainer
@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class NsslTouchDispatchTest : SysuiTestCase() {
    private lateinit var parent: ViewGroup
    private lateinit var underTest: NotificationStackScrollLayout
    private lateinit var controller: NotificationStackScrollLayoutController
    private lateinit var sibling: TouchRecordingView

    private var touchSlop: Int = 0

    private val kosmos = testKosmos()
    private val motionEventBuilder = MotionEventBuilder()

    @Before
    fun setup() {
        injectTestDependencies()

        // 1. Initialize Views
        sibling = TouchRecordingView(context)
        underTest =
            NotificationStackScrollLayout(context, null).apply {
                initView(context, mock(), mock())
                setScrollingEnabled(true)
                // ScrollView requires at least one child to become a touch target for the initial
                // down.
                addView(DummyExpandableView(context))
            }

        // 2. Setup Controller
        controller = TestController(nssl = underTest, sibling = sibling)
        underTest.setController(controller)

        // 3. Configure Bounds (Crucial for NSSL#isIncontentBounds to return true.)
        setupStackBounds(left = 0f, top = 0f, bottom = 1000f, right = 1000f)

        // 4. Assemble and Layout
        parent =
            FrameLayout(context).apply {
                addView(sibling)
                addView(underTest)
                measureAndLayout(width = 1000, height = 1000)
            }

        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    @Test
    fun verticalSwipe_exceedsSlop() {
        val dispatchedEvents = sibling.capturedEvents
        var yPosition = 100f

        onDownEvent(pointerId = 0, x = 100f, y = yPosition)
        assertThat(dispatchedEvents).isEmpty()

        onMoveEvent(pointerId = 0, x = 100f, y = ++yPosition)
        assertThat(dispatchedEvents).isEmpty()

        yPosition += touchSlop * 2 // exceed the slop
        onMoveEvent(pointerId = 0, x = 100f, y = yPosition)
        // The stack is being dragged, a touch is to be dispatched on the next ACTION_MOVE.
        assertThat(underTest.isBeingDragged()).isTrue()
        assertThat(dispatchedEvents).isEmpty()

        onMoveEvent(pointerId = 0, x = 100f, y = ++yPosition)
        assertThat(dispatchedEvents.last()).isDown()

        onUpEvent()
        assertThat(dispatchedEvents.last()).isUp()

        assertThat(dispatchedEvents).hasSize(2)
    }

    private fun injectTestDependencies() {
        // Migrate these to Kosmos one day.
        mDependency.injectMockDependency(ShadeController::class.java)
        mDependency.injectMockDependency(NotificationSectionsManager::class.java)
        mDependency.injectMockDependency(NotificationShelf::class.java)
        // Kosmos
        @Suppress("DEPRECATION")
        mDependency.injectTestDependency(
            com.android.systemui.flags.FeatureFlags::class.java,
            kosmos.featureFlagsClassic,
        )
        mDependency.injectTestDependency(
            SysuiStatusBarStateController::class.java,
            kosmos.statusBarStateController,
        )
        mDependency.injectTestDependency(
            GroupMembershipManager::class.java,
            kosmos.groupMembershipManager,
        )
        mDependency.injectTestDependency(
            GroupExpansionManager::class.java,
            kosmos.groupExpansionManager,
        )
        mDependency.injectTestDependency(AmbientState::class.java, kosmos.ambientState)
        mDependency.injectTestDependency(
            ScreenOffAnimationController::class.java,
            kosmos.screenOffAnimationController,
        )
    }

    private fun setupStackBounds(left: Float, top: Float, bottom: Float, right: Float) {
        val bounds = ShadeScrimBounds(left = 0f, top = 0f, bottom = 1000f, right = 1000f)
        underTest.setClippingShape(
            ShadeScrimShape(bounds = bounds, topRadius = 0, bottomRadius = 0)
        )
        underTest.updateStackBounds(YSpace(top = bounds.top, bottom = bounds.bottom))
    }

    private fun View.measureAndLayout(width: Int, height: Int) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, width, height)
    }

    /** Initial down event. Simulates a valid [MotionEvent] and sends it to the [parent] View. */
    private fun onDownEvent(pointerId: Int, x: Float, y: Float) =
        underTest.dispatchTouchEvent(motionEventBuilder.down(pointerId, x, y))

    /** Final up event. Simulates a valid [MotionEvent] and sends it to the [parent] View. */
    private fun onUpEvent() = underTest.dispatchTouchEvent(motionEventBuilder.up())

    /**
     * Moves the pointer with [pointerId]. Simulates a valid [MotionEvent] and sends it to the
     * [parent] View.
     */
    private fun onMoveEvent(pointerId: Int, x: Float, y: Float) =
        underTest.dispatchTouchEvent(motionEventBuilder.move(pointerId, x, y))

    /**
     * Non primary pointer down. Simulates a valid [MotionEvent] and sends it to the [parent] View.
     */
    private fun onPointerDownEvent(pointerId: Int, x: Float, y: Float) =
        underTest.dispatchTouchEvent(motionEventBuilder.pointerDown(pointerId, x, y))

    /**
     * Non primary pointer down. Simulates a valid [MotionEvent] and sends it to the [parent] View.
     */
    private fun onPointerUpEvent(pointerId: Int) =
        underTest.dispatchTouchEvent(motionEventBuilder.pointerUp(pointerId))
}

/**
 * Utility for generating multi-touch [MotionEvent] sequences to reduce boilerplate. Each method:
 * - Updates a single pointer, and updates the active pointers stored in this builder.
 * - Generates a new [MotionEvent] that contains information about all the active pointers.
 */
private class MotionEventBuilder {
    private var downTime: Long = SystemClock.uptimeMillis()
    private var eventTime: Long = downTime
    // PointerID -> (X, Y)
    private val activePointers = LinkedHashMap<Int, Pair<Float, Float>>()

    /** Initial down event. */
    fun down(pointerId: Int, x: Float, y: Float): MotionEvent {
        activePointers.clear()
        activePointers[pointerId] = x to y
        return buildEvent(ACTION_DOWN, pointerId)
    }

    /** Final up event. */
    fun up(): MotionEvent {
        val event = buildEvent(ACTION_UP, activePointers.keys.firstOrNull() ?: 0)
        activePointers.clear()
        return event
    }

    /** Updates a single pointer and fires a single MOVE event. */
    fun move(pointerId: Int, x: Float, y: Float): MotionEvent {
        activePointers[pointerId] = x to y
        return buildEvent(ACTION_MOVE, pointerId)
    }

    /** Non primary pointer down. */
    fun pointerDown(pointerId: Int, x: Float, y: Float): MotionEvent {
        activePointers[pointerId] = x to y
        return buildEvent(ACTION_POINTER_DOWN, pointerId)
    }

    /** Non primary pointer down. */
    fun pointerUp(pointerId: Int): MotionEvent {
        val event = buildEvent(ACTION_POINTER_UP, pointerId)
        activePointers.remove(pointerId)
        return event
    }

    private fun buildEvent(action: Int, targetPointerId: Int): MotionEvent {
        eventTime += 10 // Advance time slightly for each event
        val count = activePointers.size
        val properties = Array(count) { PointerProperties() }
        val coords = Array(count) { PointerCoords() }
        var index = 0
        var actionIndex = 0
        for ((id, pos) in activePointers) {
            properties[index].id = id
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[index].x = pos.first
            coords[index].y = pos.second
            coords[index].pressure = 1.0f
            coords[index].size = 1.0f
            if (id == targetPointerId) {
                actionIndex = index
            }
            index++
        }
        // Combine the Action with the Index of the target pointer (for POINTER_DOWN / POINTER_UP).
        val finalAction =
            if (action == ACTION_POINTER_DOWN || action == ACTION_POINTER_UP) {
                action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            } else {
                action
            }
        return MotionEvent.obtain(
            /* downTime = */ downTime,
            /* eventTime = */ eventTime,
            /* action = */ finalAction,
            /* pointerCount = */ count,
            /* pointerProperties = */ properties,
            /* pointerCoords = */ coords,
            /* metaState = */ 0,
            /* buttonState = */ 0,
            /* xPrecision = */ 1.0f,
            /* yPrecision = */ 1.0f,
            /* deviceId = */ 0,
            /* edgeFlags = */ 0,
            /* source = */ InputDevice.SOURCE_TOUCHSCREEN,
            /* flags = */ 0,
        )
    }
}

/** Captures events sent to [NotificationStackScrollLayoutController.sendTouchToSceneFramework]. */
private fun collectDispatchEvents(
    controller: NotificationStackScrollLayoutController
): ReadOnlyProperty<Any?, List<MotionEvent>> {
    val capturedEvents = ArrayList<MotionEvent>()
    doAnswer { invocation ->
            val event = invocation.getArgument(0, MotionEvent::class.java)
            // Make a deep copy, because MotionEvents are mutable and recycled by the caller.
            val copy = MotionEvent.obtain(event)
            capturedEvents.add(copy)
            Unit
        }
        .whenever(controller)
        .sendTouchToSceneFramework(any())
    return ReadOnlyProperty { _, _ -> capturedEvents.toList() }
}

/**
 * An instance of [NotificationStackScrollLayoutController] that overrides dispatching touches to
 * the provided sibling.
 */
private class TestController(nssl: NotificationStackScrollLayout, private val sibling: ViewGroup) :
    NotificationStackScrollLayoutController(
        /* view = */ nssl,
        /* allowLongPress = */ false,
        /* notificationGutsManager = */ mock(),
        /* notificationsController = */ mock(),
        /* visibilityProvider = */ mock(),
        /* wakeUpCoordinator = */ mock(),
        /* headsUpManager = */ mock(),
        /* statusBarService = */ mock(),
        /* notificationRoundnessManager = */ mock(),
        /* tunerService = */ mock(),
        /* dynamicPrivacyController = */ mock(),
        /* configurationController = */ mock(),
        /* statusBarStateController = */ mock(),
        /* keyguardMediaController = */ mock(),
        /* keyguardBypassController = */ mock(),
        /* powerInteractor = */ mock(),
        /* lockscreenUserManager = */ mock(),
        /* metricsLogger = */ mock(),
        /* colorUpdateLogger = */ mock(),
        /* dumpManager = */ mock(),
        /* falsingCollector = */ mock(),
        /* falsingManager = */ mock(),
        /* notificationSwipeHelperBuilder = */ mock<NotificationSwipeHelper.Builder>().apply {
            whenever(this.setNotificationCallback(any())).thenReturn(this)
            whenever(this.setOnMenuEventListener(any())).thenReturn(this)
            whenever(this.build()).thenReturn(mock())
        },
        /* groupManager = */ mock(),
        /* notifPipeline = */ mock(),
        /* notifCollection = */ mock(),
        /* lockscreenShadeTransitionController = */ mock(),
        /* uiEventLogger = */ mock(),
        /* visibilityLocationProviderDelegator = */ mock(),
        /* viewBinder = */ mock(),
        /* shadeController = */ mock(),
        /* windowRootView = */ mock(),
        /* jankMonitor = */ mock(),
        /* stackLogger = */ mock(),
        /* logger = */ mock(),
        /* notificationStackSizeCalculator = */ mock(),
        /* notificationTargetsHelper = */ mock(),
        /* secureSettings = */ mock(),
        /* dismissibilityProvider = */ mock(),
        /* activityStarter = */ mock(),
        /* splitShadeStateController = */ mock(),
        /* sensitiveNotificationProtectionController = */ mock(),
        /* magneticNotificationRowManager = */ mock(),
        /* sectionsManager = */ mock(),
    ) {
    override fun sendTouchToSceneFramework(ev: MotionEvent?) {
        // don't call super
        sibling.dispatchTouchEvent(ev)
    }
}

private class DummyExpandableView(context: Context) : ExpandableView(context, null) {
    override fun dump(pw: PrintWriter, args: Array<out String>) {}

    override fun performRemoveAnimation(
        duration: Long,
        delay: Long,
        translationDirection: Float,
        isHeadsUpAnimation: Boolean,
        isHeadsUpCycling: Boolean,
        onStartedRunnable: Runnable?,
        onFinishedRunnable: Runnable?,
        animationListener: AnimatorListenerAdapter?,
        clipSide: ClipSide?,
    ): Long {
        return 0L
    }

    override fun performAddAnimation(
        delay: Long,
        duration: Long,
        isHeadsUpAppear: Boolean,
        isHeadsUpCycling: Boolean,
        onEndRunnable: Runnable?,
    ) {}

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
}

private class TouchRecordingView(context: Context) : ViewGroup(context) {
    val capturedEvents = ArrayList<MotionEvent>()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Make a deep copy, because MotionEvents are mutable and recycled by the caller.
        val copy = MotionEvent.obtain(ev)
        capturedEvents.add(copy)
        return true // always wants the event
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
}
