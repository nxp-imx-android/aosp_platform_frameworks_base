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

package com.android.server.privatecompute;

import static com.google.common.truth.Truth.assertThat;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Unit tests for {@link PccSandboxManagerServiceAuditModeContextTest}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerServiceAuditModeContextTest {

    private static final String KEY_BOOLEAN = "boolean";
    private static final boolean VALUE_BOOLEAN = true;
    private static final String KEY_DOUBLE = "double";
    private static final double VALUE_DOUBLE = 1.0;
    private static final String KEY_INT = "int";
    private static final int VALUE_INT = 10;
    private static final String KEY_LONG = "long";
    private static final long VALUE_LONG = 1L;
    private static final String KEY_STRING = "string";
    private static final String VALUE_STRING = "test";
    private static final String KEY_BOOLEAN_ARRAY = "boolean_array";
    private static final boolean[] VALUE_BOOLEAN_ARRAY = new boolean[] {true, false};
    private static final String KEY_INT_ARRAY = "int_array";
    private static final int[] VALUE_INT_ARRAY = new int[] {10, 20};
    private static final String KEY_LONG_ARRAY = "long_array";
    private static final long[] VALUE_LONG_ARRAY = new long[] {1L, 2L};
    private static final String KEY_STRING_ARRAY = "string_array";
    private static final String[] VALUE_STRING_ARRAY = new String[] {"test", "test2"};

    private ByteArrayOutputStream mOutputStream;
    private AuditModeContext mAuditModeContext;

    @Before
    public void setUp() {
        mOutputStream = new ByteArrayOutputStream();
        mAuditModeContext = AuditModeContext.create(newDirectExecutorService(), mOutputStream);
    }

    @Test
    public void testWriteToAuditLog_oneBundle_getsWrittenToDisk() throws Exception {
        // Currently this test's implementation is identical to testStop_writesPendingData,
        // but in the future we might change how we write. These are two different behaviors that
        // need to be tested.
        mAuditModeContext.writeToAuditLog(getTestBundle());

        mAuditModeContext.stopAuditing(); // Triggers a write with pending data

        List<PersistableBundle> bundles = readBundlesFromStream(mOutputStream);
        assertEquals(bundles.size(), 1);
        assertEqualsToTestBundle(bundles.get(0));
    }

    @Test
    public void testWriteToAuditLog_bufferFull_getsWrittenToDisk() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        for (int i = 0; i < AuditModeContext.AUDIT_LOG_QUEUE_CAPACITY; i++) {
            mAuditModeContext.writeToAuditLog(testBundle);
        }
        assertThat(mOutputStream.size()).isEqualTo(0); // no write yet
        mAuditModeContext.writeToAuditLog(testBundle); // buffer full, triggers a write
        assertThat(mOutputStream.size()).isNotEqualTo(0);
    }

    @Test
    public void testStop_closesOutputStream() throws Exception {
        OutputStream outputStream = mock(OutputStream.class);
        mAuditModeContext = AuditModeContext.create(newDirectExecutorService(), outputStream);

        mAuditModeContext.stopAuditing();

        verify(outputStream).close();
    }

    @Test
    public void testStop_writesPendingData() throws Exception {
        mAuditModeContext.writeToAuditLog(getTestBundle());

        mAuditModeContext.stopAuditing();

        List<PersistableBundle> bundles = readBundlesFromStream(mOutputStream);
        assertEquals(bundles.size(), 1);
        assertEqualsToTestBundle(bundles.get(0));
    }

    @Test
    public void testWriteBundlesToStream_oneBundle_canBeParsedBack() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<PersistableBundle> input = new ArrayList<>();
        input.add(getTestBundle());
        mAuditModeContext.writeBundlesToStream(input, outputStream);

        List<PersistableBundle> output = readBundlesFromStream(outputStream);

        assertEquals(output.size(), 1);
        assertEqualsToTestBundle(output.get(0));
    }

    @Test
    public void testWriteBundlesToStream_twoWrites_canBeParsedBack() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<PersistableBundle> input1 = new ArrayList<>();
        input1.add(getTestBundle());
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        List<PersistableBundle> input2 = new ArrayList<>();
        input2.add(bundle2);
        mAuditModeContext.writeBundlesToStream(input1, outputStream);
        mAuditModeContext.writeBundlesToStream(input2, outputStream);

        List<PersistableBundle> output = readBundlesFromStream(outputStream);

        assertEquals(output.size(), 2);
        assertEqualsToTestBundle(output.get(0));
        assertEquals(output.get(1).getInt("test_key2"), 123);
    }

    private PersistableBundle getTestBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(KEY_BOOLEAN, VALUE_BOOLEAN);
        bundle.putDouble(KEY_DOUBLE, VALUE_DOUBLE);
        bundle.putInt(KEY_INT, VALUE_INT);
        bundle.putLong(KEY_LONG, VALUE_LONG);
        bundle.putString(KEY_STRING, VALUE_STRING);
        bundle.putBooleanArray(KEY_BOOLEAN_ARRAY, VALUE_BOOLEAN_ARRAY);
        bundle.putIntArray(KEY_INT_ARRAY, VALUE_INT_ARRAY);
        bundle.putLongArray(KEY_LONG_ARRAY, VALUE_LONG_ARRAY);
        bundle.putStringArray(KEY_STRING_ARRAY, VALUE_STRING_ARRAY);
        // TODO: Test for byte[] array once ag/36835652 is merged.
        // TODO: Test for nested bundles.
        return bundle;
    }

    // Needed because .equals() on Bundle doesn't work for inner arrays.
    private void assertEqualsToTestBundle(PersistableBundle bundle) {
        assertEquals(9, bundle.size());
        assertEquals(VALUE_BOOLEAN, bundle.getBoolean(KEY_BOOLEAN));
        assertEquals(VALUE_DOUBLE, bundle.getDouble(KEY_DOUBLE), 0.0);
        assertEquals(VALUE_INT, bundle.getInt(KEY_INT));
        assertEquals(VALUE_LONG, bundle.getLong(KEY_LONG));
        assertEquals(VALUE_STRING, bundle.getString(KEY_STRING));
        assertArrayEquals(VALUE_BOOLEAN_ARRAY, bundle.getBooleanArray(KEY_BOOLEAN_ARRAY));
        assertArrayEquals(VALUE_INT_ARRAY, bundle.getIntArray(KEY_INT_ARRAY));
        assertArrayEquals(VALUE_LONG_ARRAY, bundle.getLongArray(KEY_LONG_ARRAY));
        assertArrayEquals(VALUE_STRING_ARRAY, bundle.getStringArray(KEY_STRING_ARRAY));
        // TODO: Test for byte[] array once ag/36835652 is merged.
        // TODO: Test for nested bundles.
    }

    /** Reads a list of length-prefixed PersistableBundle from an output stream. */
    private List<PersistableBundle> readBundlesFromStream(ByteArrayOutputStream stream)
            throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(stream.toByteArray());
        List<PersistableBundle> result = new ArrayList<>();
        while (inputStream.available() > 0) {
            byte[] size = new byte[4];
            int bytesRead = inputStream.read(size, 0, 4);
            if (bytesRead < 4) {
                // Avoids EOFException if stream ends unexpectedly
                break;
            }
            int sizeInt = ByteBuffer.wrap(size).getInt();
            byte[] dataBytes = new byte[sizeInt];
            inputStream.read(dataBytes, 0, sizeInt);
            ByteArrayInputStream dataStream = new ByteArrayInputStream(dataBytes);
            result.add(PersistableBundle.readFromStream(dataStream));
        }
        return result;
    }
}
