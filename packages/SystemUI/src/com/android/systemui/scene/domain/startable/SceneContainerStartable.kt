/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.systemui.scene.domain.startable

import android.app.StatusBarManager
import android.os.PowerManager
import android.view.SurfaceControl
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.internal.logging.UiEventLogger
import com.android.keyguard.AuthInteractionProperties
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import com.android.systemui.bouncer.domain.interactor.SimBouncerInteractor
import com.android.systemui.bouncer.shared.logging.BouncerUiEvent
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.classifier.FalsingCollectorActual
import com.android.systemui.common.domain.interactor.SysUIStateDisplaysInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryHapticsInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.deviceentry.shared.model.DeviceUnlockSource
import com.android.systemui.kairos.internal.util.fastForEach
import com.android.systemui.keyguard.DismissCallbackRegistry
import com.android.systemui.keyguard.WindowManagerLockscreenVisibilityManager
import com.android.systemui.keyguard.domain.interactor.KeyguardDismissActionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardEnabledInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardShowWhileAwakeInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardSurfaceBehindInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardWakeDirectlyToGoneInteractor
import com.android.systemui.keyguard.domain.interactor.ShowWhileAwakeReason
import com.android.systemui.keyguard.domain.interactor.TrustInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.AodToGoneTransition
import com.android.systemui.keyguard.shared.model.KeyguardTransitionKeys.WithAnimationOverLockscreen
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.model.SceneContainerPlugin
import com.android.systemui.model.SceneContainerPluginImpl
import com.android.systemui.model.StateChange
import com.android.systemui.model.SysUiState
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.FalsingManager.FalsingBeliefListener
import com.android.systemui.power.data.model.PowerButtonLaunchEvent
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.data.model.peek
import com.android.systemui.scene.domain.SceneFrameworkTableLog
import com.android.systemui.scene.domain.interactor.DisabledContentInteractor
import com.android.systemui.scene.domain.interactor.OnBootTransitionInteractor
import com.android.systemui.scene.domain.interactor.SceneBackInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.domain.startable.SceneContainerStartable.HideOverlayCommand.HideSome
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.logger.SceneLogger
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToAlwaysOnDisplay
import com.android.systemui.scene.shared.model.isKeyguardScene
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.SysuiStatusBarStateController
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.phone.CentralSurfaces
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import com.android.systemui.util.asIndenting
import com.android.systemui.util.kotlin.Quad
import com.android.systemui.util.kotlin.getOrNull
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.kotlin.sample
import com.android.systemui.util.printSection
import com.android.systemui.util.println
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.Lazy
import java.io.PrintWriter
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Hooks up business logic that manipulates the state of the [SceneInteractor] for the system UI
 * scene container based on state from other systems.
 */
