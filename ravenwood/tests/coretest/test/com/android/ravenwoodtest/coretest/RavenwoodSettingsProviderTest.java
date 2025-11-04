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
package com.android.ravenwoodtest.coretest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.ravenwood.RavenwoodUnsupportedApiException;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public class RavenwoodSettingsProviderTest {

    private Context mContext;
    private ContentResolver mCr;

    @Before
    public void setup() {
        mContext =  InstrumentationRegistry.getInstrumentation().getContext();
        mCr = mContext.getContentResolver();
    }

    @Test
    public void testGetValue() {
        assertThrows(SettingNotFoundException.class,
                () -> Global.getInt(mCr, Settings.Global.AIRPLANE_MODE_ON));

        assertEquals(999, Global.getInt(mCr, Global.AIRPLANE_MODE_ON, 999));

        assertEquals(null, Secure.getString(mCr, Secure.AUTOFILL_SERVICE));

        assertEquals(1, System.getInt(mCr, System.MIN_REFRESH_RATE, 1));
    }

    @Test
    public void testPutValue() {
        try {
            Settings.Global.putInt(mCr, Settings.Global.AIRPLANE_MODE_ON, 999);
            fail("putInt should not be allowed");
        } catch (RavenwoodUnsupportedApiException e) {
            assertTrue(e.getReason().contains("SettingsProvider#call"));
        }
    }

    @Test
    public void testTestableSettingsProvider() {
        var context = new TestableContext(mContext);
        var cr = context.getContentResolver();
        assertEquals(999, Global.getInt(cr, Global.AIRPLANE_MODE_ON, 999));

        Global.putInt(cr, Global.AIRPLANE_MODE_ON, 1000);

        assertEquals(1000, Global.getInt(cr, Global.AIRPLANE_MODE_ON, 999));

        Secure.putString(cr, Secure.AUTOFILL_SERVICE, "abc");
        assertEquals("abc", Secure.getString(cr, Secure.AUTOFILL_SERVICE));

        Secure.putInt(cr, System.MIN_REFRESH_RATE, 3);
        assertEquals(3, Secure.getInt(cr, System.MIN_REFRESH_RATE, 0));
    }
}
