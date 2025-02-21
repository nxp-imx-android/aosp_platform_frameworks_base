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

package com.android.server.notification;

import android.content.Context;
import android.os.Parcel;
import android.service.notification.SystemZenRules;
import android.service.notification.ZenModeConfig;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class ZenConfigTrimmer {

    private static final String TAG = "ZenConfigTrimmer";
    private static final int MAXIMUM_PARCELED_SIZE = 150_000; // bytes

    private final HashSet<String> mTrustedPackages;

    ZenConfigTrimmer(Context context) {
        mTrustedPackages = new HashSet<>();
        mTrustedPackages.add(SystemZenRules.PACKAGE_ANDROID);
        mTrustedPackages.addAll(ConditionProviders.getDefaultDndAccessPackages(context));
    }

    void trimToMaximumSize(ZenModeConfig config) {
        Map<String, PackageRules> rulesPerPackage = new HashMap<>();
        for (ZenModeConfig.ZenRule rule : config.automaticRules.values()) {
            PackageRules pkgRules = rulesPerPackage.computeIfAbsent(rule.pkg, PackageRules::new);
            pkgRules.mRules.add(rule);
        }

        int totalSize = 0;
        for (PackageRules pkgRules : rulesPerPackage.values()) {
            totalSize += pkgRules.dataSize();
        }

        if (totalSize > MAXIMUM_PARCELED_SIZE) {
            List<PackageRules> deletionCandidates = new ArrayList<>();
            for (PackageRules pkgRules : rulesPerPackage.values()) {
                if (!mTrustedPackages.contains(pkgRules.mPkg)) {
                    deletionCandidates.add(pkgRules);
                }
            }
            deletionCandidates.sort(Comparator.comparingInt(PackageRules::dataSize).reversed());

            evictPackagesFromConfig(config, deletionCandidates, totalSize);
        }
    }

    private static void evictPackagesFromConfig(ZenModeConfig config,
            List<PackageRules> deletionCandidates, int currentSize) {
        while (currentSize > MAXIMUM_PARCELED_SIZE && !deletionCandidates.isEmpty()) {
            PackageRules rulesToDelete = deletionCandidates.removeFirst();
            Slog.w(TAG, String.format("Evicting %s zen rules from package '%s' (%s bytes)",
                    rulesToDelete.mRules.size(), rulesToDelete.mPkg, rulesToDelete.dataSize()));

            for (ZenModeConfig.ZenRule rule : rulesToDelete.mRules) {
                config.automaticRules.remove(rule.id);
            }

            currentSize -= rulesToDelete.dataSize();
        }
    }

    private static class PackageRules {
        private final String mPkg;
        private final List<ZenModeConfig.ZenRule> mRules;
        private int mParceledSize = -1;

        PackageRules(String pkg) {
            mPkg = pkg;
            mRules = new ArrayList<>();
        }

        private int dataSize() {
            if (mParceledSize >= 0) {
                return mParceledSize;
            }
            Parcel parcel = Parcel.obtain();
            try {
                parcel.writeParcelableList(mRules, 0);
                mParceledSize = parcel.dataSize();
                return mParceledSize;
            } finally {
                parcel.recycle();
            }
        }
    }
}
