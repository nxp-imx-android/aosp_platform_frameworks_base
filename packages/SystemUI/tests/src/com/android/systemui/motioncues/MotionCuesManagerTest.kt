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

package com.android.systemui.motioncues

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.app.motioncues.MotionCuesSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.CommandQueue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isA
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class MotionCuesManagerTest : SysuiTestCase() {

    @Mock
    private lateinit var context: Context
    @Mock
    private lateinit var commandQueue: CommandQueue
    @Mock
    private lateinit var motionCuesUi: MotionCuesUi

    @Captor
    private lateinit var serviceConnectionCaptor: ArgumentCaptor<ServiceConnection>

    private lateinit var manager: MotionCuesManager

    private val componentName = ComponentName("com.example", "com.example.Service")
    private val userId = 1
    private val settings = MotionCuesSettings.Builder().build()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        manager = MotionCuesManager(context, commandQueue, motionCuesUi)
    }

    @Test
    fun testStart() {
        manager.start()
        verify(commandQueue).addCallback(manager)
    }

    @Test
    fun startMotionCuesSession_whenAlreadyStarted_logsWarning() {
        whenever(motionCuesUi.isStarted).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)
        verify(context, never()).bindService(any(Intent::class.java), any(ServiceConnection::class.java), anyInt())
    }

    @Test
    fun startMotionCuesSession_bindServiceFails_endsSession() {
        whenever(context.bindService(any(Intent::class.java), any(ServiceConnection::class.java), anyInt())).thenReturn(false)

        manager.startMotionCuesSession(componentName, userId, settings)

        verify(motionCuesUi).stop()
    }

    @Test
    fun startMotionCuesSession_bindServiceSucceeds_startsUi() {
        whenever(motionCuesUi.isStarted).thenReturn(false)
        whenever(context.bindService(any(Intent::class.java), serviceConnectionCaptor.capture(), eq(Context.BIND_AUTO_CREATE))).thenReturn(true)

        manager.startMotionCuesSession(componentName, userId, settings)

        verify(motionCuesUi).start(settings, userId, componentName.packageName)
    }

    @Test
    fun endMotionCuesSession_unbindsAndStopsUi() {
        // Start a session first to ensure there's something to end
        whenever(motionCuesUi.isStarted).thenReturn(false)
        whenever(context.bindService(any(Intent::class.java), serviceConnectionCaptor.capture(), eq(Context.BIND_AUTO_CREATE))).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        manager.endMotionCuesSession()

        verify(context).unbindService(serviceConnectionCaptor.value)
        verify(motionCuesUi).stop()
    }

    @Test
    fun onServiceDisconnected_resetsSession() {
        whenever(context.bindService(any(Intent::class.java), serviceConnectionCaptor.capture(), anyInt())).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        serviceConnectionCaptor.value.onServiceDisconnected(componentName)

        verify(motionCuesUi).stop()
    }

    @Test
    fun onBindingDied_endsSession() {
        whenever(context.bindService(any(Intent::class.java), serviceConnectionCaptor.capture(), anyInt())).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        serviceConnectionCaptor.value.onBindingDied(componentName)
        verify(context).unbindService(serviceConnectionCaptor.value)
        verify(motionCuesUi).stop()
    }

    @Test
    fun onNullBinding_endsSession() {
        whenever(context.bindService(any(Intent::class.java), serviceConnectionCaptor.capture(), anyInt())).thenReturn(true)
        manager.startMotionCuesSession(componentName, userId, settings)

        serviceConnectionCaptor.value.onNullBinding(componentName)
        verify(context).unbindService(serviceConnectionCaptor.value)
        verify(motionCuesUi).stop()
    }
}
