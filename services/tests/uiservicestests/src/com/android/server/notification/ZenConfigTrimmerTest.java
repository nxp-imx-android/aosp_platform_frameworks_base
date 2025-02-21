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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeConfig.ZenRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.UiServiceTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ZenConfigTrimmerTest extends UiServiceTestCase {

    private static final String TRUSTED_PACKAGE = "com.trust.me";
    private static final int ONE_PERCENT = 1_500;

    private ZenConfigTrimmer mTrimmer;

    @Before
    public void setUp() {
        mContext.getOrCreateTestableResources().addOverride(
                R.string.config_defaultDndAccessPackages, TRUSTED_PACKAGE);

        mTrimmer = new ZenConfigTrimmer(mContext);
    }

    @Test
    public void trimToMaximumSize_belowMax_untouched() {
        ZenModeConfig config = new ZenModeConfig();
        addZenRule(config, "1", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "2", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "3", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "4", "pkg2", 20 * ONE_PERCENT);
        addZenRule(config, "5", "pkg2", 20 * ONE_PERCENT);

        mTrimmer.trimToMaximumSize(config);

        assertThat(config.automaticRules.keySet()).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    public void trimToMaximumSize_exceedsMax_removesAllRulesOfLargestPackages() {
        ZenModeConfig config = new ZenModeConfig();
        addZenRule(config, "1", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "2", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "3", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "4", "pkg2", 20 * ONE_PERCENT);
        addZenRule(config, "5", "pkg2", 20 * ONE_PERCENT);
        addZenRule(config, "6", "pkg3", 35 * ONE_PERCENT);
        addZenRule(config, "7", "pkg4", 38 * ONE_PERCENT);

        mTrimmer.trimToMaximumSize(config);

        assertThat(config.automaticRules.keySet()).containsExactly("1", "2", "3", "6");
        assertThat(config.automaticRules.values().stream().map(r -> r.pkg).distinct())
                .containsExactly("pkg1", "pkg3");
    }

    @Test
    public void trimToMaximumSize_keepsRulesFromTrustedPackages() {
        ZenModeConfig config = new ZenModeConfig();
        addZenRule(config, "1", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "2", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "3", "pkg1", 10 * ONE_PERCENT);
        addZenRule(config, "4", TRUSTED_PACKAGE, 60 * ONE_PERCENT);
        addZenRule(config, "5", "pkg2", 20 * ONE_PERCENT);
        addZenRule(config, "6", "pkg3", 35 * ONE_PERCENT);

        mTrimmer.trimToMaximumSize(config);

        assertThat(config.automaticRules.keySet()).containsExactly("4", "5");
        assertThat(config.automaticRules.values().stream().map(r -> r.pkg).distinct())
                .containsExactly(TRUSTED_PACKAGE, "pkg2");
    }

    /**
     * Create a ZenRule that, when serialized to a Parcel, will take <em>approximately</em>
     * {@code desiredSize} bytes (within 100 bytes). Try to make the tests not rely on a very tight
     * fit.
     */
    private static void addZenRule(ZenModeConfig config, String id, String pkg, int desiredSize) {
        ZenRule rule = new ZenRule();
        rule.id = id;
        rule.pkg = pkg;
        config.automaticRules.put(id, rule);

        // Make the ZenRule as large as desired. Not to the exact byte, because otherwise this
        // test would have to be adjusted whenever we change the parceling of ZenRule in any way.
        // (Still might need adjustment if we change the serialization _significantly_).
        int nameLength = desiredSize - id.length() - pkg.length() - 232;
        rule.name = "A".repeat(nameLength);

        Parcel verification = Parcel.obtain();
        try {
            verification.writeParcelable(rule, 0);
            assertThat(verification.dataSize()).isWithin(100).of(desiredSize);
        } finally {
            verification.recycle();
        }
    }
}
