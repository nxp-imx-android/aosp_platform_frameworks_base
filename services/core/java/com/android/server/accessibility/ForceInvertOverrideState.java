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
package com.android.server.accessibility;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.util.ArrayUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds the state of the force invert overrides from Settings.
 *
 * @param packagesToEnable set of package names that will always have force invert applied
 * @param packagesToDisable set of package names that will never have force invert applied
 * @param packagesInBlockList set of package names that are set to never be force inverted
 *                            by the system
 */
public record ForceInvertOverrideState(
        Set<String> packagesToEnable, Set<String> packagesToDisable,
        Set<String> packagesInBlockList) {

    /**
     * An empty force invert override state.
     */
    public static final ForceInvertOverrideState EMPTY = new ForceInvertOverrideState(Set.of(),
            Set.of(), Set.of());

    private static final String DELIMITER = ",";

    /** Loads the force invert override state from Settings using a system_server context. */
    @NonNull
    public static ForceInvertOverrideState loadFrom(Context context, int userId) {
        var resolver = context.getContentResolver();
        var packageDisableSet = parseCsv(resolver, userId,
                Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE);
        var packageEnableSet = parseCsv(resolver, userId,
                Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE);

        var blocklistArray = context.getResources().getStringArray(
                com.android.internal.R.array.config_forceInvertPackageBlocklist);
        var packageBlockSet = blocklistArray == null ? new HashSet<String>()
                : new HashSet<>(ArrayUtils.toList(blocklistArray));

        return new ForceInvertOverrideState(
                packageEnableSet, packageDisableSet, packageBlockSet);
    }

    /**
     * Returns the override state for the given package.
     */
    @UiModeManager.ForceInvertPackageOverrideState
    public int getStateForPackage(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED;
        }

        // 1. Overrides take precedence over config blocklist
        // 2. If the package exists in both packagesToDisable and packagesToEnable, disable takes
        // precedence
        if (packagesToDisable.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE;
        }
        if (packagesToEnable.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE;
        }
        if (packagesInBlockList.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE;
        }

        return UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED;
    }

    /**
     * Returns the list of force invert always disable apps.
     *
     * @hide
     */
    public List<String> getAllForceInvertAlwaysDisableApps() {
        var disableApps = new HashSet<String>();
        disableApps.addAll(packagesToDisable);
        disableApps.addAll(packagesInBlockList);
        return disableApps.stream().toList();
    }

    /**
     * Sets the ForceInvertOverrideState for a specific package.
     *
     * @hide
     */
    public boolean setForceInvertOverrideStateForPackage(
            ContentResolver resolver,
            @Nullable String packageName,
            @UiModeManager.ForceInvertPackageOverrideState int newState,
            int userId) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        boolean resultToDisable = false;
        boolean resultToEnable = false;
        switch (newState) {
            case UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE -> {
                resultToDisable = packagesToDisable.add(packageName);
                resultToEnable = packagesToEnable.remove(packageName);
            }
            case UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE -> {
                resultToDisable = packagesToDisable.remove(packageName);
                resultToEnable = packagesToEnable.add(packageName);
            }
            case UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED -> {
                resultToDisable = packagesToDisable.remove(packageName);
                resultToEnable = packagesToEnable.remove(packageName);
            }
        }

        if (resultToDisable) {
            saveToCsv(resolver, userId,
                    Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE,
                    packagesToDisable);
        }
        if (resultToEnable) {
            saveToCsv(resolver, userId,
                    Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
                    packagesToEnable);
        }

        return resultToDisable || resultToEnable;
    }

    @NonNull
    private static Set<String> parseCsv(ContentResolver resolver, int userId, String settingsKey) {
        var csv = Settings.System.getStringForUser(resolver, settingsKey, userId);
        if (csv == null) {
            return new HashSet<>();
        }

        return new HashSet<>(ArrayUtils.toList(TextUtils.split(csv, DELIMITER)));
    }

    private static boolean saveToCsv(ContentResolver resolver, int userId,
            String settingsKey, Set<String> packageSet) {
        return Settings.System.putStringForUser(
                resolver, settingsKey, TextUtils.join(DELIMITER, packageSet), userId);
    }
}


