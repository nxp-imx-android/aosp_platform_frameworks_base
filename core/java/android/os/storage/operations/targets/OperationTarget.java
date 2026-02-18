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
import android.os.Parcelable;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

/** Abstract base class for the target of a file operation. */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
@SuppressWarnings({"ParcelCreator", "ParcelNotFinal"})
@RavenwoodKeepWholeClass
public abstract class OperationTarget implements Parcelable {
    OperationTarget() {}

    /**
     * Returns a human-readable representation of this target.
     *
     * <p>Concrete implementations should return a string that describes the target in a way that is
     * useful for debugging and logging (e.g., including key paths or identifiers).
     *
     * <p><b>Note:</b> This string is intended for diagnostic purposes only and should not be parsed
     * or relied upon as a stable identifier for the files or the target configuration.
     */
    @Override
    @NonNull
    public abstract String toString();

    /**
     * Performs a robust syntactic validation of the target to ensure it is well-formed.
     *
     * <p>This method acts as a "cheap" fail-fast mechanism to intercept invalid or malicious
     * requests—such as those attempting path traversal—before they are added to the operation
     * queue.
     *
     * <p><b>Implementation Requirements:</b>
     *
     * <ul>
     *   <li><b>Syntactic Only:</b> Validation must be performed purely on the path's syntax (e.g.,
     *       using {@link java.nio.file.Path} or regex) and must not perform any blocking disk I/O.
     *   <li><b>Path Traversal Protection:</b> Subclasses should use normalization to detect and
     *       reject traversal elements like ".." or "." that could allow escaping the intended
     *       directory.
     *   <li><b>Unsafe Character Filtering:</b> Implementations must check for and reject invisible
     *       or control characters that could be used for path manipulation or obfuscation.
     *   <li><b>Relativity:</b> Depending on the target type, implementations must enforce whether
     *       paths are relative or absolute to prevent unauthorized filesystem access.
     * </ul>
     *
     * <p><b>Security Note:</b> This check is not authoritative. Because the system server often
     * lacks visibility into specific application storage contexts, the final ownership and
     * permission verification is performed by the specific processor during execution.
     *
     * @return {@code true} if the target configuration is syntactically valid; {@code false}
     *     otherwise.
     * @hide
     */
    public abstract boolean isValid();
}
