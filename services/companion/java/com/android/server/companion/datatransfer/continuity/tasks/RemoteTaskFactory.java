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

package com.android.server.companion.datatransfer.continuity.tasks;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.datatransfer.continuity.RemoteTask;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.util.Slog;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import java.util.Objects;

public class RemoteTaskFactory {

    private static final String TAG = RemoteTaskFactory.class.getSimpleName();

    private final Icon mDefaultIcon;
    private final PackageManager mPackageManager;
    private final String mBrowserPackageName;
    private final int mUserId;

    public RemoteTaskFactory(
            int userId, @NonNull Context context, @NonNull PackageManager packageManager) {
        mUserId = userId;
        mPackageManager = Objects.requireNonNull(packageManager);
        mDefaultIcon = Icon.createWithResource(context, android.R.drawable.sym_def_app_icon);
        mBrowserPackageName = packageManager.getDefaultBrowserPackageNameAsUser(userId);
    }

    @Nullable
    public RemoteTask create(
            int associationId,
            @Nullable String associationDisplayName,
            @NonNull RemoteTaskInfo remoteTaskInfo) {
        Objects.requireNonNull(remoteTaskInfo);

        boolean canTaskBeHandedOffLocally = canTaskBeHandedOffNatively(remoteTaskInfo);
        boolean canTaskBeHandedOffByBrowser = canTaskBeHandedOffByBrowser(remoteTaskInfo);

        if (!canTaskBeHandedOffLocally && !canTaskBeHandedOffByBrowser) {
            return null;
        }

        String packageName =
                canTaskBeHandedOffLocally ? remoteTaskInfo.packageName() : mBrowserPackageName;

        return new RemoteTask.Builder(associationId, remoteTaskInfo.id())
                .setPackageName(packageName)
                .setLabel(getPackageLabel(packageName))
                .setLastUsedTimestampMillis(remoteTaskInfo.lastUsedTimeMillis())
                .setAssociationDisplayName(associationDisplayName)
                .setIcon(getPackageIcon(packageName))
                .setTaskInForeground(remoteTaskInfo.isInForeground())
                .setHandoffEnabled(remoteTaskInfo.handoffOptions().isHandoffEnabled())
                .build();
    }

    @Nullable
    private String getPackageLabel(@NonNull String packageName) {
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo == null) {
            return null;
        }

        return mPackageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
    }

    @Nullable
    private Icon getPackageIcon(@NonNull String packageName) {
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo == null) {
            return mDefaultIcon;
        }

        if (packageInfo.applicationInfo.icon == 0) {
            return mDefaultIcon;
        }

        return Icon.createWithResource(packageName, packageInfo.applicationInfo.icon);
    }

    @Nullable
    private PackageInfo getPackageInfo(@NonNull String packageName) {
        try {
            return mPackageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Could not find package info for package: " + packageName);
            return null;
        }
    }

    private boolean canTaskBeHandedOffNatively(@NonNull RemoteTaskInfo remoteTaskInfo) {
        Objects.requireNonNull(remoteTaskInfo);

        if (!remoteTaskInfo.handoffOptions().isHandoffEnabled()) {
            return false;
        }

        return getPackageInfo(remoteTaskInfo.packageName()) != null;
    }

    private boolean canTaskBeHandedOffByBrowser(@NonNull RemoteTaskInfo remoteTaskInfo) {
        Objects.requireNonNull(remoteTaskInfo);
        return mBrowserPackageName != null
                && remoteTaskInfo.handoffOptions().isHandoffEnabled()
                && !remoteTaskInfo.handoffOptions().requirePackageInstalled();
    }
}
