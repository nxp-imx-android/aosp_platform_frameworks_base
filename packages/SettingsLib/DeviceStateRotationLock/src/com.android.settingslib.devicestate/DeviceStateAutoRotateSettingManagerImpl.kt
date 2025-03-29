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
package com.android.settingslib.devicestate

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED
import android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED
import android.util.IndentingPrintWriter
import android.util.Log
import android.util.SparseIntArray
import com.android.internal.R
import com.android.settingslib.devicestate.DeviceStateAutoRotateSettingManager.DeviceStateAutoRotateSettingListener
import com.android.window.flags.Flags
import java.io.PrintWriter
import java.util.concurrent.Executor

/**
 * Implementation of [DeviceStateAutoRotateSettingManager]. This implementation is a part of
 * refactoring, it should be used when [Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR]
 * is enabled.
 */
class DeviceStateAutoRotateSettingManagerImpl(
    context: Context,
    backgroundExecutor: Executor,
    private val secureSettings: SecureSettings,
    private val mainHandler: Handler,
    private val posturesHelper: PosturesHelper,
) : DeviceStateAutoRotateSettingManager {
    // TODO: b/397928958 rename the fields and apis from rotationLock to autoRotate.

    private val settingListeners: MutableList<DeviceStateAutoRotateSettingListener> =
        mutableListOf()
    private val fallbackPostureMap = SparseIntArray()
    private val defaultDeviceStateAutoRotateSetting = SparseIntArray()
    private val settableDeviceState: MutableList<SettableDeviceState> = mutableListOf()

    private val autoRotateSettingValue: String
        get() = secureSettings.getStringForUser(DEVICE_STATE_ROTATION_LOCK, UserHandle.USER_CURRENT)

    init {
        loadAutoRotateDeviceStates(context)
        val contentObserver =
            object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) = notifyListeners()
            }
        backgroundExecutor.execute {
            secureSettings.registerContentObserver(
                DEVICE_STATE_ROTATION_LOCK, false, contentObserver, UserHandle.USER_CURRENT
            )
        }
    }

    override fun registerListener(settingListener: DeviceStateAutoRotateSettingListener) {
        settingListeners.add(settingListener)
    }

    override fun unregisterListener(settingListener: DeviceStateAutoRotateSettingListener) {
        if (!settingListeners.remove(settingListener)) {
            Log.w(TAG, "Attempting to unregister a listener hadn't been registered")
        }
    }

    @Settings.Secure.DeviceStateRotationLockSetting
    override fun getRotationLockSetting(deviceState: Int): Int? {
        val devicePosture = posturesHelper.deviceStateToPosture(deviceState)
        val deviceStateAutoRotateSetting = getRotationLockSetting()
        val autoRotateSettingValue =
            extractSettingForDevicePosture(devicePosture, deviceStateAutoRotateSetting)

        // If the setting is ignored for this posture, check the fallback posture.
        if (autoRotateSettingValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
            val fallbackPosture =
                fallbackPostureMap.get(devicePosture, DEVICE_STATE_ROTATION_LOCK_IGNORED)
            return extractSettingForDevicePosture(fallbackPosture, deviceStateAutoRotateSetting)
        }

        return autoRotateSettingValue
    }

    override fun getRotationLockSetting(): SparseIntArray? {
        val serializedSetting = autoRotateSettingValue
        if (serializedSetting.isEmpty()) return null
        return try {
            serializedSetting
                .split(SEPARATOR_REGEX)
                .hasEvenSize()
                .chunked(2)
                .map(::parsePostureSettingPair)
                .toSparseIntArray()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Invalid format in serializedSetting=$serializedSetting: ${e.message}"
            )
            return null
        }
    }

    override fun isRotationLocked(deviceState: Int) =
        getRotationLockSetting(deviceState)?.let { it == DEVICE_STATE_ROTATION_LOCK_LOCKED }

    override fun isRotationLockedForAllStates(): Boolean? =
        getRotationLockSetting()?.allSettingValuesLocked()

    override fun getSettableDeviceStates(): List<SettableDeviceState> = settableDeviceState

    override fun updateSetting(deviceState: Int, autoRotate: Boolean) {
        // TODO: b/350946537 - Create IPC to update the setting, and call it here.
        throw UnsupportedOperationException("API updateSetting is not implemented yet")
    }

    override fun dump(writer: PrintWriter, args: Array<out String>?) {
        val indentingWriter = IndentingPrintWriter(writer)
        indentingWriter.println("DeviceStateAutoRotateSettingManagerImpl")
        indentingWriter.increaseIndent()
        indentingWriter.println("fallbackPostureMap: $fallbackPostureMap")
        indentingWriter.println("settableDeviceState: $settableDeviceState")
        indentingWriter.decreaseIndent()
    }

    override fun getDefaultRotationLockSetting() = defaultDeviceStateAutoRotateSetting.clone()

    private fun notifyListeners() =
        settingListeners.forEach { listener -> listener.onSettingsChanged() }

    /**
     * Loads the [R.array.config_perDeviceStateRotationLockDefaults] array and populates the
     * [fallbackPostureMap], [settableDeviceState], and [defaultDeviceStateAutoRotateSetting]
     * fields.
     */
    private fun loadAutoRotateDeviceStates(context: Context) {
        val perDeviceStateAutoRotateDefaults =
            context.resources.getStringArray(R.array.config_perDeviceStateRotationLockDefaults)
        for (entry in perDeviceStateAutoRotateDefaults) {
            entry.parsePostureEntry()?.let { (posture, autoRotate, fallbackPosture) ->
                posturesHelper.postureToDeviceState(posture).also {
                    if (it == null) {
                        Log.wtf(TAG, "No matching device state for posture: $posture")
                        return@also
                    }
                    settableDeviceState.add(
                        SettableDeviceState(
                            it,
                            autoRotate != DEVICE_STATE_ROTATION_LOCK_IGNORED
                        )
                    )
                }
                if (autoRotate == DEVICE_STATE_ROTATION_LOCK_IGNORED && fallbackPosture != null) {
                    fallbackPostureMap.put(posture, fallbackPosture)
                } else if (autoRotate == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                    Log.w(TAG, "Auto rotate setting is IGNORED, but no fallback-posture defined")
                }
                defaultDeviceStateAutoRotateSetting.put(posture, autoRotate)
            }
        }
    }

    private fun List<String>.hasEvenSize(): List<String> {
        if (this.size % 2 != 0) {
            throw IllegalStateException("Odd number of elements in the list")
        }
        return this
    }

    private fun parsePostureSettingPair(settingPair: List<String>): Pair<Int, Int> {
        return settingPair.let { (keyStr, valueStr) ->
            val key = keyStr.toIntOrNull()
            val value = valueStr.toIntOrNull()
            if (key != null && value != null && value in 0..2) {
                key to value
            } else {
                throw IllegalStateException("Invalid key or value in pair: $keyStr, $valueStr")
            }
        }
    }

    private fun extractSettingForDevicePosture(
        devicePosture: Int,
        deviceStateAutoRotateSetting: SparseIntArray?
    ): Int? =
        deviceStateAutoRotateSetting?.let {
            it[devicePosture] ?: DEVICE_STATE_ROTATION_LOCK_IGNORED
        }

    private fun String.parsePostureEntry(): Triple<Int, Int, Int?>? {
        val values = split(SEPARATOR_REGEX)
        if (values.size !in 2..3) { // It should contain 2 or 3 values.
            Log.wtf(TAG, "Invalid number of values in entry: '$this'")
            return null
        }
        return try {
            val posture = values[0].toInt()
            val rotationLockSetting = values[1].toInt()
            val fallbackPosture = if (values.size == 3) values[2].toIntOrNull() else null
            Triple(posture, rotationLockSetting, fallbackPosture)
        } catch (e: NumberFormatException) {
            Log.wtf(TAG, "Invalid number format in '$this': ${e.message}")
            null
        }
    }

    private fun List<Pair<Int, Int>>.toSparseIntArray(): SparseIntArray {
        val sparseArray = SparseIntArray()
        forEach { (key, value) ->
            sparseArray.put(key, value)
        }
        return sparseArray
    }

    private fun SparseIntArray.allSettingValuesLocked(): Boolean {
        for (i in 0 until size()) {
            if (valueAt(i) != DEVICE_STATE_ROTATION_LOCK_LOCKED) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val TAG = "DSAutoRotateMngr"
        private const val SEPARATOR_REGEX = ":"
    }
}
