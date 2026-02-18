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

import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.internal.logging.UiEventLogger
import com.android.internal.policy.IKeyguardDismissCallback
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.shared.logging.BouncerUiEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.data.repository.DeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.keyguard.DismissCallbackRegistry
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissActionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.AodToGoneTransition
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.WithAnimationOverLockscreen
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.data.model.peek
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.domain.startable.SceneContainerStartable.HideOverlayCommand
import com.android.systemui.scene.domain.startable.SceneContainerStartable.SwitchSceneCommand
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.isKeyguardScene
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.SysuiStatusBarStateController
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
    private val statusBarStateController: SysuiStatusBarStateController,
    private val uiEventLogger: UiEventLogger,
    private val keyguardInteractor: KeyguardInteractor,
    private val shadeInteractor: Lazy<ShadeInteractor>,
    private val shadeModeInteractor: ShadeModeInteractor,
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
                    // Make sure device unlock status is definitely unlocked before
                    // considering the device "entered".
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

        // Check if the device is already authenticated by trust agent/passive biometrics.
        // If authentication is required:
        //     Show SPFS/UDFPS bouncer if it is available AlternateBouncerInteractor.show, else
        //     transition to bouncer scene
        // If authentication is not required:
        //     Determine whether there is a need to transition to Gone, Shade, or to stay on the
        //     current Scene and remove the lockscreen from the backstack
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
                    // If the device doesn't need to animate to Gone, the current scene is Shade or
                    // QS, and the back stack is not empty, replacing the Lockscreen scene at
                    // the bottom of the stack triggers device entry without dismissing the Shade.
                    sceneBackInteractor
                        .get()
                        .replaceLockscreenSceneOnBackStack(
                            reason =
                                "unlocking while on shade or qs, and animation isn't needed," +
                                    " original reason for request: $loggingReason"
                        )
                } else if (statusBarStateController.leaveOpenOnKeyguardHide()) {
                    enterDeviceAndShowShade(
                        isPrimaryBouncerShowing = false,
                        loggingReason =
                            "request to unlock and transition to shade (leaveOpenOnKeyguardHide)" +
                                " while authentication isn't required, original reason for" +
                                " request: $loggingReason",
                        willAnimateDismissAction = willAnimateToGone,
                    )
                } else {
                    sceneInteractor
                        .get()
                        .changeScene(
                            toScene = Scenes.Gone,
                            transitionKey =
                                WithAnimationOverLockscreen.takeIf { willAnimateToGone },
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
                        sceneBackInteractor
                            .get()
                            .addLockscreenToBackStack(
                                reason = "lock requested when authentication is not required"
                            )
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

    /**
     * Handles scene transitions based on the latest unlock status, backStack and
     * leaveOpenOnKeyguardHide state.
     */
    fun handleDeviceUnlockStatus(
        switchToScene:
            (
                targetSceneKey: SceneKey,
                loggingReason: String,
                transitionKey: TransitionKey?,
                keyguardState: KeyguardState?,
                freezeAndAnimateToCurrentState: Boolean,
                hideOveralays: HideOverlayCommand,
                instantlySnapScenes: Boolean,
                forDoubleTapPowerGesture: Boolean,
            ) -> Unit
    ) {
        applicationScope.launch {
            // Track the previous scene, so that we know where to go when the device is unlocked
            // whilst on the bouncer.
            val previousScene =
                sceneBackInteractor
                    .get()
                    .backScene
                    .stateIn(this, SharingStarted.Eagerly, initialValue = null)
            deviceUnlockedInteractor
                .get()
                .deviceUnlockStatus
                .map { deviceUnlockStatus ->
                    val (renderedScenes: List<SceneKey>, renderedOverlays: Set<OverlayKey>) =
                        when (
                            val transitionState = sceneInteractor.get().transitionStateFlow.value
                        ) {
                            is ObservableTransitionState.Idle ->
                                listOf(transitionState.currentScene) to
                                    transitionState.currentOverlays
                            is ObservableTransitionState.Transition.ChangeScene ->
                                listOf(transitionState.fromScene, transitionState.toScene) to
                                    transitionState.currentOverlays
                            is ObservableTransitionState.Transition.OverlayTransition ->
                                listOf(transitionState.currentScene) to
                                    setOfNotNull(
                                        transitionState.toContent as? OverlayKey,
                                        transitionState.fromContent as? OverlayKey,
                                    )
                        }
                    val isOnLockscreen = renderedScenes.contains(Scenes.Lockscreen)
                    val isOnShade = renderedScenes.contains(Scenes.Shade)
                    val isOnCommunal = renderedScenes.contains(Scenes.Communal)
                    val isAlternateBouncerVisible =
                        alternateBouncerInteractor.get().isVisibleState()
                    val isOnPrimaryBouncer = Overlays.Bouncer in renderedOverlays
                    if (!deviceUnlockStatus.isUnlocked) {
                        return@map if (
                            renderedScenes.any { it.isKeyguardScene() } ||
                                Overlays.Bouncer in renderedOverlays
                        ) {
                            // The device locked while already on a keyguard scene or bouncer, no
                            // need to change scenes. But make sure to replace the Gone scene in
                            // the back stack with Lockscreen.
                            sceneBackInteractor
                                .get()
                                .replaceGoneSceneOnBackStack(
                                    reason = "device locked while on a keyguard scene or bouncer"
                                )
                            SwitchSceneCommand.NoOp
                        } else {
                            // The device locked while on a scene that's not a keyguard scene, go
                            // to Lockscreen.
                            SwitchSceneCommand.SwitchToScene(
                                targetSceneKey = Scenes.Lockscreen,
                                loggingReason = "device locked in a non-keyguard scene",
                            )
                        }
                    }

                    if (
                        isOnPrimaryBouncer &&
                            deviceUnlockStatus.deviceUnlockSource == DeviceUnlockSource.TrustAgent
                    ) {
                        uiEventLogger.log(BouncerUiEvent.BOUNCER_DISMISS_EXTENDED_ACCESS)
                    }

                    val enterDeviceAndShowShade = statusBarStateController.leaveOpenOnKeyguardHide()
                    val willAnimateDismissAction =
                        keyguardDismissActionInteractor
                            .get()
                            .willAnimateDismissActionOnLockscreen
                            .value

                    when {
                        isAlternateBouncerVisible -> {
                            // When the device becomes unlocked when the alternate bouncer is
                            // showing, always hide the alternate bouncer
                            alternateBouncerInteractor.get().hide()

                            // ... and go to Shade, Gone or stay on the current scene
                            if (enterDeviceAndShowShade) {
                                enterDeviceAndShowShade(
                                    isPrimaryBouncerShowing = false,
                                    loggingReason =
                                        "device was unlocked while alternate bouncer" +
                                            " was showing and shade should show",
                                    willAnimateDismissAction = willAnimateDismissAction,
                                )
                                SwitchSceneCommand.NoOp
                            } else if (isOnCommunal || isOnLockscreen) {
                                SwitchSceneCommand.SwitchToScene(
                                    targetSceneKey = Scenes.Gone,
                                    transitionKey =
                                        WithAnimationOverLockscreen.takeIf {
                                            willAnimateDismissAction
                                        },
                                    loggingReason =
                                        "device was unlocked while alternate bouncer" +
                                            " was showing and shade didn't need to be show",
                                )
                            } else {
                                sceneBackInteractor
                                    .get()
                                    .replaceLockscreenSceneOnBackStack(
                                        reason = "unlocked while alternate bouncer is showing"
                                    )
                                SwitchSceneCommand.NoOp
                            }
                        }
                        isOnPrimaryBouncer -> {
                            // When the device becomes unlocked in primary bouncer, transition to
                            // Gone or remain in the current scene. If transition is a scene change,
                            // take the destination scene.
                            val targetScene = renderedScenes.last()
                            if (
                                targetScene == Scenes.Lockscreen ||
                                    targetScene == Scenes.Communal ||
                                    !enterDeviceAndShowShade
                            ) {
                                val loggingReason = buildString {
                                    append(
                                        "device was unlocked while the primary bouncer was showing"
                                    )
                                    if (enterDeviceAndShowShade) {
                                        append(" and shade needs to show")
                                    } else {
                                        append(" and shade doesn't need to show")

                                        if (willAnimateDismissAction) {
                                            append(" and will animate dismiss action")
                                        } else {
                                            append(" and will not animate dismiss action")
                                        }
                                    }
                                }
                                if (enterDeviceAndShowShade) {
                                    enterDeviceAndShowShade(
                                        isPrimaryBouncerShowing = true,
                                        loggingReason = loggingReason,
                                        willAnimateDismissAction = willAnimateDismissAction,
                                    )
                                    SwitchSceneCommand.NoOp
                                } else if (
                                    willAnimateDismissAction ||
                                        // If the device is switching to Gone mid-transition from
                                        // Ls -> Bouncer, animate the scene change to
                                        // avoid a jump-cut from partially visible LS/Bouncer to
                                        // Gone.
                                        sceneInteractor
                                            .get()
                                            .transitionStateFlow
                                            .value
                                            .isTransitioning(
                                                from = Scenes.Lockscreen,
                                                to = Overlays.Bouncer,
                                            )
                                ) {
                                    // Do not snap to scene here or this will break the notification
                                    // animation, but instantly hide the bouncer to prevent any
                                    // unwanted overlap
                                    sceneInteractor
                                        .get()
                                        .instantlyHideOverlay(
                                            Overlays.Bouncer,
                                            "Instant hide bouncer for animation",
                                        )
                                    SwitchSceneCommand.SwitchToScene(
                                        targetSceneKey = Scenes.Gone,
                                        hideOverlays = HideOverlayCommand.HideAll,
                                        transitionKey =
                                            WithAnimationOverLockscreen.takeIf {
                                                willAnimateDismissAction
                                            },
                                        loggingReason = loggingReason,
                                        instantlySnapScenes = false,
                                    )
                                } else {
                                    // Snap to scene to avoid any flicker of the current scene
                                    // This is intentionally not using [SwitchToScene] as the
                                    // scene transition needs to happen before the overlay is
                                    // hidden.
                                    sceneInteractor
                                        .get()
                                        .snapToScene(
                                            toScene = Scenes.Gone,
                                            loggingReason = loggingReason,
                                            hideAllOverlays = false,
                                        )
                                    sceneInteractor
                                        .get()
                                        .hideOverlay(Overlays.Bouncer, loggingReason)
                                    SwitchSceneCommand.NoOp
                                }
                            } else if (targetScene == Scenes.Shade && willAnimateDismissAction) {
                                SwitchSceneCommand.SwitchToScene(
                                    targetSceneKey = Scenes.Gone,
                                    loggingReason =
                                        "device was unlocked with primary bouncer" +
                                            " showing, from shade, and device is animating the" +
                                            " dismiss (from Shade -> Gone)",
                                    instantlySnapScenes = false,
                                )
                            } else {
                                if (previousScene.value != Scenes.Gone) {
                                    sceneBackInteractor
                                        .get()
                                        .replaceLockscreenSceneOnBackStack(
                                            reason = "previous scene is not gone"
                                        )
                                }
                                SwitchSceneCommand.SwitchToScene(
                                    targetSceneKey = targetScene,
                                    loggingReason =
                                        "device was unlocked with primary bouncer" +
                                            " showing, from sceneKey=${targetScene.debugName}",
                                    instantlySnapScenes = true,
                                )
                            }
                        }
                        isOnLockscreen || isOnCommunal ->
                            // The lockscreen should be dismissed automatically in 2 scenarios:
                            // 1. When face auth bypass is enabled and authentication happens while
                            //    the user is on the lockscreen.
                            // 2. Whenever the user authenticates using an active authentication
                            //    mechanism like fingerprint auth. Since canSwipeToEnter is true
                            //    when the user is passively authenticated, the false value here
                            //    when the unlock state changes indicates this is an active
                            //    authentication attempt.
                            when {
                                deviceUnlockStatus.deviceUnlockSource?.dismissesLockscreen ==
                                    true ->
                                    SwitchSceneCommand.SwitchToScene(
                                        targetSceneKey = Scenes.Gone,
                                        transitionKey =
                                            when {
                                                willAnimateDismissAction ->
                                                    WithAnimationOverLockscreen
                                                BiometricUnlockMode.isWakeAndDismiss(
                                                    keyguardInteractor.biometricUnlockState.value
                                                        .mode
                                                ) -> AodToGoneTransition
                                                else -> null
                                            },
                                        loggingReason =
                                            "device was unlocked while lockscreen" +
                                                " with bypass enabled or using an active" +
                                                " authentication mechanism:" +
                                                " ${deviceUnlockStatus.deviceUnlockSource}",
                                    )
                                else -> SwitchSceneCommand.NoOp
                            }
                        isOnShade -> {
                            val unlockSourceDismissesLockscreen =
                                deviceUnlockStatus.deviceUnlockSource?.dismissesLockscreen == true
                            val unlockSourceIsFingerPrint =
                                deviceUnlockStatus.deviceUnlockSource is
                                    DeviceUnlockSource.Fingerprint
                            when {
                                // This represents the case when the fingerprint will cause the
                                // device to enter while the shade is expanded.
                                unlockSourceDismissesLockscreen && unlockSourceIsFingerPrint ->
                                    SwitchSceneCommand.SwitchToScene(
                                        targetSceneKey = Scenes.Gone,
                                        loggingReason =
                                            "device was entered while in shade by using the" +
                                                " Fingerprint",
                                    )
                                else -> {
                                    // Remain in the shade but replace the Lockscreen scene from
                                    // the bottom of the navigation with the Gone scene since the
                                    // device is unlocked.
                                    sceneBackInteractor
                                        .get()
                                        .replaceLockscreenSceneOnBackStack(
                                            reason = "unlocked while shade is open"
                                        )
                                    SwitchSceneCommand.NoOp
                                }
                            }
                        }
                        // Not on lockscreen or bouncer, so remain in the current scene but since
                        // unlocked, replace the Lockscreen scene from the bottom of the navigation
                        // back stack with the Gone scene.
                        else -> {
                            sceneBackInteractor
                                .get()
                                .replaceLockscreenSceneOnBackStack(
                                    reason = "unlocked on a scene other than lockscreen or bouncer"
                                )
                            SwitchSceneCommand.NoOp
                        }
                    }
                }
                .collect { command: SwitchSceneCommand ->
                    when (command) {
                        is SwitchSceneCommand.SwitchToScene -> {
                            switchToScene(
                                command.targetSceneKey,
                                command.loggingReason,
                                command.transitionKey,
                                /* keyguardState */ null,
                                /* freezeAndAnimateToCurrentState */ false,
                                command.hideOverlays,
                                command.instantlySnapScenes,
                                /* forDoubleTapPowerGesture */ false,
                            )
                        }
                        is SwitchSceneCommand.NoOp -> Unit
                    }
                }
        }
    }

    private fun willShadeShowOnBouncerHide(): Boolean {
        return if (shadeModeInteractor.isDualShade) {
            val currentOverlays = sceneInteractor.get().currentOverlays.value
            Overlays.QuickSettingsShade in currentOverlays ||
                Overlays.NotificationsShade in currentOverlays
        } else {
            val topOfBackStack = sceneBackInteractor.get().backStack.value.peek()
            topOfBackStack == Scenes.Shade || topOfBackStack == Scenes.QuickSettings
        }
    }

    private fun enterDeviceAndShowShade(
        isPrimaryBouncerShowing: Boolean,
        loggingReason: String,
        willAnimateDismissAction: Boolean,
    ) {
        // Once the device is entered, will the shade be visible without intervention?
        val shadeWillShowOnDeviceEntry =
            if (isPrimaryBouncerShowing) {
                willShadeShowOnBouncerHide()
            } else {
                shadeInteractor.get().isAnyFullyExpanded.value
            }
        val isDualShade = shadeModeInteractor.isDualShade

        if (isDualShade) {
            // For DualShade:
            //     Shade is an Overlay.
            //     To enter the device, switch the scene to Gone.
            if (isPrimaryBouncerShowing) {
                sceneInteractor.get().hideOverlay(Overlays.Bouncer, loggingReason)
            }
            if (!shadeWillShowOnDeviceEntry) {
                // Assume if the shade isn't already showing, the request is to show
                // the NotificationsShade (not QS).
                shadeInteractor.get().expandNotificationsShade(loggingReason = loggingReason)
            }
            sceneInteractor
                .get()
                .changeScene(
                    toScene = Scenes.Gone,
                    loggingReason = loggingReason,
                    transitionKey = WithAnimationOverLockscreen.takeIf { willAnimateDismissAction },
                    hideAllOverlays = false,
                )
        } else {
            // For SingleShade or SplitShade:
            //     Shade is a Scene.
            //     To enter the device, call replaceLockscreenSceneOnBackStack.
            if (shadeWillShowOnDeviceEntry) {
                sceneBackInteractor.get().replaceLockscreenSceneOnBackStack(reason = loggingReason)
                sceneInteractor.get().hideOverlay(Overlays.Bouncer, loggingReason)
            } else {
                // SceneContainerStartable#hydrateBackStack will replaceLockscreenSceneOnBackStack
                // once the scene changes to Shade. Doing this in hydrateBackStack's onSceneChange
                // will ensure the lockscreen is on the backstack when
                // replaceLockscreenSceneOnBackStack is called.
                sceneInteractor
                    .get()
                    .changeScene(
                        toScene = Scenes.Shade,
                        loggingReason = loggingReason,
                        transitionKey =
                            WithAnimationOverLockscreen.takeIf { willAnimateDismissAction },
                        hideAllOverlays = true, // hides bouncer overlay if showing
                    )
            }
        }
    }
}
