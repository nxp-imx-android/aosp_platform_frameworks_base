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

package com.android.server.supervision;

import android.annotation.NonNull;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.app.supervision.SupervisionManager;
import android.app.supervision.flags.Flags;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.util.Slog;

import android.content.Context;

import com.android.server.pm.UserManagerInternal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SupervisionPolicyMigrator {
    private final Context mContext;
    private final UserManagerInternal mUserManagerInternal;
    private final SupervisionService.Injector mInjector;
    private final DevicePolicyManager mDpm;

    SupervisionPolicyMigrator(
            Context context,
            @NonNull UserManagerInternal userManagerInternal,
            @NonNull SupervisionService.Injector injector,
            @NonNull DevicePolicyManager dpm) {
        mContext = context;
        mUserManagerInternal = userManagerInternal;
        mInjector = injector;
        mDpm = dpm;
    }

    boolean upgrade(int fromVersion, int toVersion) {
        if (fromVersion >= toVersion) {
            return true;
        }
        Slog.i(
                SupervisionLog.TAG,
                "Upgrading supervision settings from " + fromVersion + " to " + toVersion);

        if (fromVersion < 1) {
            if (!upgradeToVersion1()) {
                return false;
            }
        }
        return true;
    }

    private boolean upgradeToVersion1() {
        // Clear "application hidden" DPM restrictions set by each of the current Supervision
        // Role holders.
        List<UserInfo> users = mUserManagerInternal.getUsers(false /*excludeDying*/);
        for (UserInfo user : users) {
            int userId = user.id;
            // Get holders for both supervision roles
            List<String> supervisionRoleHolders =
                    mInjector.getRoleHoldersAsUser(
                            RoleManager.ROLE_SUPERVISION, UserHandle.of(userId));
            List<String> systemSupervisionRoleHolders =
                    mInjector.getRoleHoldersAsUser(
                            RoleManager.ROLE_SYSTEM_SUPERVISION, UserHandle.of(userId));

            // Combine them into a single list/set
            Set<String> allRoleHolders = new HashSet<>(supervisionRoleHolders);
            allRoleHolders.addAll(systemSupervisionRoleHolders);

            if (!allRoleHolders.isEmpty()) {
                Slog.i(
                        SupervisionLog.TAG,
                        "Clearing application hidden policies for user "
                                + userId
                                + " set by "
                                + allRoleHolders);
                // Unhide any apps that were hidden by the old supervision admins.
                List<ComponentName> activeAdmins = mDpm.getActiveAdminsAsUser(userId);
                if (activeAdmins != null) {
                    List<ComponentName> supervisionAdmins = new ArrayList<>();
                    for (ComponentName admin : activeAdmins) {
                        if (allRoleHolders.contains(admin.getPackageName())) {
                            supervisionAdmins.add(admin);
                        }
                    }

                    if (!supervisionAdmins.isEmpty()) {
                        PackageManager pm = mContext.getPackageManager();
                        List<PackageInfo> installedPackages =
                                pm.getInstalledPackagesAsUser(
                                        PackageManager.GET_UNINSTALLED_PACKAGES, userId);
                        for (PackageInfo pi : installedPackages) {
                            if (pm.getApplicationHiddenSettingAsUser(
                                    pi.packageName, UserHandle.of(userId))) {
                                // Package is hidden, check if it was hidden by a supervision role
                                // holder
                                for (ComponentName admin : supervisionAdmins) {
                                    if (mDpm.isApplicationHidden(admin, pi.packageName)) {
                                        mDpm.setApplicationHidden(admin, pi.packageName, false);
                                        // Once unhidden, no need to check other admins
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }
}
