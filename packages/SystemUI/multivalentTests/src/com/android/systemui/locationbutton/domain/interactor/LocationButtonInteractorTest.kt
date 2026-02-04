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
        // Min 48dp = 48px at 1.0x density
        val sessionId = 1
        val model = createTestButtonModel(widthPx = 10)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(48) // Clamped to 48
    }

    @Test
    fun setButtonState_widthValid_remainsUnchanged() {
        val sessionId = 1
        val model = createTestButtonModel(widthPx = 100)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(100)
    }

    @Test
    fun setButtonState_widthTooSmall_highDensity_clampedToScaledMin() {
        // Min 48dp = 96px at 2.0x density
        val sessionId = 1
        // Input 80px is < 96px
        val model = createTestButtonModel(widthPx = 80, density = 2.0f)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(96)
    }

    @Test
    fun setButtonState_heightTooSmall_clampedToMin() {
        val sessionId = 1
        val model = createTestButtonModel(heightPx = 10)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.height).isEqualTo(48)
    }

    @Test
    fun setButtonState_heightTooLarge_clampedToMax() {
        // Max 136dp = 408px at 3.0x density
        val sessionId = 1
        val model = createTestButtonModel(heightPx = 500, density = 3.0f)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.height).isEqualTo(408)
    }

    @Test
    fun setBackgroundColor_forcesOpaque() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val transparentRed = Color.Red.copy(alpha = 0.5f).toArgb()

        underTest.setBackgroundColor(sessionId, transparentRed)

        val state = underTest.getButtonState(sessionId)!!
        // Alpha should be forced to 255 (opaque)
        assertThat(state.backgroundColor.toArgb()).isEqualTo(Color.Red.toArgb())
    }

    @Test
    fun setButtonState_heightValid_remainsUnchanged() {
        val sessionId = 1
        val model = createTestButtonModel(heightPx = 100)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.height).isEqualTo(100)
    }

    @Test
    fun setButtonState_heightValidation_highDensity_scalesCorrectly() {
        // Density 2.0x: Min 48dp = 96px, Max 136dp = 272px
        val sessionId = 1

        // Test Min Clamp
        underTest.setButtonState(sessionId, createTestButtonModel(heightPx = 50, density = 2.0f))
        assertThat(underTest.getButtonState(sessionId)!!.height).isEqualTo(96)

        // Test Max Clamp
        underTest.setButtonState(sessionId, createTestButtonModel(heightPx = 300, density = 2.0f))
        assertThat(underTest.getButtonState(sessionId)!!.height).isEqualTo(272)
    }

    @Test
    fun setButtonState_paddingTooLarge_highDensity_clampedToScaledMax() {
        // Max 8dp = 24px at 3.0x density
        val sessionId = 1
        val model =
            createTestButtonModel(
                paddingLeftPx = 100,
                paddingTopPx = 100,
                paddingRightPx = 100,
                paddingBottomPx = 100,
                density = 3.0f,
            )

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.paddingLeft).isEqualTo(24)
        assertThat(state.paddingTop).isEqualTo(24)
        assertThat(state.paddingRight).isEqualTo(24)
        assertThat(state.paddingBottom).isEqualTo(24)
    }

    @Test
    fun setButtonState_paddingValid_remainsUnchanged() {
        val sessionId = 1
        val model = createTestButtonModel(paddingLeftPx = 5)

        underTest.setButtonState(sessionId, model)

        assertThat(underTest.getButtonState(sessionId)!!.paddingLeft).isEqualTo(5)
    }

    @Test
    fun setButtonState_paddingHighDensity_clampedToScaledMax() {
        // Max 8dp = 12px at 1.5x density
        val sessionId = 1
        val model = createTestButtonModel(paddingLeftPx = 20, density = 1.5f)

        underTest.setButtonState(sessionId, model)

        assertThat(underTest.getButtonState(sessionId)!!.paddingLeft).isEqualTo(12)
    }

    @Test
    fun setButtonState_strokeWidthTooLarge_clampedToMax() {
        // Max 3dp = 3px at 1.0x density
        val sessionId = 1
        val model = createTestButtonModel(strokeWidthPx = 10)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeWidth).isEqualTo(MAX_STROKE_WIDTH_DP)
    }

    @Test
    fun setButtonState_strokeWidthValid_remainsUnchanged() {
        val sessionId = 1
        val model = createTestButtonModel(strokeWidthPx = 2)

        underTest.setButtonState(sessionId, model)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeWidth).isEqualTo(2)
    }

    @Test
    fun setStrokeWidth_updatesAndValidates() {
        // Max 3dp = 6px at 2.0x density
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel(density = 2.0f))

        // Update to 10px (too large, should clamp to 6)
        underTest.setStrokeWidth(sessionId, 10)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.strokeWidth).isEqualTo(6)
    }

    @Test
    fun setConfiguration_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())

        val newConfig = Configuration().apply { densityDpi = 320 }
        underTest.setConfiguration(sessionId, newConfig, newConfig.densityDpi / 160f)

        assertThat(underTest.getButtonState(sessionId)!!.configuration).isEqualTo(newConfig)
    }

    @Test
    fun setSize_validatesAndUpdatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())

        // Set size too small (10x10) -> Should clamp to 48x48
        underTest.setSize(sessionId, 10, 10)

        val state = underTest.getButtonState(sessionId)!!
        assertThat(state.width).isEqualTo(48)
        assertThat(state.height).isEqualTo(48)
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
    fun setTextColor_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val newColor = Color.Cyan.toArgb()
        underTest.setTextColor(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.textColor.toArgb()).isEqualTo(newColor)
    }

    @Test
    fun setIconTint_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val newColor = Color.Green.toArgb()
        underTest.setIconTint(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.iconTint.toArgb()).isEqualTo(newColor)
    }

    @Test
    fun setTextId_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val newTextId = 12345
        underTest.setTextId(sessionId, newTextId)

        assertThat(underTest.getButtonState(sessionId)!!.textResId).isEqualTo(newTextId)
    }

    @Test
    fun setStrokeColor_updatesState() {
        val sessionId = 1
        underTest.setButtonState(sessionId, createTestButtonModel())
        val newColor = Color.Yellow.toArgb()
        underTest.setStrokeColor(sessionId, newColor)

        assertThat(underTest.getButtonState(sessionId)!!.strokeColor.toArgb()).isEqualTo(newColor)
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
    fun setConfiguration_adjustsContrast() {
        val sessionId = 1
        val lowContrastButtonModel =
            createTestButtonModel(
                backgroundColor = Color.White,
                textColor = Color.White,
                iconTint = Color.White,
            )
        // Inject an invalid contrast ButtonModel into the repository.
        kosmos.locationButtonRepository.setButtonState(sessionId, lowContrastButtonModel)

        val config = Configuration()
        underTest.setConfiguration(sessionId, config, config.densityDpi / 160f)

        val state = underTest.getButtonState(sessionId)!!
        // In the current implementation, colors ARE adjusted during setConfiguration.
        assertThat(state.backgroundColor).isEqualTo(Color.White)
        assertThat(state.textColor.toArgb()).isNotEqualTo(Color.White.toArgb())
        assertThat(state.iconTint.toArgb()).isNotEqualTo(Color.White.toArgb())
        assertThat(
                ContrastColorUtil.calculateContrast(state.textColor.toArgb(), Color.White.toArgb())
            )
            .isAtLeast(MIN_CONTRAST_RATIO)
        assertThat(
                ContrastColorUtil.calculateContrast(state.iconTint.toArgb(), Color.White.toArgb())
            )
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
        widthPx: Int = 100,
        heightPx: Int = 50,
        strokeWidthPx: Int = 0,
        paddingLeftPx: Int = 0,
        paddingTopPx: Int = 0,
        paddingRightPx: Int = 0,
        paddingBottomPx: Int = 0,
        backgroundColor: Color = Color.Black,
        textColor: Color = Color.White,
        iconTint: Color = Color.White,
        strokeColor: Color = Color.White,
        config: Configuration? = null,
        density: Float? = null,
    ): ButtonModel {
        val actualConfig =
            config
                ?: Configuration().apply {
                    densityDpi = if (density != null) (density * 160).toInt() else DENSITY_DPI
                }
        val actualDensity = density ?: (actualConfig.densityDpi / 160f)
        return ButtonModel(
            width = widthPx,
            height = heightPx,
            paddingLeft = paddingLeftPx,
            paddingTop = paddingTopPx,
            paddingRight = paddingRightPx,
            paddingBottom = paddingBottomPx,
            backgroundColor = backgroundColor,
            strokeColor = strokeColor,
            strokeWidth = strokeWidthPx,
            cornerRadius = 10f,
            pressedCornerRadius = 8f,
            iconTint = iconTint,
            textResId = null,
            textColor = textColor,
            configuration = actualConfig,
            density = actualDensity,
        )
    }

    private companion object {
        const val MAX_STROKE_WIDTH_DP = 3
        const val MIN_TOUCH_TARGET_DP = 48
        const val DENSITY_DP = 240
        private const val MIN_CONTRAST_RATIO = 4.5
        private val COLOR_LIGHT_GRAY = Color(0xFFBBBBBB).toArgb()
        const val DENSITY_DPI = 160
    }
}
