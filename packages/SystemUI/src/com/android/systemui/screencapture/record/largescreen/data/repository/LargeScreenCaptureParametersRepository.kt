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
import com.android.systemui.common.data.datastore.DataStoreWrapper
import com.android.systemui.common.data.datastore.DataStoreWrapperFactory
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.user.data.repository.UserRepository
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.text.substringAfter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

@SysUISingleton
class LargeScreenCaptureParametersRepository
@Inject
constructor(
    private val dataStoreWrapperFactory: DataStoreWrapperFactory,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Background private val backgroundContext: CoroutineContext,
    @Background private val backgroundScope: CoroutineScope,
    private val userRepository: UserRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dataStore: Flow<DataStoreWrapper> =
        userRepository.selectedUserInfo
            .map { it.id }
            .distinctUntilChanged()
            .transformLatest { userId ->
                withContext(backgroundContext) {
                    emit(
                        dataStoreWrapperFactory.create(
                            dataStoreFileName = DATA_STORE_FILE_NAME,
                            userId = userId,
                            scope = this@withContext,
                        )
                    )
                    awaitCancellation()
                }
            }
            .stateIn(scope = backgroundScope, started = SharingStarted.Lazily, initialValue = null)
            .filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    val customSaveLocationUriString: Flow<String> =
        dataStore
            .flatMapLatest { it.data }
            .map { preferencesMap -> preferencesMap[CUSTOM_SAVE_LOCATION_URI_KEY_NAME].orEmpty() }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val isCustomSaveLocationActive: Flow<Boolean> =
        dataStore
            .flatMapLatest { it.data }
            .map { preferencesMap ->
                preferencesMap[CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME].toBoolean()
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
    suspend fun updateCustomSaveLocationUriString(uri: Uri?) {
        if (uri == null) {
            updateIsCustomSaveLocationActive(false)
            return
        }

        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val path = documentId?.substringAfter("primary:")

        if (path == DEFAULT_SCREENSHOTS_FOLDER) {
            updateIsCustomSaveLocationActive(false)
        } else {
            val uriString = uri.toString()
            updateIsCustomSaveLocationActive(true)
            withContext(backgroundDispatcher) {
                val currentDataStore = dataStore.first()

                currentDataStore.edit { preferencesMap ->
                    val keyName = CUSTOM_SAVE_LOCATION_URI_KEY_NAME

                    val currentUri = preferencesMap[keyName].orEmpty()

                    if (currentUri != uriString) {
                        preferencesMap[keyName] = uriString
                    }
                }
            }
        }
    }

    /**
     * Updates whether the custom save location is active.
     *
     * If active, it means we should be using the custom save location If inactive, it means we
     * should use the default save location
     *
     * @param isActive Whether the custom save location is active.
     */
    suspend fun updateIsCustomSaveLocationActive(isActive: Boolean) =
        withContext(backgroundDispatcher) {
            val currentDataStore = dataStore.first()

            currentDataStore.edit { preferencesMap ->
                val keyName = CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME
                val currentIsActive = preferencesMap[keyName].toBoolean()

                if (currentIsActive != isActive) {
                    preferencesMap[keyName] = isActive.toString()
                }
            }
        }

    companion object {
        private const val CUSTOM_SAVE_LOCATION_URI_KEY_NAME = "custom_save_location_uri"
        private const val CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME = "custom_save_location_is_active"
        private const val DATA_STORE_FILE_NAME = "screen_capture_settings.preferences_pb"
        private val DEFAULT_SCREENSHOTS_FOLDER =
            Environment.DIRECTORY_PICTURES + File.separator + Environment.DIRECTORY_SCREENSHOTS
    }
}
