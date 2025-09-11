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

package com.android.server.companion.virtual.computercontrol;

import android.companion.virtual.computercontrol.IAutomatedPackageListener;
import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Keeps track of all packages running on computer control sessions and notifies listeners. */
public class AutomatedPackagesRepository {

    private static final String TAG = AutomatedPackagesRepository.class.getSimpleName();

    private final Handler mHandler;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final RemoteCallbackList<IAutomatedPackageListener> mAutomatedPackageListeners =
            new RemoteCallbackList<>();

    // Currently automated package names keyed by device owner and user id.
    // The listeners need to be notified if this changes.
    @GuardedBy("mLock")
    final ArrayMap<String, SparseArray<ArraySet<String>>> mAutomatedPackages = new ArrayMap<>();

    // Full mapping of deviceId -> userId -> packageNames running on that device.
    // We need the deviceId for correctness, as there may be multiple devices with the same owner.
    @GuardedBy("mLock")
    final SparseArray<SparseArray<ArraySet<String>>> mDevicePackages = new SparseArray<>();

    // Mapping of deviceId to the package name of the owner of that device.
    @GuardedBy("mLock")
    final SparseArray<String> mDeviceOwnerPackageNames = new SparseArray<>();

    public AutomatedPackagesRepository(Handler handler) {
        mHandler = handler;
    }

    /** Register a listener for automated package changes. */
    public void registerAutomatedPackageListener(IAutomatedPackageListener listener) {
        synchronized (mLock) {
            mAutomatedPackageListeners.register(listener);
        }
    }

    /** Unregister a listener for automated package changes. */
    public void unregisterAutomatedPackageListener(IAutomatedPackageListener listener) {
        synchronized (mLock) {
            mAutomatedPackageListeners.unregister(listener);
        }
    }

    /** Update the list of packages running on a device. */
    public void update(int deviceId, String deviceOwnerPackageName,
            ArraySet<Pair<Integer, String>> runningPackageUids) {
        synchronized (mLock) {
            updateLocked(deviceId, deviceOwnerPackageName, runningPackageUids);
        }
    }

    private void updateLocked(int deviceId, String deviceOwnerPackageName,
            ArraySet<Pair<Integer, String>> uidPackagePairs) {
        if (uidPackagePairs.isEmpty()) {
            mDeviceOwnerPackageNames.remove(deviceId);
            mDevicePackages.remove(deviceId);
        } else {
            mDeviceOwnerPackageNames.put(deviceId, deviceOwnerPackageName);
            mDevicePackages.put(deviceId, mapUserIdToPackages(uidPackagePairs));
        }

        // userId -> automatedPackages for this device owner.
        // This aggregates packages from all virtual devices associated with the current
        // deviceOwnerPackageName.
        final SparseArray<ArraySet<String>> deviceOwnerAutomatedPackages = new SparseArray<>();
        for (int i = 0; i < mDevicePackages.size(); ++i) {
            final int id = mDevicePackages.keyAt(i);
            final String ownerPackage = mDeviceOwnerPackageNames.get(id);
            if (!Objects.equals(deviceOwnerPackageName, ownerPackage)) {
                continue;
            }

            final SparseArray<ArraySet<String>> userPackageMap = mDevicePackages.valueAt(i);
            for (int j = 0; j < userPackageMap.size(); ++j) {
                final int userId = userPackageMap.keyAt(j);
                final ArraySet<String> packages = userPackageMap.valueAt(j);
                if (!deviceOwnerAutomatedPackages.contains(userId)) {
                    deviceOwnerAutomatedPackages.put(userId, new ArraySet<>());
                }
                deviceOwnerAutomatedPackages.get(userId).addAll(packages);
            }
        }

        SparseArray<ArraySet<String>> oldPackages = mAutomatedPackages.get(deviceOwnerPackageName);
        // Collect all user IDs from both old and new states.
        final ArraySet<Integer> allUserIds = new ArraySet<>();
        if (oldPackages != null) {
            for (int i = 0; i < oldPackages.size(); i++) {
                allUserIds.add(oldPackages.keyAt(i));
            }
        }
        for (int i = 0; i < deviceOwnerAutomatedPackages.size(); i++) {
            allUserIds.add(deviceOwnerAutomatedPackages.keyAt(i));
        }

        // For each user, compare the old and new package sets and notify if they differ.
        for (int i = 0; i < allUserIds.size(); i++) {
            final int userId = allUserIds.valueAt(i);
            final ArraySet<String> oldUserPackages =
                    (oldPackages == null) ? null : oldPackages.get(userId);
            final ArraySet<String> newUserPackages = deviceOwnerAutomatedPackages.get(userId);

            if (Objects.equals(oldUserPackages, newUserPackages)) {
                continue;
            }

            // A change occurred. Notify with the new list of packages.
            // If the new list is null, the user's packages were all removed, so notify with an
            // empty list.
            final List<String> packagesToReport = (newUserPackages == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(newUserPackages);
            notifyAutomatedPackagesChanged(deviceOwnerPackageName, packagesToReport, userId);
        }

        // Update the main automated packages map with the newly computed state.
        if (deviceOwnerAutomatedPackages.size() == 0) {
            mAutomatedPackages.remove(deviceOwnerPackageName);
        } else {
            mAutomatedPackages.put(deviceOwnerPackageName, deviceOwnerAutomatedPackages);
        }
    }

    private SparseArray<ArraySet<String>> mapUserIdToPackages(
            ArraySet<Pair<Integer, String>> uidPackagePairs) {
        final SparseArray<ArraySet<String>> userIdToPackages = new SparseArray<>();
        for (int i = 0; i < uidPackagePairs.size(); ++i) {
            final Pair<Integer, String> uidAndPackage = uidPackagePairs.valueAt(i);
            final int uid = uidAndPackage.first;
            final int userId = UserHandle.getUserId(uid);
            if (!userIdToPackages.contains(userId)) {
                userIdToPackages.put(userId, new ArraySet<>());
            }
            final String packageName = uidAndPackage.second;
            if (packageName != null) {
                userIdToPackages.get(userId).add(packageName);
            }
        }
        return userIdToPackages;
    }

    private void notifyAutomatedPackagesChanged(
            String ownerPackageName, List<String> packageNames, int userId) {
        final UserHandle userHandle = UserHandle.of(userId);
        mHandler.post(() -> {
            synchronized (mLock) {
                mAutomatedPackageListeners.broadcast(listener -> {
                    try {
                        listener.onAutomatedPackagesChanged(
                                ownerPackageName, packageNames, userHandle);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Failed to invoke onAutomatedPackagesChanged listener: "
                                + e.getMessage());
                    }
                });
            }
        });
    }
}
