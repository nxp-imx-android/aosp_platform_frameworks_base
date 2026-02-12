/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os.storage.operations.targets;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * A target representing the calling application's Private Compute Core (PCC) data directory.
 *
 * <p>Files directed to this target will be securely stored within the PCC component of the
 * application. The optional path prefix can be used to organize files into subdirectories within
 * the PCC storage area.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * // Create a target that places files in a "processed_results" subdirectory within PCC
 * PccTarget target = new PccTarget("processed_results/daily_logs");
 *
 * // If a file named "report.dat" is sent to this target, it will be stored at:
 * // [PCC_ROOT]/processed_results/daily_logs/report.dat
 * }</pre>
 *
 * <p><b>Prefix Requirements:</b>
 *
 * <ul>
 *   <li><b>Must be relative:</b> Absolute paths are rejected to prevent escaping the PCC root
 *       directory.
 *   <li><b>No Path Traversal:</b> The prefix cannot contain ".." or "." segments that resolve to
 *       parent or current directories.
 *   <li><b>No Control Characters:</b> Invisible or control characters (e.g., null, newline,
 *       surrogates) are prohibited for security.
 * </ul>
 */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
@SuppressLint("ParcelNotFinal")
@RavenwoodKeepWholeClass
public final class PccTarget extends OperationTarget {
    /**
     * Pattern to detect "suspicious" characters in file paths. Matches Unicode categories: - \p{C}:
     * Other/Control characters (null, newline, surrogates, unassigned, etc.) - \p{Cf}: Format
     * characters (zero-width spaces, joiners, layout marks, etc.)
     */
    private static final Pattern UNSAFE_PATH_PATTERN = Pattern.compile("[\\p{C}\\p{Cf}]");

    private final String mPathPrefix;

    /** Creates a new PCC target with no path prefix. */
    public PccTarget() {
        mPathPrefix = "";
    }

    /**
     * Creates a new PCC target with the specified path prefix.
     *
     * @param pathPrefix The prefix to be added to the destination path of files.
     */
    public PccTarget(@NonNull String pathPrefix) {
        mPathPrefix = pathPrefix;
    }

    /** @hide */
    @Override
    public boolean isValid() {
        if (mPathPrefix == null) {
            return false;
        }

        if (mPathPrefix.isEmpty()) {
            return true;
        }

        // Use Path API for robust syntactic validation (no disk I/O)
        final Path path = Paths.get(mPathPrefix);

        // 1. Enforce relative path (must be within PCC directory)
        // If the path is absolute, it would ignore the PCC root directory when combined,
        // allowing escape to arbitrary filesystem locations.
        if (path.isAbsolute()) {
            return false;
        }

        // 2. Detect Path Traversal (e.g. "subdir/../other" or "../parent")
        // normalize() resolves ".." and ".". If the path changes, it contained
        // traversal/redundancy.
        // We also explicitly check for leading ".." which normalize() preserves in relative paths.
        if (!path.normalize().equals(path) || path.startsWith("..")) {
            return false;
        }

        // 3. Check for invisible/control characters
        if (UNSAFE_PATH_PATTERN.matcher(mPathPrefix).find()) {
            return false;
        }

        return true;
    }

    /** @hide */
    public @NonNull File getTargetPath(
            @Nullable String volumeUuid,
            boolean isDeviceEncrypted,
            int userId,
            @NonNull String packageName) {
        File root;
        if (isDeviceEncrypted) {
            root = Environment.getPccDataUserDePackageDirectory(volumeUuid, userId, packageName);
        } else {
            root = Environment.getPccDataUserCePackageDirectory(volumeUuid, userId, packageName);
        }

        if (mPathPrefix.isEmpty()) {
            return root;
        } else {
            return new File(root, mPathPrefix);
        }
    }

    /** implemented for Parcelable */
    @Override
    public int describeContents() {
        return 0;
    }

    /** implemented for Parcelable */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mPathPrefix);
    }

    @NonNull
    public static final Parcelable.Creator<PccTarget> CREATOR =
            new Parcelable.Creator<PccTarget>() {
                public PccTarget createFromParcel(Parcel in) {
                    return new PccTarget(in.readString8());
                }

                public PccTarget[] newArray(int size) {
                    return new PccTarget[size];
                }
            };

    @Override
    @NonNull
    public String toString() {
        return "PccTarget: prefix=" + mPathPrefix;
    }
}
