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

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.RequiresNoPermission;
import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager;
import android.app.modes.ContextualModesMutation;
import android.app.modes.IContextualModeListener;
import android.app.modes.IContextualModeManager;
import android.app.modes.IContextualModeSyncListener;
import android.content.Context;
import android.os.PermissionEnforcer;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.List;

/**
 * A system service managing contextual modes. E.g. Do not disturb. See {@link
 * ContextualModeManager}.
 */
public final class ContextualModeManagerService extends SystemService {
    private static final String TAG = "CtxModeManagerService";

    private final BinderService mBinderService;

    public ContextualModeManagerService(@NonNull Context context) {
        this(context, PermissionEnforcer.fromContext(context));
    }

    @VisibleForTesting
    ContextualModeManagerService(Context context, PermissionEnforcer permissionEnforcer) {
        super(context);
        mBinderService = new BinderService(permissionEnforcer);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.CONTEXTUAL_MODE_SERVICE, mBinderService);
    }

    @VisibleForTesting
    IContextualModeManager getBinderService() {
        return mBinderService;
    }

    /** Binder service for clients to interact with. */
    // TODO(b/430676215): implement
    private class BinderService extends IContextualModeManager.Stub {

        BinderService(PermissionEnforcer permissionEnforcer) {
            super(permissionEnforcer);
        }

        @Override
        @RequiresNoPermission
        public boolean isModeSyncSupported() {
            return false;
        }

        @Override
        @RequiresNoPermission
        public boolean isModeSyncEnabled(UserHandle userHandle) {
            return false;
        }

        @Override
        @EnforcePermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        public void setModeSyncEnabled(boolean enabled) {
            setModeSyncEnabled_enforcePermission();
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public List<ContextualMode> getModes(UserHandle userHandle) {
            getModes_enforcePermission();
            return List.of();
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public void mutateModes(UserHandle userHandle, ContextualModesMutation mutation) {
            mutateModes_enforcePermission();
        }

        @Override
        @RequiresNoPermission
        public void registerModeSyncListener(
                UserHandle userHandle, IContextualModeSyncListener listener) {}

        @Override
        @RequiresNoPermission
        public void unregisterModeSyncListener(IContextualModeSyncListener listener) {}

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public void registerModeListener(UserHandle userHandle, IContextualModeListener listener) {
            registerModeListener_enforcePermission();
        }

        @Override
        @EnforcePermission(Manifest.permission.MANAGE_CONTEXTUAL_MODES)
        public void unregisterModeListener(IContextualModeListener listener) {
            unregisterModeListener_enforcePermission();
        }
    }
}
