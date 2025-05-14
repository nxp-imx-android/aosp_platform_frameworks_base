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

package com.android.systemui.topwindoweffects.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyevent.domain.interactor.KeyEventInteractor
import com.android.systemui.topwindoweffects.data.repository.SqueezeEffectRepository
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

@SysUISingleton
class SqueezeEffectInteractor
@Inject
constructor(
    private val squeezeEffectRepository: SqueezeEffectRepository,
    keyEventInteractor: KeyEventInteractor,
    @Background private val coroutineContext: CoroutineContext,
) {
    val isSqueezeEffectEnabled = squeezeEffectRepository.isSqueezeEffectEnabled
    val isSqueezeEffectHapticEnabled = squeezeEffectRepository.isSqueezeEffectHapticEnabled

    val isPowerButtonDownAsSingleKeyGesture: Flow<Boolean> =
        combine(
                keyEventInteractor.isPowerButtonDown,
                squeezeEffectRepository.isPowerButtonDownInKeyCombination,
                ::Pair,
            )
            .mapLatestConflated { (down, isInCombination) -> down && !isInCombination }
            .flowOn(coroutineContext)
            .distinctUntilChanged()

    suspend fun getInvocationEffectInitialDelayMs() =
        squeezeEffectRepository.getInvocationEffectInitialDelayMs()

    suspend fun getInvocationEffectInwardsAnimationDurationMs() =
        squeezeEffectRepository.getInvocationEffectInwardsAnimationDurationMs()
}
