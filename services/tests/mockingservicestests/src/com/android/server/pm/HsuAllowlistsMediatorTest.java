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
package com.android.server.pm;

import static android.content.ComponentName.unflattenFromString;

import static com.android.server.pm.HsuAllowlistsMediator.DEBUG;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.android.server.ExpectableTestCase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class HsuAllowlistsMediatorTest extends ExpectableTestCase {
    private static final String TAG = HsuAllowlistsMediatorTest.class.getSimpleName();

    private static final String PERM_NAME_1 = "perm/one.is.the.loniest.number";
    private static final String PERM_NAME_2 = "perm/two.to.tango";
    private static final String PERM_NAME_3 = "perm/three.is.a.charm";

    private static final String TEMP_NAME_1 = "temp/one.is.the.loniest.number";
    private static final String TEMP_NAME_2 = "temp/two.to.tango";
    private static final String TEMP_NAME_3 = "temp/three.is.a.charm";

    private static final ComponentName PERM_ACTIVITY_1 = unflattenFromString(PERM_NAME_1);
    private static final ComponentName PERM_ACTIVITY_2 = unflattenFromString(PERM_NAME_2);
    private static final ComponentName PERM_ACTIVITY_3 = unflattenFromString(PERM_NAME_3);

    private static final ComponentName TEMP_ACTIVITY_1 = unflattenFromString(TEMP_NAME_1);
    private static final ComponentName TEMP_ACTIVITY_2 = unflattenFromString(TEMP_NAME_2);
    private static final ComponentName TEMP_ACTIVITY_3 = unflattenFromString(TEMP_NAME_3);


    private static final ComponentName NOT_ALLOWLISTED_ACTIVITY =
            unflattenFromString("allowlisted/I.am...NOT");
    private static final String INVALID_NAME = "invalid.I.am"; // missing package

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    private final Context mRealContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();

    // Need to spy the real context otherwise we'd have to mock resources, which is a PITA...
    private final Context mSpiedContext = spy(mRealContext);

    @Mock
    private Resources mMockResources;

    @Before
    public void setFixtures() {
        doReturn(mMockResources).when(mSpiedContext).getResources();
    }

    @Test
    public void testIsActivityAllowed_null() throws Exception {
        var ham = createHam();

        assertThrows(NullPointerException.class, () -> ham.isActivityAllowed(null));
    }

    @Test
    public void testIsActivityAllowed_emptyConfig() throws Exception {
        var ham = createHam();

        expectAllowed(ham, PERM_ACTIVITY_1);
    }

    @Test
    public void testIsActivityAllowed_configWithOne() throws Exception {
        var ham = createHam(PERM_NAME_1);

        expectNotAllowed(ham, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(ham, PERM_ACTIVITY_1);
    }

    @Test
    public void testIsActivityAllowed_configWithTwo() throws Exception {
        var ham = createHam(PERM_NAME_1, PERM_NAME_2);

        expectNotAllowed(ham, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(ham, PERM_ACTIVITY_1, PERM_ACTIVITY_2);
    }

    @Test
    public void testIsActivityAllowed_configWithMultiple() throws Exception {
        var allowlistedShortened = new ComponentName("allowlisted", ".I.am");
        var allowlistedNotShortened = new ComponentName("allowlisted", "allowlisted.me.too");
        var ham = createHam(PERM_NAME_1, PERM_NAME_2, PERM_NAME_3,
                "allowlisted/.I.am", "allowlisted/allowlisted.me.too", INVALID_NAME);

        expectNotAllowed(ham, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(ham, PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3,
                allowlistedShortened, allowlistedNotShortened);
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromEmptyConfig() throws Exception {
        var ham = createHam();

        testSetAndResetTemporaryList(ham, () -> expectAllowed(ham, NOT_ALLOWLISTED_ACTIVITY));
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromConfigWithOne() throws Exception {
        var ham = createHam(PERM_NAME_1);

        testSetAndResetTemporaryList(ham, () -> {
            expectNotAllowed(ham, NOT_ALLOWLISTED_ACTIVITY);
            expectAllowed(ham, PERM_ACTIVITY_1);
        });
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromConfigWithTwo() throws Exception {
        var ham = createHam(PERM_NAME_1, PERM_NAME_2);

        testSetAndResetTemporaryList(ham, () -> {
            expectNotAllowed(ham, NOT_ALLOWLISTED_ACTIVITY);
            expectAllowed(ham, PERM_ACTIVITY_1, PERM_ACTIVITY_2);
        });
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromConfigWithMultiple() throws Exception {
        var ham = createHam(PERM_NAME_1, PERM_NAME_2, PERM_NAME_3);

        testSetAndResetTemporaryList(ham, () -> {
            expectNotAllowed(ham, NOT_ALLOWLISTED_ACTIVITY);
            expectAllowed(ham, PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
        });
    }

    private void testSetAndResetTemporaryList(HsuAllowlistsMediator ham,
            Runnable permanentAllowListStateChecker) {
        // Check permanent state before
        permanentAllowListStateChecker.run();


        // Set as empty - everything should be allowed
        List<ComponentName> emptyList = emptyList();

        ham.setTemporaryActivitiesAllowlist(emptyList);

        expectAllowedAfterSettingTemporaryList(ham, emptyList,
                NOT_ALLOWLISTED_ACTIVITY,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3,
                PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3
        );

        // One temporary activity
        List<ComponentName> listWithOne = asList(TEMP_ACTIVITY_1);

        ham.setTemporaryActivitiesAllowlist(listWithOne);

        expectAllowedAfterSettingTemporaryList(ham, listWithOne,
                TEMP_ACTIVITY_1);
        expectNotAllowedAfterSettingTemporaryList(ham, listWithOne,
                NOT_ALLOWLISTED_ACTIVITY, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);
        expectDefaultPermanentActivitiesNotAllowed(ham);


        // Two temporary activities
        List<ComponentName> listWithOneAndTwo = asList(TEMP_ACTIVITY_1, TEMP_ACTIVITY_2);

        ham.setTemporaryActivitiesAllowlist(listWithOneAndTwo);

        expectAllowedAfterSettingTemporaryList(ham, listWithOneAndTwo,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2);
        expectNotAllowedAfterSettingTemporaryList(ham, listWithOneAndTwo,
                NOT_ALLOWLISTED_ACTIVITY, TEMP_ACTIVITY_3);
        expectDefaultPermanentActivitiesNotAllowed(ham);


        // Three temporary activities
        List<ComponentName> listWithThree =
                asList(TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);

        ham.setTemporaryActivitiesAllowlist(listWithThree);

        expectAllowedAfterSettingTemporaryList(ham, listWithThree,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);
        expectNotAllowedAfterSettingTemporaryList(ham, listWithThree, NOT_ALLOWLISTED_ACTIVITY);
        expectDefaultPermanentActivitiesNotAllowed(ham);

        // Reset the list
        ham.setTemporaryActivitiesAllowlist(null);

        permanentAllowListStateChecker.run();
    }

    @Test
    public void testDump_emptyConfig() throws Exception {
        var ham = createHam();

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: allowlisting disabled
                        permanent activities allowlist is empty.
                        temporary activities allowlist is not set.
                           """, DEBUG));
    }

    @Test
    public void testDump_configWithOneActivity() throws Exception {
        var ham = createHam("allowlisted/I.am");

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 1 activity:
                          allowlisted/I.am
                        temporary activities allowlist is not set.
                           """, DEBUG));
    }

    @Test
    public void testDump_configWithMultipleActivities() throws Exception {
        var ham = createHam("allowlisted/I.am", "so/am.I");

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 2 activities:
                          allowlisted/I.am
                          so/am.I
                        temporary activities allowlist is not set.
                          """, DEBUG));
    }

    @Test
    public void testDump_configWithOneInvalidActivity() throws Exception {
        var ham = createHam(INVALID_NAME);

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                        HsuAllowlistsMediator (HAM)
                          DEBUG: %b
                          activities allowlist status: allowlisting disabled
                          permanent activities allowlist is empty.
                          temporary activities allowlist is not set.
                             """, DEBUG));
    }

    @Test
    public void testDump_configWithValidAndInvalidActivities() throws Exception {
        var ham = createHam("allowlisted/I.am", "so/am.I", INVALID_NAME);

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 2 activities:
                          allowlisted/I.am
                          so/am.I
                        temporary activities allowlist is not set.
                          """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistEmpty_emptyConfig() throws Exception {
        var ham = createHam();
        ham.setTemporaryActivitiesAllowlist(emptyList());

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: allowlisting disabled
                        permanent activities allowlist is empty.
                        temporary activities allowlist is empty.
                           """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistEmpty_nonEmptyConfig() throws Exception {
        var ham = createHam("allowlisted/I.am", "so/am.I");
        ham.setTemporaryActivitiesAllowlist(emptyList());

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: allowlisting disabled
                        permanent activities allowlist has 2 activities:
                          allowlisted/I.am
                          so/am.I
                        temporary activities allowlist is empty.
                           """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistWithOne_emptyConfig() throws Exception {
        var ham = createHam();
        ham.setTemporaryActivitiesAllowlist(asList(unflattenFromString("and/me.too")));

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: using temporary allowlist
                        permanent activities allowlist is empty.
                        temporary activities allowlist has 1 activity:
                          and/me.too
                           """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistWithMultiple_nonEmptyConfig() throws Exception {
        var ham = createHam("allowlisted/I.am", "so/am.I");
        ham.setTemporaryActivitiesAllowlist(asList(
                unflattenFromString("and/me.too"),
                unflattenFromString("dont/forget.about.me")));

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      HsuAllowlistsMediator (HAM)
                        DEBUG: %b
                        activities allowlist status: using temporary allowlist
                        permanent activities allowlist has 2 activities:
                          allowlisted/I.am
                          so/am.I
                        temporary activities allowlist has 2 activities:
                          and/me.too
                          dont/forget.about.me
                           """, DEBUG));
    }

    private HsuAllowlistsMediator createHam(String... configAllowlist) {
        mockConfigHsuAllowList(configAllowlist);
        return new HsuAllowlistsMediator(mSpiedContext);
    }

    private void mockConfigHsuAllowList(String... componentNames) {
        Log.d(TAG, "mockConfigHsuAllowList(): " + Arrays.toString(componentNames));
        when(mMockResources
                .getStringArray(com.android.internal.R.array.config_hsu_allowlist_activitivies))
                        .thenReturn(componentNames);
    }

    private void expectNotAllowed(HsuAllowlistsMediator ham, ComponentName... activities) {
        for (var activity : activities) {
            expectWithMessage("isActivityAllowed(%s)", activity)
                    .that(ham.isActivityAllowed(activity))
                    .isFalse();
        }
    }

    private void expectAllowed(HsuAllowlistsMediator ham, ComponentName... activities) {
        for (var activity : activities) {
            expectWithMessage("isActivityAllowed(%s)", activity)
                    .that(ham.isActivityAllowed(activity))
                    .isTrue();
        }
    }

    private void expectNotAllowedAfterSettingTemporaryList(HsuAllowlistsMediator ham,
            Collection<ComponentName> temporaryAllowList, ComponentName...activities) {
        for (var activity: activities) {
            expectWithMessage("isActivityAllowed(%s) after SetTemporaryActivitiesAllowlist(%s)",
                    activity, temporaryAllowList).that(ham.isActivityAllowed(activity)).isFalse();
        }
    }

    private void expectAllowedAfterSettingTemporaryList(HsuAllowlistsMediator ham,
            Collection<ComponentName> temporaryAllowList, ComponentName...activities) {
        for (var activity: activities) {
            expectWithMessage("isActivityAllowed(%s) after SetTemporaryActivitiesAllowlist(%s)",
                    activity, temporaryAllowList).that(ham.isActivityAllowed(activity)).isTrue();
        }
    }

    private void expectDefaultPermanentActivitiesNotAllowed(HsuAllowlistsMediator ham) {
        expectNotAllowed(ham, PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
    }

    private static String dump(HsuAllowlistsMediator ham) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            ham.dump(new PrintWriter(sw), /* args= */ null);
            return sw.toString();
        }
    }
}
