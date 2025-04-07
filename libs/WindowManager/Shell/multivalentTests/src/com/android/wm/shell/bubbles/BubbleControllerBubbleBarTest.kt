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
 * limitations under the License.
 */

package com.android.wm.shell.bubbles

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.IWindowManager
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.internal.statusbar.IStatusBarService
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.BubbleBarUpdate
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewRepository
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** Tests for [BubbleController] when using bubble bar */
@SmallTest
@EnableFlags(Flags.FLAG_ENABLE_BUBBLE_BAR)
@RunWith(AndroidJUnit4::class)
class BubbleControllerBubbleBarTest {

    companion object {
        private const val SCREEN_WIDTH = 2000
        private const val SCREEN_HEIGHT = 1000
    }

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var bubbleController: BubbleController
    private lateinit var uiEventLoggerFake: UiEventLoggerFake
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var bubbleData: BubbleData
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var expandedViewManager: FakeBubbleExpandedViewManager
    private lateinit var iconFactory: BubbleIconFactory
    private lateinit var bubbleTaskViewFactory: BubbleTaskViewFactory

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        uiEventLoggerFake = UiEventLoggerFake()
        val bubbleLogger = BubbleLogger(uiEventLoggerFake)

        bubbleTaskViewFactory = FakeBubbleTaskViewFactory(context, mainExecutor)
        expandedViewManager = FakeBubbleExpandedViewManager()
        iconFactory =
            BubbleIconFactory(
                context,
                context.resources.getDimensionPixelSize(R.dimen.bubble_size),
                context.resources.getDimensionPixelSize(R.dimen.bubble_badge_size),
                Color.BLACK,
                context.resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.importance_ring_stroke_width
                )
            )

        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40),
            )

        bubblePositioner = BubblePositioner(context, deviceConfig)

        bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                bubblePositioner,
                BubbleEducationController(context),
                mainExecutor,
                bgExecutor,
            )

        val shellInit = ShellInit(mainExecutor)

        bubbleController =
            createBubbleController(
                shellInit,
                bubbleData,
                bubbleLogger,
                bubblePositioner,
                mainExecutor,
                bgExecutor,
            )
        bubbleController.asBubbles().setSysuiProxy(mock<SysuiProxy>())

        shellInit.init()

        mainExecutor.flushAll()
        bgExecutor.flushAll()
    }

    @After
    fun tearDown() {
        mainExecutor.flushAll()
        bgExecutor.flushAll()
    }

    @Test
    fun testEventLogging_bubbleBar_dragBarLeft() {
        bubblePositioner.isShowingInBubbleBar = true
        bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.LEFT,
            BubbleBarLocation.UpdateSource.DRAG_BAR,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_BAR.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBarRight() {
        bubblePositioner.isShowingInBubbleBar = true
        bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.RIGHT,
            BubbleBarLocation.UpdateSource.DRAG_BAR,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_BAR.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBubbleLeft() {
        bubblePositioner.isShowingInBubbleBar = true
        bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.LEFT,
            BubbleBarLocation.UpdateSource.DRAG_BUBBLE,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_BUBBLE.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBubbleRight() {
        bubblePositioner.isShowingInBubbleBar = true
        bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.RIGHT,
            BubbleBarLocation.UpdateSource.DRAG_BUBBLE,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_BUBBLE.id)
    }

    @Test
    fun registerBubbleBarListener_switchToBarAndBackToStack() {
        val bubble = addBubble()
        inflateBubble(bubble)
        assertThat(bubbleController.hasBubbles()).isTrue()

        assertFloatingMode(bubble)

        bubblePositioner.isShowingInBubbleBar = true
        bubbleController.registerBubbleStateListener(FakeBubblesStateListener())

        assertBarMode(bubble)

        bubbleController.unregisterBubbleStateListener()

        assertFloatingMode(bubble)
    }

    private fun assertFloatingMode(bubble: Bubble) {
        assertThat(bubbleController.stackView).isNotNull()
        assertThat(bubbleController.layerView).isNull()
        assertThat(bubble.iconView).isNotNull()
        assertThat(bubble.expandedView).isNotNull()
        assertThat(bubble.bubbleBarExpandedView).isNull()
    }

    private fun assertBarMode(bubble: Bubble) {
        assertThat(bubbleController.stackView).isNull()
        assertThat(bubbleController.layerView).isNotNull()
        assertThat(bubble.iconView).isNull()
        assertThat(bubble.expandedView).isNull()
        assertThat(bubble.bubbleBarExpandedView).isNotNull()
    }

    private fun addBubble(): Bubble {
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val shortcutInfo = ShortcutInfo.Builder(context, "key").setIcon(icon).build()
        val bubble = FakeBubbleFactory.createChatBubble(context, shortcutInfo = shortcutInfo)
        bubble.setInflateSynchronously(true)
        bubbleData.notificationEntryUpdated(
            bubble,
            /* suppressFlyout= */ true,
            /* showInShade= */ true,
        )
        return bubble
    }

    private fun inflateBubble(bubble: Bubble) {
        val semaphore = Semaphore(0)
        val callback: BubbleViewInfoTask.Callback =
            BubbleViewInfoTask.Callback { semaphore.release() }
        bubble.inflate(
            callback,
            context,
            expandedViewManager,
            bubbleTaskViewFactory,
            bubblePositioner,
            bubbleController.stackView,
            bubbleController.layerView,
            iconFactory,
            false
        )

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubble.isInflated).isTrue()
    }

    private fun createBubbleController(
        shellInit: ShellInit,
        bubbleData: BubbleData,
        bubbleLogger: BubbleLogger,
        bubblePositioner: BubblePositioner,
        mainExecutor: TestShellExecutor,
        bgExecutor: TestShellExecutor,
    ): BubbleController {
        val shellCommandHandler = ShellCommandHandler()
        val shellController =
            ShellController(
                context,
                shellInit,
                shellCommandHandler,
                mock<DisplayInsetsController>(),
                mock<UserManager>(),
                mainExecutor,
            )
        val surfaceSynchronizer = { obj: Runnable -> obj.run() }

        val bubbleDataRepository =
            BubbleDataRepository(
                mock<LauncherApps>(),
                mainExecutor,
                bgExecutor,
                BubblePersistentRepository(context),
            )

        val shellTaskOrganizer = mock<ShellTaskOrganizer>()
        whenever(shellTaskOrganizer.executor).thenReturn(directExecutor())

        return BubbleController(
            context,
            shellInit,
            shellCommandHandler,
            shellController,
            bubbleData,
            surfaceSynchronizer,
            FloatingContentCoordinator(),
            bubbleDataRepository,
            mock<BubbleTransitions>(),
            mock<IStatusBarService>(),
            mock<WindowManager>(),
            mock<DisplayInsetsController>(),
            mock<DisplayImeController>(),
            mock<UserManager>(),
            mock<LauncherApps>(),
            bubbleLogger,
            mock<TaskStackListenerImpl>(),
            shellTaskOrganizer,
            bubblePositioner,
            mock<DisplayController>(),
            /* oneHandedOptional= */ Optional.empty(),
            mock<DragAndDropController>(),
            mainExecutor,
            mock<Handler>(),
            bgExecutor,
            mock<TaskViewRepository>(),
            mock<TaskViewTransitions>(),
            mock<Transitions>(),
            SyncTransactionQueue(TransactionPool(), mainExecutor),
            mock<IWindowManager>(),
            BubbleResizabilityChecker(),
            HomeIntentProvider(context),
        )
    }

    private class FakeBubblesStateListener : Bubbles.BubbleStateListener {
        override fun onBubbleStateChange(update: BubbleBarUpdate?) {}

        override fun animateBubbleBarLocation(location: BubbleBarLocation?) {}

        override fun onDragItemOverBubbleBarDragZone(location: BubbleBarLocation) {}

        override fun onItemDraggedOutsideBubbleBarDropZone() {}
    }
}
