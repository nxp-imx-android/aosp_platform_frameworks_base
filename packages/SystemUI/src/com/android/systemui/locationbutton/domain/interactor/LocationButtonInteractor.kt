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

import android.app.permissionui.LocationButtonRequest
import android.app.permissionui.LocationButtonSession.TextType
import android.content.Context
import android.content.res.Configuration
import android.util.Slog
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.internal.graphics.ColorUtils
import com.android.internal.util.ContrastColorUtil
import com.android.settingslib.Utils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.locationbutton.data.repository.LocationButtonRepository
import com.android.systemui.locationbutton.shared.model.ButtonModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlin.math.roundToInt

@SysUISingleton
class LocationButtonInteractor
@Inject
constructor(
    @ShadeDisplayAware private val ctx: Context,
    private val repository: LocationButtonRepository,
) {
    fun getButtonState(sessionId: Int): ButtonModel? = repository.getButtonState(sessionId)

    fun setButtonState(sessionId: Int, request: LocationButtonRequest, density: Float) {
        val rawBackgroundColor =
            if (request.hasBackgroundColor()) {
                request.backgroundColor
            } else {
                Utils.getColorAttrDefaultColor(ctx, com.android.internal.R.attr.colorAccentPrimary)
            }
        val backgroundColor = validateBackgroundColor(rawBackgroundColor)

        val rawTextColor =
            if (request.hasTextColor()) {
                request.textColor
            } else {
                Utils.getColorAttrDefaultColor(ctx, com.android.internal.R.attr.textColorOnAccent)
            }
        val textColor = validateForegroundColor("Text color", Color(rawTextColor), backgroundColor)

        val rawIconTint =
            if (request.hasIconTint()) {
                request.iconTint
            } else {
                Utils.getColorAttrDefaultColor(ctx, com.android.internal.R.attr.textColorOnAccent)
            }
        val iconTint = validateForegroundColor("Icon tint", Color(rawIconTint), backgroundColor)

        val strokeColor =
            if (request.hasStrokeColor()) {
                Color(request.strokeColor)
            } else {
                backgroundColor
            }

        val width = validateWidth(request.width, density)
        val height = validateHeight(request.height, density)

        val paddingLeft =
            if (request.hasPaddingLeft()) {
                validatePadding(request.paddingLeft, density)
            } else {
                0
            }
        val paddingTop =
            if (request.hasPaddingTop()) {
                validatePadding(request.paddingTop, density)
            } else {
                0
            }
        val paddingRight =
            if (request.hasPaddingRight()) {
                validatePadding(request.paddingRight, density)
            } else {
                0
            }
        val paddingBottom =
            if (request.hasPaddingBottom()) {
                validatePadding(request.paddingBottom, density)
            } else {
                0
            }

        val strokeWidth =
            if (request.hasStrokeWidth()) {
                validateStrokeWidth(request.strokeWidth, density)
            } else {
                0
            }

        val cornerRadius =
            if (request.hasCornerRadius()) {
                validateCornerRadius(request.cornerRadius)
            } else {
                null
            }
        val pressedCornerRadius =
            if (request.hasPressedCornerRadius()) {
                validateCornerRadius(request.pressedCornerRadius)
            } else {
                null
            }

        val textType =
            if (request.hasTextType()) {
                request.textType
            } else {
                android.app.permissionui.LocationButtonSession.TEXT_TYPE_NONE
            }

        val validatedModel =
            ButtonModel(
                width = width,
                height = height,
                paddingLeft = paddingLeft,
                paddingTop = paddingTop,
                paddingRight = paddingRight,
                paddingBottom = paddingBottom,
                backgroundColor = backgroundColor,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius,
                pressedCornerRadius = pressedCornerRadius,
                iconTint = iconTint,
                textResId = getTextResourceId(textType),
                textColor = textColor,
                configuration = request.configuration,
                density = density,
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

    fun setPressedCornerRadius(sessionId: Int, pressedCornerRadius: Float) {
        repository.updateButtonState(sessionId) {
            it.copy(pressedCornerRadius = validateCornerRadius(pressedCornerRadius))
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

    fun setTextType(sessionId: Int, textType: Int) {
        repository.updateButtonState(sessionId) { it.copy(textResId = getTextResourceId(textType)) }
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

    fun setPadding(sessionId: Int, left: Int, top: Int, right: Int, bottom: Int) {
        val model = repository.getButtonState(sessionId)
        if (model == null) {
            Slog.e(LOG_TAG, "Cannot setPadding: No state found for session $sessionId")
            return
        }

        repository.updateButtonState(sessionId) {
            it.copy(
                paddingLeft = validatePadding(left, model.density),
                paddingTop = validatePadding(top, model.density),
                paddingRight = validatePadding(right, model.density),
                paddingBottom = validatePadding(bottom, model.density),
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

    @StringRes
    private fun getTextResourceId(@TextType textType: Int): Int? =
        when (textType) {
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_PRECISE_LOCATION ->
                R.string.location_button_text_precise_location
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_USE_PRECISE_LOCATION ->
                R.string.location_button_text_use_precise_location
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_SHARE_PRECISE_LOCATION ->
                R.string.location_button_text_share_precise_location
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_NEAR_MY_PRECISE_LOCATION ->
                R.string.location_button_text_near_my_precise_location
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_NEAR_YOUR_PRECISE_LOCATION ->
                R.string.location_button_text_near_your_precise_location
            android.app.permissionui.LocationButtonSession.TEXT_TYPE_NONE -> null
            else -> {
                Slog.w(LOG_TAG, "Text type $textType is not supported. Using no text.")
                null
            }
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
