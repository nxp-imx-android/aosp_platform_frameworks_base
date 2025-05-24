/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.crashrecovery;



import static com.google.common.truth.Truth.assertThat;

import static org.mockito.quality.Strictness.LENIENT;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Environment;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;


/**
 * Test CrashRecovery Utils.
 */
@RunWith(AndroidJUnit4.class)
public class CrashRecoveryUtilsTest {

    private MockitoSession mStaticMockSession;
    private final String mLogMsg = "Logging from test";
    private final String mCrashrecoveryEventTag = "CrashRecovery Events: ";
    private File mCacheDir;

    @Before
    public void setup() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        mCacheDir = context.getCacheDir();
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .spyStatic(Environment.class)
                .strictness(LENIENT)
                .startMocking();
        ExtendedMockito.doReturn(mCacheDir).when(() -> Environment.getDataDirectory());

        createCrashRecoveryEventsTempDir();
        createKeyValuesTempDir();
    }

    @After
    public void tearDown() throws IOException {
        mStaticMockSession.finishMocking();
        deleteCrashRecoveryEventsTempFile();
        deleteKeyValuesTempFile();
    }

    @Test
    public void testCrashRecoveryUtils() {
        testLogCrashRecoveryEvent();
        testDumpCrashRecoveryEvents();
    }

    @Test
    public void testDumpCrashRecoveryEventsWithoutAnyLogs() {
        assertThat(getCrashRecoveryEventsTempFile().exists()).isFalse();
        StringWriter sw = new StringWriter();
        IndentingPrintWriter ipw = new IndentingPrintWriter(sw, "  ");
        CrashRecoveryUtils.dumpCrashRecoveryEvents(ipw);
        ipw.close();

        String dump = sw.getBuffer().toString();
        assertThat(dump).contains(mCrashrecoveryEventTag);
        assertThat(dump).doesNotContain(mLogMsg);
    }

    @Test
    public void testKeyValuesUtils() throws IOException {
        SparseArray<String> keyValues = new SparseArray<>();
        keyValues.put(123, "com.android.foo");
        keyValues.put(456, "com.android.bar");
        keyValues.put(789, "com.android.zoo");

        testPutKeyValue(keyValues);
        testReadAllKeyValues(keyValues);

        SparseArray<String> newKeyValues = new SparseArray<>();
        newKeyValues.put(123, "com.android.foo2");
        newKeyValues.put(222, "com.android.bar2");

        testWriteAllKeyValues(newKeyValues);
        testReadAllKeyValues(newKeyValues);
    }

    private void testLogCrashRecoveryEvent() {
        assertThat(getCrashRecoveryEventsTempFile().exists()).isFalse();
        CrashRecoveryUtils.logCrashRecoveryEvent(Log.WARN, mLogMsg);

        assertThat(getCrashRecoveryEventsTempFile().exists()).isTrue();
        String fileContent = null;
        try {
            File file = getCrashRecoveryEventsTempFile();
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            fileContent = new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Unable to read the events file");
        }
        assertThat(fileContent).contains(mLogMsg);
    }

    private void testDumpCrashRecoveryEvents() {
        StringWriter sw = new StringWriter();
        IndentingPrintWriter ipw = new IndentingPrintWriter(sw, "  ");
        CrashRecoveryUtils.dumpCrashRecoveryEvents(ipw);
        ipw.close();

        String dump = sw.getBuffer().toString();
        assertThat(dump).contains(mCrashrecoveryEventTag);
        assertThat(dump).contains(mLogMsg);
    }

    private void testPutKeyValue(SparseArray<String> keyValues) throws IOException {
        final File keyValuesTempFile = getKeyValuesTempFile();
        assertThat(keyValuesTempFile.exists()).isFalse();

        if (keyValues.size() == 0) {
            return;
        }

        for (int i = 0; i < keyValues.size(); i++) {
            int key = keyValues.keyAt(i);
            String value = keyValues.get(key);
            CrashRecoveryUtils.putKeyValue(keyValuesTempFile, key, value);
        }

        final Path keyValuesTempFilePath = Paths.get(keyValuesTempFile.getAbsolutePath());
        try (Stream<String> lines = Files.lines(keyValuesTempFilePath, StandardCharsets.UTF_8)) {
            assertThat(lines.count()).isEqualTo(keyValues.size());
        }
    }

    private void testWriteAllKeyValues(SparseArray<String> keyValues) throws IOException {
        final File keyValuesTempFile = getKeyValuesTempFile();
        CrashRecoveryUtils.writeAllKeyValues(keyValuesTempFile, keyValues);

        final Path keyValuesTempFilePath = Paths.get(keyValuesTempFile.getAbsolutePath());
        try (Stream<String> lines = Files.lines(keyValuesTempFilePath, StandardCharsets.UTF_8)) {
            assertThat(lines.count()).isEqualTo(keyValues.size());
        }
    }

    private void testReadAllKeyValues(SparseArray<String> keyValues) {
        final File keyValuesTempFile = getKeyValuesTempFile();
        SparseArray<String> readKeyValues = CrashRecoveryUtils.readAllKeyValues(keyValuesTempFile);
        assertThat(readKeyValues.size()).isEqualTo(keyValues.size());
        for (int i = 0; i < keyValues.size(); i++) {
            int key = keyValues.keyAt(i);
            assertThat(readKeyValues.get(key)).isEqualTo(keyValues.get(key));
        }
    }

    private void createCrashRecoveryEventsTempDir() throws IOException {
        Files.deleteIfExists(getCrashRecoveryEventsTempFile().toPath());
        File mMockDirectory = new File(mCacheDir, "system");
        if (!mMockDirectory.exists()) {
            assertThat(mMockDirectory.mkdir()).isTrue();
        }
    }

    private void deleteCrashRecoveryEventsTempFile() throws IOException {
        Files.deleteIfExists(getCrashRecoveryEventsTempFile().toPath());
    }

    private File getCrashRecoveryEventsTempFile() {
        File systemTempDir = new File(mCacheDir, "system");
        return new File(systemTempDir, "crashrecovery-events.txt");
    }

    private void createKeyValuesTempDir() throws IOException {
        deleteKeyValuesTempFile();
        File mockDirectory = new File(mCacheDir, "rollback-observer");
        if (!mockDirectory.exists()) {
            assertThat(mockDirectory.mkdir()).isTrue();
        }
    }

    private void deleteKeyValuesTempFile() throws IOException {
        Files.deleteIfExists(getKeyValuesTempFile().toPath());
    }

    private File getKeyValuesTempFile() {
        File keyValuesTempDir = new File(mCacheDir, "rollback-observer");
        return new File(keyValuesTempDir, "key-values");
    }
}
