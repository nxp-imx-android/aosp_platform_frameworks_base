package com.android.systemui.privacy

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.UserHandle
import android.permission.PermissionUsageHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class PermissionUsageHelperTest : SysuiTestCase() {
    @get:Rule val mocks = MockitoJUnit.rule()

    private val TEST_PKG = "com.test.pkg"
    private val TEST_UID = 12345
    private val TEST_USER_HANDLE = UserHandle.getUserHandleForUid(TEST_UID)
    private val TEST_DEVICE_ID = "test_device_id"

    @Mock private lateinit var mAppOpsManager: AppOpsManager
    @Mock private lateinit var mActivityManager: ActivityManager
    @Mock private lateinit var mLocationManager: LocationManager
    @Mock private lateinit var packageManager: PackageManager

    private lateinit var mHelper: PermissionUsageHelper

    @Before
    fun setup() {
        mContext.addMockSystemService(AppOpsManager::class.java, mAppOpsManager)
        mContext.addMockSystemService(ActivityManager::class.java, mActivityManager)
        mContext.addMockSystemService(LocationManager::class.java, mLocationManager)
        mContext.setMockPackageManager(packageManager)
        mHelper = PermissionUsageHelper(mContext)
    }

    @Test
    fun testLocationIndicatorNotShownForBackgroundApp() {
        val attributedOpEntry = mock(AppOpsManager.AttributedOpEntry::class.java)
        // Ensure the last fg access time is older than the running threshold.
        val oldAccessTime = System.currentTimeMillis() - 20000L
        `when`(attributedOpEntry.getLastAccessForegroundTime(anyInt())).thenReturn(oldAccessTime)
        `when`(attributedOpEntry.isRunning).thenReturn(false)

        val opEntry =
            AppOpsManager.OpEntry(
                AppOpsManager.OP_FINE_LOCATION,
                AppOpsManager.MODE_ALLOWED,
                mapOf(null as String? to attributedOpEntry),
            )

        val packageOps = AppOpsManager.PackageOps(TEST_PKG, TEST_UID, listOf(opEntry))
        val ops = listOf(packageOps)

        `when`(mAppOpsManager.getPackagesForOps(any(Array<String>::class.java), eq(TEST_DEVICE_ID)))
            .thenReturn(ops)

        // Ensure it is a non-system app. A non-system app has no special permission flags.
        `when`(packageManager.getPermissionFlags(any(), any(), any())).thenReturn(0)

        // Ensure it is a background app by setting the process importance to a background state.
        val processInfo = ActivityManager.RunningAppProcessInfo()
        processInfo.uid = TEST_UID
        processInfo.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
        `when`(mActivityManager.runningAppProcesses).thenReturn(listOf(processInfo))

        val usages = mHelper.getOpUsageDataForIndicatorsByDevice(true, TEST_DEVICE_ID)
        assertTrue("Usage data should be empty for background apps", usages.isEmpty())
    }
}
