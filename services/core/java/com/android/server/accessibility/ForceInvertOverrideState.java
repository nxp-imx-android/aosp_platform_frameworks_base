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

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the state of the force invert overrides from Settings.
 *
 * @param packagesToEnable list of package names that will always have force invert applied
 * @param packagesToDisable list of package names that will never have force invert applied
 */
public record ForceInvertOverrideState(
        List<String> packagesToEnable, List<String> packagesToDisable) {
    // TODO(b/448469020): Migrate the List to Set

    /**
     * An empty force invert override state.
     */
    public static final ForceInvertOverrideState EMPTY = new ForceInvertOverrideState(List.of(),
            List.of());

    private static final String DELIMITER = ",";

    /** Loads the force invert override state from Settings using a system_server context. */
    @NonNull
    public static ForceInvertOverrideState loadFrom(Context context, int userId) {
        var resolver = context.getContentResolver();
        var packageDisableList = parseCsv(resolver, userId,
                Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE);
        var packageEnableList = parseCsv(resolver, userId,
                Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE);

        var blocklistArray = context.getResources().getStringArray(
                com.android.internal.R.array.config_forceInvertPackageBlocklist);
        var packageBlocklist = blocklistArray == null ? new ArrayList<String>()
                : ArrayUtils.toList(blocklistArray);

        // Overrides take precedence over config blocklist
        packageBlocklist.removeAll(packageEnableList);
        packageDisableList.addAll(packageBlocklist);

        return new ForceInvertOverrideState(packageEnableList, packageDisableList);
    }

    /**
     * Returns the override state for the given package.
     */
    @UiModeManager.ForceInvertPackageOverrideState
    public int getStateForPackage(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED;
        }

        if (packagesToDisable.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE;
        }

        if (packagesToEnable.contains(packageName)) {
            return UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE;
        }

        return UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED;
    }

    /**
     * Returns the list of force invert always disable apps.
     *
     * @hide
     */
    public List<String> getAllForceInvertAlwaysDisableApps() {
        return packagesToDisable;
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

        boolean result = false;
        switch (newState) {
            case UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_DISABLE -> {
                result = !packagesToDisable.contains(packageName);
                if (result) {
                    packagesToDisable.add(packageName);
                    packagesToEnable.remove(packageName);
                }
            }
            case UiModeManager.FORCE_INVERT_PACKAGE_ALWAYS_ENABLE -> {
                result = !packagesToEnable.contains(packageName);
                if (result) {
                    packagesToDisable.remove(packageName);
                    packagesToEnable.add(packageName);
                }
            }
            case UiModeManager.FORCE_INVERT_PACKAGE_ALLOWED -> {
                result = packagesToDisable.remove(packageName);
                result |= packagesToEnable.remove(packageName);
            }
        }

        if (result) {
            saveToCsv(resolver, userId,
                    Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_DISABLE,
                    packagesToDisable);
            saveToCsv(resolver, userId,
                    Settings.System.ACCESSIBILITY_FORCE_INVERT_COLOR_OVERRIDE_PACKAGES_TO_ENABLE,
                    packagesToEnable);
        }

        return result;
    }

    @NonNull
    private static List<String> parseCsv(ContentResolver resolver, int userId, String settingsKey) {
        var csv = Settings.System.getStringForUser(resolver, settingsKey, userId);
        if (csv == null) {
            return new ArrayList<>();
        }

        return ArrayUtils.toList(TextUtils.split(csv, DELIMITER));
    }

    private static boolean saveToCsv(ContentResolver resolver, int userId,
            String settingsKey, List<String> packageList) {
        return Settings.System.putStringForUser(
                resolver, settingsKey, TextUtils.join(DELIMITER, packageList), userId);
    }
}


