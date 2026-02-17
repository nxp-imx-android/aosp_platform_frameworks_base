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

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;
import static com.android.server.privatecompute.AuditModeTestUtils.assertEqualsToTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromFile;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromStream;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.privatecompute.AuditModeContext.Injector;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AuditModeContext}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class AuditModeContextTest {

    private static final String TEST_PACKAGE_NAME = "test_package";
    private static final int MAX_FILES = 3;
    private static final int MAX_SIZE_KILOBYTES = 1 * 1024; // 1 MB, otherwise the test is slow.

    private AuditModeContext mAuditModeContext;

    @Rule(order = 0) public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule(order = 1) public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Mock private Injector mInjector;

    @Before
    public void setUp() {
        when(mInjector.auditModeLogFileMaxSizeKb()).thenReturn(MAX_SIZE_KILOBYTES);
        when(mInjector.auditModeMaxLogFiles()).thenReturn(MAX_FILES);
        mAuditModeContext =
                new AuditModeContext(
                        newDirectExecutorService(),
                        newDirectExecutorService(),
                        mTemporaryFolder.getRoot(),
                        mInjector);
    }

    @Test
    public void testWriteToAuditLog_oneBundle_getsWrittenToDiskAndCanBeParsedBack()
            throws Exception {
        // Currently this test's implementation is identical to testStopAuditing_writesPendingData,
        // but in the future we might change how we write. These are two different behaviors that
        // need to be tested.
        mAuditModeContext.writeToAuditLog(getTestBundle(), TEST_PACKAGE_NAME);

        mAuditModeContext.stopAuditing(); // Triggers a write with pending data

        File file = mAuditModeContext.getCurrentAuditLogFile();
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    @Test
    public void testWriteToAuditLog_bufferFull_getsWrittenToDisk() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        }
        File file = mAuditModeContext.getCurrentAuditLogFile();
        assertThat(file.length()).isEqualTo(0); // no write yet

        // buffer full, triggers a write
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);

        assertThat(file.length()).isNotEqualTo(0); // write triggered
    }

    @Test
    public void testWriteToAuditLog_whenBufferFull_createsNewFile() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        }
        File file = mAuditModeContext.getCurrentAuditLogFile();

        // buffer full, triggers a write
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);

        File newFile = mAuditModeContext.getCurrentAuditLogFile();
        assertThat(newFile.getName()).isNotEqualTo(file.getName());
    }

    @Test
    public void testWriteToAuditLog_doesNotCreateMoreThanMaxFiles() throws Exception {
        // Arrange
        File newFolder = mTemporaryFolder.newFolder();
        AuditModeContext auditModeContext =
                new AuditModeContext(
                        newDirectExecutorService(),
                        newDirectExecutorService(),
                        newFolder,
                        mInjector);
        PersistableBundle testBundle = getTestBundle();
        File file0 = auditModeContext.getCurrentAuditLogFile();
        // This creates MAX_FILES files.
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < MAX_FILES * nEntriesToWrite; i++) {
            auditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        }
        PersistableBundle testBundle2 = testBundle.deepCopy();
        testBundle2.putInt("test_key2", 123);

        // Sanity check: the first file should be full, and contain only testBundles.
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file0);
        assertEquals(entries.size(), nEntriesToWrite);
        for (int i = 0; i < nEntriesToWrite; i++) {
            assertEqualsToTestBundle(entries.get(i).mData);
        }

        // Act: write one more bundle to the ringbuffer, then trigger a write.
        auditModeContext.writeToAuditLog(testBundle2, TEST_PACKAGE_NAME);
        auditModeContext.stopAuditing();

        // Assert: Should behave like a ringbuffer, and overwrite the first file.
        // First file only contains testBundle2.
        assertThat(newFolder.listFiles()).hasLength(MAX_FILES);
        File newFile = auditModeContext.getCurrentAuditLogFile();
        assertThat(newFile.getName()).isEqualTo("audit_log.0.bin");
        List<AuditLogEntry> entries2 = readAuditLogFileFromFile(newFile);
        assertEquals(entries2.size(), 1);
        assertEquals(entries2.get(0).mData.getInt("test_key2"), 123);
    }

    @Test
    public void testWriteToAuditLog_overwritesFileDoesNotAppend() throws Exception {
        File file = mAuditModeContext.getCurrentAuditLogFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(new byte[] {1, 2, 3}); // left-over file with garbage data
            fileOutputStream.flush();
        }
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        }

        // buffer full, triggers a write
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);

        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        List<AuditLogEntry> entries = readAuditLogFileFromStream(stream);
        assertEquals(nEntriesToWrite, entries.size()); // should already be sufficient
        assertEqualsToTestBundle(entries.get(0).mData); // sanity-check: first bundle is correct
    }

    @Test
    public void testWriteToAuditLog_correctlySetsPackageName() throws Exception {
        String packageName = "package_name";
        PersistableBundle testBundle = getTestBundle();
        File file = mAuditModeContext.getCurrentAuditLogFile();

        mAuditModeContext.writeToAuditLog(testBundle, packageName);

        mAuditModeContext.stopAuditing(); // flushes 1 log entry to disk
        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        List<AuditLogEntry> entries = readAuditLogFileFromStream(stream);
        assertEquals(1, entries.size());
        assertEquals(entries.get(0).mCallingPackage, packageName);
    }

    @Test
    public void testStopAuditing_writesPendingData() throws Exception {
        mAuditModeContext.writeToAuditLog(getTestBundle(), TEST_PACKAGE_NAME);

        mAuditModeContext.stopAuditing();

        File file = mAuditModeContext.getCurrentAuditLogFile();
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    @Test
    public void testStopAuditing_whenStopped_noMoreDataIsWritten() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        mAuditModeContext.stopAuditing(); // flushes 1 log entry to disk
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);

        // nEntriesToWrite writes, would trigger a write if not for the stopAuditing()
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        }

        File file = mAuditModeContext.getCurrentAuditLogFile();
        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        List<AuditLogEntry> entries = readAuditLogFileFromStream(stream);
        assertEquals(1, entries.size());
    }

    @Test
    public void testWriteToAuditLog_realExecutors_bundleGetsWrittenToDiskAndCanBeParsedBack()
            throws Exception {
        // Real executors.
        ExecutorService serializerExecutor = AuditModeContext.getBundleSerializerExecutorService();
        ExecutorService writerExecutor = AuditModeContext.getDiskWriterExecutorService();
        mAuditModeContext =
                new AuditModeContext(
                        serializerExecutor, writerExecutor, mTemporaryFolder.getRoot(), mInjector);
        mAuditModeContext.writeToAuditLog(getTestBundle(), TEST_PACKAGE_NAME);
        serializerExecutor.awaitTermination(10, TimeUnit.SECONDS); // Wait for the pending tasks.
        writerExecutor.awaitTermination(10, TimeUnit.SECONDS); // Wait for the pending write.

        mAuditModeContext.stopAuditing(); // Triggers a write with pending data

        File file = mAuditModeContext.getCurrentAuditLogFile();
        assertThat(file.toPath().toString()).contains("audit_log.0.bin");
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
        serializerExecutor.shutdown();
        serializerExecutor.awaitTermination(1, TimeUnit.SECONDS);
        writerExecutor.shutdown();
        writerExecutor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testWriteToAuditLog_realExecutors_writeIsAsync() throws Exception {
        // Real executors.
        ExecutorService serializerExecutor = AuditModeContext.getBundleSerializerExecutorService();
        ExecutorService writerExecutor = AuditModeContext.getDiskWriterExecutorService();
        mAuditModeContext =
                new AuditModeContext(
                        serializerExecutor, writerExecutor, mTemporaryFolder.getRoot(), mInjector);
        CountDownLatch slowTaskStarted = new CountDownLatch(1);
        CountDownLatch allowSlowTaskToFinish = new CountDownLatch(1);
        serializerExecutor.execute( // Add a slow task to the serializerExecutor
                () -> {
                    slowTaskStarted.countDown(); // Signal that we have occupied the thread
                    try {
                        // Wait until the test says we can finish.
                        boolean ignored = allowSlowTaskToFinish.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        slowTaskStarted.await(1, TimeUnit.SECONDS); // Wait until the slow task has started
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        }

        // Act: Triggers a write by the serializerExecutor, who is busy with the slow task.
        long startTime = System.currentTimeMillis();
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME);
        long endTime = System.currentTimeMillis();

        // Assert: The write should be fast, even though the executor is busy with the slow task.
        assertThat(endTime - startTime).isLessThan(50L);

        // Cleanup: Let the background thread finish so we don't leak threads.
        allowSlowTaskToFinish.countDown();
        serializerExecutor.shutdown();
        serializerExecutor.awaitTermination(1, TimeUnit.SECONDS);
        writerExecutor.shutdown();
        writerExecutor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testSerializeAndWrite_invalidBundle_throwsSecurityExceptionAndDoesNotWrite()
            throws Exception {
        PersistableBundle invalidBundle = getDeepNestedBundle101();
        AuditLogEntry entry = new AuditLogEntry(invalidBundle, 234L, TEST_PACKAGE_NAME, 123);

        assertThrows(
                SecurityException.class,
                () -> mAuditModeContext.serializeAndWrite(entry));

        mAuditModeContext.stopAuditing();

        File file = mAuditModeContext.getCurrentAuditLogFile();
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 0);
    }

    /** Create a nested bundle of depth 101. */
    private static PersistableBundle getDeepNestedBundle101() {
        PersistableBundle rootBundle = new PersistableBundle();

        PersistableBundle currentBundle = rootBundle;
        for (int i = 0; i < 100; i++) {
            PersistableBundle newInnerBundle = new PersistableBundle();
            currentBundle.putPersistableBundle("NESTED_BUNDLE_KEY", newInnerBundle);
            currentBundle = newInnerBundle; // Move down one level
        }

        currentBundle.putPersistableBundle("NESTED_BUNDLE_KEY", new PersistableBundle());

        return rootBundle;
    }
}
