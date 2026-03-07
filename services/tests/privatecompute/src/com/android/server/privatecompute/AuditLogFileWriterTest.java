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
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_PACKAGE_NAME;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_TIMESTAMP;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_UID;
import static com.android.server.privatecompute.AuditModeTestUtils.assertEqualsToTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestEntry;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromFile;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link AuditLogFileWriter}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class AuditLogFileWriterTest {

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void writeEntries_prependsTheVersionNumber() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);

        writer.writeEntries(new ArrayList<>());

        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        assertEquals(stream.readInt(), 0); // version
    }

    @Test
    public void writeEntries_oneEntry_canBeParsedBack() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        long timestamp = 1764409688L; // 2025-11-29 09:48:08 GMT
        String packageName = "test_package";
        int uid = 12;
        AuditLogEntry entry = new AuditLogEntry(getTestBundle(), timestamp, packageName, uid);

        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertEquals(entries.get(0).mTimestamp, timestamp);
        assertEquals(entries.get(0).mCallingPackage, packageName);
        assertEquals(entries.get(0).mCallingUid, uid);
    }

    @Test
    public void writeEntries_twoEntries_canBeParsedBack() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        AuditLogEntry entry1 = getTestEntry();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, "other_package", 23);

        writer.writeEntries(ImmutableList.of(entry1.toByteArray(), entry2.toByteArray()));

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 2);
        assertEquals(entries.get(0).mTimestamp, TEST_TIMESTAMP);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, TEST_UID);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertEquals(entries.get(1).mTimestamp, 234L);
        assertEquals(entries.get(1).mCallingPackage, "other_package");
        assertEquals(entries.get(1).mCallingUid, 23);
        assertThat(entries.get(1).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void writeEntries_secondWritesOverwritesFile_sameInstance() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogEntry entry = getTestEntry();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, "other_package", 23);
        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        writer.writeEntries(ImmutableList.of(entry2.toByteArray())); // 2nd write to same instance

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).mTimestamp, 234L);
        assertEquals(entries.get(0).mCallingPackage, "other_package");
        assertEquals(entries.get(0).mCallingUid, 23);
        assertThat(entries.get(0).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void writeEntries_secondWritesOverwritesFile_newInstance() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogEntry entry = getTestEntry();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, "other_package", 23);
        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        AuditLogFileWriter writer2 = new AuditLogFileWriter(file); // new instance, same file
        writer2.writeEntries(ImmutableList.of(entry2.toByteArray()));

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).mTimestamp, 234L);
        assertEquals(entries.get(0).mCallingPackage, "other_package");
        assertEquals(entries.get(0).mCallingUid, 23);
        assertThat(entries.get(0).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void writeEntries_createsDirectoryIfItDoesNotExist()
            throws Exception {
        File newDir = new File(mTemporaryFolder.getRoot(), "new_dir");
        File file = new File(newDir, "test_log");
        assertThat(newDir.exists()).isFalse();

        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        AuditLogEntry entry = getTestEntry();

        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        assertThat(newDir.exists()).isTrue();
        assertThat(file.exists()).isTrue();
    }
}
