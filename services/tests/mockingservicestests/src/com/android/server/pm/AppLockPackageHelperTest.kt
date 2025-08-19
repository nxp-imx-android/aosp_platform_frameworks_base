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

package com.android.server.pm

import android.app.supervision.SupervisionManagerInternal
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Process
import android.util.ArrayMap
import android.util.ArraySet
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.pm.parsing.pkg.AndroidPackageInternal
import com.android.internal.pm.pkg.component.ParsedActivity
import com.android.internal.pm.pkg.component.ParsedActivityImpl
import com.android.internal.pm.pkg.component.ParsedIntentInfoImpl
import com.android.server.LocalServices
import com.android.server.extendedtestutils.wheneverStatic
import com.android.server.pm.pkg.PackageStateInternal
import com.android.server.pm.pkg.PackageUserStateInternal
import com.android.server.testutils.mock
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.util.function.Consumer
import kotlin.test.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.pm.AppLockPackageHelperTest
 */
@RunWith(TestParameterInjector::class)
class AppLockPackageHelperTest : PackageHelperTestBase() {

    private lateinit var appLockPackageHelper: AppLockPackageHelper
    private val testInjector = TestInjector()
    private val testLauncherIntentFilter =
        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    private val testLauncherIntentFilters: Array<IntentFilter> = arrayOf(testLauncherIntentFilter)
    private val activities =
        listOf<ParsedActivity>(createActivity(TEST_PACKAGE_NAME, testLauncherIntentFilters))
    private val mockSnapshot: Computer = mock()
    private val mockPackageStateInternal: PackageStateInternal = mock()
    private val mockPackageUserStateInternal: PackageUserStateInternal = mock()
    private val mockAndroidPackage: AndroidPackageInternal = mock()
    private var availableFeatures = ArrayMap<String, FeatureInfo>(1)

    val userInfo: UserInfo = mock()
    val smInternal: SupervisionManagerInternal = mock()

