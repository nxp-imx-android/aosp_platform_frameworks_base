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

package com.android.server.companion.datatransfer.continuity.connectivity;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.content.pm.PackageManagerInternal;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class AssociationProfileManagerTest {

    @Mock
    private PackageManagerInternal mMockPackageManagerInternal;

    private AssociationProfileManager mAssociationProfileManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mAssociationProfileManager = new AssociationProfileManager(mMockPackageManagerInternal);
    }

    @Test
    public void isAssociationAvailableForUser_returnsTrueIfPackageUidIsGreaterThanMinusOne() {
        String packageName = "com.android.test";
        int userId = 0;
        AssociationInfo associationInfo =
                new AssociationInfo.Builder(1, userId, packageName)
                        .setDisplayName("Test Device")
                        .build();
        when(mMockPackageManagerInternal.getPackageUid(packageName, 0, userId)).thenReturn(100);
        assertThat(
                mAssociationProfileManager.isAssociationAvailableForUser(
                        userId, associationInfo))
                .isTrue();
    }

    @Test
    public void isAssociationAvailableForUser_returnsFalseIfPackageUidIsLessThanMinusOne() {
        String packageName = "com.android.test";
        int userId = 0;
        AssociationInfo associationInfo =
                new AssociationInfo.Builder(1, userId, packageName)
                        .setDisplayName("Test Device")
                        .build();
        when(mMockPackageManagerInternal.getPackageUid(packageName, 0, userId)).thenReturn(-1);
        assertThat(
                mAssociationProfileManager.isAssociationAvailableForUser(
                        userId, associationInfo))
                .isFalse();
    }
}
