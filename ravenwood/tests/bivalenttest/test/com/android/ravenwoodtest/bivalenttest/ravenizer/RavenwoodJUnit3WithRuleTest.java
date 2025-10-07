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
package com.android.ravenwoodtest.bivalenttest.ravenizer;

import android.os.SystemProperties;
import android.platform.test.ravenwood.RavenwoodRule;

import junit.framework.TestCase;

public class RavenwoodJUnit3WithRuleTest extends TestCase implements RavenwoodRule.Provider {
    public static final String TAG = "RavenwoodJUnit3WithRuleTest";

    @Override
    public RavenwoodRule getRavenwoodRule() {
        return new RavenwoodRule.Builder()
                .setSystemPropertyImmutable("a.b.c", "foo")
                .build();
    }

    public void testProperty() {
        assertEquals("foo", SystemProperties.get("a.b.c"));
    }
}
