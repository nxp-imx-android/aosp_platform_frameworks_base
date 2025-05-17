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

package com.android.server.security.authenticationpolicy;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

import android.annotation.SuppressLint;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.PowerManager;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.wm.WindowManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * atest FrameworksServicesTests:SecureLockDeviceServiceTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SecureLockDeviceServiceTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    @Mock private IPowerManager mIPowerManager;
    @Mock private IThermalService mThermalService;
    @Mock private SecureLockDeviceServiceInternal mSecureLockDeviceServiceInternal;
    @Mock private WindowManagerInternal mWindowManagerInternal;

    private final EnableSecureLockDeviceParams mEnableParams =
            new EnableSecureLockDeviceParams("test");
    private final DisableSecureLockDeviceParams mDisableParams =
            new DisableSecureLockDeviceParams("test");

    private SecureLockDeviceService mSecureLockDeviceService;

    @SuppressLint("VisibleForTests")
    @Before
    public void setUp() {
        // Unable to mock PowerManager directly because final class
        mContext.addMockSystemService(PowerManager.class,
                new PowerManager(mContext, mIPowerManager, mThermalService, null));

        mLocalServiceKeeperRule.overrideLocalService(SecureLockDeviceServiceInternal.class,
                mSecureLockDeviceServiceInternal);
        mLocalServiceKeeperRule.overrideLocalService(WindowManagerInternal.class,
                mWindowManagerInternal);

        mSecureLockDeviceService = new SecureLockDeviceService(mContext);
        mSecureLockDeviceService.onLockSettingsReady();
    }

    @Test
    public void enableSecureLockDevice_goesToSleep_locksDevice() throws RemoteException {
        enableSecureLockDevice();

        verify(mIPowerManager).goToSleep(anyLong(), anyInt(), anyInt());
        verify(mWindowManagerInternal).lockNow();
    }

    private int enableSecureLockDevice() {
        return mSecureLockDeviceService.enableSecureLockDevice(mEnableParams);
    }

    private int disableSecureLockDevice() {
        return mSecureLockDeviceService.disableSecureLockDevice(mDisableParams);
    }
}
