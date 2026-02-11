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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class InstalledAppsRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    // TODO: b/478225883 - Put InstalledAppsRepository in a test fixture.
    private val Kosmos.underTest by
        Kosmos.Fixture { InstalledAppsRepositoryImpl(kosmos.testDispatcher, kosmos.packageManager) }

    @Test
    fun fetchInstalledApps_getsAll() =
        kosmos.runTest {
            val drawable1 = mock<Drawable>()
            val appInfo1 =
                mock<ApplicationInfo>().apply {
                    whenever(this.loadLabel(eq(packageManager))).thenReturn("App 1")
                    whenever(this.loadIcon(eq(packageManager))).thenReturn(drawable1)
                }

            val drawable2 = mock<Drawable>()
            val appInfo2 =
                mock<ApplicationInfo>().apply {
                    whenever(this.loadLabel(eq(packageManager))).thenReturn("App 2")
                    whenever(this.loadIcon(eq(packageManager))).thenReturn(drawable2)
                }

            whenever(packageManager.getInstalledApplications(any<Int>()))
                .thenReturn(listOf(appInfo1, appInfo2))

            val result = underTest.fetchInstalledApps()

            assertThat(result)
                .containsExactly(
                    AppModel(label = "App 1", icon = drawable1),
                    AppModel(label = "App 2", icon = drawable2),
                )
                .inOrder()
        }
}
