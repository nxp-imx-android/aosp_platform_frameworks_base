/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.disableflags.dagger

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Default
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.statusbar.disableflags.data.repository.DisableFlagsRepository
import com.android.systemui.statusbar.disableflags.data.repository.DisableFlagsRepositoryImpl
import com.android.systemui.statusbar.disableflags.domain.interactor.DisableFlagsInteractor
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope

/** Provides information related to disable flags. */
@Module
object DisableFlagsModule {
    @Provides
    @SysUISingleton
    @Default // Dagger doesn't support providing AssistedInject classes without a qualifier
    fun bindDisableFlagsInteractor(
        factory: DisableFlagsInteractor.Factory,
        @Default repository: DisableFlagsRepository,
    ): DisableFlagsInteractor {
        return factory.create(repository)
    }

    @Provides
    @SysUISingleton
    @Default // Dagger doesn't support providing AssistedInject classes without a qualifier
    fun bindDisableFlagsRepo(
        factory: DisableFlagsRepositoryImpl.Factory,
        @DisplayId displayId: Int,
        @Application scope: CoroutineScope,
    ): DisableFlagsRepository {
        return factory.create(displayId, scope)
    }
}
