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

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Handles business logic for secure lock device. */
@SysUISingleton
class SecureLockDeviceInteractor
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    secureLockDeviceRepository: SecureLockDeviceRepository,
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

    /**
     * Whether the device should listen for biometric auth while secure lock device is enabled. The
     * device should stop listening when pending authentication, when authenticated, or when the
     * biometric auth screen is exited without authenticating.
     */
    val shouldListenForBiometricAuth: Flow<Boolean> =
        // TODO (b/405120698, b/405120700): update to consider confirm / try again buttons
        requiresStrongBiometricAuthForSecureLockDevice
}
