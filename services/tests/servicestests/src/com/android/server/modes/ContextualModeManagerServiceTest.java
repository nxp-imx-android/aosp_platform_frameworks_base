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
package com.android.server.modes;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.Manifest.permission;
import android.app.modes.ContextualModesMutation;
import android.app.modes.IContextualModeListener;
import android.app.modes.IContextualModeManager;
import android.app.modes.IContextualModeSyncListener;
import android.content.Context;
import android.os.PermissionEnforcer;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.notification.Flags;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_ENABLE_DND_SYNC)
public class ContextualModeManagerServiceTest {
    @Rule public final SetFlagsRule mCheckFlagsRule = new SetFlagsRule();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock private PermissionEnforcer mPermissionEnforcer;

    private Context mContext;
    private ContextualModeManagerService mService;
    private IContextualModeManager mBinderService;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mService = new ContextualModeManagerService(mContext, mPermissionEnforcer);
        mBinderService = mService.getBinderService();
    }

    @Test
    public void testIsModeSyncSupported_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.isModeSyncSupported();
    }

    @Test
    public void testIsModeSyncEnabled_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.isModeSyncEnabled(UserHandle.SYSTEM);
        mBinderService.isModeSyncEnabled(UserHandle.CURRENT);
    }

    @Test
    public void testSetModeSyncEnabled_requiresPermission() throws Exception {
        mBinderService.setModeSyncEnabled(true);

        denyPermission(permission.WRITE_SECURE_SETTINGS);

        assertThrows(SecurityException.class, () -> mBinderService.setModeSyncEnabled(true));
    }

    @Test
    public void testGetModes_requiresPermission() throws Exception {
        mBinderService.getModes(UserHandle.SYSTEM);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(SecurityException.class, () -> mBinderService.getModes(UserHandle.SYSTEM));
    }

    @Test
    public void testMutateModes_requiresPermission() throws Exception {
        ContextualModesMutation mutation = new ContextualModesMutation.Builder().build();

        mBinderService.mutateModes(UserHandle.SYSTEM, mutation);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.mutateModes(UserHandle.SYSTEM, mutation));
    }

    @Test
    public void testRegisterModeSyncListener_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.registerModeSyncListener(
                UserHandle.SYSTEM, mock(IContextualModeSyncListener.class));
    }

    @Test
    public void testUnregisterModeSyncListener_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.unregisterModeSyncListener(mock(IContextualModeSyncListener.class));
    }

    @Test
    public void testRegisterModeListener_requiresPermission() throws Exception {
        IContextualModeListener listener = mock(IContextualModeListener.class);

        mBinderService.registerModeListener(UserHandle.SYSTEM, listener);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.registerModeListener(UserHandle.SYSTEM, listener));
    }

    @Test
    public void testUnregisterModeListener_requiresPermission() throws Exception {
        IContextualModeListener listener = mock(IContextualModeListener.class);

        mBinderService.unregisterModeListener(listener);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(
                SecurityException.class, () -> mBinderService.unregisterModeListener(listener));
    }

    private void denyPermission(String permission) {
        doThrow(SecurityException.class)
                .when(mPermissionEnforcer)
                .enforcePermission(eq(permission), anyInt(), anyInt());
    }

    private void denyAllPermissions() {
        doThrow(SecurityException.class)
                .when(mPermissionEnforcer)
                .enforcePermission(anyString(), anyInt(), anyInt());
    }
}