    @Before
    @Throws(Exception::class)
    override fun setup() {
        super.setup()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation
            .adoptShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT")

        appLockPackageHelper =
            AppLockPackageHelper(instrumentation.context, pms, broadcastHelper, testInjector)
        whenever(mockSnapshot.getPackageStateInternal(eq(EXEMPT_PACKAGE_NAME)))
            .thenReturn(mockPackageStateInternal)
        whenever(mockSnapshot.getPackageStateInternal(eq(TEST_PACKAGE_NAME)))
            .thenReturn(mockPackageStateInternal)
        whenever(mockPackageStateInternal.getUserStateOrDefault(
            eq(TEST_USER_ID))).thenReturn(mockPackageUserStateInternal)

        whenever(mockPackageStateInternal.pkg).thenReturn(mockAndroidPackage)
        whenever(mockAndroidPackage.activities).thenReturn(activities)
        whenever(rule.mocks().systemConfig.appLockExemptPackages).thenReturn(
            ArraySet<String>().apply { add(EXEMPT_PACKAGE_NAME) })
        whenever(rule.mocks().systemConfig.availableFeatures).thenReturn(availableFeatures)
        whenever(userInfo.isFull).thenReturn(true)
        whenever(smInternal.isSupervisionEnabledForUser(anyInt())).thenReturn(false)
        whenever(rule.mocks().userManagerInternal.getUserInfo(TEST_USER_ID))
            .thenReturn(userInfo)
        wheneverStatic { LocalServices.getService(UserManagerInternal::class.java) }
            .thenReturn(rule.mocks().userManagerInternal)
        wheneverStatic { LocalServices.getService(SupervisionManagerInternal::class.java) }
            .thenReturn(smInternal)
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_systemConfigExemptApp_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                EXEMPT_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID
            )
        ).isFalse()
        // Disk write occurs only if the current state was true, as exempt apps cannot have App Lock
        // enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false, EXEMPT_PACKAGE_NAME)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_headlessApp_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(mockAndroidPackage.activities).thenReturn(
            listOf<ParsedActivity?>()
        )
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID
            )
        ).isFalse()
        // Disk write occurs only if the current state was true, as headless apps cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_noUser_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(rule.mocks().userManagerInternal.getUserInfo(TEST_USER_ID))
            .thenReturn(null)
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID
            )
        ).isFalse()
        // Disk write occurs only if the current state was true, as profile users cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_profileUser_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(userInfo.isFull).thenReturn(false)
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID
            )
        ).isFalse()
        // Disk write occurs only if the current state was true, as profile users cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false, TEST_PACKAGE_NAME)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_supervisedUser_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(smInternal.isSupervisionEnabledForUser(eq(TEST_USER_ID)))
            .thenReturn(true)
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID
            )
        ).isFalse()
        // Disk write occurs only if the current state was true, as supervised users cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_deviceNotSecure_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = false,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID
            )
        ).isFalse()
        // Disk write occurs only if the current state was true, as App Lock is not supported on
        // insecure devices.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_invalidUid_false_noDiskWrite(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.INVALID_UID
            )
        ).isFalse()
        verifyNoWriteOrPackageUpdate()
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_appUid_false_noDiskWrite(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState, /* callingUid= */
                12345
            )
        ).isFalse()
        verifyNoWriteOrPackageUpdate()
    }

    @Test
    fun setPackageAppLockEnabled(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
        @TestParameter appLockSupportedForSecureDevice: Boolean,
    ) {
        val targetState = appLockSupportedForSecureDevice && newEnabledState
        val expectDiskWrite = currentEnabledState != targetState
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        if (appLockSupportedForSecureDevice) {
            whenever(mockAndroidPackage.activities).thenReturn(activities)
        } else {
            whenever(mockAndroidPackage.activities)
                .thenReturn(mutableListOf<ParsedActivity?>())
        }

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID,
                newEnabledState,
                Process.SYSTEM_UID,
            )
        ).isEqualTo(appLockSupportedForSecureDevice)
        if (expectDiskWrite) {
            verifyDiskWriteAndPackageUpdate(targetState)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun isPackageAppLockEnabled_appLockIsEnabled_true() {
        whenever(mockPackageUserStateInternal.isAppLockEnabled).thenReturn(true)

        assertThat(
            appLockPackageHelper.isPackageAppLockEnabled(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID
            )
        ).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun isPackageAppLockEnabled_appLockIsDisabled_false() {
        whenever(mockPackageUserStateInternal.isAppLockEnabled).thenReturn(false)

        assertThat(
            appLockPackageHelper.isPackageAppLockEnabled(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID
            )
        ).isFalse()
    }

    @Test
    @Throws(Exception::class)
    fun getEnableAppLockIntentForPackage_appLockIsNotSupported_nullIntent() {
        whenever(mockAndroidPackage.activities).thenReturn(
            listOf<ParsedActivity?>()
        )

        assertThat(
            appLockPackageHelper.getEnableAppLockIntentForPackage(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID, /* enabled= */ true
            )
        ).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun getEnableAppLockIntentForPackage_appLockAlreadySetToTargetState_nullIntent() {
        whenever(mockPackageUserStateInternal.isAppLockEnabled).thenReturn(false)

        assertThat(
            appLockPackageHelper.getEnableAppLockIntentForPackage(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID, /* enabled= */ false,
            )
        ).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun getEnableAppLockIntentForPackage_pendingIntent(@TestParameter newEnabledState: Boolean) {
        whenever(mockPackageUserStateInternal.isAppLockEnabled).thenReturn(!newEnabledState)
        testInjector.setDeviceSecure(true)

        val intent = assertNotNull(
            appLockPackageHelper.getEnableAppLockIntentForPackage(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID, newEnabledState
            ), "Pending intent was expected to not be null"
        ).intent

        assertThat(intent.action).isEqualTo(PackageManager.ACTION_SET_APP_LOCK)
        assertThat(intent.hasExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE)).isTrue()
        assertThat(
            intent.getBooleanExtra(
                PackageManager.EXTRA_APP_LOCK_NEW_STATE,
                false
            )
        ).isEqualTo(newEnabledState)
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(TEST_PACKAGE_NAME)
    }

    private fun verifyDiskWriteAndPackageUpdate(
        stateToWrite: Boolean,
        packageName: String = TEST_PACKAGE_NAME
    ) {
        verify(pms).commitPackageStateMutation(any(), any())
        verify(pms).scheduleWritePackageRestrictions(eq(TEST_USER_ID))
        verify(broadcastHelper).sendPackageAppLockStateChangedForUser(
            eq(packageName),
            eq(TEST_USER_ID),
            eq(stateToWrite)
        )
    }

    private fun verifyNoWriteOrPackageUpdate() {
        verify(pms, never()).commitPackageStateMutation(any(), any())
        verify(pms, never()).scheduleWritePackageRestrictions(anyInt())
        verify(broadcastHelper, never()).sendPackageAppLockStateChangedForUser(
            any(),
            anyInt(),
            anyBoolean()
        )
    }

    private fun setupTestForSetPackageAppLockEnabled(
        deviceSecure: Boolean,
        currentEnabledState: Boolean,
    ) {
        testInjector.setDeviceSecure(deviceSecure)
        whenever(mockPackageUserStateInternal.isAppLockEnabled).thenReturn(currentEnabledState)
    }

    private class TestInjector : AppLockPackageHelper.Injector {
        private var mIsDeviceSecure = false

        fun setDeviceSecure(isDeviceSecure: Boolean) {
            mIsDeviceSecure = isDeviceSecure
        }

        override fun isDeviceSecure(context: Context?, userId: Int): Boolean {
            return mIsDeviceSecure
        }
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "testpackagename"
        private const val EXEMPT_PACKAGE_NAME = "exempt.package.name"
        private const val TEST_USER_ID = 1
        private const val INVALID_USER_ID = -1
        private fun createActivity(
            packageName: String, filters: Array<IntentFilter>
        ): ParsedActivity {
            val activity = ParsedActivityImpl()
            activity.packageName = packageName
            for (filter in filters) {
                val info = ParsedIntentInfoImpl()
                val intentInfoFilter = info.intentFilter
                if (filter.countActions() > 0) {
                    filter.actionsIterator().forEachRemaining(Consumer { action: String? ->
                        intentInfoFilter.addAction(action)
                    })
                }
                if (filter.countCategories() > 0) {
                    filter.categoriesIterator().forEachRemaining(Consumer { category: String? ->
                        intentInfoFilter.addCategory(category)
                    })
                }
                if (filter.countDataAuthorities() > 0) {
                    filter.authoritiesIterator()
                        .forEachRemaining(Consumer { ent: IntentFilter.AuthorityEntry? ->
                            intentInfoFilter.addDataAuthority(ent)
                        })
                }
                if (filter.countDataSchemes() > 0) {
                    filter.schemesIterator().forEachRemaining(Consumer { scheme: String? ->
                        intentInfoFilter.addDataScheme(scheme)
                    })
                }
                activity.addIntent(info)
                activity.isExported = true
                activity.isEnabled = true
            }
            return activity
        }
    }
}