@SysUISingleton
class SceneContainerStartable
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val sceneInteractor: SceneInteractor,
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val deviceEntryHapticsInteractor: DeviceEntryHapticsInteractor,
    private val deviceUnlockedInteractor: DeviceUnlockedInteractor,
    private val bouncerInteractor: BouncerInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val sceneLogger: SceneLogger,
    @FalsingCollectorActual private val falsingCollector: FalsingCollector,
    private val falsingManager: FalsingManager,
    private val powerInteractor: PowerInteractor,
    private val simBouncerInteractor: Lazy<SimBouncerInteractor>,
    private val authenticationInteractor: Lazy<AuthenticationInteractor>,
    private val windowController: NotificationShadeWindowController,
    private val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    private val centralSurfacesOptLazy: Lazy<Optional<CentralSurfaces>>,
    private val headsUpInteractor: HeadsUpNotificationInteractor,
    private val occlusionInteractor: KeyguardOcclusionInteractor,
    private val faceUnlockInteractor: DeviceEntryFaceAuthInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val uiEventLogger: UiEventLogger,
    private val sceneBackInteractor: SceneBackInteractor,
    private val shadeSessionStorage: SessionStorage,
    private val keyguardEnabledInteractor: KeyguardEnabledInteractor,
    private val dismissCallbackRegistry: DismissCallbackRegistry,
    private val statusBarStateController: SysuiStatusBarStateController,
    private val alternateBouncerInteractor: AlternateBouncerInteractor,
    private val vibratorHelper: VibratorHelper,
    private val msdlPlayer: MSDLPlayer,
    private val disabledContentInteractor: DisabledContentInteractor,
    private val activityTransitionAnimator: ActivityTransitionAnimator,
    private val shadeModeInteractor: ShadeModeInteractor,
    @SceneFrameworkTableLog private val tableLogBuffer: TableLogBuffer,
    private val trustInteractor: TrustInteractor,
    private val sysuiStateInteractor: SysUIStateDisplaysInteractor,
    shadeDisplaysInteractor: Lazy<ShadeDisplaysInteractor>,
    private val surfaceBehindInteractor: KeyguardSurfaceBehindInteractor,
    private val keyguardDismissActionInteractor: KeyguardDismissActionInteractor,
    private val wakeDirectlyToGoneInteractor: KeyguardWakeDirectlyToGoneInteractor,
    private val keyguardShowWhileAwakeInteractor: KeyguardShowWhileAwakeInteractor,
    private val windowManagerLockscreenVisibilityManager: WindowManagerLockscreenVisibilityManager,
    private val bootInteractor: OnBootTransitionInteractor,
) : CoreStartable {
    private val centralSurfaces: CentralSurfaces?
        get() = centralSurfacesOptLazy.get().getOrNull()

    private val authInteractionProperties = AuthInteractionProperties()

    private val shadePendingDisplayId: Flow<Int> = shadeDisplaysInteractor.get().pendingDisplayId

    override fun start() {
        if (SceneContainerFlag.isEnabled) {
            applicationScope.launch { sceneLogger.activate() }
            sceneLogger.logFrameworkEnabled(isEnabled = true)
            applicationScope.launch { hydrateTableLogBuffer() }
            maybeShowLockscreenOnStart()
            hydrateVisibility()
            automaticallySwitchScenes()
            hydrateSystemUiState()
            collectFalsingSignals()
            respondToFalsingDetections()
            hydrateInteractionState()
            handleBouncerOverscroll()
            handleOcclusion()
            handleDeviceEntryHapticsWhileDeviceNotGone()
            hydrateWindowController()
            hydrateBackStack()
            resetShadeSessions()
            handleKeyguardEnabledness()
            notifyKeyguardDismissCancelledCallbacks()
            refreshLockscreenEnabled()
            hydrateActivityTransitionAnimationState()
            lockWhenDeviceBecomesUntrusted()
            lockWhenKeyguardShowWhenAwake()
            showDismissibleKeyguardWhenFolded()
            wakeFromDozingOnContentChange()
        } else {
            sceneLogger.logFrameworkEnabled(isEnabled = false)
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        with(pw.asIndenting()) {
            printSection("SceneContainerFlag") {
                printSection("Framework availability") {
                    println("isEnabled", SceneContainerFlag.isEnabled)
                    println("isEnabledOnVariant", SceneContainerFlag.isEnabledOnVariant)
                }

                if (!SceneContainerFlag.isEnabled) {
                    return
                }

                printSection("Framework state") {
                    sceneInteractor.dump(this)
                    println("backStack", sceneBackInteractor.backStack.value)
                    println("shadeMode", shadeModeInteractor.shadeMode.value)
                }

                printSection("Authentication state") {
                    println("isKeyguardEnabled", keyguardEnabledInteractor.isKeyguardEnabled.value)
                    println(
                        "isUnlocked",
                        deviceUnlockedInteractor.deviceUnlockStatus.value.isUnlocked,
                    )
                    println("isDeviceEntered", deviceEntryInteractor.isDeviceEntered.value)
                    println(
                        "isFaceAuthEnabledAndEnrolled",
                        faceUnlockInteractor.isFaceAuthEnabledAndEnrolled(),
                    )
                    println("canSwipeToEnter", deviceEntryInteractor.canSwipeToEnter.value)
                }

                printSection("Power state") {
                    println("detailedWakefulness", powerInteractor.detailedWakefulness.value)
                    println("isDozing", keyguardInteractor.isDozing.value)
                    println("isAodAvailable", keyguardInteractor.isAodAvailable.value)
                }
            }
        }
    }

    private suspend fun hydrateTableLogBuffer() {
        coroutineScope {
            launch { sceneInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { keyguardEnabledInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { faceUnlockInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { powerInteractor.hydrateTableLogBuffer(tableLogBuffer) }
            launch { keyguardInteractor.hydrateTableLogBuffer(tableLogBuffer) }
        }
    }

    private fun resetShadeSessions() {
        applicationScope.launch {
            combine(
                    sceneBackInteractor.backStack
                        // We are in a session if either Shade or QuickSettings is on the back stack
                        .map { backStack ->
                            backStack.asIterable().any {
                                // TODO(b/356596436): Include overlays in the back stack as well.
                                it == Scenes.Shade || it == Scenes.QuickSettings
                            }
                        }
                        .distinctUntilChanged(),
                    // We are also in a session if either Notifications Shade or QuickSettings Shade
                    // is currently shown (whether idle or animating).
                    shadeInteractor.isAnyExpanded,
                ) { inBackStack, isShadeShown ->
                    inBackStack || isShadeShown
                }
                // Once a session has ended, clear the session storage.
                .filter { inSession -> !inSession }
                .collect { shadeSessionStorage.clear() }
        }
    }

    private fun <T> CoroutineScope.reportEvents(
        from: Flow<T>,
        eventBuilder: (T) -> SceneInteractor.Event,
    ) {
        launch { from.collect { sceneInteractor.handleEvent(eventBuilder(it)) } }
    }

    /**
     * Updates states in [SceneInteractor] that it needs to calculate the visibility of the scene
     * container.
     */
    private fun hydrateVisibility() {
        applicationScope.launch {
            coroutineScope {
                reportEvents(deviceProvisioningInteractor.isDeviceProvisioned) {
                    SceneInteractor.Event.DeviceProvisioningChange(it)
                }

                reportEvents(deviceUnlockedInteractor.deviceUnlockStatus) {
                    SceneInteractor.Event.DeviceUnlockChange(it.isUnlocked)
                }

                reportEvents(headsUpInteractor.isHeadsUpOrAnimatingAway) {
                    SceneInteractor.Event.HeadsUpNotificationVisibilityChange(it)
                }

                reportEvents(alternateBouncerInteractor.isVisible) {
                    SceneInteractor.Event.AlternateBouncerVisibilityChange(it)
                }

                reportEvents(surfaceBehindInteractor.isAnimatingSurface) {
                    SceneInteractor.Event.SurfaceBehindAnimationChange(it)
                }
            }
        }
    }

    /** Switches between scenes based on ever-changing application state. */
    private fun automaticallySwitchScenes() {
        handleBouncerImeVisibility()
        handleBouncerHiding()
        handleSimUnlock()
        handleDeviceUnlockStatus()
        handlePowerState()
        handleDisableFlags()
    }

    private fun handleBouncerImeVisibility() {
        applicationScope.launch {
            // TODO (b/308001302): Move this to a bouncer specific interactor.
            bouncerInteractor.onImeHiddenByUser.collectLatest {
                sceneInteractor.hideOverlay(
                    overlay = Overlays.Bouncer,
                    loggingReason = "IME hidden.",
                )
            }
        }
    }

    private fun handleBouncerHiding() {
        applicationScope.launch {
            repeatWhen(
                condition =
                    authenticationInteractor
                        .get()
                        .authenticationMethod
                        .map { !it.isSecure }
                        .distinctUntilChanged()
            ) {
                sceneInteractor.hideOverlay(
                    overlay = Overlays.Bouncer,
                    loggingReason = "Authentication method changed to a non-secure one.",
                )
            }
        }
    }

    private fun handleSimUnlock() {
        applicationScope.launch {
            simBouncerInteractor.get().isAnySimSecure.collect { isAnySimLocked ->
                val unlockStatus = deviceUnlockedInteractor.deviceUnlockStatus.value
                when {
                    isAnySimLocked -> {
                        switchToScene(
                            targetSceneKey = Scenes.Lockscreen,
                            loggingReason = "SIM unlock required",
                            hideOverlays =
                                HideSome(
                                    overlays =
                                        listOf(
                                            Overlays.NotificationsShade,
                                            Overlays.QuickSettingsShade,
                                        )
                                ),
                        )
                        sceneInteractor.showOverlay(
                            overlay = Overlays.Bouncer,
                            loggingReason = "Need to authenticate locked SIM card.",
                        )
                    }
                    unlockStatus.isUnlocked &&
                        deviceEntryInteractor.canSwipeToEnter.value == false -> {
                        val loggingReason =
                            "All SIM cards unlocked and device already unlocked and" +
                                " lockscreen doesn't require a swipe to dismiss."
                        switchToScene(targetSceneKey = Scenes.Gone, loggingReason = loggingReason)
                    }
                    else -> {
                        val loggingReason =
                            "All SIM cards unlocked and device still locked" +
                                " or lockscreen still requires a swipe to dismiss."
                        switchToScene(
                            targetSceneKey = Scenes.Lockscreen,
                            loggingReason = loggingReason,
                        )
                    }
                }
            }
        }
    }

    private fun handleDeviceUnlockStatus() {
        applicationScope.launch {
            // Track the previous scene, so that we know where to go when the device is unlocked
            // whilst on the bouncer.
            val previousScene =
                sceneBackInteractor.backScene.stateIn(
                    this,
                    SharingStarted.Eagerly,
                    initialValue = null,
                )
            deviceUnlockedInteractor.deviceUnlockStatus
                .map { deviceUnlockStatus ->
                    val (renderedScenes: List<SceneKey>, renderedOverlays: Set<OverlayKey>) =
                        when (val transitionState = sceneInteractor.transitionStateFlow.value) {
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
                    val isAlternateBouncerVisible = alternateBouncerInteractor.isVisibleState()
                    val isOnPrimaryBouncer = Overlays.Bouncer in renderedOverlays
                    if (!deviceUnlockStatus.isUnlocked) {
                        return@map if (
                            renderedScenes.any { it.isKeyguardScene() } ||
                                Overlays.Bouncer in renderedOverlays
                        ) {
                            // The device locked while already on a keyguard scene or bouncer, no
                            // need to change scenes. But make sure to replace the Gone scene in
                            // the back stack with Lockscreen.
                            sceneBackInteractor.replaceGoneSceneOnBackStack(
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

                    val leaveShadeOpen = statusBarStateController.leaveOpenOnKeyguardHide()
                    val willAnimateDismissAction =
                        keyguardDismissActionInteractor.willAnimateDismissActionOnLockscreen.value

                    when {
                        isAlternateBouncerVisible -> {
                            // When the device becomes unlocked when the alternate bouncer is
                            // showing, always hide the alternate bouncer
                            alternateBouncerInteractor.hide()

                            // ... and go to Gone or stay on the current scene
                            if (isOnCommunal || isOnLockscreen || !leaveShadeOpen) {
                                SwitchSceneCommand.SwitchToScene(
                                    targetSceneKey = Scenes.Gone,
                                    transitionKey =
                                        WithAnimationOverLockscreen.takeIf {
                                            willAnimateDismissAction
                                        },
                                    loggingReason =
                                        "device was unlocked while alternate bouncer" +
                                            " was showing and shade didn't need to be left open",
                                )
                            } else {
                                sceneBackInteractor.replaceLockscreenSceneOnBackStack(
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
                                    !leaveShadeOpen
                            ) {
                                val loggingReason = buildString {
                                    append(
                                        "device was unlocked while the primary bouncer was showing"
                                    )
                                    if (leaveShadeOpen) {
                                        append(" and shade needed to be left open")
                                    } else {
                                        append(" and shade didn't need to be left open")

                                        if (willAnimateDismissAction) {
                                            append(" and will animate dismiss action")
                                        } else {
                                            append(" and will not animate dismiss action")
                                        }
                                    }
                                }
                                if (leaveShadeOpen) {
                                    SwitchSceneCommand.SwitchToScene(
                                        targetSceneKey = Scenes.Gone,
                                        hideOverlays =
                                            // Only hide the bouncer overlay, leaving any other
                                            // overlay (right now the only other overlays are
                                            // shades) visible.
                                            HideSome(Overlays.Bouncer),
                                        loggingReason = loggingReason,
                                        instantlySnapScenes = true,
                                    )
                                } else if (
                                    willAnimateDismissAction ||
                                        // If we're switching to Gone mid-transition from Ls ->
                                        // Bouncer, we'll want to animate the scene change to
                                        // avoid a jump-cut from partially visible LS/Bouncer to
                                        // Gone.
                                        sceneInteractor.transitionStateFlow.value.isTransitioning(
                                            from = Scenes.Lockscreen,
                                            to = Overlays.Bouncer,
                                        )
                                ) {
                                    // Do not snap to scene here or this will break the notification
                                    // animation, but instantly hide the bouncer to prevent any
                                    // unwanted overlap
                                    sceneInteractor.instantlyHideOverlay(
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
                                    sceneInteractor.snapToScene(
                                        toScene = Scenes.Gone,
                                        loggingReason = loggingReason,
                                        hideAllOverlays = false,
                                    )
                                    sceneInteractor.hideOverlay(Overlays.Bouncer, loggingReason)
                                    SwitchSceneCommand.NoOp
                                }
                            } else if (targetScene == Scenes.Shade && willAnimateDismissAction) {
                                SwitchSceneCommand.SwitchToScene(
                                    targetSceneKey = Scenes.Gone,
                                    loggingReason =
                                        "device was unlocked with primary bouncer" +
                                            " showing, from shade, and we're animating the" +
                                            " dismiss (from Shade -> Gone)",
                                    instantlySnapScenes = false,
                                )
                            } else {
                                if (previousScene.value != Scenes.Gone) {
                                    sceneBackInteractor.replaceLockscreenSceneOnBackStack(
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
                                    sceneBackInteractor.replaceLockscreenSceneOnBackStack(
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
                            sceneBackInteractor.replaceLockscreenSceneOnBackStack(
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
                                targetSceneKey = command.targetSceneKey,
                                hideOverlays = command.hideOverlays,
                                loggingReason = command.loggingReason,
                                instantlySnapScenes = command.instantlySnapScenes,
                                transitionKey = command.transitionKey,
                            )
                        }
                        is SwitchSceneCommand.NoOp -> Unit
                    }
                }
        }
    }

    private fun handlePowerState() {
        applicationScope.launch {
            powerInteractor.powerButtonLaunchEvents.collect {
                // If we were entered when the gesture started, we can unlock and return to Gone. We
                // also should do this if we launched while not entered, but can wake directly to
                // Gone (we should never end up Occluded in this case).
                if (
                    it == PowerButtonLaunchEvent.LAUNCH_FROM_ENTERED ||
                        wakeDirectlyToGoneInteractor.canWakeDirectlyToGone.value
                ) {
                    deviceUnlockedInteractor.unlockNowForPowerButtonGesture(
                        "double-tap power gesture arrived and we were asleep/waking from " +
                            "entered"
                    )
                    switchToScene(
                        targetSceneKey = Scenes.Gone,
                        loggingReason = "double-tap power gesture",
                        instantlySnapScenes = true,
                        forDoubleTapPowerGesture = true,
                    )
                } else if (it == PowerButtonLaunchEvent.LAUNCH_FROM_NOT_ENTERED) {
                    switchToScene(
                        Scenes.Occluded,
                        "double tap power while not entered when going to sleep",
                    )
                }
            }
        }
        applicationScope.launch {
            powerInteractor.isAsleep.collect { isAsleep ->
                if (isAsleep) {
                    alternateBouncerInteractor.hide()
                    dismissCallbackRegistry.notifyDismissCancelled()
                    val isAodAvailable = keyguardInteractor.isAodAvailable.value

                    switchToScene(
                        targetSceneKey = Scenes.Lockscreen,
                        loggingReason = "device is starting to sleep",
                        transitionKey = ToAlwaysOnDisplay.takeIf { isAodAvailable },
                        keyguardState = getKeyguardStateForWakefulness(isAwake = false),
                        freezeAndAnimateToCurrentState = !isAodAvailable,
                    )
                } else {
                    if (wakeDirectlyToGoneInteractor.canWakeDirectlyToGone.value) {
                        switchToScene(
                            targetSceneKey = Scenes.Gone,
                            loggingReason =
                                "device is waking up while we can wake directly to gone",
                            // If we're waking directly to Gone from DOZING (no AOD), there's
                            // nothing visible on screen to animate out, so we should snap.
                            instantlySnapScenes = !keyguardInteractor.isAodAvailable.value,
                        )
                    } else if (
                        authenticationInteractor.get().authenticationMethod.value ==
                            AuthenticationMethodModel.Sim
                    ) {
                        sceneInteractor.showOverlay(
                            overlay = Overlays.Bouncer,
                            loggingReason = "device is starting to wake up with a locked sim",
                        )
                    } else if (
                        occlusionInteractor.isKeyguardOccluded.value &&
                            !keyguardInteractor.isDreaming.value
                    ) {
                        switchToScene(
                            targetSceneKey = Scenes.Occluded,
                            loggingReason = "device is waking up while occluded",
                        )
                    }
                }
            }
        }

        applicationScope.launch {
            // Mainly used for tests that are frequently changing keyguard enabled state. There is a
            // race condition on wake up, where checks for suppression happen on background threads
            // that lead to calls to wakeDirectlyToGoneInteractor.canWakeDirectlyToGone.value
            // retrieving the value too early. See [KeyguardEnabledInteractor#isKeyguardSuppressed]
            combine(
                    wakeDirectlyToGoneInteractor.shouldSuppressKeyguard,
                    wakeDirectlyToGoneInteractor.canWakeDirectlyToGone,
                    ::Pair,
                )
                .collect { (shouldSuppressKeyguard, canWakeDirectlyToGone) ->
                    if (shouldSuppressKeyguard && canWakeDirectlyToGone) {
                        switchToScene(
                            targetSceneKey = Scenes.Gone,
                            loggingReason = "keyguard suppressed and can wake to gone",
                            instantlySnapScenes = !keyguardInteractor.isAodAvailable.value,
                        )
                    }
                }
        }
    }

    private fun handleDisableFlags() {
        applicationScope.launch {
            launch {
                sceneInteractor.currentScene.collectLatest { currentScene ->
                    disabledContentInteractor.repeatWhenDisabled(currentScene) {
                        switchToScene(
                            targetSceneKey = SceneFamilies.Home,
                            loggingReason =
                                "Current scene ${currentScene.debugName} became" + " disabled",
                        )
                    }
                }
            }

            launch {
                sceneInteractor.currentOverlays.collectLatest { overlays ->
                    overlays.forEach { overlay ->
                        launch {
                            disabledContentInteractor.repeatWhenDisabled(overlay) {
                                sceneInteractor.hideOverlay(
                                    overlay = overlay,
                                    loggingReason =
                                        "Overlay ${overlay.debugName} became" + " disabled",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDeviceEntryHapticsWhileDeviceNotGone() {
        applicationScope.launch {
            sceneInteractor.currentScene.collectLatest { currentScene ->
                // Only check for haptics signals before device is entered
                if (currentScene != Scenes.Gone) {
                    coroutineScope {
                        launch {
                            deviceEntryHapticsInteractor.playSuccessHapticOnDeviceEntry.collect {
                                currentScene ->
                                if (Flags.msdlFeedback()) {
                                    msdlPlayer.playToken(
                                        MSDLToken.UNLOCK,
                                        authInteractionProperties,
                                    )
                                } else {
                                    vibratorHelper.vibrateAuthSuccess(
                                        "$TAG, $currentScene device-entry::success"
                                    )
                                }
                            }
                        }

                        launch {
                            deviceEntryHapticsInteractor.playErrorHaptic.collect { currentScene ->
                                if (Flags.msdlFeedback()) {
                                    msdlPlayer.playToken(
                                        MSDLToken.FAILURE,
                                        authInteractionProperties,
                                    )
                                } else {
                                    vibratorHelper.vibrateAuthError(
                                        "$TAG, $currentScene device-entry::error"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Keeps [SysUiState] up-to-date */
    private fun hydrateSystemUiState() {
        applicationScope.launch {
            combine(
                    sceneInteractor.transitionStateFlow
                        .mapNotNull { it as? ObservableTransitionState.Idle }
                        .distinctUntilChanged(),
                    sceneInteractor.isVisibleFlow,
                    shadePendingDisplayId,
                    sceneBackInteractor.backStack,
                    shadeModeInteractor.shadeMode,
                ) { idleState, isVisible, displayId, backStack, shadeMode ->
                    displayId to
                        SceneContainerPlugin.SceneContainerPluginState(
                            scene = idleState.currentScene,
                            sceneBehind = backStack.peek(),
                            overlays = idleState.currentOverlays,
                            isVisible = isVisible,
                            shadeMode = shadeMode,
                        )
                }
                .map { (displayId, sceneContainerPluginState) ->
                    displayId to
                        SceneContainerPluginImpl.EvaluatorByFlag.map { (flag, evaluator) ->
                            flag to evaluator(sceneContainerPluginState)
                        }
                }
                .distinctUntilChanged()
                .collect { (displayId: Int, flagMap: List<Pair<Long, Boolean>>) ->
                    sysuiStateInteractor.setFlagsExclusivelyToDisplay(
                        targetDisplayId = displayId,
                        stateChanges = StateChange.from(flagMap),
                    )
                }
        }
    }

    private fun hydrateWindowController() {
        applicationScope.launch {
            sceneInteractor.transitionStateFlow
                .map {
                    !it.isIdle(Scenes.Gone) ||
                        // We must be idle on Gone here, so we check if the overlays are empty
                        (it is ObservableTransitionState.Idle && it.currentOverlays.isNotEmpty())
                }
                .distinctUntilChanged()
                .collect { windowController.setNotificationShadeFocusable(it) }
        }

        applicationScope.launch {
            combine(
                    deviceEntryInteractor.isDeviceEntered,
                    sceneInteractor.transitionStateFlow,
                    ::Pair,
                )
                .map { (isDeviceEntered, transitionState) ->
                    !isDeviceEntered ||
                        transitionState.isTransitioningSets(
                            from = setOf(Scenes.Lockscreen, Scenes.Occluded, Overlays.Bouncer),
                            to = setOf(Scenes.Gone),
                        )
                }
                .distinctUntilChanged()
                .collect { windowController.setKeyguardShowing(it) }
        }

        applicationScope.launch {
            occlusionInteractor.isKeyguardOccluded.collect { isKeyguardOccluded ->
                windowController.setKeyguardOccluded(isKeyguardOccluded)
            }
        }
    }

    /** Collects and reports signals into the falsing system. */
    private fun collectFalsingSignals() {
        applicationScope.launch {
            deviceEntryInteractor.isDeviceEntered.collect { isLockscreenDismissed ->
                if (isLockscreenDismissed) {
                    falsingCollector.onSuccessfulUnlock()
                }
            }
        }

        applicationScope.launch {
            keyguardInteractor.isDozing.collect { isDozing ->
                falsingCollector.setShowingAod(isDozing)
            }
        }

        applicationScope.launch {
            powerInteractor.detailedWakefulness
                .distinctUntilChangedBy { it.isAwake() }
                .collect { wakefulness ->
                    when {
                        wakefulness.isAwakeFromTouch() -> falsingCollector.onScreenOnFromTouch()
                        wakefulness.isAwake() -> falsingCollector.onScreenTurningOn()
                        wakefulness.isAsleep() -> falsingCollector.onScreenOff()
                    }
                }
        }

        applicationScope.launch {
            sceneInteractor.currentOverlays
                .map { Overlays.Bouncer in it }
                .distinctUntilChanged()
                .collect { switchedToBouncerOverlay ->
                    if (switchedToBouncerOverlay) {
                        falsingCollector.onBouncerShown()
                    } else {
                        falsingCollector.onBouncerHidden()
                    }
                }
        }
    }

    /** Switches to the lockscreen when falsing is detected. */
    private fun respondToFalsingDetections() {
        applicationScope.launch {
            conflatedCallbackFlow {
                    val listener = FalsingBeliefListener { trySend(Unit) }
                    falsingManager.addFalsingBeliefListener(listener)
                    awaitClose { falsingManager.removeFalsingBeliefListener(listener) }
                }
                .collect {
                    val loggingReason = "Falsing detected."
                    switchToScene(targetSceneKey = Scenes.Lockscreen, loggingReason = loggingReason)
                }
        }
    }

    /** Keeps the interaction state of [CentralSurfaces] up-to-date. */
    private fun hydrateInteractionState() {
        applicationScope.launch {
            deviceUnlockedInteractor.deviceUnlockStatus
                .map { !it.isUnlocked }
                .flatMapLatest { isDeviceLocked ->
                    if (isDeviceLocked) {
                        sceneInteractor.transitionStateFlow
                            .mapNotNull { it as? ObservableTransitionState.Idle }
                            .map { it.currentScene to it.currentOverlays }
                            .distinctUntilChanged()
                            .map { (currentScene, currentOverlays) ->
                                when {
                                    // When locked, showing the lockscreen scene should be reported
                                    // as "interacting" while showing other scenes should report as
                                    // "not interacting".
                                    //
                                    // This is done here in order to match the legacy
                                    // implementation. The real reason why is lost to lore and myth.
                                    Overlays.NotificationsShade in currentOverlays -> false
                                    Overlays.QuickSettingsShade in currentOverlays -> null
                                    Overlays.Bouncer in currentOverlays -> false
                                    currentScene == Scenes.Lockscreen -> true
                                    currentScene == Scenes.Shade -> false
                                    else -> null
                                }
                            }
                    } else {
                        flowOf(null)
                    }
                }
                .collect { isInteractingOrNull ->
                    isInteractingOrNull?.let { isInteracting ->
                        centralSurfaces?.setInteracting(
                            StatusBarManager.WINDOW_STATUS_BAR,
                            isInteracting,
                        )
                    }
                }
        }
    }

    private fun handleBouncerOverscroll() {
        applicationScope.launch {
            sceneInteractor.transitionStateFlow
                // Only consider transitions.
                .filterIsInstance<ObservableTransitionState.Transition>()
                // Only consider user-initiated (e.g. drags) that go from bouncer to lockscreen.
                .filter { transition ->
                    transition.fromContent == Overlays.Bouncer &&
                        transition.toContent == Scenes.Lockscreen &&
                        transition.isInitiatedByUserInput
                }
                .flatMapLatest { it.progress }
                // Figure out the direction of scrolling.
                .map { progress ->
                    when {
                        progress > 0 -> 1
                        progress < 0 -> -1
                        else -> 0
                    }
                }
                .distinctUntilChanged()
                // Only consider negative scrolling, AKA overscroll.
                .filter { it == -1 }
                .collect { faceUnlockInteractor.onSwipeUpOnBouncer() }
        }
    }

    private fun handleOcclusion() {
        applicationScope.launch {
            occlusionInteractor.isKeyguardOccluded
                .sample(
                    combine(
                        keyguardInteractor.isDreamingNotDozing,
                        sceneBackInteractor.backScene,
                        powerInteractor.isAwake,
                        ::Triple,
                    )
                ) { occluded, (dreaming, backScene, isAwake) ->
                    Quad(occluded, dreaming, backScene, isAwake)
                }
                .collect { (occluded, dreaming, backScene, isAwake) ->
                    // Dreaming is a special case where the keyguard is occluded, and is handled
                    // separately. See [handleDreamState].
                    if (occluded && !dreaming) {
                        // This does not use the scene family to resolve, as there is a race
                        // condition when they both update state based off of the isKeyguardOccluded
                        // value.
                        switchToScene(Scenes.Occluded, "isKeyguardOccluded == true")
                    } else if (sceneInteractor.currentScene.value == Scenes.Occluded) {
                        if (backScene == Scenes.Communal) {
                            switchToScene(Scenes.Communal, "unoccluded and previously on communal")
                        } else if (deviceEntryInteractor.isDeviceEntered.value) {
                            switchToScene(Scenes.Gone, "unoccluded and device entered")
                        } else if (
                            sceneInteractor.currentOverlays.value.contains(Overlays.Bouncer)
                        ) {
                            // We've unoccluded while the bouncer was showing over an occluding
                            // activity. This can happen if the occluding activity crashed or
                            // finished itself behind the bouncer. It can also happen if a CTS
                            // test/very adversarial user launched a non-SHOW_WHEN_LOCKED activity
                            // with FLAG_DISMISS_KEYGUARD over a SHOW_WHEN_LOCKED activity. In that
                            // case, FLAG_DISMISS_KEYGUARD will cause the bouncer to show, but then
                            // the lack of SHOW_WHEN_LOCKED will cause WM to kill the activity. CTS
                            // tests expect to be able to enter the PIN and unlock the device in
                            // this case, so leave the bouncer visible.
                            switchToScene(
                                Scenes.Lockscreen,
                                "unoccluded and device not entered, " +
                                    "bouncer was showing; leaving it up",
                                hideOverlays = HideOverlayCommand.HideNone,
                                keyguardState = getKeyguardStateForWakefulness(isAwake),
                            )
                        } else {
                            switchToScene(
                                Scenes.Lockscreen,
                                "unoccluded and device not entered",
                                keyguardState = getKeyguardStateForWakefulness(isAwake),
                            )
                        }
                    }
                    if (!occluded) {
                        sceneBackInteractor.removeOccludedSceneOnBackStack(
                            reason = "removing occluded from backstack, if present"
                        )
                    }
                }
        }
    }

    private fun handleKeyguardEnabledness() {
        // Automatically switches scenes when keyguard is enabled or disabled, as needed.
        applicationScope.launch {
            keyguardEnabledInteractor.isKeyguardEnabled
                .filter { enabled -> !enabled }
                .sample(deviceUnlockedInteractor.isInLockdown)
                .collect { inLockdown ->
                    if (!inLockdown && !deviceEntryInteractor.isDeviceEntered.value) {
                        switchToScene(Scenes.Gone, "Keyguard was disabled")
                    }
                }
        }
    }

    private fun switchToScene(
        targetSceneKey: SceneKey,
        loggingReason: String,
        transitionKey: TransitionKey? = null,
        keyguardState: KeyguardState? = null,
        freezeAndAnimateToCurrentState: Boolean = false,
        hideOverlays: HideOverlayCommand = HideOverlayCommand.HideAll,
        instantlySnapScenes: Boolean = false,
        forDoubleTapPowerGesture: Boolean = false,
    ) {
        if (hideOverlays is HideSome) {
            hideOverlays.overlays.fastForEach { overlay ->
                sceneInteractor.hideOverlay(overlay, loggingReason)
            }
        }

        if (instantlySnapScenes) {
            if (forDoubleTapPowerGesture) {
                // Special case to skip validation, since unlock flows may not emit by the time the
                // scene transition starts.
                sceneInteractor.snapToGoneForUnlockedPowerLaunchGesture(
                    keyguardState = keyguardState,
                    loggingReason = loggingReason,
                    hideAllOverlays = hideOverlays == HideOverlayCommand.HideAll,
                )
            } else {
                sceneInteractor.snapToScene(
                    toScene = targetSceneKey,
                    keyguardState = keyguardState,
                    loggingReason = loggingReason,
                    hideAllOverlays = hideOverlays == HideOverlayCommand.HideAll,
                )
            }
        } else {
            sceneInteractor.changeScene(
                toScene = targetSceneKey,
                loggingReason = loggingReason,
                transitionKey = transitionKey,
                keyguardState = keyguardState,
                forceSettleToTargetScene = freezeAndAnimateToCurrentState,
                hideAllOverlays = hideOverlays == HideOverlayCommand.HideAll,
            )
        }
    }

    private fun hydrateBackStack() {
        applicationScope.launch {
            sceneInteractor.currentScene.pairwise().collect { (from, to) ->
                sceneBackInteractor.onSceneChange(from = from, to = to)
            }
        }
    }

    private fun notifyKeyguardDismissCancelledCallbacks() {
        applicationScope.launch {
            combine(deviceEntryInteractor.isUnlocked, sceneInteractor.currentOverlays.pairwise()) {
                    isUnlocked,
                    overlayChange ->
                    val difference = overlayChange.previousValue - overlayChange.newValue
                    !isUnlocked &&
                        sceneInteractor.currentScene.value != Scenes.Gone &&
                        Overlays.Bouncer in difference
                }
                .collect { notifyKeyguardDismissCancelled ->
                    if (notifyKeyguardDismissCancelled) {
                        dismissCallbackRegistry.notifyDismissCancelled()
                    }
                }
        }
    }

    /**
     * Keeps the value of [DeviceEntryInteractor.isLockscreenEnabled] fresh.
     *
     * This is needed because that value is sourced from a non-observable data source
     * (`LockPatternUtils`, which doesn't expose a listener or callback for this value). Therefore,
     * every time a transition to the `Lockscreen` scene is started, the value is re-fetched and
     * cached.
     */
    private fun refreshLockscreenEnabled() {
        applicationScope.launch {
            sceneInteractor.transitionStateFlow
                .map { it.isTransitioning(to = Scenes.Lockscreen) }
                .distinctUntilChanged()
                .filter { it }
                .collectLatest { deviceEntryInteractor.refreshLockscreenEnabled() }
        }
    }

    /**
     * Wires the scene framework to activity transition animations that originate from anywhere. A
     * subset of these may actually originate from UI inside one of the scenes in the framework.
     *
     * Telling the scene framework about ongoing activity transition animations is critical so the
     * scene framework doesn't make its scene container invisible during a transition.
     *
     * As it turns out, making the scene container view invisible during a transition animation
     * disrupts the animation and causes interaction jank CUJ tracking to ignore reports of the CUJ
     * ending or being canceled.
     */
    private fun hydrateActivityTransitionAnimationState() {
        activityTransitionAnimator.addListener(
            object : ActivityTransitionAnimator.Listener {
                override fun onTransitionAnimationStart() {
                    sceneInteractor.onTransitionAnimationStart()
                }

                override fun onTransitionAnimationEnd(transaction: SurfaceControl.Transaction) {
                    sceneInteractor.onTransitionAnimationEnd()
                }
            }
        )
    }

    private fun lockWhenDeviceBecomesUntrusted() {
        applicationScope.launch {
            trustInteractor.isTrusted.pairwise().collect { (wasTrusted, isTrusted) ->
                if (wasTrusted && !isTrusted && !deviceEntryInteractor.isDeviceEntered.value) {
                    deviceEntryInteractor.lockNow(
                        "Exited trusted environment while not device not entered"
                    )
                }
            }
        }
    }

    private fun lockWhenKeyguardShowWhenAwake() {
        applicationScope.launch {
            keyguardShowWhileAwakeInteractor.showWhileAwakeEvents
                .filter {
                    it == ShowWhileAwakeReason.KEYGUARD_TIMEOUT_WHILE_SCREEN_ON ||
                        it == ShowWhileAwakeReason.KEYGUARD_REENABLED
                }
                .collect {
                    // If keyguard is enabled, lock and switch to Lockscreen scene if needed.
                    // If it's not enabled, it'll be re-shown when it's enabled again.
                    if (keyguardEnabledInteractor.isKeyguardEnabled.value) {
                        deviceEntryInteractor.lockNow("Screen timed out or WM#lockNow() called")

                        // If we're dreaming, DreamStartable will take us to Scenes.Dream.
                        if (!keyguardInteractor.isDreamingNotDozing.value) {
                            switchToScene(Scenes.Lockscreen, "Not dreaming, and $it")
                        }
                    }
                }
        }
    }

    /**
     * Handles showing the keyguard (but *not* locking) when a foldable device is folded when the
     * "swipe up to continue using apps on fold" (or whatever that setting is currently called) is
     * enabled.
     *
     * This should only happen if we're enabled, and unlike other reasons for showing keyguard while
     * it's disabled, should not cause us to re-show keyguard when it's re-enabled if it was
     * disabled when this request came in (since this wasn't explicitly a request to secure the
     * device).
     */
    private fun showDismissibleKeyguardWhenFolded() {
        applicationScope.launch {
            keyguardShowWhileAwakeInteractor.showWhileAwakeEvents
                .filter { it == ShowWhileAwakeReason.FOLDED_WITH_SWIPE_UP_TO_CONTINUE }
                .collect {
                    if (
                        keyguardEnabledInteractor.isKeyguardEnabled.value &&
                            !occlusionInteractor.isKeyguardOccluded.value
                    ) {
                        switchToScene(Scenes.Lockscreen, "folded with swipe up to continue")
                    }
                }
        }
    }

    /**
     * Wake up the device if we're dozing and no longer displaying the lockscreen Scene. This
     * includes both Scene and Overlay transitions.
     */
    private fun wakeFromDozingOnContentChange() {
        applicationScope.launch {
            launch {
                sceneInteractor.transitionStateFlow
                    .filter {
                        it.isTransitioning(from = Scenes.Lockscreen) ||
                            !it.isIdle(Scenes.Lockscreen)
                    }
                    .distinctUntilChanged()
                    .collect {
                        powerInteractor.wakeUpIfDozing(
                            "Wake-up from dozing. Transitioning away from Scenes.Lockscreen",
                            PowerManager.WAKE_REASON_GESTURE,
                        )
                    }
            }
        }
    }

    private fun maybeShowLockscreenOnStart() {
        // This needs to happen immediately upon start(), we can't wait for onSystemReady,
        // onBootCompleted, or any more reasonable events, since otherwise unlocked app content
        // may be visible during boot. Once those events come through, the
        // WindowManagerLockscreenVisibilityViewModel will take over.
        windowManagerLockscreenVisibilityManager.setLockscreenShowing(
            bootInteractor.showLockscreenOnBoot(),
            "initial lockscreen visibility on start()",
        )
    }

    /**
     * Helper to return the appropriate keyguard state given the current wakefulness of the device.
     */
    private fun getKeyguardStateForWakefulness(isAwake: Boolean): KeyguardState {
        return if (isAwake) {
            KeyguardState.LOCKSCREEN
        } else {
            keyguardInteractor.asleepKeyguardState.value
        }
    }

    private suspend fun repeatWhen(condition: Flow<Boolean>, block: suspend () -> Unit) {
        condition.distinctUntilChanged().collectLatest { conditionMet ->
            if (conditionMet) {
                block()
            }
        }
    }

    sealed interface SwitchSceneCommand {
        data object NoOp : SwitchSceneCommand

        data class SwitchToScene(
            val targetSceneKey: SceneKey,
            val loggingReason: String,
            val hideOverlays: HideOverlayCommand = HideOverlayCommand.HideAll,
            val instantlySnapScenes: Boolean = false,
            val transitionKey: TransitionKey? = null,
        ) : SwitchSceneCommand
    }

    sealed interface HideOverlayCommand {
        data object HideAll : HideOverlayCommand

        data object HideNone : HideOverlayCommand

        class HideSome(val overlays: List<OverlayKey>) : HideOverlayCommand {
            constructor(overlay: OverlayKey) : this(listOf(overlay))
        }
    }

    companion object {
        private const val TAG = "SceneContainerStartable"
    }
}
