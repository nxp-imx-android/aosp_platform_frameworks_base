/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.deviceentry.domain.interactor

import com.android.internal.policy.IKeyguardDismissCallback
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.data.repository.DeviceEntryRepository
import com.android.systemui.keyguard.DismissCallbackRegistry
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissActionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.data.model.peek
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.isKeyguardScene
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Hosts application business logic related to device entry.
 *
 * Device entry occurs when the user successfully dismisses (or bypasses) the lockscreen, regardless
 * of the authentication method used.
 */
@SysUISingleton
class DeviceEntryInteractor
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val repository: Lazy<DeviceEntryRepository>,
    private val authenticationInteractor: Lazy<AuthenticationInteractor>,
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    private val alternateBouncerInteractor: Lazy<AlternateBouncerInteractor>,
    private val dismissCallbackRegistry: Lazy<DismissCallbackRegistry>,
    private val sceneBackInteractor: Lazy<SceneBackInteractor>,
    @SceneFrameworkTableLog private val tableLogBuffer: Lazy<TableLogBuffer>,
    private val keyguardDismissActionInteractor: Lazy<KeyguardDismissActionInteractor>,
) {
    /**
     * Whether the device is unlocked.
     *
     * A device that is not yet unlocked requires unlocking by completing an authentication
     * challenge according to the current authentication method, unless in cases when the current
     * authentication method is not "secure" (for example, None and Swipe); in such cases, the value
     * of this flow will always be `true`, even if the lockscreen is showing and still needs to be
     * dismissed by the user to proceed.
     */
    val isUnlocked: StateFlow<Boolean> by lazy {
        deviceUnlockedInteractor
            .get()
            .deviceUnlockStatus
            .map { it.isUnlocked }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = deviceUnlockedInteractor.get().deviceUnlockStatus.value.isUnlocked,
            )
    }

    /**
     * Emits `true` when the current scene switches to [Scenes.Gone] for the first time after having
     * been on [Scenes.Lockscreen] or any other keyguard scenes.
     *
     * Different from [isDeviceEnteredOnBackStack] such that the current scene must actually go
     * through [Scenes.Gone] to produce a `true`. [isDeviceEnteredOnBackStack] takes into account
     * the navigation back stack and will produce a `true` value when the bottommost entry of the
     * navigation back stack switched from [Scenes.Lockscreen] to [Scenes.Gone] while the user is
     * staring at another scene.
     */
    val isDeviceEnteredDirectly: StateFlow<Boolean> by lazy {
        sceneInteractor
            .get()
            .currentScene
            .filter { currentScene ->
                currentScene == Scenes.Gone || currentScene.isKeyguardScene()
            }
            .mapLatestConflated { scene ->
                if (scene == Scenes.Gone) {
                    // Make sure device unlock status is definitely unlocked before we
                    // consider the device "entered".
                    deviceUnlockedInteractor.get().deviceUnlockStatus.first { it.isUnlocked }
                    true
                } else {
                    false
                }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }

    /**
     * Emits `true` when the bottom of the navigation back stack switches to [Scenes.Gone], and
     * `false` when it switches to [Scenes.Lockscreen].
     *
     * Different from [isDeviceEnteredDirectly] such that the current scene may not change while the
     * device becomes "entered" or "not entered" underneath. E.g. shade is open while device gets
     * unlocked underneath.
     */
    private val isDeviceEnteredOnBackStack: StateFlow<Boolean> by lazy {
        sceneBackInteractor
            .get()
            .backStack
            // The bottom of the back stack, which is Lockscreen, Gone, or null if empty.
            .map { it.asIterable().lastOrNull() }
            // Filter out cases where the stack changes but the bottom remains unchanged.
            .distinctUntilChanged()
            // Device is entered when the bottom of the back stack is Gone, and not entered when it
            // is Lockscreen or null.
            .map { bottomScene -> bottomScene == Scenes.Gone }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }

    /**
     * Whether the device has been entered (i.e. the lockscreen has been dismissed, by any method).
     * This can be `false` when the device is unlocked, e.g. when the user still needs to swipe away
     * the non-secure lockscreen, even though they've already authenticated.
     *
     * Note: This does not imply that the lockscreen is visible or not.
     */
    val isDeviceEntered: StateFlow<Boolean> by lazy {
        combine(
                // This flow emits true when the currentScene switches to Gone for the first time
                // after having been on Lockscreen.
                isDeviceEnteredDirectly,
                // This flow emits true when the bottom of the navigation back stack switches to
                // Gone, and false when it switches to Lockscreen.
                isDeviceEnteredOnBackStack,
            ) { enteredDirectly, enteredOnBackStack ->
                enteredOnBackStack || enteredDirectly
            }
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer.get(),
                columnName = "isDeviceEntered",
                initialValue = false,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
    }

    val isLockscreenEnabled: Flow<Boolean> by lazy {
        repository.get().isLockscreenEnabled.onStart { refreshLockscreenEnabled() }
    }

    /**
     * Whether it's currently possible to swipe up to enter the device without requiring
     * authentication or when the device is already authenticated using a passive authentication
     * mechanism like face or trust manager. This returns `false` whenever the lockscreen has been
     * dismissed.
     *
     * A value of `null` is meaningless and is used as placeholder while the actual value is still
     * being loaded in the background.
     *
     * Note: `true` doesn't mean the lockscreen is visible. It may be occluded or covered by other
     * UI.
     */
    val canSwipeToEnter: StateFlow<Boolean?> by lazy {
        combine(
                authenticationInteractor.get().authenticationMethod.map {
                    it == AuthenticationMethodModel.None
                },
                isLockscreenEnabled,
                deviceUnlockedInteractor.get().deviceUnlockStatus,
                isDeviceEntered,
            ) { isNoneAuthMethod, isLockscreenEnabled, deviceUnlockStatus, isDeviceEntered ->
                val isSwipeAuthMethod = isNoneAuthMethod && isLockscreenEnabled
                (isSwipeAuthMethod ||
                    (deviceUnlockStatus.isUnlocked &&
                        deviceUnlockStatus.deviceUnlockSource?.dismissesLockscreen == false)) &&
                    !isDeviceEntered
            }
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer.get(),
                columnName = "canSwipeToEnter",
                initialValue = false,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                // Starts as null to prevent downstream collectors from falsely assuming that the
                // user can or cannot swipe to enter the device while the real value is being loaded
                // from upstream data sources.
                initialValue = null,
            )
    }

    /**
     * Attempt to enter the device and dismiss the lockscreen. If authentication is required to
     * unlock the device it will transition to bouncer.
     *
     * @param callback An optional callback to invoke when the attempt succeeds, fails, or is
     *   canceled
     */
    @JvmOverloads
    fun attemptDeviceEntry(loggingReason: String, callback: IKeyguardDismissCallback? = null) {
        callback?.let { dismissCallbackRegistry.get().addCallback(it) }

        // TODO (b/307768356),
        //       1. Check if the device is already authenticated by trust agent/passive biometrics
        //       2. Show SPFS/UDFPS bouncer if it is available AlternateBouncerInteractor.show
        //       3. For face auth only setups trigger face auth, delay transitioning to bouncer for
        //          a small amount of time.
        //       4. Transition to bouncer scene
        applicationScope.launch {
            if (isAuthenticationRequired()) {
                if (alternateBouncerInteractor.get().canShowAlternateBouncer.value) {
                    alternateBouncerInteractor.get().forceShow()
                } else {
                    sceneInteractor
                        .get()
                        .showOverlay(
                            overlay = Overlays.Bouncer,
                            loggingReason =
                                "request to unlock device while authentication" +
                                    " required, original reason for request: $loggingReason",
                        )
                }
            } else {
                val willAnimateToGone =
                    keyguardDismissActionInteractor.get().willAnimateDismissActionOnLockscreen.value
                val currentScene = sceneInteractor.get().currentScene.value
                if (
                    !willAnimateToGone &&
                        (currentScene == Scenes.Shade || currentScene == Scenes.QuickSettings) &&
                        sceneBackInteractor.get().backStack.value.peek() != null
                ) {
                    // If we don't need to animate to Gone, the current scene is Shade or Quick
                    // Settings, and the back stack is not empty, replacing the Lockscreen scene at
                    // the bottom of the stack triggers device entry without dismissing the Shade.
                    sceneBackInteractor.get().replaceLockscreenSceneOnBackStack()
                } else {
                    val transitionKey =
                        if (willAnimateToGone) {
                            KeyguardTransitionKeys.WithAnimationOverLockscreen
                        } else {
                            null
                        }
                    sceneInteractor
                        .get()
                        .changeScene(
                            toScene = Scenes.Gone,
                            transitionKey = transitionKey,
                            loggingReason =
                                "request to unlock device while authentication isn't " +
                                    "required, original reason for request: $loggingReason",
                        )
                }
            }
        }
    }

    /**
     * Returns `true` if the device currently requires authentication before entry is granted;
     * `false` if the device can be entered without authenticating first.
     */
    fun isAuthenticationRequired(): Boolean {
        return !deviceUnlockedInteractor.get().deviceUnlockStatus.value.isUnlocked &&
            authenticationInteractor.get().authenticationMethod.value.isSecure
    }

    /**
     * Whether the lockscreen is enabled for the current user. This is `true` whenever the user has
     * chosen any secure authentication method and even if they set the lockscreen to be dismissed
     * when the user swipes on it.
     */
    suspend fun isLockscreenEnabled(): Boolean {
        return repository.get().isLockscreenEnabled()
    }

    /**
     * Forces a refresh of the value of [isLockscreenEnabled] such that the flow emits the latest
     * value.
     *
     * Without calling this method, the flow will have a stale value unless the collector is removed
     * and re-added.
     */
    suspend fun refreshLockscreenEnabled() {
        isLockscreenEnabled()
    }

    /** Locks the device instantly. */
    fun lockNow(debuggingReason: String) {
        deviceUnlockedInteractor.get().lockNow(debuggingReason)

        applicationScope.launch {
            // The device unlock interactor can't lock the device if the device has SWIPE as screen
            // lock, so we need to manually transition to lockscreen in this case.
            if (isLockscreenEnabled() && !isAuthenticationRequired()) {
                sceneInteractor.get().resolveSceneFamilyOrNull(SceneFamilies.Home)?.value?.let {
                    resolvedScene ->
                    // If the resolved scene is Gone, we should always show the lockscreen.
                    val toScene = resolvedScene.takeIf { it != Scenes.Gone } ?: Scenes.Lockscreen
                    if (toScene != Scenes.Lockscreen) {
                        // We should never be in a state where the current scene is the
                        // [Scenes.Lockscreen] and the lockscreen is also on the back stack.
                        sceneBackInteractor.get().addLockscreenToBackStack()
                    }
                    sceneInteractor
                        .get()
                        .changeScene(
                            toScene = toScene,
                            loggingReason =
                                "lock now with SWIPE auth method, reason: $debuggingReason",
                        )
                }
            }
        }
    }
}
