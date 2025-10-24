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

import static android.integration.multiuser.ActivitiesHelper.assertCanLaunchActivity;
import static android.integration.multiuser.ActivitiesHelper.assertCannotLaunchActivity;
import static android.integration.multiuser.InstrumentationHelper.getInstrumentation;
import static android.integration.multiuser.InstrumentationHelper.runShellCommand;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.multiuser.Flags;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tests integration with the allowlist mechanism used to block activities when the current user
 * is the HSU (Headless System User).
 */
@RequiresFlagsEnabled(Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
public final class HsuActivitiesAllowlistTest {

    private static final String TAG = HsuActivitiesAllowlistTest.class.getSimpleName();

    @Rule(order = 0)
    public final RequiresRootRule requiresRoot = new RequiresRootRule(
            "to call UserManager.setTemporaryActivitiesAllowlist()");

    @Rule(order = 1)
    public final CheckFlagsRule flags = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Context mContext = getInstrumentation().getTargetContext();

    private final UserManager mUm = mContext.getSystemService(UserManager.class);

    @Before
    @After
    public void resetAllowlist() throws Exception {
        setTemporaryAllowlist((ComponentName[]) null);
    }

    @Test
    public void testActivityLaunchIsAllowed_whenAllowlistIsDisabledByDefault() throws Exception {
        assumeAllowlistDisabled();

        assertCanLaunchActivity(Activity1.class);
    }

    @Test
    public void testActivityLaunchIsDisallowed_whenAllowlistIsEnabledByDefault() throws Exception {
        assumeAllowlistEnabled();

        assertCannotLaunchActivity(Activity1.class);
    }

    @Test
    public void testActivityLaunchIsAllowed_whenAllowlistIsExplicitlyDisabled() throws Exception {
        disableAllowlist();

        assertCanLaunchActivity(Activity1.class);
    }

    @Test
    public void testActivityLaunch_whenAllowlistIsExplicitlySet() throws Exception {
        setTemporaryAllowlist(new ComponentName(mContext, Activity1.class));

        assertCanLaunchActivity(Activity1.class);
        assertCannotLaunchActivity(Activity2.class);
    }

    private void setTemporaryAllowlist(@Nullable ComponentName...activities) throws IOException {
        String testPkg = getInstrumentation().getTargetContext().getPackageName();
        String permission = "android.permission.MANAGE_HEADLESS_SYSTEM_USER_ALLOWLISTS";
        Set<ComponentName> allowlist = activities == null
                ? null
                : new LinkedHashSet<>(Arrays.asList(activities));
        Log.i(TAG, "setTemporaryAllowlist(): " + allowlist);

        runShellCommand("pm grant --user 0 %s %s", testPkg, permission);
        try {
            mUm.setTemporaryActivitiesAllowlist(USER_TYPE_SYSTEM_HEADLESS, allowlist);
        } finally {
            runShellCommand("pm revoke --user 0 %s %s", testPkg, permission);
        }
    }

    private void disableAllowlist() throws IOException {
        setTemporaryAllowlist();
    }

    private void assumeAllowlistEnabled() throws IOException {
        assumeTrue("allowlist is enabled", isAllowlistEnabled());
    }

    private void assumeAllowlistDisabled() throws IOException {
        assumeFalse("allowlist is disabled", isAllowlistEnabled());
    }

    private boolean isAllowlistEnabled() throws IOException {
        String dump = runShellCommand("dumpsys user --uta");

        boolean result = !dump.contains("activities allowlist status: allowlisting disabled");
        Log.d(TAG, "isAllowlistEnabled(): returning " + result);

        return result;
    }
}
