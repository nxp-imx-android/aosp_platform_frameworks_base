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
package com.android.systemui.securelockdevice.dagger

import android.security.authenticationpolicy.AuthenticationPolicyManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepository
import com.android.systemui.securelockdevice.data.repository.SecureLockDeviceRepositoryImpl
import com.android.systemui.securelockdevice.domain.interactor.SecureLockDeviceInteractor
import dagger.Module
import dagger.Provides
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope

@Module
interface SecureLockDeviceModule {

    companion object {
        @Provides
        @SysUISingleton
        fun providesSecureLockDeviceRepository(
            @Background backgroundExecutor: Executor,
            authenticationPolicyManager: AuthenticationPolicyManager?,
        ): SecureLockDeviceRepository {
            return SecureLockDeviceRepositoryImpl(
                backgroundExecutor = backgroundExecutor,
                authenticationPolicyManager = authenticationPolicyManager,
            )
        }

        @Provides
        @SysUISingleton
        fun providesSecureLockDeviceInteractor(
            @Application applicationScope: CoroutineScope,
            secureLockDeviceRepository: SecureLockDeviceRepository,
        ): SecureLockDeviceInteractor {
            return SecureLockDeviceInteractor(
                applicationScope = applicationScope,
                secureLockDeviceRepository = secureLockDeviceRepository,
            )
        }
    }
}
