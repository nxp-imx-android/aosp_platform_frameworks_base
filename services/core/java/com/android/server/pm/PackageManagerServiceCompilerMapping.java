/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.pm;

import android.os.SystemProperties;

import com.android.server.art.model.DexoptParams;

import dalvik.system.DexFile;

/**
 * Manage (retrieve) mappings from compilation reason to compilation filter.
 */
public class PackageManagerServiceCompilerMapping {
    // Names for compilation reasons.
    public static final String REASON_STRINGS[] = {
        "first-boot",
        "boot-after-ota",
        "post-boot",
        "install",
        "install-fast",
        "install-bulk",
        "install-bulk-secondary",
        "install-bulk-downgraded",
        "install-bulk-secondary-downgraded",
        "bg-dexopt",
        "ab-ota",
        "inactive",
        "cmdline",
        "boot-after-mainline-update",
        // "shared" must be the last entry
        "shared"
    };

    static final int REASON_SHARED_INDEX = REASON_STRINGS.length - 1;

    // Static block to ensure the strings array is of the right length.
    static {
        if (PackageManagerService.REASON_LAST + 1 != REASON_STRINGS.length) {
            throw new IllegalStateException("REASON_STRINGS not correct");
        }
        if (!"shared".equals(REASON_STRINGS[REASON_SHARED_INDEX])) {
            throw new IllegalStateException("REASON_STRINGS not correct because of shared index");
        }
    }

    private static String getSystemPropertyName(int reason) {
        if (reason < 0 || reason >= REASON_STRINGS.length) {
            throw new IllegalArgumentException("reason " + reason + " invalid");
        }

        return "pm.dexopt." + REASON_STRINGS[reason];
    }

    // Load the property for the given reason and check for validity. This will throw an
    // exception in case the reason or value are invalid.
    private static String getAndCheckValidity(int reason) {
        String sysPropValue = SystemProperties.get(getSystemPropertyName(reason));
        if (sysPropValue == null || sysPropValue.isEmpty()
                || !(sysPropValue.equals(DexoptParams.COMPILER_FILTER_NOOP)
                        || DexFile.isValidCompilerFilter(sysPropValue))) {
            throw new IllegalStateException("Value \"" + sysPropValue +"\" not valid "
                    + "(reason " + REASON_STRINGS[reason] + ")");
        } else if (!isFilterAllowedForReason(reason, sysPropValue)) {
            throw new IllegalStateException("Value \"" + sysPropValue +"\" not allowed "
                    + "(reason " + REASON_STRINGS[reason] + ")");
        }

        return sysPropValue;
    }

    private static boolean isFilterAllowedForReason(int reason, String filter) {
        return reason != REASON_SHARED_INDEX || !DexFile.isProfileGuidedCompilerFilter(filter);
    }

    public static String getCompilerFilterForReason(int reason) {
        return getAndCheckValidity(reason);
    }
}
