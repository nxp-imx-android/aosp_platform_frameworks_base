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
package com.android.systemui.lowlightclock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.service.dreams.DreamService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import com.android.dream.lowlight.LowLightTransitionCoordinator
import com.android.systemui.res.R
import java.util.Optional
import javax.inject.Inject
import javax.inject.Provider

/** A dark themed text clock dream to be shown when the device is in a low light environment. */
class LowLightClockDreamService
@Inject
constructor(
    private val chargingStatusProvider: ChargingStatusProvider,
    private val animationProvider: LowLightClockAnimationProvider,
    private val lowLightTransitionCoordinator: LowLightTransitionCoordinator,
    optionalDisplayController: Optional<Provider<LowLightDisplayController>>,
) : DreamService(), LowLightTransitionCoordinator.LowLightExitListener {

    private val displayController: LowLightDisplayController? by lazy {
        optionalDisplayController.map { it.get() }.orElse(null)
    }

    private var isDimBrightnessSupported = false
    private lateinit var chargingStatusTextView: TextView
    private lateinit var textClock: TextClock
    private var animationIn: Animator? = null
    private var animationOut: Animator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = false
        isFullscreen = true

        setContentView(
            LayoutInflater.from(applicationContext).inflate(R.layout.low_light_clock_dream, null)
        )

        textClock = findViewById(R.id.low_light_text_clock)
        chargingStatusTextView = findViewById(R.id.charging_status_text_view)

        chargingStatusProvider.startUsing(this::updateChargingMessage)
        lowLightTransitionCoordinator.setLowLightExitListener(this)
    }

    override fun onDreamingStarted() {
        animationIn = animationProvider.provideAnimationIn(textClock, chargingStatusTextView)
        animationIn?.start()

        displayController?.let { controller ->
            isDimBrightnessSupported = controller.isDisplayBrightnessModeSupported()
            if (isDimBrightnessSupported) {
                Log.v(TAG, "setting dim brightness state")
                controller.setDisplayBrightnessModeEnabled(true)
            } else {
                Log.v(TAG, "dim brightness not supported. setting screen brightness to minimum")
                setScreenBrightness(0f)
            }
        }
    }

    override fun onDreamingStopped() {
        if (isDimBrightnessSupported) {
            Log.v(TAG, "clearing dim brightness state")
            displayController?.setDisplayBrightnessModeEnabled(false)
        }
    }

    override fun onWakeUp() {
        animationIn?.cancel()
        animationOut = animationProvider.provideAnimationOut(textClock, chargingStatusTextView)
        animationOut?.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    super@LowLightClockDreamService.onWakeUp()
                }
            }
        )
        animationOut?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationOut?.cancel()
        chargingStatusProvider.stopUsing()
        lowLightTransitionCoordinator.setLowLightExitListener(null)
    }

    private fun updateChargingMessage(showChargingStatus: Boolean, chargingStatusMessage: String) {
        chargingStatusTextView.text = chargingStatusMessage
        chargingStatusTextView.visibility = if (showChargingStatus) View.VISIBLE else View.INVISIBLE
    }

    override fun onBeforeExitLowLight(): Animator? {
        animationOut = animationProvider.provideAnimationOut(textClock, chargingStatusTextView)
        animationOut?.start()

        // Return the animator so that the transition coordinator waits for the low light exit
        // animations to finish before entering low light, as otherwise the default DreamActivity
        // animation plays immediately and there's no time for this animation to play.
        return animationOut
    }

    companion object {
        private const val TAG = "LowLightClockDreamService"
    }
}
