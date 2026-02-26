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

package com.android.systemui.dreams.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dreams.domain.interactor.dreamInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.testKosmos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DreamSwitcherDialogControllerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.dialog: SystemUIDialog by Kosmos.Fixture { mock() }
    private val Kosmos.delegate: DreamSwitcherDialogDelegate by
        Kosmos.Fixture { mock { on { createDialog() }.thenReturn(dialog) } }

    private val Kosmos.underTest: DreamSwitcherDialogController by
        Kosmos.Fixture { DreamSwitcherDialogController(testDispatcher, delegate, dreamInteractor) }

    private val Kosmos.lifecycleOwner: TestLifecycleOwner by
        Kosmos.Fixture {
            TestLifecycleOwner(
                initialState = Lifecycle.State.CREATED,
                coroutineDispatcher = testDispatcher,
            )
        }

    @Before
    fun setUp() {
        with(kosmos) {
            Dispatchers.setMain(testDispatcher)
            underTest.init(lifecycleOwner)
        }
        onTeardown { Dispatchers.resetMain() }
    }

    @Test
    fun showRequest_beforeLifecycleStart_areDropped() =
        kosmos.runTest {
            // Request to show the dialog before the lifecycle is started.
            dreamInteractor.showSwitcherDialog()

            // Verify the dialog is not created yet.
            verify(delegate, never()).createDialog()

            // Start the lifecycle.
            lifecycleOwner.currentState = Lifecycle.State.STARTED

            // Verify the dialog is still not shown.
            verify(delegate, never()).createDialog()
        }

    @Test
    fun showRequest_afterLifecycleStart_createsAndShowsDialog() =
        kosmos.runTest {
            // Start the lifecycle.
            lifecycleOwner.currentState = Lifecycle.State.STARTED

            // Request to show the dialog.
            dreamInteractor.showSwitcherDialog()

            // Verify the delegate creates the dialog and it is shown.
            verify(delegate).createDialog()
            verify(dialog).show()
        }

    @Test
    fun dismissRequest_dismissesDialog() =
        kosmos.runTest {
            // Given the dialog is showing.
            lifecycleOwner.currentState = Lifecycle.State.STARTED
            dreamInteractor.showSwitcherDialog()
            verify(dialog).show()

            // When a request to dismiss the dialog is made.
            dreamInteractor.dismissSwitcherDialog()

            // Then the dialog is dismissed.
            verify(dialog).dismiss()
        }

    @Test
    fun lifecycleStop_dismissesDialog() =
        kosmos.runTest {
            // Given the dialog is showing.
            lifecycleOwner.currentState = Lifecycle.State.STARTED
            dreamInteractor.showSwitcherDialog()
            verify(dialog).show()

            // When the lifecycle moves to a stopped state.
            lifecycleOwner.currentState = Lifecycle.State.CREATED

            // Then the dialog is dismissed.
            verify(dialog).dismiss()
        }

    @Test
    fun lifecycleDestroy_dismissesDialog() =
        kosmos.runTest {
            // Given the dialog is showing.
            lifecycleOwner.currentState = Lifecycle.State.STARTED
            dreamInteractor.showSwitcherDialog()
            verify(dialog).show()

            // When the lifecycle is destroyed.
            lifecycleOwner.currentState = Lifecycle.State.DESTROYED

            // Then the dialog is dismissed.
            verify(dialog).dismiss()
        }

    @Test
    fun showRequest_whenDialogShowing_doesNothing() =
        kosmos.runTest {
            // Given the dialog is showing.
            lifecycleOwner.currentState = Lifecycle.State.STARTED
            dreamInteractor.showSwitcherDialog()
            verify(delegate).createDialog()
            verify(dialog).show()

            // When another request to show the dialog is made.
            dreamInteractor.showSwitcherDialog()

            // Then the delegate is not asked to create another dialog.
            verify(delegate, times(1)).createDialog()
        }
}
