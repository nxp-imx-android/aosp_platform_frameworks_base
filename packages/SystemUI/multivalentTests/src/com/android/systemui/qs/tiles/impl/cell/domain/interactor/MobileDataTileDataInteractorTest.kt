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

import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.FlagsParameterization
import android.text.Html
import androidx.test.filters.SmallTest
import com.android.settingslib.mobile.TelephonyIcons
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.Flags
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fake
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.ui.MobileContextProvider
import com.android.systemui.statusbar.pipeline.mobile.NewSatelliteIcon
import com.android.systemui.statusbar.pipeline.mobile.data.model.DataConnectionState
import com.android.systemui.statusbar.pipeline.mobile.data.model.NetworkNameModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeMobileConnectionRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.repository.mobileConnectionsRepository
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractor
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.MobileIconsInteractorImpl
import com.android.systemui.statusbar.pipeline.shared.connectivityConstants
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.policy.data.repository.userSetupRepository
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.util.CarrierConfigTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class MobileDataTileDataInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope

    // Fakes needed for MobileIconsInteractorImpl
    private val userRepository = kosmos.fakeUserRepository
    private val connectivityConstants = kosmos.connectivityConstants
    private val connectivityRepository = kosmos.connectivityRepository
    private val mobileConnectionsRepository = kosmos.mobileConnectionsRepository
    private val userSetupRepo = kosmos.userSetupRepository
    private val featureFlags = kosmos.featureFlagsClassic
    private val carrierConfigTracker: CarrierConfigTracker = mock()

    // Real MobileIconsInteractor, fed by fakes
    private var mobileIconsInteractor: MobileIconsInteractor =
        MobileIconsInteractorImpl(
            mobileConnectionsRepository,
            carrierConfigTracker,
            logcatTableLogBuffer(kosmos, "MobileIconsInteractorTest"),
            connectivityRepository,
            userSetupRepo,
            testScope.backgroundScope,
            context,
            featureFlags,
        )

    private val mobileContextProvider: MobileContextProvider = mock {
        on { getMobileContextForSub(anyInt(), any()) } doReturn context
    }

    private var underTest: MobileDataTileDataInteractor =
        MobileDataTileDataInteractor(
            context,
            userRepository,
            mobileIconsInteractor,
            mobileContextProvider,
            connectivityConstants,
        )

    @Before
    fun setUp() {
        featureFlags.fake.set(Flags.FILTER_PROVISIONING_NETWORK_SUBSCRIPTIONS, true)
    }

    @Test
    fun tileData_noActiveSim_emitsInactiveModel() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(-1)
            runCurrent()

            val expectedModel = MobileDataTileModel(isSimActive = false, isEnabled = false)
            assertThat(tileData).isEqualTo(expectedModel)
        }

    @Test
    fun tileData_activeSim_dataDisabled() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected

            // Set data to disabled
            mobileConnectionRepo.dataEnabled.value = false
            runCurrent()

            assertThat(tileData?.isSimActive).isTrue()
            assertThat(tileData?.isEnabled).isFalse()
        }

    @Test
    fun tileData_activeSim_dataEnabled() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected
            mobileConnectionRepo.dataEnabled.value = true

            // Update the signal level in the fake repo
            mobileConnectionRepo.setAllLevels(0)
            runCurrent()

            assertThat(tileData?.isSimActive).isTrue()
            assertThat(tileData?.isEnabled).isTrue()
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    fun availability_flagEnabledDataSupportedMainUser_isTrue() =
        kosmos.runTest {
            assertThat(QsSplitInternetTile.isEnabled).isTrue()
            connectivityConstants.hasDataCapabilities = true
            fakeUserRepository.mainUserId = USER.identifier

            val availability by collectLastValue(underTest.availability(USER))
            assertThat(availability).isTrue()
        }

    @Test
    @DisableFlags(QsSplitInternetTile.FLAG_NAME)
    fun availability_flagDisabled_isFalse() =
        kosmos.runTest {
            assertThat(QsSplitInternetTile.isEnabled).isFalse()
            val availability by collectLastValue(underTest.availability(USER))
            assertThat(availability).isFalse()
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    fun availability_dataNotSupported_isFalse() =
        kosmos.runTest {
            assertThat(QsSplitInternetTile.isEnabled).isTrue()
            connectivityConstants.hasDataCapabilities = false
            fakeUserRepository.mainUserId = USER.identifier

            val availability by collectLastValue(underTest.availability(USER))
            assertThat(availability).isFalse()
        }

    @Test
    @EnableFlags(QsSplitInternetTile.FLAG_NAME)
    fun availability_notMainUser_isFalse() =
        kosmos.runTest {
            assertThat(QsSplitInternetTile.isEnabled).isTrue()
            connectivityConstants.hasDataCapabilities = true
            fakeUserRepository.mainUserId = USER.identifier + 1

            val availability by collectLastValue(underTest.availability(USER))
            assertThat(availability).isFalse()
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun tileData_dataDisconnected_nullSecondaryLabel() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataEnabled.value = true
            mobileConnectionRepo.networkName.value =
                NetworkNameModel.SubscriptionDerived("some name")

            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Disconnected
            runCurrent()

            assertThat(tileData?.secondaryLabel).isNull()
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun tileData_secondaryLabel_isSetCorrectly() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataEnabled.value = true
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected
            runCurrent()

            // When network name and data content description are available, they are concatenated.
            val networkName = "Test Carrier"
            mobileConnectionRepo.networkName.value =
                NetworkNameModel.SubscriptionDerived(networkName)
            val networkType = TelephonyIcons.LTE
            mobileConnectionRepo.setNetworkTypeKey(mobileConnectionsRepository.fake.LTE_KEY)
            mobileConnectionRepo.setAllRoaming(false)
            runCurrent()

            val expectedDesc = context.getString(networkType.dataContentDescription)
            val expectedSecondaryLabel = getSecondaryLabel(networkName, expectedDesc)
            assertThat(tileData?.secondaryLabel.toString())
                .isEqualTo(expectedSecondaryLabel.toString())

            // When roaming, roaming text is prepended to data content description.
            mobileConnectionRepo.setAllRoaming(true)
            runCurrent()

            val roamingText = context.getString(R.string.data_connection_roaming)
            val expectedRoamingContent =
                context.getString(R.string.mobile_data_text_format, roamingText, expectedDesc)
            val expectedRoamingSecondaryLabel =
                getSecondaryLabel(networkName, expectedRoamingContent)
            assertThat(tileData?.secondaryLabel.toString())
                .isEqualTo(expectedRoamingSecondaryLabel.toString())

            // When satellite, satellite text is used.
            mobileConnectionRepo.isNonTerrestrial.value = true
            mobileConnectionRepo.setAllRoaming(false)
            runCurrent()

            val satelliteText = context.getString(R.string.qs_tile_satellite_label)
            val expectedSatelliteSecondaryLabel =
                context.getString(R.string.mobile_carrier_text_format, networkName, satelliteText)
            assertThat(tileData?.secondaryLabel.toString())
                .isEqualTo(expectedSatelliteSecondaryLabel.toString())

            // Back to cellular without roaming.
            mobileConnectionRepo.isNonTerrestrial.value = false
            mobileConnectionRepo.setAllRoaming(false)
            runCurrent()
            assertThat(tileData?.secondaryLabel.toString())
                .isEqualTo(expectedSecondaryLabel.toString())
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun tileData_secondaryLabel_roaming_isSetCorrectly() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataEnabled.value = true
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected
            runCurrent()

            val networkName = "Test Carrier"
            mobileConnectionRepo.networkName.value =
                NetworkNameModel.SubscriptionDerived(networkName)
            val networkType = TelephonyIcons.LTE
            mobileConnectionRepo.setNetworkTypeKey(mobileConnectionsRepository.fake.LTE_KEY)
            val expectedDesc = context.getString(networkType.dataContentDescription)

            // When roaming, roaming text is prepended to data content description.
            mobileConnectionRepo.setAllRoaming(true)
            runCurrent()

            val roamingText = context.getString(R.string.data_connection_roaming)
            val expectedRoamingContent =
                context.getString(R.string.mobile_data_text_format, roamingText, expectedDesc)
            val expectedRoamingSecondaryLabel =
                getSecondaryLabel(networkName, expectedRoamingContent)
            assertThat(tileData?.secondaryLabel.toString())
                .isEqualTo(expectedRoamingSecondaryLabel.toString())
        }

    @Test
    @EnableFlags(NewSatelliteIcon.FLAG_NAME)
    fun tileData_secondaryLabel_satelliteMode_isSetCorrectly() =
        kosmos.runTest {
            val tileData by collectLastValue(underTest.tileData())
            val mobileConnectionRepo =
                FakeMobileConnectionRepository(SUB_ID, logcatTableLogBuffer(kosmos))
            mobileConnectionsRepository.fake.setMobileConnectionRepositoryMap(
                mapOf(SUB_ID to mobileConnectionRepo)
            )
            mobileConnectionsRepository.fake.setActiveMobileDataSubscriptionId(SUB_ID)
            mobileConnectionRepo.dataEnabled.value = true
            mobileConnectionRepo.dataConnectionState.value = DataConnectionState.Connected
            runCurrent()

            val networkName = "Test Carrier"
            mobileConnectionRepo.networkName.value =
                NetworkNameModel.SubscriptionDerived(networkName)
            val networkType = TelephonyIcons.LTE
            mobileConnectionRepo.setNetworkTypeKey(mobileConnectionsRepository.fake.LTE_KEY)
            mobileConnectionRepo.setAllRoaming(false)
            // When satellite, satellite text is used.
            mobileConnectionRepo.isNonTerrestrial.value = true
            runCurrent()

            val satelliteText = context.getString(R.string.qs_tile_satellite_label)
            val expectedSatelliteSecondaryLabel =
                context.getString(R.string.mobile_carrier_text_format, networkName, satelliteText)
            assertThat(tileData?.secondaryLabel.toString())
                .isEqualTo(expectedSatelliteSecondaryLabel.toString())
        }

    private fun getSecondaryLabel(
        networkName: String,
        dataContentDesc: CharSequence,
    ): CharSequence =
        Html.fromHtml(
            context.getString(R.string.mobile_carrier_text_format, networkName, dataContentDesc),
            0,
        )

    private companion object {
        const val SUB_ID = 1
        private val USER = UserHandle.of(0)

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
