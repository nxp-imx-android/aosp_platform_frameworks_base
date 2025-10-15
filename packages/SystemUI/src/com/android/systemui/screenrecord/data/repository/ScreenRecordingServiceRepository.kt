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

package com.android.systemui.screenrecord.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.media.projection.StopReason
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.view.Display
import androidx.annotation.WorkerThread
import com.android.app.tracing.coroutines.flow.asStateFlowTraced
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.app.tracing.coroutines.launchInTraced
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screenrecord.ScreenRecordUxController
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.service.IScreenRecordingService
import com.android.systemui.screenrecord.service.IScreenRecordingServiceCallback
import com.android.systemui.screenrecord.service.ScreenRecordingService
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.kotlin.pairwiseBy
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class ScreenRecordingServiceRepository
@Inject
constructor(
    private val context: Context,
    @Background coroutineScope: CoroutineScope,
    private val userRepository: UserRepository,
    private val screenRecordUxController: ScreenRecordUxController,
) : ScreenRecordingStartStopRepository {
    private val serviceCallback = ServiceCallback()
    private val isServiceBound = MutableStateFlow(false)
    private val service: Flow<RecordingService?> =
        isServiceBound
            .flatMapLatest { currentIsServiceBound ->
                if (currentIsServiceBound) bindService() else flowOf(null)
            }
            .pairwiseBy { old: IScreenRecordingService?, new: IScreenRecordingService? ->
                old?.setCallback(null)
                if (new == null) {
                    // The service died. Update isServiceBound to match its state
                    isServiceBound.value = false
                } else {
                    new.setCallback(serviceCallback)
                }
                new
            }
            .map { it?.let(::RecordingService) }
            .stateInTraced(
                "ScreenRecordingServiceInteractor#service",
                coroutineScope,
                SharingStarted.WhileSubscribed(),
                null,
            )

    private val _status = MutableStateFlow<Status>(Status.Initial)
    val status: StateFlow<Status> =
        _status.asStateFlowTraced("ScreenRecordingServiceInteractor#status")

    init {
        combine(status.onEach { isServiceBound.value = it is Status.Started }, service) {
                currentStatus,
                currentService ->
                RecordingContext(status = currentStatus, service = currentService)
            }
            .onEach { recordingContext ->
                with(recordingContext) {
                    if (service != null) {
                        when (status) {
                            is Status.Started -> {
                                val parameters = status.parameters
                                if (service.isRecording) {
                                    service.updateParameters(parameters)
                                } else {
                                    service.startRecording(parameters)
                                    screenRecordUxController.updateState(true)
                                }
                            }

                            is Status.Stopped -> {
                                service.stopRecording(status.reason)
                                screenRecordUxController.updateState(false)
                            }

                            is Status.Initial -> {
                                /* do nothing */
                            }
                        }
                    }
                }
            }
            .catch { Log.e("ScreenRecordingServiceInteractor", "Couldn't reach the service", it) }
            .launchInTraced("ScreenRecordingServiceInteractor#_status", coroutineScope)
    }

    override fun startRecording(parameters: ScreenRecordingParameters) {
        require(parameters.displayId != Display.INVALID_DISPLAY) { "Provide a valid displayId" }
        _status.update { currentStatus ->
            if (currentStatus is Status.Started) {
                currentStatus
            } else {
                Status.Started(parameters)
            }
        }
    }

    override fun stopRecording(@StopReason reason: Int) {
        _status.update { currentStatus ->
            if (currentStatus is Status.Stopped) {
                currentStatus
            } else {
                Status.Stopped(reason)
            }
        }
    }

    /** Updates shouldShowTaps if there is an ongoing recording */
    fun updateAudioSource(audioSource: ScreenRecordingAudioSource) {
        updateParameters { copy(audioSource = audioSource) }
    }

    /** Updates shouldShowTaps if there is an ongoing recording */
    fun updateShouldShowTaps(shouldShowTaps: Boolean) {
        updateParameters { copy(shouldShowTaps = shouldShowTaps) }
    }

    private fun updateParameters(
        update: ScreenRecordingParameters.() -> ScreenRecordingParameters
    ) {
        _status.update { currentStatus ->
            if (currentStatus is Status.Started) {
                currentStatus.copy(parameters = currentStatus.parameters.update())
            } else {
                currentStatus
            }
        }
    }

    @WorkerThread
    private fun bindService(): Flow<IScreenRecordingService?> = conflatedCallbackFlow {
        val userHandle = userRepository.selectedUser.value.userInfo.userHandle
        val userContext = context.createContextAsUser(userHandle, 0)
        val newIntent = Intent(userContext, ScreenRecordingService::class.java)
        userContext.bindService(newIntent, Connection { trySend(it) }, Context.BIND_AUTO_CREATE)
        awaitClose {
            /*
            Don't unbind the service because it stops self when done with the
            recording. In this case null service will be received in and
            isServiceBound updated later in the chain.
            */
        }
    }

    private inner class Connection(
        private val onServiceReceived: (IScreenRecordingService?) -> Unit
    ) : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            onServiceReceived(IScreenRecordingService.Stub.asInterface(service))
        }

        override fun onServiceDisconnected(name: ComponentName) {
            onServiceReceived(null)
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceReceived(null)
        }
    }

    private inner class ServiceCallback : IScreenRecordingServiceCallback.Stub() {

        override fun onRecordingStarted() {}

        override fun onRecordingInterrupted(userId: Int, reason: Int) {
            stopRecording(reason)
        }

        override fun onRecordingSaved(recordingUri: Uri?, thumbnail: Icon?) {}
    }

    private data class RecordingService(private val service: IScreenRecordingService?) {

        var isRecording: Boolean = false
            private set

        fun updateParameters(parameters: ScreenRecordingParameters) {
            require(isRecording)
            service?.updateParameters(parameters)
        }

        fun startRecording(parameters: ScreenRecordingParameters) {
            service?.startRecording(parameters)
            isRecording = true
        }

        fun stopRecording(reason: Int) {
            service?.stopRecording(reason)
            isRecording = false
        }
    }

    private data class RecordingContext(val status: Status, val service: RecordingService?)
}

sealed interface Status {

    data object Initial : Status

    data class Started(val parameters: ScreenRecordingParameters) : Status

    data class Stopped(val reason: Int) : Status
}
