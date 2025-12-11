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

package com.android.systemui.screencapture.record.largescreen.data.repository

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.user.data.repository.UserRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@SysUISingleton
class LargeScreenCaptureParametersRepository
@Inject
constructor(
    private val secureSettingsRepository: SecureSettingsRepository,
    userRepository: UserRepository,
) {

    /** [Flow] that observes the custom save location URI string for the current user */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customSaveLocationUriString: Flow<String> =
        userRepository.selectedUserInfo
            .flatMapLatest { userInfo ->
                secureSettingsRepository.stringSetting(CUSTOM_SAVE_LOCATION_URI_KEY_NAME).map {
                    it.orEmpty()
                }
            }
            .distinctUntilChanged()

    /** [Flow] that observes the custom save location active status for the current user */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isCustomSaveLocationActive: Flow<Boolean> =
        userRepository.selectedUserInfo
            .flatMapLatest {
                secureSettingsRepository.boolSetting(CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME)
            }
            .distinctUntilChanged()

    /**
     * Updates the custom save location URI string.
     * - If [uri] is null or corresponds to the default screenshots folder, the custom save location
     *   is deactivated (this means we will use the default folder).
     * - Otherwise, the custom save location is activated, and the [uri] string is saved to the data
     *   store.
     *
     * @param uri The [Uri] of the custom save location, or null to deactivate it.
     */
    suspend fun updateCustomSaveLocationUriString(uri: Uri) {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val path = documentId?.substringAfter("primary:")

        if (path == DEFAULT_SCREENSHOTS_FOLDER) {
            updateIsCustomSaveLocationActive(false)
        } else {
            val uriString = uri.toString()
            updateIsCustomSaveLocationActive(true)
            secureSettingsRepository.setString(CUSTOM_SAVE_LOCATION_URI_KEY_NAME, uriString)
        }
    }

    /**
     * Updates whether the custom save location is active. This is ran by System User (user 0),
     * performing action on behalf of the specific user.
     *
     * If active, it means we should be using the custom save location If inactive, it means we
     * should use the default save location
     *
     * @param isActive Whether the custom save location is active.
     */
    suspend fun updateIsCustomSaveLocationActive(isActive: Boolean) {
        secureSettingsRepository.setBoolean(CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME, isActive)
    }

    companion object {
        private const val CUSTOM_SAVE_LOCATION_URI_KEY_NAME = "custom_save_location_uri"
        private const val CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME = "custom_save_location_is_active"
        private val DEFAULT_SCREENSHOTS_FOLDER =
            Environment.DIRECTORY_PICTURES + File.separator + Environment.DIRECTORY_SCREENSHOTS
    }
}
