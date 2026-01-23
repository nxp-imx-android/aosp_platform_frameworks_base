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
package com.android.systemui.locationbutton.domain.interactor

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.ContrastColorUtil
import com.android.systemui.SysuiTestCase
import com.android.systemui.locationbutton.data.repository.locationButtonRepository
import com.android.systemui.locationbutton.shared.model.ButtonModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LocationButtonInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = kosmos.locationButtonInteractor

    @Test
    fun setButtonState_validatesDimensionsAndSetsState() {
        val sessionId = 1
        val buttonModel = createTestButtonModel(width = 10, height = 10, strokeWidth = 100)

        underTest.setButtonState(sessionId, buttonModel)

        val state = underTest.getButtonState(sessionId)
        assertThat(state).isNotNull()
        assertThat(state!!.width).isEqualTo(MIN_PIXEL_SIZE)
        assertThat(state.height).isEqualTo(MIN_PIXEL_SIZE)
        assertThat(state.strokeWidth).isEqualTo((MAX_STROKE_WIDTH_DP * 1.5).toInt())
    }

    @Test
    fun removeButtonState_removesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        underTest.removeButtonState(sessionId)

        assertThat(underTest.getButtonState(sessionId)).isNull()
    }

    @Test
    fun setCornerRadius_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val newRadius = 25f
        underTest.setCornerRadius(sessionId, newRadius)

        assertThat(underTest.getButtonState(sessionId)!!.cornerRadius).isEqualTo(newRadius)
    }

    @Test
    fun setBackgroundColor_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val newColor = Color.Red.toArgb()
        underTest.setBackgroundColor(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.backgroundColor.toArgb())
            .isEqualTo(newColor)
    }

    @Test
    fun setSize_validatesAndUpdatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        underTest.setSize(sessionId, 10, 10)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(MIN_PIXEL_SIZE)
        assertThat(state.height).isEqualTo(MIN_PIXEL_SIZE)
    }

    @Test
    fun setConfiguration_revalidatesDimensionsAndUpdatesState() {
        val sessionId = 1
        val initialModel = createTestButtonModel(width = 80, height = 80) // Valid for 1.5x
        underTest.setButtonState(sessionId, initialModel)

        // New config with higher density, making 80px too small
        val newConfig = Configuration().apply { densityDpi = 480 } // 3.0x density
        val newMinPixelSize = 48 * 3 // 144
        underTest.setConfiguration(sessionId, newConfig)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.configuration).isEqualTo(newConfig)
        assertThat(state.width).isEqualTo(newMinPixelSize)
        assertThat(state.height).isEqualTo(newMinPixelSize)
    }

    @Test
    fun clearRepositoryState_clearsState() {
        underTest.setButtonState(1, createTestButtonModel())
        underTest.setButtonState(2, createTestButtonModel())
        underTest.clearRepositoryState()

        assertThat(underTest.getButtonState(1)).isNull()
        assertThat(underTest.getButtonState(2)).isNull()
    }

    @Test
    fun setButtonState_preservesHighContrastColorsAndUpdatesState() {
        val sessionId = 1
        val buttonModel =
            createTestButtonModel(
                backgroundColor = Color.Black,
                textColor = Color.White,
                iconTint = Color.White,
                strokeColor = Color.White,
            )

        underTest.setButtonState(sessionId, buttonModel)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        assertThat(state.backgroundColor).isEqualTo(Color.Black)
        assertThat(state.textColor).isEqualTo(Color.White)
        assertThat(state.iconTint).isEqualTo(Color.White)
        assertThat(state.strokeColor).isEqualTo(Color.White)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setButtonState_adjustsLowContrastIconTintAndTextColorsAndUpdatesState() {
        val sessionId = 1
        val lowContrastButtonModel =
            createTestButtonModel(
                backgroundColor = Color.White,
                textColor = Color(COLOR_LIGHT_GRAY),
                iconTint = Color(COLOR_LIGHT_GRAY),
                strokeColor = Color(COLOR_LIGHT_GRAY),
            )
        // Pre-validate that the initial color contrast ratios are insufficient
        assertThat(
                ContrastColorUtil.calculateContrast(
                    lowContrastButtonModel.iconTint.toArgb(),
                    lowContrastButtonModel.backgroundColor.toArgb(),
                )
            )
            .isLessThan(MIN_CONTRAST_RATIO)
        assertThat(
                ContrastColorUtil.calculateContrast(
                    lowContrastButtonModel.textColor.toArgb(),
                    lowContrastButtonModel.backgroundColor.toArgb(),
                )
            )
            .isLessThan(MIN_CONTRAST_RATIO)

        underTest.setButtonState(sessionId, lowContrastButtonModel)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(
                lowContrastButtonModel.iconTint.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(
                lowContrastButtonModel.textColor.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        assertThat(state.strokeColor.toArgb()).isEqualTo(COLOR_LIGHT_GRAY)
    }

    @Test
    fun setConfiguration_adjustsLowContrastIconTintAndTextColorsAndUpdatesState() {
        val sessionId = 1
        val lowContrastButtonModel =
            createTestButtonModel(
                backgroundColor = Color.White,
                textColor = Color.White,
                iconTint = Color.White,
            )
        // Inject an invalid contrast ButtonModel into the repository to force a refresh with an
        // invalid model.
        kosmos.locationButtonRepository.setButtonState(sessionId, lowContrastButtonModel)

        underTest.setConfiguration(sessionId, Configuration())

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        assertThat(backgroundArgb).isEqualTo(Color.White.toArgb())
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(
                lowContrastButtonModel.iconTint.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(
                lowContrastButtonModel.textColor.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setBackgroundColor_adjustsLowContrastIconTintAndTextColorsAndUpdatesState() {
        val sessionId = 1
        val buttonModel =
            createTestButtonModel(
                backgroundColor = Color.Black,
                textColor = Color.White,
                iconTint = Color.White,
            )
        underTest.setButtonState(sessionId, buttonModel)
        val newLowContrastBackgroundColorArgb = Color.White.toArgb()

        underTest.setBackgroundColor(sessionId, newLowContrastBackgroundColorArgb)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        assertThat(backgroundArgb).isEqualTo(newLowContrastBackgroundColorArgb)
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(
                buttonModel.iconTint.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(
                buttonModel.textColor.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setIconTint_adjustsLowContrastIconTintColorAndUpdatesState() {
        val sessionId = 1
        val buttonModel =
            createTestButtonModel(backgroundColor = Color.White, iconTint = Color.Black)
        underTest.setButtonState(sessionId, buttonModel)
        val newLowContrastIconTintArgb = Color.White.toArgb()

        underTest.setIconTint(sessionId, newLowContrastIconTintArgb)

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        val expectedIconTint =
            ContrastColorUtil.ensureContrast(
                newLowContrastIconTintArgb,
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.iconTint.toArgb()).isEqualTo(expectedIconTint)
        assertThat(ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setTextColor_adjustsLowContrastTextColorAndUpdatesState() {
        val sessionId = 1
        val buttonModel =
            createTestButtonModel(backgroundColor = Color.White, textColor = Color.Black)
        underTest.setButtonState(sessionId, buttonModel)
        val newLowContrastTextColor = Color.White

        underTest.setTextColor(sessionId, newLowContrastTextColor.toArgb())

        val state = underTest.getButtonState(sessionId)!!
        val backgroundArgb = state.backgroundColor.toArgb()
        val expectedTextColor =
            ContrastColorUtil.ensureContrast(
                newLowContrastTextColor.toArgb(),
                backgroundArgb,
                MIN_CONTRAST_RATIO,
            )
        assertThat(state.textColor.toArgb()).isEqualTo(expectedTextColor)
        assertThat(ContrastColorUtil.calculateContrast(state.textColor.toArgb(), backgroundArgb))
            .isAtLeast(MIN_CONTRAST_RATIO)
    }

    @Test
    fun setStrokeColor_setStrokeColorAndUpdatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel(backgroundColor = Color.White))
        val newStrokeColor = COLOR_LIGHT_GRAY

        underTest.setStrokeColor(sessionId, newStrokeColor)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeColor.toArgb()).isEqualTo(newStrokeColor)
    }

    private fun createTestButtonModel(
        width: Int = 100,
        height: Int = 50,
        strokeWidth: Int = 2,
        backgroundColor: Color = Color.Black,
        textColor: Color = Color.White,
        iconTint: Color = Color.White,
        strokeColor: Color = Color.White,
    ): ButtonModel {
        return ButtonModel(
            width = width,
            height = height,
            backgroundColor = backgroundColor,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            cornerRadius = 10f,
            iconTint = iconTint,
            textResId = android.R.string.ok,
            textColor = textColor,
            configuration = Configuration().apply { densityDpi = DENSITY_DP },
        )
    }

    private companion object {
        const val MAX_STROKE_WIDTH_DP = 4
        const val MIN_TOUCH_TARGET_DP = 48
        const val DENSITY_DP = 240
        private const val MIN_PIXEL_SIZE = MIN_TOUCH_TARGET_DP * DENSITY_DP / 160
        private const val MIN_CONTRAST_RATIO = 4.5
        private val COLOR_LIGHT_GRAY = Color(0xFFBBBBBB).toArgb()
    }
}
