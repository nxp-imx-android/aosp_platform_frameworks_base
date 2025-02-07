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

package com.android.wm.shell.shared.bubbles

import android.graphics.Insets
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.shared.bubbles.DragZoneFactory.DesktopWindowModeChecker
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

private typealias DragZoneVerifier = (dragZone: DragZone) -> Unit

@SmallTest
@RunWith(AndroidJUnit4::class)
/** Unit tests for [DragZoneFactory]. */
class DragZoneFactoryTest {

    private lateinit var dragZoneFactory: DragZoneFactory
    private val tabletPortrait =
        DeviceConfig(
            windowBounds = Rect(0, 0, 1000, 2000),
            isLargeScreen = true,
            isSmallTablet = false,
            isLandscape = false,
            isRtl = false,
            insets = Insets.of(0, 0, 0, 0)
        )
    private val tabletLandscape =
        tabletPortrait.copy(windowBounds = Rect(0, 0, 2000, 1000), isLandscape = true)
    private val foldablePortrait =
        tabletPortrait.copy(windowBounds = Rect(0, 0, 800, 900), isSmallTablet = true)
    private val foldableLandscape =
        foldablePortrait.copy(windowBounds = Rect(0, 0, 900, 800), isLandscape = true)
    private val splitScreenModeChecker = SplitScreenModeChecker { SplitScreenMode.NONE }
    private var isDesktopWindowModeSupported = true
    private val desktopWindowModeChecker = DesktopWindowModeChecker { isDesktopWindowModeSupported }

    @Test
    fun dragZonesForBubbleBar_tablet() {
        dragZoneFactory =
            DragZoneFactory(tabletPortrait, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.BubbleBar(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_tablet_portrait() {
        dragZoneFactory =
            DragZoneFactory(tabletPortrait, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_tablet_landscape() {
        dragZoneFactory = DragZoneFactory(tabletLandscape, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_foldable_portrait() {
        dragZoneFactory = DragZoneFactory(foldablePortrait, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_foldable_landscape() {
        dragZoneFactory = DragZoneFactory(foldableLandscape, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForExpandedView_tablet_portrait() {
        dragZoneFactory =
            DragZoneFactory(tabletPortrait, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(
                DraggedObject.ExpandedView(BubbleBarLocation.LEFT)
            )
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForExpandedView_tablet_landscape() {
        dragZoneFactory = DragZoneFactory(tabletLandscape, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.ExpandedView(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.DesktopWindow>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForExpandedView_foldable_portrait() {
        dragZoneFactory = DragZoneFactory(foldablePortrait, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.ExpandedView(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Left>(),
                verifyInstance<DragZone.Split.Right>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForExpandedView_foldable_landscape() {
        dragZoneFactory = DragZoneFactory(foldableLandscape, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.ExpandedView(BubbleBarLocation.LEFT))
        val expectedZones: List<DragZoneVerifier> =
            listOf(
                verifyInstance<DragZone.Dismiss>(),
                verifyInstance<DragZone.FullScreen>(),
                verifyInstance<DragZone.Split.Top>(),
                verifyInstance<DragZone.Split.Bottom>(),
                verifyInstance<DragZone.Bubble.Left>(),
                verifyInstance<DragZone.Bubble.Right>(),
            )
        assertThat(dragZones).hasSize(expectedZones.size)
        dragZones.zip(expectedZones).forEach { (zone, instanceVerifier) -> instanceVerifier(zone) }
    }

    @Test
    fun dragZonesForBubble_tablet_desktopModeDisabled() {
        isDesktopWindowModeSupported = false
        dragZoneFactory = DragZoneFactory(foldableLandscape, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.Bubble(BubbleBarLocation.LEFT))
        assertThat(dragZones.filterIsInstance<DragZone.DesktopWindow>()).isEmpty()
    }

    @Test
    fun dragZonesForExpandedView_tablet_desktopModeDisabled() {
        isDesktopWindowModeSupported = false
        dragZoneFactory = DragZoneFactory(foldableLandscape, splitScreenModeChecker, desktopWindowModeChecker)
        val dragZones =
            dragZoneFactory.createSortedDragZones(DraggedObject.ExpandedView(BubbleBarLocation.LEFT))
        assertThat(dragZones.filterIsInstance<DragZone.DesktopWindow>()).isEmpty()
    }

    private inline fun <reified T> verifyInstance(): DragZoneVerifier = { dragZone ->
        assertThat(dragZone).isInstanceOf(T::class.java)
    }
}
