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

import com.android.internal.annotations.GuardedBy;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Environment;
import android.os.PersistableBundle;
import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/* Current Audit Mode limitations (tracked in b/461406944):
 * - Audit mode is set to true. Instead, a system property should be introduced to toggle
 *   audit mode on/off.
 * - Incoming data is not sanitized. Depends on ag/36599762.
 * - The data is appended to a unique file that grows indefinitely.
 *   At least the size of the file should be limited.
 *   We should support rotating the file after a certain size, and delete old files.
 *   These files should be created in a subdirectory.
 * - Appending to memory should be done asynchronously if possible, e.g. with an ArrayBlockingQueue.
 * - Writing to disk should happen asynchronously, using an ExecutorThread.
 * - When the logic is multi-threaded, tests should be added to verify thread-safety.
 * - If there is an error while writing the audit log to disk, the error is silently discarded;
 *   We should consider (silently) recovering from it, e.g., try to open a new file.
 * - The serialized format is not documented/standardized. it is now a length-prefixed
 *   serialized PersistableBundle.
 * - To be able to write the length first, the Bundle is first serialized into a byte array,
 *   then copied again into the output stream.
 * - The in-memory queue is capped by a number of bundles, not a size in bytes.
 */

/**
 * Manages the state of audit mode; when on, collects logs and periodically writes them to disk.
 * This class is thread-safe.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
class AuditModeContext {
    private static final String TAG = "PccSandboxManagerServiceAuditMode";
    private static final String AUDIT_LOG_FILE_NAME = "audit_log.bin";

    // Number of PersistableBundle stored in memory. Does not translate to a size in bytes.
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static final int AUDIT_LOG_QUEUE_CAPACITY = 100;

    @SuppressWarnings("UnusedVariable") // Is used in the child CL.
    private final ExecutorService mExecutor;

    private final Object mAuditLogLock;

    @GuardedBy("mAuditLogLock")
    private final List<PersistableBundle> mAuditLogQueue;

    @GuardedBy("mAuditLogLock")
    private final OutputStream mAuditOutputStream;


    private AuditModeContext(
            ExecutorService executor,
            List<PersistableBundle> auditLogQueue,
            OutputStream auditOutputStream) {
        mExecutor = executor;
        mAuditLogQueue = auditLogQueue;
        mAuditOutputStream = auditOutputStream;
        mAuditLogLock = new Object();
    }

    /**
     * Instantiates an AuditModeContext, including an output stream to the audit log file, or
     * returns null if an error occurred.
     */
    public static @Nullable AuditModeContext create(ExecutorService executor) {
        File folder = new File(Environment.getDataDirectory(), "system");
        File auditLogFile = new File(folder, AUDIT_LOG_FILE_NAME);
        try {
            OutputStream outputStream =
                    new BufferedOutputStream(
                            Files.newOutputStream(
                                    auditLogFile.toPath(),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.APPEND));
            return AuditModeContext.create(executor, outputStream);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start audit mode", e);
        }
        return null;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static AuditModeContext create(ExecutorService executor, OutputStream auditOutputStream) {
        List<PersistableBundle> auditLogQueue = new ArrayList<>(AUDIT_LOG_QUEUE_CAPACITY);
        return new AuditModeContext(executor, auditLogQueue, auditOutputStream);
    }

    /** Stops audit mode, writing pending data to disk. */
    public void stopAuditing() {
        synchronized (mAuditLogLock) {
            try {
                // This is a sync call for simplicity.
                writeBundlesToStream(mAuditLogQueue, mAuditOutputStream);
                mAuditOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write audit log to disk", e);
            } finally {
                mAuditLogQueue.clear();
            }
        }
    }

    /**
     * Writes data to the in-memory audit log, returning immediately.
     *
     * <p>If the in-memory queue is full, it is emptied and asynchronously written to disk. The
     * order is still preserved in the queue, and because there is a single executor.
     */
    void writeToAuditLog(@NonNull PersistableBundle data) {
        synchronized (mAuditLogLock) {
            if (mAuditLogQueue.size() < AUDIT_LOG_QUEUE_CAPACITY) {
                mAuditLogQueue.add(data);
                return;
            }

            Log.d(TAG, "Audit log queue is full, writing to disk.");
            try {
                writeBundlesToStream(mAuditLogQueue, mAuditOutputStream);
            } catch (IOException e) {
                Log.e(TAG, "Failed to write audit log to disk", e);
            }

            // Will succeed now that the queue is empty.
            mAuditLogQueue.clear();
            mAuditLogQueue.add(data);
        }
    }

    /**
     * Encodes and writes a list of PersistableBundle to the audit log file.
     *
     * @param bundles The list of PersistableBundle to write.
     * @throws IOException If an error occurs.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void writeBundlesToStream(List<PersistableBundle> bundles, OutputStream stream)
            throws IOException {
        final int size = bundles.size();
        for (int i = 0; i < size; i++) {
            writeBundleToStream(bundles.get(i), stream);
        }
        stream.flush();
    }

    /**
     * Encodes a PersistableBundle into a byte array.
     *
     * <p>The first 4 bytes of the byte array are the length of the PersistableBundle, followed by
     * the bytes of the PersistableBundle.
     *
     * <p>TODO: also write the calling package name/UID, and the timestamp.
     *
     * @param data The PersistableBundle to encode.
     * @throws IOException If an error occurs.
     */
    private void writeBundleToStream(PersistableBundle data, OutputStream stream)
            throws IOException {
        // Write the PersistableBundle to a byte array to know its length.
        ByteArrayOutputStream bundleOs = new ByteArrayOutputStream();
        data.writeToStream(bundleOs);
        byte[] bundleBytes = bundleOs.toByteArray();
        DataOutputStream dos = new DataOutputStream(stream);

        try {
            dos.writeInt(bundleBytes.length);
            dos.write(bundleBytes);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write PersistableBundle to audit log", e);
        }
    }
}
