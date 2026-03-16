/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.server.wm

import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.Presubmit
import android.view.Display
import android.view.WindowManager
import android.view.WindowManager.RemoveContentMode
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Tests for applying display updates coming from DM to WM
 *
 * Build/Install/Run:
 * atest WmTests:DisplayUpdateTests
 */
@SmallTest
@Presubmit
@EnableFlags(com.android.window.flags.Flags.FLAG_SYNCED_DISPLAY_MODE_UPDATES)
@RunWith(WindowTestRunner::class)
class DisplayUpdateTests : WindowTestsBase() {

    private lateinit var displayManager: TestDisplayManager
    private lateinit var testPlayer: TestTransitionPlayer

    private lateinit var secondaryDisplay: DisplayContent
    private var secondaryDisplayId: Int = Display.INVALID_DISPLAY

    private lateinit var anotherSecondaryDisplay: DisplayContent
    private var anotherSecondaryDisplayId: Int = Display.INVALID_DISPLAY

    @Before
    fun before() {
        displayManager = mSystemServicesTestRule.testDisplayManager
        testPlayer = registerTestTransitionPlayer()
        displayManager.setReturnDisplaysFromWm(false)

        spyOn(mWm.mDisplayWindowSettings)

        // Mock that displays have content
        spyOn(mRootWindowContainer)
        doReturn(true).whenever(mRootWindowContainer)
            .handleNotObscuredLocked(any(), anyBoolean(), anyBoolean())

        // Create secondary displays in addition to the default one for testing
        displayManager.updateDisplays {
            secondaryDisplayId = add().displayInfo.displayId
            anotherSecondaryDisplayId = add().displayInfo.displayId
        }

        secondaryDisplay = mRootWindowContainer.getDisplayContent(secondaryDisplayId)
        addActivityToDisplay(secondaryDisplay)

        anotherSecondaryDisplay = mRootWindowContainer.getDisplayContent(anotherSecondaryDisplayId)
        addActivityToDisplay(anotherSecondaryDisplay)

        testPlayer.flush()
        mSystemServicesTestRule.waitUntilWindowManagerHandlersIdle()
    }

    @Test
    fun testDisplayAdded_addsDisplayContent() {
        val newDisplayId = addDisplayInDisplayManager()

        assertThat(mRootWindowContainer.displays)
            .comparingElementsUsing(idCorrespondence)
            .contains(newDisplayId)
    }

    @Test
    fun testDisplayAdded_requestsTransitionToAddDisplay() {
        val newDisplayId = addDisplayInDisplayManager()

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .contains(newDisplayId)

        val displayChange = testPlayer.mLastTransit.mChanges
            .get(mRootWindowContainer.getDisplayContent(newDisplayId))
        assertThat(displayChange).isNotNull()
        assertThat(displayChange!!.mExistenceChanged).isTrue()
    }

