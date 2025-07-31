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

package com.android.server.display;

import static org.mockito.Mockito.verify;

import android.hardware.display.DisplayManagerGlobal;
import android.util.SparseIntArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.FrameworkStatsLog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link com.android.server.display.DisplayFrameworkStatsLogger}.
 *
 * <p>Build with: atest DisplayFrameworkStatsLoggerTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayFrameworkStatsLoggerTest {

    @InjectMocks private DisplayFrameworkStatsLogger mLogger;

    @Mock private FrameworkStatsLog mFrameworkStatsLogMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testLogDisplayEvent_displayAdded_writesToStatsLog() {
        final int event = DisplayManagerGlobal.EVENT_DISPLAY_ADDED;
        final SparseIntArray uidMap =
                new SparseIntArray() {
                    {
                        put(1001, 1);
                        put(1002, 3);
                    }
                };
        final int expectedProtoType =
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_ADDED;

        mLogger.logDisplayEvent(event, uidMap);

        verify(mFrameworkStatsLogMock)
                .write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        uidMap.copyKeys());
    }

    @Test
    public void testLogDisplayEvent_brightnessChanged_writesToStatsLog() {
        final int event = DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED;
        final SparseIntArray uidMap =
                new SparseIntArray() {
                    {
                        put(1005, 1);
                    }
                };
        final int expectedProtoType =
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_BRIGHTNESS_CHANGED;

        mLogger.logDisplayEvent(event, uidMap);

        verify(mFrameworkStatsLogMock)
                .write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        uidMap.copyKeys());
    }

    @Test
    public void testLogDisplayEvent_unknownEvent_writesUnknownTypeToStatsLog() {
        final int event = -1;
        final SparseIntArray uidMap =
                new SparseIntArray() {
                    {
                        put(9999, 6);
                    }
                };
        final int expectedProtoType =
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_UNKNOWN;

        mLogger.logDisplayEvent(event, uidMap);

        verify(mFrameworkStatsLogMock)
                .write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        uidMap.copyKeys());
    }
}
