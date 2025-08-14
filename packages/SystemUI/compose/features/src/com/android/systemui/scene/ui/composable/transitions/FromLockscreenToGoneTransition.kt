package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.shared.model.Scenes

fun TransitionBuilder.lockscreenToGoneTransition() {
    spec = tween(durationMillis = 500)

    fade(Scenes.Lockscreen.rootElementKey)
    fade(LockscreenElementKeys.BehindScrim)
}
