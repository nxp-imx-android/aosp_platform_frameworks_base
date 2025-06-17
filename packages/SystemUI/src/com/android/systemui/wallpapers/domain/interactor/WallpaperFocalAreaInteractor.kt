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

package com.android.systemui.wallpapers.domain.interactor

import android.content.Context
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.View
import com.android.app.animation.MathUtils
import com.android.systemui.customization.clocks.R as customR
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardSmartspaceInteractor
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.wallpapers.data.repository.WallpaperFocalAreaRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@SysUISingleton
class WallpaperFocalAreaInteractor
@Inject
constructor(
    var context: Context,
    private val wallpaperFocalAreaRepository: WallpaperFocalAreaRepository,
    shadeRepository: ShadeRepository,
    smartspaceInteractor: KeyguardSmartspaceInteractor,
) {
    val hasFocalArea = wallpaperFocalAreaRepository.hasFocalArea

    val smartspaceBottom =
        combine(
                wallpaperFocalAreaRepository.notificationDefaultTop,
                smartspaceInteractor.bcSmartspaceVisibility,
                ::Pair,
            )
            .map { (notificationDefaultTop, bcSmartspaceVisibility) ->
                when (bcSmartspaceVisibility) {
                    View.VISIBLE -> {
                        notificationDefaultTop +
                            context.resources
                                .getDimensionPixelSize(customR.dimen.enhanced_smartspace_height)
                                .toFloat()
                    }
                    else -> {
                        notificationDefaultTop
                    }
                }
            }

    val wallpaperFocalAreaBounds: Flow<RectF> =
        combine(
                shadeRepository.isShadeLayoutWide,
                wallpaperFocalAreaRepository.notificationStackAbsoluteBottom,
                wallpaperFocalAreaRepository.shortcutAbsoluteTop,
                smartspaceBottom,
            ) {
                isShadeLayoutWide,
                notificationStackAbsoluteBottom,
                shortcutAbsoluteTop,
                smartspaceBottom ->
                // Wallpaper will be zoomed in with config_wallpaperMaxScale in lockscreen
                // so we need to give a bounds taking this scale in consideration
                val wallpaperZoomedInScale = getSystemWallpaperMaximumScale(context)

                val screenBounds =
                    RectF(
                        0F,
                        0F,
                        context.resources.displayMetrics.widthPixels.toFloat(),
                        context.resources.displayMetrics.heightPixels.toFloat(),
                    )
                val scaledBounds =
                    RectF(
                        screenBounds.centerX() - screenBounds.width() / 2F / wallpaperZoomedInScale,
                        screenBounds.centerY() -
                            screenBounds.height() / 2F / wallpaperZoomedInScale,
                        screenBounds.centerX() + screenBounds.width() / 2F / wallpaperZoomedInScale,
                        screenBounds.centerY() + screenBounds.height() / 2F / wallpaperZoomedInScale,
                    )

                val (left, right) =
                    Pair(
                        scaledBounds.centerX() - scaledBounds.width() / 2F,
                        scaledBounds.centerX() + scaledBounds.width() / 2F,
                    )
                val scaledBottomMargin =
                    (context.resources.displayMetrics.heightPixels - shortcutAbsoluteTop) /
                        wallpaperZoomedInScale
                val top =
                    // tablet landscape
                    if (context.resources.getBoolean(R.bool.center_align_focal_area_shape)) {
                        // no strict constraints for top, use bottom margin to make it symmetric
                        // vertically
                        scaledBounds.top + scaledBottomMargin
                    }
                    // unfold foldable landscape
                    else if (isShadeLayoutWide) {
                        // For all landscape, we should use bottom of smartspace to constrain
                        scaledBounds.top + smartspaceBottom / wallpaperZoomedInScale
                        // handheld / portrait
                    } else {
                        scaledBounds.top +
                            MathUtils.max(smartspaceBottom, notificationStackAbsoluteBottom) /
                                wallpaperZoomedInScale
                    }
                val bottom = scaledBounds.bottom - scaledBottomMargin
                RectF(left, top, right, bottom).also { Log.d(TAG, "Focal area changes to $it") }
            }
            // Make sure a valid rec
            .filter { it.width() >= 0 && it.height() >= 0 }
            .distinctUntilChanged()

    fun setFocalAreaBounds(bounds: RectF) {
        wallpaperFocalAreaRepository.setWallpaperFocalAreaBounds(bounds)
    }

    fun setNotificationDefaultTop(top: Float) {
        wallpaperFocalAreaRepository.setNotificationDefaultTop(top)
    }

    fun setTapPosition(x: Float, y: Float) {
        // Focal area should only react to touch event within its bounds
        val wallpaperZoomedInScale = getSystemWallpaperMaximumScale(context)
        // Because there's a scale applied on wallpaper in lockscreen
        // we should map it to the unscaled position on wallpaper
        val screenCenterX = context.resources.displayMetrics.widthPixels / 2F
        val newX = (x - screenCenterX) / wallpaperZoomedInScale + screenCenterX
        val screenCenterY = context.resources.displayMetrics.heightPixels / 2F
        val newY = (y - screenCenterY) / wallpaperZoomedInScale + screenCenterY
        wallpaperFocalAreaRepository.setTapPosition(PointF(newX, newY))
    }

    companion object {
        fun getSystemWallpaperMaximumScale(context: Context): Float {
            val scale =
                context.resources.getFloat(
                    Resources.getSystem()
                        .getIdentifier(
                            /* name= */ "config_wallpaperMaxScale",
                            /* defType= */ "dimen",
                            /* defPackage= */ "android",
                        )
                )
            return if (scale == 0f) 1f else scale
        }

        private val TAG = WallpaperFocalAreaInteractor::class.simpleName
    }
}
