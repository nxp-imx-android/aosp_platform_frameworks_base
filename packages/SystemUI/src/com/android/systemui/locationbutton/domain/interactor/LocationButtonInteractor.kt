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
import android.util.Slog
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.internal.util.ContrastColorUtil
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.locationbutton.data.repository.LocationButtonRepository
import com.android.systemui.locationbutton.shared.model.ButtonModel
import javax.inject.Inject
import kotlin.math.roundToInt

@SysUISingleton
class LocationButtonInteractor
@Inject
constructor(private val repository: LocationButtonRepository) {
    fun getButtonState(sessionId: Int): ButtonModel? = repository.getButtonState(sessionId)

    fun setButtonState(sessionId: Int, requestModel: ButtonModel) {
        val validatedModel =
            requestModel.copy(
                width = validateWidth(requestModel.width, requestModel.configuration),
                height = validateHeight(requestModel.height, requestModel.configuration),
                strokeWidth =
                    validateStrokeWidth(requestModel.strokeWidth, requestModel.configuration),
                iconTint =
                    ensureColorContrast(
                        "Icon tint",
                        requestModel.iconTint,
                        requestModel.backgroundColor,
                    ),
                textColor =
                    ensureColorContrast(
                        "Text color",
                        requestModel.textColor,
                        requestModel.backgroundColor,
                    ),
            )
        repository.setButtonState(sessionId, validatedModel)
    }

    fun removeButtonState(sessionId: Int) {
        repository.removeButtonState(sessionId)
    }

    fun setCornerRadius(sessionId: Int, cornerRadius: Float) {
        repository.updateButtonState(sessionId) { it.copy(cornerRadius = cornerRadius) }
    }

    fun setBackgroundColor(sessionId: Int, color: Int) {
        // TODO: Validate and fix background color opacity
        repository.updateButtonState(sessionId) {
            it.copy(
                backgroundColor = Color(color),
                iconTint = ensureColorContrast("Icon tint", it.iconTint, Color(color)),
                textColor = ensureColorContrast("Text color", it.textColor, Color(color)),
            )
        }
    }

    fun setTextColor(sessionId: Int, textColor: Int) {
        repository.updateButtonState(sessionId) {
            it.copy(
                textColor = ensureColorContrast("Text color", Color(textColor), it.backgroundColor)
            )
        }
    }

    fun setIconTint(sessionId: Int, color: Int) {
        repository.updateButtonState(sessionId) {
            it.copy(iconTint = ensureColorContrast("Icon tint", Color(color), it.backgroundColor))
        }
    }

    fun setTextId(sessionId: Int, @StringRes textId: Int?) {
        repository.updateButtonState(sessionId) { it.copy(textResId = textId) }
    }

    fun setStrokeColor(sessionId: Int, color: Int) {
        repository.updateButtonState(sessionId) { it.copy(strokeColor = Color(color)) }
    }

    fun setStrokeWidth(sessionId: Int, width: Int) {
        val currentState = repository.getButtonState(sessionId)
        if (currentState == null) {
            Slog.e(TAG, "Cannot setStrokeWidth: No state found for session $sessionId")
            return
        }
        val validatedWidth = validateStrokeWidth(width, currentState.configuration)
        repository.updateButtonState(sessionId) { it.copy(strokeWidth = validatedWidth) }
    }

    fun setSize(sessionId: Int, width: Int, height: Int) {
        val currentState = repository.getButtonState(sessionId)
        if (currentState == null) {
            Slog.e(TAG, "Cannot setSize: No state found for session $sessionId")
            return
        }

        // Validate the incoming width and height
        val validatedWidth = validateWidth(width, currentState.configuration)
        val validatedHeight = validateHeight(height, currentState.configuration)

        repository.updateButtonState(sessionId) {
            it.copy(width = validatedWidth, height = validatedHeight)
        }
    }

    fun setConfiguration(sessionId: Int, newConfig: Configuration) {
        val currentState = repository.getButtonState(sessionId)
        if (currentState == null) {
            Slog.e(TAG, "Cannot setConfiguration: No state found for session $sessionId")
            return
        }

        // Re-validate existing values against the NEW configuration
        val validatedWidth = validateWidth(currentState.width, newConfig)
        val validatedHeight = validateHeight(currentState.height, newConfig)
        val validatedStrokeWidth = validateStrokeWidth(currentState.strokeWidth, newConfig)
        val validatedIconTint =
            ensureColorContrast("Icon tint", currentState.iconTint, currentState.backgroundColor)
        val validatedTextColor =
            ensureColorContrast("Text color", currentState.textColor, currentState.backgroundColor)
        repository.updateButtonState(sessionId) {
            it.copy(
                configuration = newConfig,
                width = validatedWidth,
                height = validatedHeight,
                strokeWidth = validatedStrokeWidth,
                iconTint = validatedIconTint,
                textColor = validatedTextColor,
            )
        }
    }

    fun clearRepositoryState() {
        repository.clear()
    }

    /**
     * Ensures that the given [foregroundColor] meets the minimum accessibility contrast ratio
     * against the [backgroundColor].
     *
     * If the color does not meet the required ratio, it will be adjusted by modifying its lightness
     * while attempting to preserve the hue, using [ContrastColorUtil].
     *
     * @param colorName A string indicating which color is being adjusted (e.g., "Text color", "Icon
     *   tint").
     * @param foregroundColor The color to check and potentially adjust.
     * @param backgroundColor The background color to contrast against.
     * @return The foreground [Color], adjusted if necessary to ensure sufficient contrast against
     *   the background color.
     */
    private fun ensureColorContrast(
        colorName: String,
        foregroundColor: Color,
        backgroundColor: Color,
    ): Color {
        val foregroundArgb = foregroundColor.toArgb()
        val backgroundArgb = backgroundColor.toArgb()
        val validatedArgb =
            ContrastColorUtil.ensureContrast(foregroundArgb, backgroundArgb, MIN_CONTRAST_RATIO)
        if (foregroundArgb != validatedArgb) {
            Slog.w(
                TAG,
                "$colorName color #${Integer.toHexString(foregroundArgb)} adjusted to #${
                    Integer.toHexString(validatedArgb)
                } for contrast against #${Integer.toHexString(backgroundArgb)}",
            )
        }
        return Color(validatedArgb)
    }

    /** Helper to get min 48dp size in pixels */
    private fun getMinPixelSize(configuration: Configuration): Int {
        val density = configuration.densityDpi / 160f
        return (MIN_TOUCH_TARGET_DP * density).roundToInt() // 48dp in pixels
    }

    private fun validateWidth(widthInPixels: Int, configuration: Configuration): Int {
        val minPixelWidth = getMinPixelSize(configuration)
        val validatedWidth = widthInPixels.coerceAtLeast(minPixelWidth)

        if (widthInPixels < validatedWidth) {
            Slog.w(TAG, "Clamping width from $widthInPixels px to $validatedWidth px (min 48dp)")
        }
        return validatedWidth
    }

    private fun validateHeight(heightInPixels: Int, configuration: Configuration): Int {
        val minPixelHeight = getMinPixelSize(configuration)
        val validatedHeight = heightInPixels.coerceAtLeast(minPixelHeight)

        if (heightInPixels < validatedHeight) {
            Slog.w(TAG, "Clamping height from $heightInPixels px to $validatedHeight px (min 48dp)")
        }
        return validatedHeight
    }

    private fun validateStrokeWidth(widthInPixels: Int, configuration: Configuration): Int {
        val density = configuration.densityDpi / 160f
        val maxPixelWidth = (MAX_STROKE_WIDTH_DP * density).roundToInt()
        val validatedWidth = widthInPixels.coerceAtMost(maxPixelWidth)
        if (widthInPixels > validatedWidth) {
            Slog.w(
                TAG,
                "Clamping strokeWidth from $widthInPixels px to $validatedWidth px (max 4dp)",
            )
        }
        return validatedWidth
    }

    private companion object {
        const val MIN_CONTRAST_RATIO = 4.5
        const val MAX_STROKE_WIDTH_DP = 4
        const val MIN_TOUCH_TARGET_DP = 48
        const val TAG = "LocationButton"
    }
}
