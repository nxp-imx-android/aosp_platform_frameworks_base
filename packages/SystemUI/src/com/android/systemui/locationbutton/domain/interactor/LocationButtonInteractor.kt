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
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.internal.graphics.ColorUtils
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
        val density = requestModel.density
        val backgroundColor = validateBackgroundColor(requestModel.backgroundColor.toArgb())
        val validatedModel =
            requestModel.copy(
                width = validateWidth(requestModel.width, density),
                height = validateHeight(requestModel.height, density),
                paddingLeft = validatePadding(requestModel.paddingLeft, density),
                paddingTop = validatePadding(requestModel.paddingTop, density),
                paddingRight = validatePadding(requestModel.paddingRight, density),
                paddingBottom = validatePadding(requestModel.paddingBottom, density),
                strokeWidth = validateStrokeWidth(requestModel.strokeWidth, density),
                cornerRadius = validateCornerRadius(requestModel.cornerRadius),
                pressedCornerRadius = validateCornerRadius(requestModel.pressedCornerRadius),
                backgroundColor = backgroundColor,
                iconTint =
                    validateForegroundColor("Icon tint", requestModel.iconTint, backgroundColor),
                textColor =
                    validateForegroundColor("Text color", requestModel.textColor, backgroundColor),
            )
        repository.setButtonState(sessionId, validatedModel)
    }

    fun removeButtonState(sessionId: Int) {
        repository.removeButtonState(sessionId)
    }

    fun setCornerRadius(sessionId: Int, cornerRadius: Float) {
        repository.updateButtonState(sessionId) {
            it.copy(cornerRadius = validateCornerRadius(cornerRadius))
        }
    }

    fun setBackgroundColor(sessionId: Int, color: Int) {
        repository.updateButtonState(sessionId) {
            val backgroundColor = validateBackgroundColor(color)
            it.copy(
                backgroundColor = backgroundColor,
                iconTint = validateForegroundColor("Icon tint", it.iconTint, backgroundColor),
                textColor = validateForegroundColor("Text color", it.textColor, backgroundColor),
            )
        }
    }

    fun setTextColor(sessionId: Int, textColor: Int) {
        repository.updateButtonState(sessionId) {
            it.copy(
                textColor =
                    validateForegroundColor("Text color", Color(textColor), it.backgroundColor)
            )
        }
    }

    fun setIconTint(sessionId: Int, color: Int) {
        repository.updateButtonState(sessionId) {
            it.copy(
                iconTint = validateForegroundColor("Icon tint", Color(color), it.backgroundColor)
            )
        }
    }

    fun setTextId(sessionId: Int, @StringRes textId: Int?) {
        repository.updateButtonState(sessionId) { it.copy(textResId = textId) }
    }

    fun setStrokeColor(sessionId: Int, color: Int) {
        repository.updateButtonState(sessionId) { it.copy(strokeColor = Color(color)) }
    }

    fun setStrokeWidth(sessionId: Int, width: Int) {
        repository.updateButtonState(sessionId) {
            it.copy(strokeWidth = validateStrokeWidth(width, it.density))
        }
    }

    fun setSize(sessionId: Int, width: Int, height: Int) {
        repository.updateButtonState(sessionId) {
            it.copy(
                width = validateWidth(width, it.density),
                height = validateHeight(height, it.density),
            )
        }
    }

    fun setConfiguration(sessionId: Int, newConfig: Configuration, density: Float) {
        repository.updateButtonState(sessionId) {
            val backgroundColor = validateBackgroundColor(it.backgroundColor.toArgb())
            it.copy(
                configuration = newConfig,
                density = density,
                width = validateWidth(it.width, density),
                height = validateHeight(it.height, density),
                paddingLeft = validatePadding(it.paddingLeft, density),
                paddingTop = validatePadding(it.paddingTop, density),
                paddingRight = validatePadding(it.paddingRight, density),
                paddingBottom = validatePadding(it.paddingBottom, density),
                strokeWidth = validateStrokeWidth(it.strokeWidth, density),
                backgroundColor = backgroundColor,
                iconTint = validateForegroundColor("Icon tint", it.iconTint, backgroundColor),
                textColor = validateForegroundColor("Text color", it.textColor, backgroundColor),
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
    private fun validateForegroundColor(
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
                LOG_TAG,
                "$colorName color #${Integer.toHexString(foregroundArgb)} adjusted to #${
                    Integer.toHexString(validatedArgb)
                } for contrast against #${Integer.toHexString(backgroundArgb)}",
            )
        }
        return Color(validatedArgb)
    }

    // Make background color fully opaque.
    private fun validateBackgroundColor(@ColorInt backgroundColor: Int) =
        Color(ColorUtils.setAlphaComponent(backgroundColor, 255))

    private fun validateWidth(width: Int, density: Float): Int {
        val minWidth = (MIN_TOUCH_TARGET_SIZE_DP * density).roundToInt()
        if (width < minWidth) {
            Slog.w(LOG_TAG, "Clamping width up from $width to $minWidth px (min 48dp)")
            return minWidth
        }
        return width
    }

    private fun validateHeight(height: Int, density: Float): Int {
        val minHeight = (MIN_TOUCH_TARGET_SIZE_DP * density).roundToInt()
        val maxHeight = (MAX_HEIGHT_DP * density).roundToInt()

        if (height < minHeight) {
            Slog.w(LOG_TAG, "Clamping height up from $height to $minHeight px (min 48dp)")
            return minHeight
        }
        if (height > maxHeight) {
            Slog.w(LOG_TAG, "Clamping height down from $height to $maxHeight px (max 136dp)")
            return maxHeight
        }

        return height
    }

    private fun validateCornerRadius(cornerRadius: Float): Float {
        if (cornerRadius < 0) {
            Slog.w(LOG_TAG, "cornerRadius can't be negative.")
            return 0f
        }
        return cornerRadius
    }

    private fun validateStrokeWidth(strokeWidth: Int, density: Float): Int {
        if (strokeWidth < 0) {
            Slog.w(LOG_TAG, "strokeWidth can't be negative.")
            return 0
        }
        val maxStrokeWidth = (MAX_STROKE_WIDTH_DP * density).roundToInt()

        if (strokeWidth > maxStrokeWidth) {
            Slog.w(
                LOG_TAG,
                "Clamping strokeWidth from $strokeWidth to $maxStrokeWidth px (max 3dp)",
            )
            return maxStrokeWidth
        }
        return strokeWidth
    }

    private fun validatePadding(padding: Int, density: Float): Int {
        if (padding < 0) {
            Slog.w(LOG_TAG, "Padding can't be negative.")
            return 0
        }

        val maxPadding = (MAX_PADDING_DP * density).roundToInt()

        if (padding > maxPadding) {
            Slog.w(LOG_TAG, "Clamping padding from $padding to $maxPadding px (max 8dp)")
            return maxPadding
        }
        return padding
    }

    private companion object {
        const val MIN_CONTRAST_RATIO = 4.5
        const val MAX_STROKE_WIDTH_DP = 3
        const val MIN_TOUCH_TARGET_SIZE_DP = 48
        const val MAX_HEIGHT_DP = 136
        const val MAX_PADDING_DP = 8
        const val LOG_TAG = "LocationButton"
    }
}
