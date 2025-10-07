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

import static com.android.server.pm.UserActivitiesAllowlist.DEBUG;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;

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
import java.util.Collection;
import java.util.List;

public final class UserActivitiesAllowlistTest extends ExpectableTestCase {

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

    private static final String SHORT_NAME = "i.am/.groot";
    private static final String FULL_NAME = "i.am/i.am.groot";
    private static final ComponentName ACTIVITY_SHORT_NAME = unflattenFromString(SHORT_NAME);
    private static final ComponentName ACTIVITY_FULL_NAME = unflattenFromString(FULL_NAME);


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

    @Before
    public void assertFullAndShortActivitiesAreTheSame() {
        expectWithMessage("ACTIVITY_FULL_NAME")
                .that(ACTIVITY_FULL_NAME).isEqualTo(ACTIVITY_SHORT_NAME);
        expectWithMessage("ACTIVITY_SHORT_NAME")
                .that(ACTIVITY_SHORT_NAME).isEqualTo(ACTIVITY_FULL_NAME);
    }

    @Test
    public void testIsActivityAllowed_null() throws Exception {
        var allowlist = createAllowlist();

        assertThrows(NullPointerException.class, () -> allowlist.isAllowed(null));
    }

    @Test
    public void testIsActivityAllowed_emptyConfig() throws Exception {
        var allowlist = createAllowlist();

        expectAllowed(allowlist, PERM_ACTIVITY_1);
        expectEffectiveActiviesAllowlist(allowlist);
    }

    @Test
    public void testIsActivityAllowed_configWithOne() throws Exception {
        var allowlist = createAllowlist(PERM_NAME_1);

        expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(allowlist, PERM_ACTIVITY_1);
        expectEffectiveActiviesAllowlist(allowlist, PERM_ACTIVITY_1);
    }

