/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles.impl.cell.domain.interactor

import android.annotation.StringRes
import android.content.Context
import android.os.UserHandle
import android.text.Html
import com.android.settingslib.graph.SignalDrawable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.tiles.base.domain.interactor.QSTileDataInteractor
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileIcon
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.ui.MobileContextProvider
import com.android.systemui.statusbar.pipeline.mobile.NewSatelliteIcon
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalCoroutinesApi::class)
class MobileDataTileDataInteractor
@Inject
constructor(
    @Application private val context: Context,
    private val mobileIconsInteractor: MobileIconsInteractor,
    mobileContextProvider: MobileContextProvider,
) : QSTileDataInteractor<MobileDataTileModel> {
    private val mobileDataLabel: String =
        context.getString(R.string.quick_settings_cellular_detail_title)

    override fun tileData(
        user: UserHandle,
        triggers: Flow<DataUpdateTrigger>,
    ): Flow<MobileDataTileModel> = tileData()

    private val mobileDataContentName: Flow<CharSequence?> =
        mobileIconsInteractor.activeDataIconInteractor.flatMapLatest {
            if (it == null) {
                flowOf(null)
            } else {
                if (NewSatelliteIcon.isEnabled) {
                    combine(it.isRoaming, it.networkTypeIconGroup, it.isNonTerrestrial) {
                        isRoaming,
                        networkTypeIconGroup,
                        isNonTerrestrial ->
                        val mobileContext =
                            mobileContextProvider.getMobileContextForSub(it.subscriptionId, context)
                        val cd = loadString(networkTypeIconGroup.contentDescription, mobileContext)
                        if (isNonTerrestrial) {
                            mobileContext.getString(R.string.qs_tile_satellite_label)
                        } else if (isRoaming) {
                            val roaming = mobileContext.getString(R.string.data_connection_roaming)
                            if (cd != null) {
                                mobileContext.getString(
                                    R.string.mobile_data_text_format,
                                    roaming,
                                    cd,
                                )
                            } else {
                                roaming
                            }
                        } else {
                            cd
                        }
                    }
                } else {
                    combine(it.isRoaming, it.networkTypeIconGroup) { isRoaming, networkTypeIconGroup
                        ->
                        val mobileContext =
                            mobileContextProvider.getMobileContextForSub(it.subscriptionId, context)
                        val cd = loadString(networkTypeIconGroup.contentDescription, mobileContext)
                        if (isRoaming) {
                            val roaming = mobileContext.getString(R.string.data_connection_roaming)
                            if (cd != null) {
                                mobileContext.getString(
                                    R.string.mobile_data_text_format,
                                    roaming,
                                    cd,
                                )
                            } else {
                                roaming
                            }
                        } else {
                            cd
                        }
                    }
                }
            }
        }

    private val mobileDescriptionFlow: Flow<CharSequence?> =
        mobileIconsInteractor.activeDataIconInteractor.flatMapLatest { it ->
            it?.isDataConnected?.flatMapLatest { isConnected ->
                if (!isConnected) {
                    flowOf(null)
                } else {
                    if (NewSatelliteIcon.isEnabled) {
                        combine(it.networkName, mobileDataContentName) {
                            networkNameModel,
                            dataContentDescription ->
                            mobileDataContentConcat(networkNameModel.name, dataContentDescription)
                        }
                    } else {
                        combine(it.networkName, it.signalLevelIcon, mobileDataContentName) {
                                networkNameModel,
                                signalIcon,
                                dataContentDescription ->
                                Triple(networkNameModel, signalIcon, dataContentDescription)
                            }
                            .mapLatestConflated {
                                (networkNameModel, signalIcon, dataContentDescription) ->
                                when (signalIcon) {
                                    is SignalIconModel.CellularTypeIconModel -> {
                                        mobileDataContentConcat(
                                            networkNameModel.name,
                                            dataContentDescription,
                                        )
                                    }

                                    is SignalIconModel.Satellite -> {
                                        val satelliteDescription =
                                            signalIcon.icon.contentDescription
                                                ?.loadContentDescription(context)
                                        if (satelliteDescription.isNullOrBlank()) {
                                            null
                                        } else {
                                            satelliteDescription
                                        }
                                    }
                                }
                            }
                    }
                }
            } ?: flowOf(null)
        }

    private fun mobileDataContentConcat(
        networkName: String?,
        dataContentDescription: CharSequence?,
    ): CharSequence {
        if (dataContentDescription == null) {
            return networkName ?: ""
        }
        if (networkName == null) {
            return Html.fromHtml(dataContentDescription.toString(), 0)
        }

        return Html.fromHtml(
            context.getString(
                R.string.mobile_carrier_text_format,
                networkName,
                dataContentDescription,
            ),
            0,
        )
    }

    private fun loadString(@StringRes resId: Int, context: Context): CharSequence? =
        if (resId != 0) {
            context.getString(resId)
        } else {
            null
        }

    fun tileData(): Flow<MobileDataTileModel> =
        mobileIconsInteractor.activeDataIconInteractor.flatMapLatest {
            if (it == null) {
                    flowOf(
                        MobileDataTileModel(
                            isSimActive = false,
                            isEnabled = false,
                            icon =
                                MobileDataTileIcon.ResourceIcon(
                                    Icon.Resource(
                                        com.android.settingslib.R.drawable.ic_mobile_4_4_bar,
                                        ContentDescription.Loaded(mobileDataLabel),
                                    )
                                ),
                        )
                    )
                } else {
                    combine(it.isDataEnabled, it.signalLevelIcon, mobileDescriptionFlow) {
                        isDataEnabled,
                        signalLevelIcon,
                        description ->
                        val icon =
                            if (isDataEnabled) {
                                when (signalLevelIcon) {
                                    is SignalIconModel.CellularTypeIconModel -> {
                                        val signalState =
                                            SignalDrawable.getState(
                                                signalLevelIcon.level,
                                                signalLevelIcon.numberOfLevels,
                                                signalLevelIcon.showExclamationMark,
                                            )
                                        MobileDataTileIcon.SignalIcon(signalState)
                                    }

                                    is SignalIconModel.Satellite -> {
                                        MobileDataTileIcon.ResourceIcon(
                                            Icon.Resource(
                                                signalLevelIcon.icon.resId,
                                                signalLevelIcon.icon.contentDescription,
                                            )
                                        )
                                    }
                                }
                            } else {
                                MobileDataTileIcon.ResourceIcon(
                                    Icon.Resource(
                                        R.drawable.ic_signal_mobile_data_off,
                                        ContentDescription.Loaded(mobileDataLabel),
                                    )
                                )
                            }
                        MobileDataTileModel(
                            isSimActive = true,
                            isEnabled = isDataEnabled,
                            icon = icon,
                            secondaryLabel = description,
                        )
                    }
                }
                .onStart {
                    MobileDataTileModel(
                        isSimActive = false,
                        isEnabled = false,
                        icon =
                            MobileDataTileIcon.ResourceIcon(
                                Icon.Resource(
                                    R.drawable.ic_signal_mobile_data_off,
                                    ContentDescription.Loaded(mobileDataLabel),
                                )
                            ),
                    )
                }
        }

    override fun availability(user: UserHandle): Flow<Boolean> = flowOf(isAvailable())

    fun isAvailable(): Boolean {
        return QsSplitInternetTile.isEnabled
    }
}
