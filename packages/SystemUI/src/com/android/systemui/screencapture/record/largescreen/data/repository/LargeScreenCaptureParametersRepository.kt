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

import com.android.systemui.common.data.datastore.DataStoreWrapper
import com.android.systemui.common.data.datastore.DataStoreWrapperFactory
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.user.data.repository.UserRepository
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
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

    suspend fun updateCustomSaveLocationUriString(uriString: String) =
        withContext(backgroundDispatcher) {
            val currentDataStore = dataStore.first()

            currentDataStore.edit { preferencesMap ->
                val keyName = CUSTOM_SAVE_LOCATION_URI_KEY_NAME
                val currentUri = preferencesMap[keyName].orEmpty()

                if (currentUri != uriString) {
                    // UriString is empty when location is set to default
                    if (uriString.isEmpty()) {
                        preferencesMap.remove(keyName)
                    } else {
                        preferencesMap[keyName] = uriString
                    }
                }
            }
        }

    companion object {
        private const val CUSTOM_SAVE_LOCATION_URI_KEY_NAME = "custom_save_location_uri"
        private const val DATA_STORE_FILE_NAME = "screen_capture_settings.preferences_pb"
    }
}
