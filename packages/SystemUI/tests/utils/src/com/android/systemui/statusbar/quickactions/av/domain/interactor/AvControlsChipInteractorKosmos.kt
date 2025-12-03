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

package com.android.systemui.statusbar.quickactions.av.domain.interactor

import android.app.AppOpsManager
import android.app.activityManagerInterface
import android.content.packageManager
import android.content.testableContext
import android.permission.PermissionManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.shade.data.repository.privacyChipRepository
import com.android.systemui.statusbar.data.repository.statusBarModeRepository
import com.android.systemui.statusbar.notification.row.icon.appIconProvider
import com.android.systemui.statusbar.policy.FakeIndividualSensorPrivacyController
import com.android.systemui.statusbar.quickactions.av.AvControlsChipModule
import com.android.systemui.statusbar.quickactions.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.quickactions.av.shared.model.SensorActivityModel
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.Mockito.mock

val Kosmos.avControlsChipInteractor: AvControlsChipInteractor by
    Kosmos.Fixture {
        AvControlsChipModule()
            .provideAvControlsChipInteractor(
                avControlsChipSupported = Provider { avControlsChipInteractorImpl },
                avControlsChipNotSupported = Provider { noOpAvControlsChipInteractor },
            )
    }

val Kosmos.sensorPrivacyController: FakeIndividualSensorPrivacyController by
    Kosmos.Fixture { FakeIndividualSensorPrivacyController() }

val Kosmos.appOpsManagerMock: AppOpsManager by Kosmos.Fixture { mock(AppOpsManager::class.java) }

val Kosmos.avControlsChipInteractorImpl: AvControlsChipInteractorImpl by
    Kosmos.Fixture {
        AvControlsChipInteractorImpl(
            backgroundScope = backgroundScope,
            privacyChipRepository = privacyChipRepository,
            bgDispatcher = testDispatcher,
            statusBarModeRepositoryStore = statusBarModeRepository,
            sensorPrivacyController = sensorPrivacyController,
            permissionManager = PermissionManager(testableContext),
            packageManager = packageManager,
            selectedUserInteractor = selectedUserInteractor,
            appIconProvider = appIconProvider,
            appOpsManager = appOpsManagerMock,
            activityManager = activityManagerInterface,
        )
    }

val Kosmos.noOpAvControlsChipInteractor: NoOpAvControlsChipInteractor by
    Kosmos.Fixture { NoOpAvControlsChipInteractor() }

val Kosmos.fakeAvControlsChipInteractor: FakeAvControlsChipInteractor by
    Kosmos.Fixture { FakeAvControlsChipInteractor() }

class FakeAvControlsChipInteractor : AvControlsChipInteractor {
    override val model: MutableStateFlow<AvControlsChipModel> =
        MutableStateFlow(AvControlsChipModel(sensorActivityModel = SensorActivityModel.Inactive))
    override val isShowingAvChip: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val _cameraBlocked = MutableStateFlow(false)
    override val cameraBlocked = _cameraBlocked

    override fun setCameraBlocked(value: Boolean) {
        _cameraBlocked.value = value
    }

    private val _microphoneBlocked = MutableStateFlow(false)
    override val microphoneBlocked = _microphoneBlocked

    override fun setMicrophoneBlocked(value: Boolean) {
        _microphoneBlocked.value = value
    }

    override fun closeApp(packageName: String) {}
}