    @Test
    public void testIsActivityAllowed_configWithTwo() throws Exception {
        var allowlist = createAllowlist(PERM_NAME_1, PERM_NAME_2);

        expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2);
        expectEffectiveActiviesAllowlist(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2);
    }

    @Test
    public void testIsActivityAllowed_configWithThree() throws Exception {
        var allowlist = createAllowlist(PERM_NAME_1, PERM_NAME_2, PERM_NAME_3);

        expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
        expectEffectiveActiviesAllowlist(allowlist,
                PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
    }

    @Test
    public void testIsActivityAllowed_configWithShortFullAndInvalidNames() throws Exception {
        var allowlist = createAllowlist(SHORT_NAME, FULL_NAME, INVALID_NAME);

        expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
        expectAllowed(allowlist, ACTIVITY_FULL_NAME);
        expectEffectiveActiviesAllowlist(allowlist, ACTIVITY_FULL_NAME);
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromEmptyConfig() throws Exception {
        var allowlist = createAllowlist();

        testSetAndResetTemporaryList(allowlist, () -> {
            expectAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
            expectEffectiveActiviesAllowlist(allowlist);
        });
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromConfigWithOne() throws Exception {
        var allowlist = createAllowlist(PERM_NAME_1);

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
            expectAllowed(allowlist, PERM_ACTIVITY_1);
            expectEffectiveActiviesAllowlist(allowlist, PERM_ACTIVITY_1);
        });
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromConfigWithTwo() throws Exception {
        var allowlist = createAllowlist(PERM_NAME_1, PERM_NAME_2);

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
            expectAllowed(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2);
            expectEffectiveActiviesAllowlist(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2);
        });
    }

    @Test
    public void testSetTemporaryActivitiesAllowlist_fromConfigWithMultiple() throws Exception {
        var allowlist = createAllowlist(PERM_NAME_1, PERM_NAME_2, PERM_NAME_3);

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, NOT_ALLOWLISTED_ACTIVITY);
            expectAllowed(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
            expectEffectiveActiviesAllowlist(allowlist,
                    PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
        });
    }

    private void testSetAndResetTemporaryList(UserActivitiesAllowlist allowlist,
            Runnable permanentAllowListStateChecker) {
        // Check permanent state before
        permanentAllowListStateChecker.run();


        // Set as empty - everything should be allowed
        List<ComponentName> emptyList = emptyList();

        allowlist.setTemporaryAllowlist(emptyList);

        expectAllowedAfterSettingTemporaryList(allowlist, emptyList,
                NOT_ALLOWLISTED_ACTIVITY,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3,
                PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3
        );
        expectEffectiveActiviesAllowlist(allowlist);

        // One temporary activity
        List<ComponentName> listWithOne = asList(TEMP_ACTIVITY_1);

        allowlist.setTemporaryAllowlist(listWithOne);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithOne,
                TEMP_ACTIVITY_1);
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithOne,
                NOT_ALLOWLISTED_ACTIVITY, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);
        expectDefaultPermanentActivitiesNotAllowed(allowlist);
        expectEffectiveActiviesAllowlist(allowlist, TEMP_ACTIVITY_1);


        // Two temporary activities
        List<ComponentName> listWithOneAndTwo = asList(TEMP_ACTIVITY_1, TEMP_ACTIVITY_2);

        allowlist.setTemporaryAllowlist(listWithOneAndTwo);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithOneAndTwo,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2);
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithOneAndTwo,
                NOT_ALLOWLISTED_ACTIVITY, TEMP_ACTIVITY_3);
        expectDefaultPermanentActivitiesNotAllowed(allowlist);
        expectEffectiveActiviesAllowlist(allowlist, TEMP_ACTIVITY_1, TEMP_ACTIVITY_2);


        // Three temporary activities
        List<ComponentName> listWithThree =
                asList(TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);

        allowlist.setTemporaryAllowlist(listWithThree);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithThree,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithThree,
                NOT_ALLOWLISTED_ACTIVITY);
        expectDefaultPermanentActivitiesNotAllowed(allowlist);
        expectEffectiveActiviesAllowlist(allowlist,
                TEMP_ACTIVITY_1, TEMP_ACTIVITY_2, TEMP_ACTIVITY_3);

        // Reset the list
        allowlist.setTemporaryAllowlist(null);

        permanentAllowListStateChecker.run();
    }

    @Test
    public void testDump_emptyConfig() throws Exception {
        var allowlist = createAllowlist();

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: allowlisting disabled
                        permanent activities allowlist is empty.
                        temporary activities allowlist is not set.
                           """, DEBUG));
    }

    @Test
    public void testDump_configWithOneActivity() throws Exception {
        var allowlist = createAllowlist("allowlisted/I.am");

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 1 activity:
                          allowlisted/I.am
                        temporary activities allowlist is not set.
                           """, DEBUG));
    }

    @Test
    public void testDump_configWithMultipleActivities() throws Exception {
        var allowlist = createAllowlist("allowlisted/I.am", "so/am.I");

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
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
        var allowlist = createAllowlist(INVALID_NAME);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                        Dumpo:
                          DEBUG: %b
                          activities allowlist status: allowlisting disabled
                          permanent activities allowlist is empty.
                          temporary activities allowlist is not set.
                             """, DEBUG));
    }

    @Test
    public void testDump_configWithValidAndInvalidActivities() throws Exception {
        var allowlist = createAllowlist("allowlisted/I.am", "so/am.I", INVALID_NAME);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 2 activities:
                          allowlisted/I.am
                          so/am.I
                        temporary activities allowlist is not set.
                          """, DEBUG));
    }


    @Test
    public void testDump_configWithFullName() throws Exception {
        // It's created with full name, but should dump the "normalized" short name
        var allowlist = createAllowlist(FULL_NAME);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 1 activity:
                          i.am/.groot
                        temporary activities allowlist is not set.
                          """, DEBUG));
    }


    @Test
    public void testDump_configWithShortAndFullNames() throws Exception {
        // Both are equivalent, so dump should only display the "normalized" one
        var allowlist = createAllowlist(SHORT_NAME, FULL_NAME);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: using permanent allowlist
                        permanent activities allowlist has 1 activity:
                          i.am/.groot
                        temporary activities allowlist is not set.
                          """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistEmpty_emptyConfig() throws Exception {
        var allowlist = createAllowlist();
        allowlist.setTemporaryAllowlist(emptyList());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: allowlisting disabled
                        permanent activities allowlist is empty.
                        temporary activities allowlist is empty.
                           """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistEmpty_nonEmptyConfig() throws Exception {
        var allowlist = createAllowlist("allowlisted/I.am", "so/am.I");
        allowlist.setTemporaryAllowlist(emptyList());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
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
        var allowlist = createAllowlist();
        allowlist.setTemporaryAllowlist(asList(unflattenFromString("and/me.too")));

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        DEBUG: %b
                        activities allowlist status: using temporary allowlist
                        permanent activities allowlist is empty.
                        temporary activities allowlist has 1 activity:
                          and/me.too
                           """, DEBUG));
    }

    @Test
    public void testDump_temporaryAllowlistWithMultiple_nonEmptyConfig() throws Exception {
        var allowlist = createAllowlist("allowlisted/I.am", "so/am.I");
        allowlist.setTemporaryAllowlist(asList(
                unflattenFromString("and/me.too"),
                unflattenFromString("dont/forget.about.me")));

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
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

    private UserActivitiesAllowlist createAllowlist(String... configAllowlist) {
        return new UserActivitiesAllowlist(configAllowlist);
    }

    private void expectNotAllowed(UserActivitiesAllowlist allowlist, ComponentName... activities) {
        for (var activity : activities) {
            expectWithMessage("isActivityAllowed(%s)", activity)
                    .that(allowlist.isAllowed(activity))
                    .isFalse();
        }
    }

    private void expectAllowed(UserActivitiesAllowlist allowlist, ComponentName... activities) {
        for (var activity : activities) {
            expectWithMessage("isActivityAllowed(%s)", activity)
                    .that(allowlist.isAllowed(activity))
                    .isTrue();
        }
    }

    private void expectNotAllowedAfterSettingTemporaryList(UserActivitiesAllowlist allowlist,
            Collection<ComponentName> temporaryAllowList, ComponentName...activities) {
        for (var activity: activities) {
            expectWithMessage("isActivityAllowed(%s) after SetTemporaryActivitiesAllowlist(%s)",
                    activity, temporaryAllowList).that(allowlist.isAllowed(activity)).isFalse();
        }
    }

    private void expectEffectiveActiviesAllowlist(UserActivitiesAllowlist allowlist,
            ComponentName... activities) {
        expectWithMessage("getEffectiveActivitiesAllowlist()")
                .that(allowlist.getEffectiveAllowlist())
                .containsExactlyElementsIn(activities);
    }

    private void expectAllowedAfterSettingTemporaryList(UserActivitiesAllowlist allowlist,
            Collection<ComponentName> temporaryAllowList, ComponentName...activities) {
        for (var activity: activities) {
            expectWithMessage("isActivityAllowed(%s) after SetTemporaryActivitiesAllowlist(%s)",
                    activity, temporaryAllowList).that(allowlist.isAllowed(activity)).isTrue();
        }
    }

    private void expectDefaultPermanentActivitiesNotAllowed(UserActivitiesAllowlist allowlist) {
        expectNotAllowed(allowlist, PERM_ACTIVITY_1, PERM_ACTIVITY_2, PERM_ACTIVITY_3);
    }

    private static String dump(UserActivitiesAllowlist allowlist) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            allowlist.dump(new PrintWriter(sw), /* prefix= */ "", /* header= */ "Dumpo");
            return sw.toString();
        }
    }
}
