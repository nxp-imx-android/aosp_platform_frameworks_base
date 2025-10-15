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
package android.integration.multiuser;

import static android.integration.multiuser.ActivitiesHelper.finishActivity;
import static android.integration.multiuser.ActivitiesHelper.launchActivity;

import android.app.Activity;
import android.multiuser.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

/**
 * Tests integration with the allowlist mechanism used to block activities when the current user
 * is the HSU (Headless System User).
 */
@RequiresFlagsEnabled(Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
public final class HsuActivitiesAllowlistTest {

    @Rule
    public final CheckFlagsRule flags = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testAllowlistDisabled_activityLaunch() {
        // TODO(b/412177078): call UM to disable the allowlist
        Activity activity = null;
        try {
            activity = launchActivity(Activity1.class);
        } finally {
            finishActivity(activity);
        }
    }
}
