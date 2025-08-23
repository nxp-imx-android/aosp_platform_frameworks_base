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

package com.android.systemui.securelockdevice.ui.viewmodel

import android.hardware.biometrics.BiometricPrompt
import android.view.accessibility.AccessibilityManager
import androidx.annotation.VisibleForTesting
import com.android.systemui.biometrics.shared.model.BiometricModality
import com.android.systemui.biometrics.ui.viewmodel.BiometricAuthIconViewModel
import com.android.systemui.biometrics.ui.viewmodel.PromptAuthState
import com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import com.android.systemui.util.kotlin.pairwise
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Models UI state for the content on the bouncer overlay during secure lock device. */
class SecureLockDeviceBiometricAuthContentViewModel
@AssistedInject
constructor(
    accessibilityManager: AccessibilityManager,
    biometricAuthIconViewModelFactory: BiometricAuthIconViewModel.Factory,
    private val deviceEntryFaceAuthInteractor: SystemUIDeviceEntryFaceAuthInteractor,
    private val secureLockDeviceInteractor: SecureLockDeviceInteractor,
) : ExclusiveActivatable() {
    private var mDisappearAnimationFinishedRunnable: Runnable? = null

    /** @see SecureLockDeviceInteractor.isSecureLockDeviceEnabled */
    val isSecureLockDeviceEnabled = secureLockDeviceInteractor.isSecureLockDeviceEnabled

    /** @see SecureLockDeviceInteractor.enrolledStrongBiometricModalities */
    val enrolledStrongBiometrics = secureLockDeviceInteractor.enrolledStrongBiometricModalities

    /** @see SecureLockDeviceInteractor.requiresPrimaryAuthForSecureLockDevice */
    val requiresPrimaryAuthForSecureLockDevice: StateFlow<Boolean> =
        secureLockDeviceInteractor.requiresPrimaryAuthForSecureLockDevice

    /** @see SecureLockDeviceInteractor.requiresStrongBiometricAuthForSecureLockDevice */
    val requiresStrongBiometricAuthForSecureLockDevice: StateFlow<Boolean> =
        secureLockDeviceInteractor.requiresStrongBiometricAuthForSecureLockDevice

    private val _isAuthenticating: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** If the user is currently authenticating (i.e. at least one biometric is scanning). */
    val isAuthenticating: Flow<Boolean> = _isAuthenticating.asStateFlow()

    private val _showingError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** Whether an error message is currently being shown. */
    val showingError: Flow<Boolean> = _showingError.asStateFlow()

    private val _isAuthenticated: MutableStateFlow<PromptAuthState> =
        MutableStateFlow(PromptAuthState(false))
    /** If the user has successfully authenticated and confirmed (when explicitly required). */
    val isAuthenticated: Flow<PromptAuthState> = _isAuthenticated.asStateFlow()

    /**
     * If authenticated and confirmation is not required, or authenticated and explicitly confirmed
     * and confirmation is required.
     */
    val isAuthenticationComplete: Flow<Boolean> =
        isAuthenticated.map { authState ->
            authState.isAuthenticatedAndConfirmed || authState.isAuthenticatedAndExplicitlyConfirmed
        }

    private val _isReadyToDismissBiometricAuth: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /**
     * True when the biometric authentication success animation has finished playing, and the
     * biometric auth UI can be dismissed.
     */
    val isReadyToDismissBiometricAuth: StateFlow<Boolean> =
        _isReadyToDismissBiometricAuth.asStateFlow()

    private val _isVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /**
     * Models UI state for the biometric icon shown in secure lock device biometric authentication.
     */
    val iconViewModel: BiometricAuthIconViewModel by lazy {
        biometricAuthIconViewModelFactory.create(
            promptViewModel = null,
            secureLockDeviceViewModel = this,
        )
    }

    private var displayErrorJob: Job? = null

    // When a11y enabled, increase message delay to ensure messages get read
    private val displayErrorLength =
        accessibilityManager
            .getRecommendedTimeoutMillis(
                BiometricPrompt.HIDE_DIALOG_DELAY,
                AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_TEXT,
            )
            .toLong()

    /**
     * Show a temporary error associated with an optional [failedModality] and play
     * [hapticFeedback].
     *
     * The [messageAfterError] will be shown via [showAuthenticating] when [authenticateAfterError]
     * is set (or via [showHelp] when not set) after the error is dismissed.
     *
     * The error is ignored if the user has already authenticated.
     */
    @VisibleForTesting
    suspend fun showTemporaryError(
        authenticateAfterError: Boolean,
        hapticFeedback: Boolean = true,
        failedModality: BiometricModality = BiometricModality.None,
    ) = coroutineScope {
        if (_isAuthenticated.value.isAuthenticated) {
            return@coroutineScope
        }
        _isAuthenticating.value = false
        _showingError.value = true
        _isAuthenticated.value = PromptAuthState(false)

        displayErrorJob?.cancel()
        displayErrorJob = launch {
            delay(displayErrorLength)
            if (authenticateAfterError) {
                showAuthenticating()
            } else {
                showHelp()
            }
        }
    }

    /**
     * Show a persistent help message.
     *
     * Will be shown even if the user has already authenticated.
     */
    @VisibleForTesting
    fun showHelp() {
        val alreadyAuthenticated = _isAuthenticated.value.isAuthenticated
        if (!alreadyAuthenticated) {
            _isAuthenticating.value = false
            _isAuthenticated.value = PromptAuthState(false)
        }

        _showingError.value = false
        displayErrorJob?.cancel()
        displayErrorJob = null
    }

    /** Show the user that biometrics are actively running and set [isAuthenticating]. */
    @VisibleForTesting
    fun showAuthenticating() {
        _isAuthenticating.value = true
        deviceEntryFaceAuthInteractor.onSecureLockDeviceBiometricAuthRequested()

        _isAuthenticated.value = PromptAuthState(false)

        _showingError.value = false
        displayErrorJob?.cancel()
        displayErrorJob = null
    }

    /**
     * Show successful authentication, set [isAuthenticated], and enter the device, or prompt for
     * explicit confirmation (if required).
     */
    @VisibleForTesting
    suspend fun showAuthenticated(modality: BiometricModality) = coroutineScope {
        _isAuthenticating.value = false
        val needsUserConfirmation = needsExplicitConfirmation(modality)
        _isAuthenticated.value = PromptAuthState(true, modality, needsUserConfirmation)

        _showingError.value = false
        displayErrorJob?.cancel()
        displayErrorJob = null
        if (needsUserConfirmation) {
            showHelp()
        }
    }

    private fun needsExplicitConfirmation(modality: BiometricModality): Boolean {
        // Only worry about confirmationRequired if face was used to unlock
        if (modality == BiometricModality.Face) {
            return true
        }
        // fingerprint only never requires confirmation
        return false
    }

    private suspend fun hasFingerprint(): Boolean {
        return enrolledStrongBiometrics.first().hasFingerprint
    }

    private fun CoroutineScope.listenForFaceMessages() {
        // Listen for any events from face authentication and update the child view models
        // TODO: showTemporaryError on face auth error, failure, help
        // TODO: showAuthenticated on face auth success
    }

    private fun CoroutineScope.listenForFingerprintMessages() {
        // Listen for any events from fingerprint authentication and update the child view
        // models
        // TODO: showTemporaryError on fingerprint auth error, failure, help
        // TODO: showAuthenticated on fingerprint auth success
    }

    @AssistedFactory
    interface Factory {
        fun create(): SecureLockDeviceBiometricAuthContentViewModel
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch {
                requiresStrongBiometricAuthForSecureLockDevice.pairwise(false).collect { (prev, cur)
                    ->
                    if (!prev && cur) {
                        secureLockDeviceInteractor.onBiometricAuthRequested()
                    }
                }
            }

            launch {
                secureLockDeviceInteractor.shouldShowBiometricAuth
                    .filter { it }
                    .collectLatest { shouldShowBiometricAuth ->
                        showAuthenticating()

                        launch { iconViewModel.activate() }

                        listenForFaceMessages()
                        listenForFingerprintMessages()

                        launch {
                            isReadyToDismissBiometricAuth
                                .filter { it }
                                .collectLatest {
                                    secureLockDeviceInteractor.onReadyToDismissBiometricAuth()
                                    _isVisible.value = false
                                }
                        }
                    }
            }
            awaitCancellation()
        }
    }

    override suspend fun onDeactivated() {
        displayErrorJob?.cancel()
        displayErrorJob = null
    }

    fun startAppearAnimation() {
        _isVisible.value = true
    }

    /**
     * Called from legacy keyguard controller to set runnable with actions to complete when the
     * disappear animation has finished.
     */
    fun setDisappearAnimationFinishedRunnable(finishRunnable: Runnable?) {
        mDisappearAnimationFinishedRunnable = finishRunnable
    }

    /**
     * Runs actions to complete when the disappear animation has finished in the legacy keyguard
     * implementation.
     */
    fun onDisappearAnimationFinished() {
        if (SceneContainerFlag.isEnabled) return
        mDisappearAnimationFinishedRunnable?.run()
    }

    /**
     * Called from Composable to indicate the final animation (i.e. successful fingerprint, face
     * confirmed, etc.) has finished playing and the biometric auth screen can be dismissed
     */
    fun onReadyToDismissBiometricAuth() {
        _isReadyToDismissBiometricAuth.value = true
    }

    companion object {
        const val TAG = "SecureLockDeviceBiometricAuthContentViewModel"
    }
}
