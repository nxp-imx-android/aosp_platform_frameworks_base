/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.externalstorage;

import static android.provider.DocumentsContract.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID;

import static com.android.externalstorage.ExternalStorageProvider.AUTHORITY;
import static com.android.externalstorage.ExternalStorageProvider.getPathFromDocId;
import static com.android.internal.content.storage.flags.Flags.FLAG_USE_FILE_SYSTEM_PROVIDER_SEARCH_LIMITS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class ExternalStorageProviderTest {
    // Tests in this class use a fake StorageManager with a fake volume (backed by real test files).
    public static class FakeStorageManagerTests {
        @Rule
        public final CheckFlagsRule mCheckFlagsRule =
                DeviceFlagsValueProvider.createCheckFlagsRule();

        @NonNull private final File mTemporaryFilesDir = sTargetContext.getCacheDir();

        @Rule
        public final CleanupTemporaryFilesRule mCleanupTemporaryFilesRule =
                new CleanupTemporaryFilesRule(mTemporaryFilesDir);

        @NonNull
        private static final Instrumentation sInstrumentation =
                InstrumentationRegistry.getInstrumentation();
        @NonNull
        private static final Context sTargetContext = sInstrumentation.getTargetContext();

        private ExternalStorageProvider mExternalStorageProvider;

        final String mRootId = "fakeVolume";
        final int mFileSystemProviderMaxResultLimit = 1000;  // FileSystemProvider.MAX_RESULTS

        @Before
        public void setUp() throws IOException {
            mExternalStorageProvider = new ExternalStorageProvider();

            attachFakeStorageManagerVolume(mRootId, mFileSystemProviderMaxResultLimit + 1,
                    mTemporaryFilesDir);
        }

        @Test
        @RequiresFlagsEnabled({FLAG_USE_FILE_SYSTEM_PROVIDER_SEARCH_LIMITS})
        public void querySearchDocuments_supportsLimits() throws IOException {
            // Test that not specifying search limit uses the legacy limit from FileSystemProvider.
            final Bundle queryArgs = new Bundle();
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "test");
            Cursor c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(23 /* FileSystemProvider.DEFAULT_SEARCH_RESULT_LIMIT */, c.getCount());

            // Test that specifying non-negative search limits works.
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
            c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(1, c.getCount());

            // Test that specifying a search limit larger than the maximum is clamped.
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT,
                    mFileSystemProviderMaxResultLimit + 1);
            c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(mFileSystemProviderMaxResultLimit, c.getCount());

            // Test that specifying zero results is honoured.
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 0);
            c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(0, c.getCount());

            // Test that specifying a negative limit uses the default.
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, -1);
            c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(23 /* FileSystemProvider.DEFAULT_SEARCH_RESULT_LIMIT */, c.getCount());
        }

        @Test
        @RequiresFlagsDisabled({FLAG_USE_FILE_SYSTEM_PROVIDER_SEARCH_LIMITS})
        public void querySearchDocuments_doesNotSupportLimits() throws IOException {
            // Test that not specifying search limit uses the legacy limit from FileSystemProvider.
            final Bundle queryArgs = new Bundle();
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "test");
            Cursor c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(23 /* FileSystemProvider.DEFAULT_SEARCH_RESULT_LIMIT */, c.getCount());

            // Test that specifying non-negative search limits is ignored.
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
            c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
            assertEquals(23, c.getCount());
        }

        /**
         * Attach a fake StorageManager to the Context and a fake volume to the StorageManager that
         * contains a deterministic set of test files.
         *
         * @param rootId for the fake Volume
         * @param testFileCount number of test files to create
         * @param temporaryFilesDir the temporary directory in which to create test files
         */
        private void attachFakeStorageManagerVolume(String rootId, int testFileCount,
                File temporaryFilesDir)
                throws IOException {
            // Set up a temporary directory in the test APK's cache directory and create a fake
            // volume to present via StorageManager.
            //
            // Mock out enough infrastructure so that ExternalStorageProvider finds our test volume.
            final Context mockContext = mock(Context.class);
            final UserManager mockUserManager = mock(UserManager.class);
            final ContentResolver mockContentResolver = mock(ContentResolver.class);
            final StorageManager spyStorageManager =
                    spy((StorageManager) sTargetContext.getSystemService(Context.STORAGE_SERVICE));

            when(mockContext.getSystemService(Context.STORAGE_SERVICE))
                    .thenReturn(spyStorageManager);
            when(mockContext.getSystemService(Context.USER_SERVICE))
                    .thenReturn(mockUserManager);
            when(mockContext.getContentResolver())
                    .thenReturn(mockContentResolver);

            // Clean up from prior tests to ensure deterministic set of test files.
            CleanupTemporaryFilesRule.removeFilesRecursively(temporaryFilesDir);

            // Create the test volume root directory and test files.
            final File rootDir = new File(temporaryFilesDir, rootId);
            assertTrue(rootDir.mkdir());

            for (int i = 0; i < testFileCount; i++) {
                File testFile = new File(rootDir.getPath(), "test" + i + ".txt");
                assertTrue(testFile.createNewFile());
            }

            // Create the fake volume that the mock StorageManager will return.
            final VolumeInfo volume = new VolumeInfo(rootId, VolumeInfo.TYPE_PUBLIC, null, null);
            volume.fsUuid = rootId;
            volume.path = rootDir.getPath();
            volume.state = VolumeInfo.STATE_MOUNTED;
            volume.mountUserId = UserHandle.myUserId();

            final List<VolumeInfo> volumes = new ArrayList<VolumeInfo>();
            volumes.add(volume);
            when(spyStorageManager.getVolumes()).thenReturn(volumes);

            // Get ExternalStorageProvider to scan for volumes and create its roots.
            final ProviderInfo providerInfo = new ProviderInfo();
            providerInfo.authority = AUTHORITY;
            sInstrumentation.runOnMainSync(() ->
                    mExternalStorageProvider.attachInfoForTesting(mockContext, providerInfo));
        }
    }

    // These tests don't fake / mock StorageManager and thus can only test simpler functionality.
    public static class NoFakeStorageManagerTests {
        @NonNull
        private static final Instrumentation sInstrumentation =
                InstrumentationRegistry.getInstrumentation();
        @NonNull
        private static final Context sTargetContext = sInstrumentation.getTargetContext();

        private ExternalStorageProvider mExternalStorageProvider;

        @Before
        public void setUp() {
            mExternalStorageProvider = new ExternalStorageProvider();
        }

        @Test
        public void onCreate_shouldUpdateVolumes() {
            final ExternalStorageProvider spyProvider = spy(mExternalStorageProvider);

            final ProviderInfo providerInfo = new ProviderInfo();
            providerInfo.authority = AUTHORITY;
            providerInfo.grantUriPermissions = true;
            providerInfo.exported = true;

            sInstrumentation.runOnMainSync(() ->
                    spyProvider.attachInfoForTesting(sTargetContext, providerInfo));

            verify(spyProvider, atLeast(1)).updateVolumes();
        }

        @Test
        public void test_getPathFromDocId() {
            final String root = "root";
            final String path = "abc/def/ghi";
            String docId = root + ":" + path;
            assertEquals(getPathFromDocId(docId), path);

            docId = root + ":" + path + "/";
            assertEquals(getPathFromDocId(docId), path);

            docId = root + ":";
            assertTrue(getPathFromDocId(docId).isEmpty());

            docId = root + ":./" + path;
            assertEquals(getPathFromDocId(docId), path);

            final String dotPath = "abc/./def/ghi";
            docId = root + ":" + dotPath;
            assertEquals(getPathFromDocId(docId), path);

            final String twoDotPath = "abc/../abc/def/ghi";
            docId = root + ":" + twoDotPath;
            assertEquals(getPathFromDocId(docId), path);
        }

        @Test
        public void test_shouldHideDocument() {
            // Should hide "Android/data", "Android/obb", "Android/sandbox" and all their
            // "subtrees".
            final String[] shouldHide = {
                    // "Android/data" and all its subdirectories
                    "Android/data",
                    "Android/data/com.my.app",
                    "Android/data/com.my.app/cache",
                    "Android/data/com.my.app/cache/image.png",
                    "Android/data/mydata",

                    // "Android/obb" and all its subdirectories
                    "Android/obb",
                    "Android/obb/com.my.app",
                    "Android/obb/com.my.app/file.blob",

                    // "Android/sandbox" and all its subdirectories
                    "Android/sandbox",
                    "Android/sandbox/com.my.app",

                    // Also make sure we are not allowing path traversals
                    "Android/./data",
                    "Android/Download/../data",
            };
            for (String path : shouldHide) {
                final String docId = buildDocId(path);
                assertTrue("ExternalStorageProvider should hide \"" + docId + "\", but it didn't",
                        mExternalStorageProvider.shouldHideDocument(docId));
            }

            // Should NOT hide anything else.
            final String[] shouldNotHide = {
                    "Android",
                    "Android/datadir",
                    "Documents",
                    "Download",
                    "Music",
                    "Pictures",
            };
            for (String path : shouldNotHide) {
                final String docId = buildDocId(path);
                assertFalse("ExternalStorageProvider should NOT hide \"" + docId + "\", but it did",
                        mExternalStorageProvider.shouldHideDocument(docId));
            }
        }

        @NonNull
        private static String buildDocId(@NonNull String path) {
            return buildDocId(EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID, path);
        }

        @NonNull
        private static String buildDocId(@NonNull String root, @NonNull String path) {
            // docId format: root:path/to/file
            return root + ':' + path;
        }
    }
}
