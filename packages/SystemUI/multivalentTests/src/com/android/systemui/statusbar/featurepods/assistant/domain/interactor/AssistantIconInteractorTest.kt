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

package com.android.systemui.statusbar.featurepods.assistant.domain.interactor

import android.content.ComponentName
import android.content.res.mainResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.featurepods.assistant.data.repository.fakeAssistantRepository
import com.android.systemui.statusbar.featurepods.assistant.shared.model.AssistantIconSharedModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AssistantIconInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val deviceEntryInteractor = kosmos.deviceEntryInteractor
    private val assistantRepository = kosmos.fakeAssistantRepository

    private val Kosmos.underTest by
        Kosmos.Fixture {
            AssistantIconInteractorImpl(
                resources = kosmos.mainResources,
                scope = kosmos.applicationCoroutineScope,
                deviceEntryInteractor = deviceEntryInteractor,
                assistantRepository = assistantRepository,
            )
        }

    @Before
    fun setup() {
        overrideResource(R.string.config_statusBarAssistantPackage, ASSISTANT_PACKAGE)
    }

    @Test
    fun model_isDefault_whenDeviceNotEntered() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))

            val assistInfo by collectLastValue(assistantRepository.assistInfo)
            val deviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEqualTo(ASSISTANT_PACKAGE)

            assertThat(deviceEntered).isFalse()

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun model_isDefault_whenAssistPackageNameIsEmpty() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName("", ASSISTANT_CLASS))
            setDeviceEntered()

            val assistInfo by collectLastValue(assistantRepository.assistInfo)
            val deviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEmpty()

            assertThat(deviceEntered).isTrue()

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun isStatusBarAssistantPackage_isFalse_whenPackageNameNotMatch() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE_2, ASSISTANT_CLASS))
            setDeviceEntered()

            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistantIconSharedModel!!.assistInfo).isNotNull()
            assertThat(assistantIconSharedModel!!.isStatusBarAssistantPackage).isFalse()
        }

    @Test
    fun isStatusBarAssistantPackage_isTrue_whenPackageNameMatch() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))
            setDeviceEntered()

            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistantIconSharedModel!!.assistInfo).isNotNull()
            assertThat(assistantIconSharedModel!!.isStatusBarAssistantPackage).isTrue()
        }

    private fun Kosmos.setDeviceEntered() {
        kosmos.fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
            SuccessFingerprintAuthenticationStatus(0, true)
        )
        kosmos.sceneInteractor.changeScene(Scenes.Gone, "test")
        assertThat(kosmos.deviceEntryInteractor.isDeviceEntered.value).isTrue()
    }

    private companion object {
        private const val ASSISTANT_PACKAGE = "the.assistant.app"
        private const val ASSISTANT_PACKAGE_2 = "the.assistant.app2"
        private const val ASSISTANT_CLASS = "the.assistant.app.class"
        private val DEFAULT_MODEL =
            AssistantIconSharedModel(
                assistInfo = null,
                isStatusBarAssistantPackage = false,
                isAssistShown = false,
            )
    }
}
