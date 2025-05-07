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

package com.android.systemui.qs.tiles.impl.modes.domain.interactor

import android.app.Flags
import android.content.Context
import android.os.UserHandle
import com.android.app.tracing.coroutines.flow.flowName
import com.android.settingslib.notification.modes.ZenIcon
import com.android.settingslib.notification.modes.ZenMode
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.flags.QsInCompose
import com.android.systemui.qs.tiles.ModesTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.policy.domain.interactor.ZenModeInteractor
import com.android.systemui.statusbar.policy.domain.model.ActiveZenModes
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ModesTileDataInteractor
@Inject
constructor(
    @ShadeDisplayAware val context: Context,
    val zenModeInteractor: ZenModeInteractor,
    @Background val bgDispatcher: CoroutineDispatcher,
) : QSTileDataInteractor<ModesTileModel> {

    override fun tileData(
        user: UserHandle,
        triggers: Flow<DataUpdateTrigger>,
    ): Flow<ModesTileModel> = tileData()

    /**
     * An adapted version of the base class' [tileData] method for use in an old-style tile.
     *
     * TODO(b/299909989): Remove after the transition.
     */
    fun tileData(): Flow<ModesTileModel> =
        if (Flags.modesUiTileReactivatesLast()) {
            zenModeInteractor.modes
                .map { modes -> buildTileData(modes) }
                .flowName("tileData")
                .flowOn(bgDispatcher)
                .distinctUntilChanged()
        } else {
            zenModeInteractor.activeModes
                .map { activeModes -> buildTileDataLegacy(activeModes) }
                .flowName("tileData")
                .flowOn(bgDispatcher)
                .distinctUntilChanged()
        }

    suspend fun getCurrentTileModel(): ModesTileModel =
        if (Flags.modesUiTileReactivatesLast()) {
            buildTileData(zenModeInteractor.modes.value)
        } else {
            buildTileDataLegacy(zenModeInteractor.getActiveModes())
        }

    private suspend fun buildTileData(modes: List<ZenMode>): ModesTileModel {
        val activeModesList =
            modes.filter { mode -> mode.isActive }.sortedWith(ZenMode.PRIORITIZING_COMPARATOR)
        val mainActiveMode = activeModesList.firstOrNull()

        val lastManualMode =
            modes
                .filter { mode ->
                    mode.isManualInvocationAllowed && mode.lastManualActivation != null
                }
                .sortedWith(
                    compareByDescending<ZenMode> { it.lastManualActivation }
                        .thenComparing(ZenMode.PRIORITIZING_COMPARATOR)
                )
                .firstOrNull()

        val icon =
            if (mainActiveMode != null) {
                zenModeInteractor.getModeIcon(mainActiveMode).toTileIcon()
            } else {
                if (QsInCompose.isEnabled && lastManualMode != null) {
                    zenModeInteractor.getModeIcon(lastManualMode).toTileIcon()
                } else {
                    getDefaultTileIcon()
                }
            }

        return ModesTileModel(
            isActivated = activeModesList.isNotEmpty(),
            activeModes = activeModesList.map { it.name },
            icon = icon,
            quickMode = lastManualMode ?: modes.single { it.isManualDnd },
        )
    }

    private fun buildTileDataLegacy(activeModes: ActiveZenModes): ModesTileModel {
        return ModesTileModel(
            isActivated = activeModes.isAnyActive(),
            activeModes = activeModes.modeNames,
            icon =
                if (activeModes.mainMode != null) activeModes.mainMode.icon.toTileIcon()
                else getDefaultTileIcon(),
            quickMode = null,
        )
    }

    private fun ZenIcon.toTileIcon(): Icon.Loaded {
        // ZenIconKey.resPackage is null if its resId is a system icon.
        return Icon.Loaded(
            drawable,
            contentDescription = null,
            res =
                if (key.resPackage == null) {
                    key.resId
                } else {
                    null
                },
        )
    }

    private fun getDefaultTileIcon() =
        Icon.Loaded(
            context.getDrawable(ModesTile.ICON_RES_ID)!!,
            contentDescription = null,
            res = ModesTile.ICON_RES_ID,
        )

    override fun availability(user: UserHandle): Flow<Boolean> = flowOf(true)
}
