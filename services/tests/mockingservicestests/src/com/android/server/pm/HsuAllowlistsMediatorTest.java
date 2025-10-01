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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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

public final class HsuAllowlistsMediatorTest extends ExpectableTestCase {
    private static final String TAG = HsuAllowlistsMediatorTest.class.getSimpleName();

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
    public void testIsActivityAllowed_emptyConfig() throws Exception {
        var ham = createHam();

        expectWithMessage("ActivityStarterTests(null)").that(ham.isActivityAllowed(null))
                .isTrue();

        expectWithMessage("ActivityStarterTests(whatever)").that(ham.isActivityAllowed("whatever"))
                .isTrue();
    }

    @Test
    public void testIsActivityAllowed_nonEmptyConfig() throws Exception {
        var allowlisted = "allowlisted/I.am";
        var notAllowlisted = "allowlisted/I.am...NOT";
        var ham = createHam(allowlisted);

        expectWithMessage("ActivityStarterTests(null)").that(ham.isActivityAllowed(null))
                .isFalse();
        expectWithMessage("isActivitivyAllowed(%s)", allowlisted)
                .that(ham.isActivityAllowed(allowlisted))
                .isTrue();
        expectWithMessage("isActivitivyAllowed(%s)", notAllowlisted)
                .that(ham.isActivityAllowed(notAllowlisted))
                .isFalse();
    }

    @Test
    public void testDump_emptyConfig() throws Exception {
        var ham = createHam();

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo("""
                      HsuAllowlistsMediator (HAM)
                        0 permanently allowlisted activities
                           """);
    }

    @Test
    public void testDump_configWithOneActivity() throws Exception {
        var ham = createHam("allowlisted/I.am");

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo("""
                      HsuAllowlistsMediator (HAM)
                        1 permanently allowlisted activities:
                          allowlisted/I.am
                          """);
    }

    @Test
    public void testDump_configWithMultipleActivities() throws Exception {
        var ham = createHam("allowlisted/I.am", "so/am.I");

        String dump = dump(ham);

        expectWithMessage("dump()").that(dump)
                .isEqualTo("""
                      HsuAllowlistsMediator (HAM)
                        2 permanently allowlisted activities:
                          allowlisted/I.am
                          so/am.I
                          """);

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

    private static String dump(HsuAllowlistsMediator ham) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            ham.dump(new PrintWriter(sw), /* args= */ null);
            return sw.toString();
        }
    }
}
