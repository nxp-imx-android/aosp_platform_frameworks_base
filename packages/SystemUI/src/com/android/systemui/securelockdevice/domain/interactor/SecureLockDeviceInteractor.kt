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

package com.android.systemui.securelockdevice.domain.interactor

import com.android.systemui.biometrics.domain.interactor.FacePropertyInteractor
import com.android.systemui.biometrics.domain.interactor.FingerprintPropertyInteractor
import com.android.systemui.biometrics.shared.model.BiometricModalities
import com.android.systemui.biometrics.shared.model.SensorStrength
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryBiometricSettingsInteractor
import com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Handles business logic for secure lock device. */
@SysUISingleton
class SecureLockDeviceInteractor
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    secureLockDeviceRepository: SecureLockDeviceRepository,
    biometricSettingsInteractor: DeviceEntryBiometricSettingsInteractor,
    private val deviceEntryFaceAuthInteractor: SystemUIDeviceEntryFaceAuthInteractor,
    fingerprintPropertyInteractor: FingerprintPropertyInteractor,
    facePropertyInteractor: FacePropertyInteractor,
) {
    /** @see SecureLockDeviceRepository.isSecureLockDeviceEnabled */
    val isSecureLockDeviceEnabled: StateFlow<Boolean> =
        secureLockDeviceRepository.isSecureLockDeviceEnabled.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    /** @see SecureLockDeviceRepository.requiresPrimaryAuthForSecureLockDevice */
    val requiresPrimaryAuthForSecureLockDevice: StateFlow<Boolean> =
        secureLockDeviceRepository.requiresPrimaryAuthForSecureLockDevice.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    /** @see SecureLockDeviceRepository.requiresStrongBiometricAuthForSecureLockDevice */
    val requiresStrongBiometricAuthForSecureLockDevice: StateFlow<Boolean> =
        secureLockDeviceRepository.requiresStrongBiometricAuthForSecureLockDevice.stateIn(
            applicationScope,
            SharingStarted.Eagerly,
            false,
        )

    private val _shouldShowBiometricAuth = MutableStateFlow<Boolean>(false)
    /** Whether the secure lock device biometric auth UI should be shown. */
    val shouldShowBiometricAuth: StateFlow<Boolean> = _shouldShowBiometricAuth.asStateFlow()

    private val _isFullyUnlockedAndReadyToDismiss = MutableStateFlow<Boolean>(false)

    /**
     * Called after the user completes successful two-factor authentication (primary + strong
     * biometric) in secure lock device and the authenticated animation has finished playing,
     * indicating the biometric auth UI is ready for dismissal
     */
    fun onReadyToDismissBiometricAuth() {
        _isFullyUnlockedAndReadyToDismiss.value = true
    }

    /**
     * Whether the device should listen for biometric auth while secure lock device is enabled. The
     * device should stop listening when pending authentication, when authenticated, or when the
     * biometric auth screen is exited without authenticating.
     */
    val shouldListenForBiometricAuth: Flow<Boolean> =
        // TODO (b/405120698, b/405120700): update to consider confirm / try again buttons
        requiresStrongBiometricAuthForSecureLockDevice

    /** Strong biometric modalities enrolled and enabled on the device. */
    val enrolledStrongBiometricModalities: Flow<BiometricModalities> =
        combine(
                biometricSettingsInteractor.isFingerprintAuthEnrolledAndEnabled,
                biometricSettingsInteractor.isFaceAuthEnrolledAndEnabled,
                fingerprintPropertyInteractor.sensorInfo,
                facePropertyInteractor.sensorInfo,
            ) { fingerprintEnrolledAndEnabled, faceEnrolledAndEnabled, fpSensorInfo, faceSensorInfo
                ->
                val hasStrongFingerprint =
                    fingerprintEnrolledAndEnabled && fpSensorInfo.strength == SensorStrength.STRONG
                val hasStrongFace =
                    faceEnrolledAndEnabled && faceSensorInfo?.strength == SensorStrength.STRONG

                if (hasStrongFingerprint && hasStrongFace) {
                    BiometricModalities(fpSensorInfo, faceSensorInfo)
                } else if (hasStrongFingerprint) {
                    BiometricModalities(fpSensorInfo, null)
                } else if (hasStrongFace) {
                    BiometricModalities(null, faceSensorInfo)
                } else {
                    BiometricModalities()
                }
            }
            .distinctUntilChanged()

    /** Called when biometric authentication is requested for secure lock device. */
    // TODO: call when secure lock device biometric auth is shown
    fun onBiometricAuthRequested() {
        _shouldShowBiometricAuth.value = true
        deviceEntryFaceAuthInteractor.onSecureLockDeviceBiometricAuthRequested()
    }
}
