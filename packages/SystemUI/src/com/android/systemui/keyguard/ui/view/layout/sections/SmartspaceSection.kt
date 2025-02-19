/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.GONE
import androidx.constraintlayout.widget.ConstraintSet.VISIBLE
import com.android.systemui.customization.R as customR
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardSmartspaceInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.binder.KeyguardSmartspaceViewBinder
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardRootViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.res.R as R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shared.R as sharedR
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle

@SysUISingleton
open class SmartspaceSection
@Inject
constructor(
    @ShadeDisplayAware val context: Context,
    val keyguardClockViewModel: KeyguardClockViewModel,
    val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
    private val keyguardSmartspaceInteractor: KeyguardSmartspaceInteractor,
    val smartspaceController: LockscreenSmartspaceController,
    val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val blueprintInteractor: Lazy<KeyguardBlueprintInteractor>,
    private val keyguardRootViewModel: KeyguardRootViewModel,
) : KeyguardSection() {
    private var smartspaceView: View? = null
    private var weatherView: View? = null
    private var dateView: ViewGroup? = null
    private var weatherViewLargeClock: View? = null
    private var dateViewLargeClock: View? = null

    private var smartspaceVisibilityListener: OnGlobalLayoutListener? = null
    private var pastVisibility: Int = -1
    private var disposableHandle: DisposableHandle? = null

    override fun onRebuildBegin() {
        smartspaceController.suppressDisconnects = true
    }

    override fun onRebuildEnd() {
        smartspaceController.suppressDisconnects = false
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) return
        smartspaceView = smartspaceController.buildAndConnectView(constraintLayout)
        weatherView = smartspaceController.buildAndConnectWeatherView(constraintLayout, false)
        dateView =
            smartspaceController.buildAndConnectDateView(constraintLayout, false) as? ViewGroup
        if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
            weatherViewLargeClock =
                smartspaceController.buildAndConnectWeatherView(constraintLayout, true)
            dateViewLargeClock =
                smartspaceController.buildAndConnectDateView(constraintLayout, true)
        }
        pastVisibility = smartspaceView?.visibility ?: View.GONE
        constraintLayout.addView(smartspaceView)
        if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
            dateView?.visibility = View.GONE
            weatherView?.visibility = View.GONE
            dateViewLargeClock?.visibility = View.GONE
            weatherViewLargeClock?.visibility = View.GONE
            constraintLayout.addView(dateView)
            constraintLayout.addView(weatherView)
            constraintLayout.addView(weatherViewLargeClock)
            constraintLayout.addView(dateViewLargeClock)
        } else {
            if (keyguardSmartspaceViewModel.isDateWeatherDecoupled) {
                constraintLayout.addView(dateView)
                // Place weather right after the date, before the extras (alarm and dnd)
                val index = if (dateView?.childCount == 0) 0 else 1
                dateView?.addView(weatherView, index)
            }
        }
        keyguardUnlockAnimationController.lockscreenSmartspace = smartspaceView
        smartspaceVisibilityListener = OnGlobalLayoutListener {
            smartspaceView?.let {
                val newVisibility = it.visibility
                if (pastVisibility != newVisibility) {
                    keyguardSmartspaceInteractor.setBcSmartspaceVisibility(newVisibility)
                    pastVisibility = newVisibility
                }
            }
        }
        smartspaceView?.viewTreeObserver?.addOnGlobalLayoutListener(smartspaceVisibilityListener)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) return
        disposableHandle?.dispose()
        disposableHandle =
            KeyguardSmartspaceViewBinder.bind(
                constraintLayout,
                keyguardRootViewModel,
                keyguardClockViewModel,
                keyguardSmartspaceViewModel,
                blueprintInteractor.get(),
            )
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) return
        val dateWeatherPaddingStart = KeyguardSmartspaceViewModel.getDateWeatherStartMargin(context)
        val smartspaceHorizontalPadding =
            KeyguardSmartspaceViewModel.getSmartspaceHorizontalMargin(context)
        constraintSet.apply {
            constrainHeight(sharedR.id.date_smartspace_view, ConstraintSet.WRAP_CONTENT)
            constrainWidth(sharedR.id.date_smartspace_view, ConstraintSet.WRAP_CONTENT)
            if (!com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                connect(
                    sharedR.id.date_smartspace_view,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    dateWeatherPaddingStart,
                )
            }

            constrainHeight(sharedR.id.bc_smartspace_view, ConstraintSet.WRAP_CONTENT)
            constrainWidth(sharedR.id.bc_smartspace_view, ConstraintSet.MATCH_CONSTRAINT)
            connect(
                sharedR.id.bc_smartspace_view,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                smartspaceHorizontalPadding,
            )
            connect(
                sharedR.id.bc_smartspace_view,
                ConstraintSet.END,
                if (keyguardSmartspaceViewModel.isShadeLayoutWide.value) R.id.split_shade_guideline
                else ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                smartspaceHorizontalPadding,
            )
            if (keyguardClockViewModel.hasCustomWeatherDataDisplay.value) {
                if (!com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                    clear(sharedR.id.date_smartspace_view, ConstraintSet.TOP)
                    connect(
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.BOTTOM,
                        sharedR.id.bc_smartspace_view,
                        ConstraintSet.TOP,
                    )
                }
            } else {
                clear(sharedR.id.date_smartspace_view, ConstraintSet.BOTTOM)
                if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                    connect(
                        sharedR.id.bc_smartspace_view,
                        ConstraintSet.TOP,
                        customR.id.lockscreen_clock_view,
                        ConstraintSet.BOTTOM,
                    )
                } else {
                    connect(
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.TOP,
                        customR.id.lockscreen_clock_view,
                        ConstraintSet.BOTTOM,
                    )
                    connect(
                        sharedR.id.bc_smartspace_view,
                        ConstraintSet.TOP,
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.BOTTOM,
                    )
                }
            }

            if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                if (keyguardClockViewModel.isLargeClockVisible.value) {
                    setVisibility(sharedR.id.weather_smartspace_view, GONE)
                    setVisibility(sharedR.id.date_smartspace_view, GONE)
                    constrainHeight(
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.WRAP_CONTENT,
                    )
                    constrainWidth(
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.WRAP_CONTENT,
                    )
                    constrainHeight(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.WRAP_CONTENT,
                    )
                    constrainWidth(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.WRAP_CONTENT,
                    )
                    connect(
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.TOP,
                        customR.id.lockscreen_clock_view_large,
                        ConstraintSet.BOTTOM,
                        dateWeatherPaddingStart,
                    )

                    connect(
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.START,
                        customR.id.lockscreen_clock_view_large,
                        ConstraintSet.START,
                    )
                    connect(
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.END,
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.START,
                    )

                    connect(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.BOTTOM,
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.BOTTOM,
                    )

                    connect(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.TOP,
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.TOP,
                    )

                    connect(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.START,
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.END,
                    )

                    connect(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.END,
                        customR.id.lockscreen_clock_view_large,
                        ConstraintSet.END,
                    )

                    setHorizontalChainStyle(
                        sharedR.id.weather_smartspace_view_large,
                        ConstraintSet.CHAIN_PACKED,
                    )
                    setHorizontalChainStyle(
                        sharedR.id.date_smartspace_view_large,
                        ConstraintSet.CHAIN_PACKED,
                    )
                } else {
                    setVisibility(sharedR.id.weather_smartspace_view_large, GONE)
                    setVisibility(sharedR.id.date_smartspace_view_large, GONE)
                    constrainHeight(sharedR.id.date_smartspace_view, ConstraintSet.WRAP_CONTENT)
                    constrainWidth(sharedR.id.date_smartspace_view, ConstraintSet.WRAP_CONTENT)
                    constrainHeight(sharedR.id.weather_smartspace_view, ConstraintSet.WRAP_CONTENT)
                    constrainWidth(sharedR.id.weather_smartspace_view, ConstraintSet.WRAP_CONTENT)

                    connect(
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.START,
                        customR.id.lockscreen_clock_view,
                        ConstraintSet.END,
                        20,
                    )
                    connect(
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.TOP,
                        customR.id.lockscreen_clock_view,
                        ConstraintSet.TOP,
                    )
                    connect(
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.BOTTOM,
                        sharedR.id.weather_smartspace_view,
                        ConstraintSet.TOP,
                    )
                    connect(
                        sharedR.id.weather_smartspace_view,
                        ConstraintSet.START,
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.START,
                    )
                    connect(
                        sharedR.id.weather_smartspace_view,
                        ConstraintSet.TOP,
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.BOTTOM,
                    )
                    connect(
                        sharedR.id.weather_smartspace_view,
                        ConstraintSet.BOTTOM,
                        customR.id.lockscreen_clock_view,
                        ConstraintSet.BOTTOM,
                    )

                    setVerticalChainStyle(
                        sharedR.id.weather_smartspace_view,
                        ConstraintSet.CHAIN_PACKED,
                    )
                    setVerticalChainStyle(
                        sharedR.id.date_smartspace_view,
                        ConstraintSet.CHAIN_PACKED,
                    )
                }
            }

            if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    sharedR.id.bc_smartspace_view,
                )
                createBarrier(
                    R.id.smart_space_barrier_top,
                    Barrier.TOP,
                    0,
                    sharedR.id.bc_smartspace_view,
                )
            } else {
                createBarrier(
                    R.id.smart_space_barrier_bottom,
                    Barrier.BOTTOM,
                    0,
                    *intArrayOf(sharedR.id.bc_smartspace_view, sharedR.id.date_smartspace_view),
                )
                createBarrier(
                    R.id.smart_space_barrier_top,
                    Barrier.TOP,
                    0,
                    *intArrayOf(sharedR.id.bc_smartspace_view, sharedR.id.date_smartspace_view),
                )
            }
        }
        updateVisibility(constraintSet)
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) return

        val list =
            if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                listOf(
                    smartspaceView,
                    dateView,
                    weatherView,
                    weatherViewLargeClock,
                    dateViewLargeClock,
                )
            } else {
                listOf(smartspaceView, dateView)
            }
        list.forEach {
            it?.let {
                if (it.parent == constraintLayout) {
                    constraintLayout.removeView(it)
                }
            }
        }
        smartspaceView?.viewTreeObserver?.removeOnGlobalLayoutListener(smartspaceVisibilityListener)
        smartspaceVisibilityListener = null

        disposableHandle?.dispose()
    }

    private fun updateVisibility(constraintSet: ConstraintSet) {

        // This may update the visibility of the smartspace views
        smartspaceController.requestSmartspaceUpdate()
        val weatherId: Int
        val dateId: Int
        if (
            com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout() &&
                keyguardClockViewModel.isLargeClockVisible.value
        ) {
            weatherId = sharedR.id.weather_smartspace_view_large
            dateId = sharedR.id.date_smartspace_view_large
        } else {
            weatherId = sharedR.id.weather_smartspace_view
            dateId = sharedR.id.date_smartspace_view
        }

        constraintSet.apply {
            val showWeather = keyguardSmartspaceViewModel.isWeatherVisible.value
            setVisibility(weatherId, if (showWeather) VISIBLE else GONE)
            setAlpha(weatherId, if (showWeather) 1f else 0f)

            val showDateView = !keyguardClockViewModel.hasCustomWeatherDataDisplay.value
            setVisibility(dateId, if (showDateView) VISIBLE else GONE)
            setAlpha(dateId, if (showDateView) 1f else 0f)

            if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                if (keyguardClockViewModel.isLargeClockVisible.value) {
                    setVisibility(sharedR.id.weather_smartspace_view, GONE)
                    setVisibility(sharedR.id.date_smartspace_view, GONE)
                } else {
                    setVisibility(sharedR.id.weather_smartspace_view_large, GONE)
                    setVisibility(sharedR.id.date_smartspace_view_large, GONE)
                }
            }
        }
    }
}
