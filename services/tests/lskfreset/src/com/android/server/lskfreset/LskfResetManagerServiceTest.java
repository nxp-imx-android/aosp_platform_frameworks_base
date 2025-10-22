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

package com.android.server.lskfreset;

import static org.junit.Assert.assertNotNull;

import android.app.lskfreset.ILskfResetManager;
import android.app.lskfreset.ILskfResetSession;
import android.app.lskfreset.flags.Flags;
import android.content.Context;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LskfResetManagerServiceTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext;
    private LskfResetManagerService mService;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mService = new LskfResetManagerService(mContext);
        mService.onStart();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testServiceStarts() {
        ILskfResetManager manager = mService.getBinderService();
        assertNotNull(manager);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LSKF_RESET_MANAGER)
    public void testCreateSession() throws Exception {
        ILskfResetManager manager = mService.getBinderService();
        ILskfResetSession session = manager.createLskfResetSession(0);
        assertNotNull(session);
    }
}
