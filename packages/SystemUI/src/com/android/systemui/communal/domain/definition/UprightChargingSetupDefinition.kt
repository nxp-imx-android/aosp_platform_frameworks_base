/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.systemui.communal.domain.definition

import com.android.systemui.communal.data.repository.ContextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import com.android.systemui.communal.domain.interactor.UprightChargingInteractor
import com.android.systemui.communal.domain.preconditions.CommonSetupPreconditions
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.util.kotlin.FlowDumper
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
class UprightChargingSetupDefinition
@Inject
constructor(
    private val commonConditions: CommonSetupPreconditions,
    private val uprightChargingInteractor: UprightChargingInteractor,
    private val contextualSetupRepo: ContextualSetupRepository,
    private val flowDumper: FlowDumper,
    override val target: SetupTarget,
) : ContextualSetupDefinition {

    override val id = FLOW_ID

    // Power Optimization:
    // We strictly gate the expensive trigger (accelerometer) behind the cheap preconditions.
    override val isReady: Flow<Boolean> =
        contextualSetupRepo
            .setupState(id)
            .combine(commonConditions.allMet) { state, commonMet ->
                state is SetupState.NotStarted && commonMet
            }
            .distinctUntilChanged()
            .flatMapLatest { preconditionsMet ->
                if (preconditionsMet) {
                    // Preconditions met: Safe to subscribe to expensive hardware trigger
                    uprightChargingInteractor.isTriggered
                } else {
                    // Preconditions failed: Ensure sensor is OFF
                    flowOf(false)
                }
            }
            .let { with(flowDumper) { it.dumpWhileCollecting("isReady") } }

    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println("UprightChargingSetupDefinition:")
        pw.println("  id: $id")
        pw.println("  target: $target")
        flowDumper.dump(pw, args)
    }

    companion object {
        const val FLOW_ID = "upright_charging_mode"
    }
}
