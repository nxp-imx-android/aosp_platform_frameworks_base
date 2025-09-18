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

package com.android.systemui.shade.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.createFakeDisplaySubcomponent
import com.android.systemui.display.data.repository.displaySubcomponentPerDisplayRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.data.repository.homeStatusBarComponentsRepository
import com.android.systemui.statusbar.disableflags.data.repository.FakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.domain.interactor.createDisableFlagsInteractor
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.statusbar.phone.PhoneStatusBarViewController
import com.android.systemui.statusbar.phone.fragment.dagger.HomeStatusBarComponent
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShadeStatusBarComponentsInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val defaultDisableFlags = DisableFlagsModel(1)
    private val secondaryDisableFlags = DisableFlagsModel(2)
    private val singleDisplayDisableFlags = DisableFlagsModel(3)
    private val defaultOngoingActivityChipsViewModel = mock<OngoingActivityChipsViewModel>()
    private val secondaryOngoingActivityChipsViewModel = mock<OngoingActivityChipsViewModel>()

    private val Kosmos.underTest by Kosmos.Fixture { shadeStatusBarComponentsInteractor }

    @Before
    fun setUp() {
        val defaultDisableFlagsRepository =
            FakeDisableFlagsRepository().also { it.disableFlags.value = defaultDisableFlags }
        val secondaryDisableFlagsRepository =
            FakeDisableFlagsRepository().also { it.disableFlags.value = secondaryDisableFlags }

        kosmos.displaySubcomponentPerDisplayRepository.apply {
            add(
                DEFAULT_DISPLAY,
                kosmos.createFakeDisplaySubcomponent(
                    disableFlagsInteractor = {
                        kosmos.createDisableFlagsInteractor(defaultDisableFlagsRepository)
                    },
                    ongoingActivityChipsViewModel = { defaultOngoingActivityChipsViewModel },
                ),
            )
            add(
                SECONDARY_DISPLAY,
                kosmos.createFakeDisplaySubcomponent(
                    disableFlagsInteractor = {
                        kosmos.createDisableFlagsInteractor(secondaryDisableFlagsRepository)
                    },
                    ongoingActivityChipsViewModel = { secondaryOngoingActivityChipsViewModel },
                ),
            )
        }
    }

    @Test
    fun phoneStatusBarViewController_initiallyNullWhenNoComponents() =
        kosmos.runTest {
            val controller by collectLastValue(underTest.phoneStatusBarViewController)
            assertThat(controller).isNull()
        }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun phoneStatusBarViewController_returnsCorrectControllerWhenComponentAdded() =
        kosmos.runTest {
            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            val mockController = mock<PhoneStatusBarViewController>()
            val mockComponent = createMockHomeStatusBarComponent(DEFAULT_DISPLAY, mockController)

            val controller by collectLastValue(underTest.phoneStatusBarViewController)
            assertThat(controller).isNull() // Ensure it's null before adding

            homeStatusBarComponentsRepository.onStatusBarViewInitialized(mockComponent)
            assertThat(controller).isEqualTo(mockController)
        }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun phoneStatusBarViewController_updatesAfterDisplayChangeToComponentPresent() =
        kosmos.runTest {
            val defaultController = mock<PhoneStatusBarViewController>()
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(DEFAULT_DISPLAY, defaultController)
            )
            val secondaryController = mock<PhoneStatusBarViewController>()
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(SECONDARY_DISPLAY, secondaryController)
            )
            val controller by collectLastValue(underTest.phoneStatusBarViewController)

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)
            assertThat(controller).isEqualTo(secondaryController)

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            assertThat(controller).isEqualTo(defaultController)
        }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun phoneStatusBarViewController_becomesNullAfterDisplayChangeToComponentAbsent() =
        kosmos.runTest {
            val defaultController = mock<PhoneStatusBarViewController>()
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(DEFAULT_DISPLAY, defaultController)
            )
            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            val controller by collectLastValue(underTest.phoneStatusBarViewController)
            assertThat(controller).isEqualTo(defaultController)

            // Change to secondary display which has no component
            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)
            assertThat(controller).isNull()
        }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun phoneStatusBarViewController_returnsDefaultControllerWhenFlagDisabled() =
        kosmos.runTest {
            // GIVEN components for default and secondary displays exist
            val defaultController = mock<PhoneStatusBarViewController>()
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(DEFAULT_DISPLAY, defaultController)
            )
            val secondaryController = mock<PhoneStatusBarViewController>()
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(SECONDARY_DISPLAY, secondaryController)
            )
            val controller by collectLastValue(underTest.phoneStatusBarViewController)

            // THEN it should be the controller for the DEFAULT display
            assertThat(controller).isEqualTo(defaultController)
        }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun phoneStatusBarViewController_ignoresDisplayChangesWhenFlagDisabled() =
        kosmos.runTest {
            val defaultController = mock<PhoneStatusBarViewController>()
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(DEFAULT_DISPLAY, defaultController)
            )
            val controller by collectLastValue(underTest.phoneStatusBarViewController)
            assertThat(controller).isEqualTo(defaultController)

            // WHEN the shade repository reports a display change
            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)

            // THEN the controller remains the default one, because the interactor is locked to it
            assertThat(controller).isEqualTo(defaultController)
        }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun phoneStatusBarViewController_becomesNullWhenDefaultComponentIsRemovedWhenFlagDisabled() =
        kosmos.runTest {
            val defaultComponent =
                createMockHomeStatusBarComponent(
                    DEFAULT_DISPLAY,
                    mock<PhoneStatusBarViewController>(),
                )
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(defaultComponent)
            // Add a secondary component to ensure it's ignored
            homeStatusBarComponentsRepository.onStatusBarViewInitialized(
                createMockHomeStatusBarComponent(SECONDARY_DISPLAY, mock())
            )
            val controller by collectLastValue(underTest.phoneStatusBarViewController)
            assertThat(controller).isNotNull()

            // WHEN the default component is removed
            homeStatusBarComponentsRepository.onStatusBarViewDestroyed(defaultComponent)

            // THEN the controller becomes null, even though the secondary component still exists
            assertThat(controller).isNull()
        }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun ongoingActivityChipsViewModel_updatesAfterDisplayChange() =
        kosmos.runTest {
            val viewModel by collectLastValue(underTest.ongoingActivityChipsViewModel)

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            assertThat(viewModel).isEqualTo(defaultOngoingActivityChipsViewModel)

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)
            assertThat(viewModel).isEqualTo(secondaryOngoingActivityChipsViewModel)
        }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME, Flags.FLAG_DISABLE_FLAGS_PER_DISPLAY)
    fun disableFlags_perDisplayDisableFlagsEnabled_updatesAfterDisplayChange() =
        kosmos.runTest {
            val disableFlags by collectLastValue(underTest.disableFlags)

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            assertThat(disableFlags).isEqualTo(defaultDisableFlags)

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)
            assertThat(disableFlags).isEqualTo(secondaryDisableFlags)
        }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    @DisableFlags(Flags.FLAG_DISABLE_FLAGS_PER_DISPLAY)
    fun disableFlags_perDisplayDisabledFlagsDisabled_alwaysUsesSingleDisplayDisableFlags() =
        kosmos.runTest {
            val disableFlags by collectLastValue(underTest.disableFlags)
            fakeDisableFlagsRepository.disableFlags.value = singleDisplayDisableFlags

            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            assertThat(disableFlags).isEqualTo(singleDisplayDisableFlags)

            fakeShadeDisplaysRepository.setDisplayId(SECONDARY_DISPLAY)
            assertThat(disableFlags).isEqualTo(singleDisplayDisableFlags)
        }

    /** Helper to create a mock HomeStatusBarComponent */
    private fun createMockHomeStatusBarComponent(
        displayId: Int,
        controller: PhoneStatusBarViewController? = null,
    ): HomeStatusBarComponent {
        val mockComponent = mock<HomeStatusBarComponent>()
        whenever(mockComponent.getDisplayId()).thenReturn(displayId)
        whenever(mockComponent.phoneStatusBarViewController).thenReturn(controller)
        return mockComponent
    }

    private companion object {
        const val DEFAULT_DISPLAY = Display.DEFAULT_DISPLAY
        const val SECONDARY_DISPLAY = DEFAULT_DISPLAY + 1
    }
}
