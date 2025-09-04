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

package com.android.server.privatecompute;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;
import static android.content.pm.PackageManager.MATCH_PCC_ONLY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.app.privatecompute.PccEntity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link PccSandboxManagerServiceImpl}.
 */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerServiceImplTest {

    private static final String PCC_APP_PKG = "com.pcc.app";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private Context mContext;
    @Mock
    private PackageManagerInternal mPackageManagerInternal;

    private PccSandboxManagerServiceImpl mService;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mService = new PccSandboxManagerServiceImpl(mContext, mPackageManagerInternal);
    }

    @Test
    public void testGetPccEntities_returnsCorrectList() {
        ApplicationInfo pccAppInfo = new ApplicationInfo();
        pccAppInfo.packageName = PCC_APP_PKG;
        List<ApplicationInfo> pccApps = Collections.singletonList(pccAppInfo);

        when(mPackageManagerInternal
                .getInstalledApplications(eq(MATCH_PCC_ONLY), anyInt(), anyInt()))
                .thenReturn(pccApps);

        List<PccEntity> actualEntities = mService.getPccEntities();

        assertNotNull(actualEntities);
        assertEquals(1, actualEntities.size());
        assertEquals(PCC_APP_PKG, actualEntities.get(0).getPackageName());
    }

    @Test
    public void testGetPccEntities_withNoPccApps_returnsEmptyList() {
        when(mPackageManagerInternal
                .getInstalledApplications(eq(MATCH_PCC_ONLY), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<PccEntity> actualEntities = mService.getPccEntities();

        assertNotNull(actualEntities);
        assertTrue(actualEntities.isEmpty());
    }
}
