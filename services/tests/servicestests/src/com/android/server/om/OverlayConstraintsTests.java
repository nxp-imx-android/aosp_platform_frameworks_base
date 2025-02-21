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

package com.android.server.om;

import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.content.om.OverlayConstraint.TYPE_DEVICE_ID;
import static android.content.om.OverlayConstraint.TYPE_DISPLAY_ID;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.testng.Assert.assertThrows;

import android.content.om.FabricatedOverlay;
import android.content.om.OverlayConstraint;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.res.Flags;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.TypedValue;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(JUnitParamsRunner.class)
public class OverlayConstraintsTests {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private OverlayManager mOverlayManager;
    private UserHandle mUserHandle;
    private OverlayIdentifier mOverlayIdentifier = null;

    @Before
    public void setUp() throws Exception {
        mOverlayManager = getApplicationContext().getSystemService(OverlayManager.class);
        mUserHandle = UserHandle.of(UserHandle.myUserId());
    }

    @After
    public void tearDown() throws Exception {
        if (mOverlayIdentifier != null) {
            OverlayManagerTransaction transaction =
                    new OverlayManagerTransaction.Builder()
                            .unregisterFabricatedOverlay(mOverlayIdentifier)
                            .build();
            mOverlayManager.commit(transaction);
            mOverlayIdentifier = null;
        }
    }

    @Test
    public void createOverlayConstraint_withInvalidType_fails() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverlayConstraint(500 /* type */, 1 /* value */));
    }

    @Test
    public void createOverlayConstraint_withInvalidValue_fails() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverlayConstraint(TYPE_DEVICE_ID, -1 /* value */));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithNullConstraints_fails() {
        FabricatedOverlay fabricatedOverlay = createFabricatedOverlay();
        assertThrows(NullPointerException.class,
                () -> mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(fabricatedOverlay)
                        .setEnabled(fabricatedOverlay.getIdentifier(), true /* enable */,
                                null /* constraints */)
                        .build()));
    }

    @Test
    @Parameters(method = "getAllConstraintLists")
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_writesConstraintsIntoOverlayInfo(
            List<OverlayConstraint> constraints) throws Exception {
        enableOverlay(constraints);

        OverlayInfo overlayInfo = mOverlayManager.getOverlayInfo(mOverlayIdentifier, mUserHandle);
        assertNotNull(overlayInfo);
        assertEquals(constraints, overlayInfo.getConstraints());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void disableOverlayWithConstraints_fails() throws Exception {
        FabricatedOverlay fabricatedOverlay = createFabricatedOverlay();
        assertThrows(SecurityException.class,
                () -> mOverlayManager.commit(new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(fabricatedOverlay)
                        .setEnabled(fabricatedOverlay.getIdentifier(), false /* enable */,
                                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, DEFAULT_DISPLAY)))
                        .build()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithNewConstraints_updatesConstraintsIntoOverlayInfo()
            throws Exception {
        List<OverlayConstraint> constraints1 =
                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, 1 /* value*/));
        enableOverlay(constraints1);

        OverlayInfo overlayInfo1 = mOverlayManager.getOverlayInfo(mOverlayIdentifier, mUserHandle);
        assertNotNull(overlayInfo1);
        assertEquals(constraints1, overlayInfo1.getConstraints());

        List<OverlayConstraint> constraints2 = List.of(
                new OverlayConstraint(TYPE_DISPLAY_ID, 2 /* value */));
        enableOverlay(constraints2);

        OverlayInfo overlayInfo2 = mOverlayManager.getOverlayInfo(mOverlayIdentifier, mUserHandle);
        assertNotNull(overlayInfo2);
        assertEquals(overlayInfo1.overlayName, overlayInfo2.overlayName);
        assertEquals(overlayInfo1.targetPackageName, overlayInfo2.targetPackageName);
        assertEquals(overlayInfo1.packageName, overlayInfo2.packageName);
        assertEquals(constraints2, overlayInfo2.getConstraints());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_RRO_CONSTRAINTS)
    public void enableOverlayWithConstraints_fails() throws Exception {
        assertThrows(SecurityException.class, () -> enableOverlay(
                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, DEFAULT_DISPLAY))));
    }

    private FabricatedOverlay createFabricatedOverlay() {
        String packageName = getApplicationContext().getPackageName();
        FabricatedOverlay fabricatedOverlay = new FabricatedOverlay.Builder(
                packageName, "testOverlay" /* name */, packageName)
                .build();
        fabricatedOverlay.setResourceValue("string/module_2_name" /* resourceName */,
                TypedValue.TYPE_STRING, "hello" /* value */, null /* configuration */);
        return fabricatedOverlay;
    }

    private void enableOverlay(List<OverlayConstraint> constraints) {
        FabricatedOverlay fabricatedOverlay = createFabricatedOverlay();
        OverlayManagerTransaction transaction =
                new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(fabricatedOverlay)
                        .setEnabled(fabricatedOverlay.getIdentifier(), true /* enable */,
                                constraints)
                        .build();
        mOverlayManager.commit(transaction);
        mOverlayIdentifier = fabricatedOverlay.getIdentifier();
    }

    private static List<OverlayConstraint>[] getAllConstraintLists() {
        return new List[]{
                Collections.emptyList(),
                List.of(new OverlayConstraint(TYPE_DISPLAY_ID, DEFAULT_DISPLAY)),
                List.of(new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID_DEFAULT)),
                List.of(new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID_DEFAULT),
                        new OverlayConstraint(TYPE_DEVICE_ID, DEVICE_ID_DEFAULT))
        };
    }
}
