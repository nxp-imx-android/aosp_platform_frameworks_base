/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.scene.domain.resolver

import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.data.model.SceneStack
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.util.kotlin.combine
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Resolver for [SceneFamilies.Home]. The "home" scene family resolves to the scene that is
 * currently underneath any "overlay" scene, such as shades or bouncer.
 */
@SysUISingleton
class HomeSceneFamilyResolver
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    deviceEntryInteractor: DeviceEntryInteractor,
    keyguardInteractor: KeyguardInteractor,
    keyguardEnabledInteractor: KeyguardEnabledInteractor,
    keyguardOcclusionInteractor: KeyguardOcclusionInteractor,
    powerInteractor: PowerInteractor,
    sceneBackInteractor: SceneBackInteractor,
) : SceneResolver {
    override val targetFamily: SceneKey = SceneFamilies.Home

    override val resolvedScene: StateFlow<SceneKey> =
        combine(
                keyguardOcclusionInteractor.isKeyguardOccluded,
                keyguardEnabledInteractor.isKeyguardEnabled,
                deviceEntryInteractor.canSwipeToEnter,
                deviceEntryInteractor.isDeviceEntered,
                deviceEntryInteractor.isUnlocked,
                keyguardInteractor.isDreamingWithOverlay,
                keyguardInteractor.isDreamingNotDozing,
                keyguardInteractor.isAodAvailable,
                powerInteractor.isAwake,
                sceneBackInteractor.backStack,
                transform = ::homeScene,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    homeScene(
                        isKeyguardOccluded = keyguardOcclusionInteractor.isKeyguardOccluded.value,
                        isKeyguardEnabled = keyguardEnabledInteractor.isKeyguardEnabled.value,
                        canSwipeToEnter = deviceEntryInteractor.canSwipeToEnter.value,
                        isDeviceEntered = deviceEntryInteractor.isDeviceEntered.value,
                        isUnlocked = deviceEntryInteractor.isUnlocked.value,
                        isDreamingWithOverlay = false,
                        isDreamingNotDozing = false,
                        isAodAvailable = false,
                        isAwake = true,
                        backStack = sceneBackInteractor.backStack.value,
                    ),
            )

    private fun homeScene(
        isKeyguardOccluded: Boolean,
        isKeyguardEnabled: Boolean,
        canSwipeToEnter: Boolean?,
        isDeviceEntered: Boolean,
        isUnlocked: Boolean,
        isDreamingWithOverlay: Boolean,
        isDreamingNotDozing: Boolean,
        isAodAvailable: Boolean,
        isAwake: Boolean,
        backStack: SceneStack,
    ): SceneKey {
        return when {
            // Dream can run even if Keyguard is disabled, thus it has the highest priority here.
            isDreamingWithOverlay && isDreamingNotDozing -> Scenes.Dream
            isKeyguardOccluded -> Scenes.Occluded
            // If we're asleep on AOD, show Lockscreen scene even if keyguard is disabled.
            !isAwake && isAodAvailable -> Scenes.Lockscreen
            !isKeyguardEnabled -> Scenes.Gone
            canSwipeToEnter == true -> Scenes.Lockscreen
            !isDeviceEntered -> Scenes.Lockscreen
            !isUnlocked -> Scenes.Lockscreen
            // If we have SWIPE security, isUnlocked will never be false. Locking the device
            // actually means showing the lockscreen. If we are unable to show the lockscreen
            // immediately and we end up adding it to the backstack, we need to resolve the
            // home scene to lockscreen.
            backStack.asIterable().lastOrNull() == Scenes.Lockscreen -> Scenes.Lockscreen
            else -> Scenes.Gone
        }
    }

    companion object {
        private val homeScenes =
            setOf(
                Scenes.Communal,
                // Dream is a home scene as the dream activity occludes keyguard and can show the
                // shade on top.
                Scenes.Dream,
                Scenes.Gone,
                Scenes.Lockscreen,
                Scenes.Occluded,
            )
    }
}

@Module
interface HomeSceneFamilyResolverModule {
    @Binds @IntoSet fun provideSceneResolver(interactor: HomeSceneFamilyResolver): SceneResolver
}