    @Test
    fun testDisplaySizeChanged_updatesDisplayContent() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        assertThat(secondaryDisplay.displayInfo.logicalWidth).isEqualTo(123)
    }

    @Test
    fun testDisplaySizeChanged_requestsTransitionToChangeDisplay() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .contains(secondaryDisplayId)
    }

    @Test
    fun testOnlyDisplayRenderRateChanged_doesNotRequestTransition() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.renderFrameRate = 123.0f
            }
        }

        val display = mRootWindowContainer.getDisplayContent(secondaryDisplayId)
        assertThat(display.displayInfo.renderFrameRate).isEqualTo(123.0f)
        assertThat(testPlayer.mLastTransit).isNull()
    }

    @Test
    fun testDisplayRemoved_removeContentDestroyMode_removesDisplayContent() {
        givenDisplayRemoveMode(secondaryDisplay, WindowManager.REMOVE_CONTENT_MODE_DESTROY)
        displayManager.updateDisplays {
            remove(secondaryDisplayId)
        }

        assertThat(secondaryDisplay.isRemoving).isTrue()
    }

    @Test
    fun testDisplayRemoved_removeContentMoveToPrimaryMode_keepsDisplayContent() {
        givenDisplayRemoveMode(secondaryDisplay, WindowManager.REMOVE_CONTENT_MODE_MOVE_TO_PRIMARY)
        displayManager.updateDisplays {
            remove(secondaryDisplayId)
        }

        // DisplayContent is kept by DisplayUpdater, the actual removal should be handled later
        // when applying the transition's WCT
        assertThat(secondaryDisplay.isRemoving).isFalse()
    }

    @Test
    fun testDisplayRemoved_requestsTransitionToRemoveDisplay() {
        displayManager.updateDisplays {
            remove(secondaryDisplayId)
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .contains(secondaryDisplayId)

        val displayChange = testPlayer.mLastTransit.mChanges.get(secondaryDisplay)
        assertThat(displayChange).isNotNull()
        assertThat(displayChange!!.mExistenceChanged).isTrue()
    }

    @Test
    fun testDisplayAddedRemovedChangedTogether_requestsTransitionWithAllChanges() {
        var addedDisplayId = Display.INVALID_DISPLAY
        displayManager.updateDisplays {
            addedDisplayId = add().displayInfo.displayId
            remove(secondaryDisplayId)
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .containsAtLeast(addedDisplayId, secondaryDisplayId, anotherSecondaryDisplayId)
    }

    @Test
    fun testDisplayAddedRemovedChangedTogether_allDisplayContentsUpdated() {
        givenDisplayRemoveMode(secondaryDisplay, WindowManager.REMOVE_CONTENT_MODE_DESTROY)
        var addedDisplayId = Display.INVALID_DISPLAY
        displayManager.updateDisplays {
            addedDisplayId = add().displayInfo.displayId
            remove(secondaryDisplayId)
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 123
            }
        }

        val addedDisplay = mRootWindowContainer.getDisplayContent(addedDisplayId)
        assertThat(addedDisplay).isNotNull()
        assertThat(anotherSecondaryDisplay.displayInfo.logicalWidth).isEqualTo(123)
        assertThat(secondaryDisplay.isRemoving).isTrue()
    }

    @Test
    fun testTwoDisplaysChangedTogether_requestsTransitionWithBothChanges() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 456
            }
        }

        assertThat(testPlayer.mLastTransit).isNotNull()
        assertThat(testPlayer.mLastTransit.mParticipants.filterIsInstance<DisplayContent>())
            .comparingElementsUsing(idCorrespondence)
            .containsAtLeast(secondaryDisplayId, anotherSecondaryDisplayId)
    }

    @Test
    fun testTwoDisplaysChangedTogether_updatesBothDisplayContents() {
        displayManager.updateDisplays {
            change(secondaryDisplayId) {
                info.logicalWidth = 123
            }
            change(anotherSecondaryDisplayId) {
                info.logicalWidth = 456
            }
        }

        assertThat(secondaryDisplay.displayInfo.logicalWidth).isEqualTo(123)
        assertThat(anotherSecondaryDisplay.displayInfo.logicalWidth).isEqualTo(456)
    }

    private fun addDisplayInDisplayManager(): Int {
        var newDisplayId: Int = Display.INVALID_DISPLAY
        displayManager.updateDisplays {
            newDisplayId = add().displayInfo.displayId
        }
        return newDisplayId
    }

    private val RootWindowContainer.displays: List<DisplayContent>
        get() {
            val result = ArrayList<DisplayContent>()
            forAllDisplays { display ->
                result.add(display)
            }
            return result
        }

    private fun givenDisplayRemoveMode(
        displayContent: DisplayContent,
        @RemoveContentMode mode: Int
    ) {
        doReturn(mode).whenever(mWm.mDisplayWindowSettings)
            .getRemoveContentModeLocked(eq(displayContent))
    }

    private fun addActivityToDisplay(display: DisplayContent) {
        val task = createTask(display)
        val act = createActivityRecord(task)
        val win = createWindowState(
            WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_BASE_APPLICATION),
            act
        )
        act.addWindow(win)
        act.setVisibleRequested(true)
    }

    private companion object {
        val idCorrespondence: Correspondence<DisplayContent, Int> =
            Correspondence.transforming<DisplayContent, Int>(
                { it.displayInfo.displayId },
                "has ID of"
            )
    }
}
