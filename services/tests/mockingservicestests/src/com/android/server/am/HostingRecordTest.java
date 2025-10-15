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

package com.android.server.am;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostingRecordTest {
    @Test
    public void testUsesNativeAppZygote() {
        String packageName = "com.android.app";
        ComponentName cn = new ComponentName(packageName, "com.android.app.Component");
        int uid = 10000;
        String processName = "com.android.app.process";

        HostingRecord nativeServiceRecord =
                HostingRecord.byAppZygote(cn, packageName, uid, processName,
                                          /*isNativeService=*/ true);
        assertTrue(nativeServiceRecord.usesAppZygote());
        assertTrue(nativeServiceRecord.usesNativeAppZygote());

        HostingRecord managedAppZygoteRecord =
                HostingRecord.byAppZygote(cn, packageName, uid, processName,
                                          /*isNativeService=*/ false);
        assertTrue(managedAppZygoteRecord.usesAppZygote());
        assertFalse(managedAppZygoteRecord.usesNativeAppZygote());
    }
}
