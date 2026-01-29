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

package android.app.privatecompute;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface for receiving the result of a startNonPccProcessForDataMigration request.
 */
@FlaggedApi(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public interface MigrationRequestResultReceiver {
    /** The migration request failed due to an invocation error. */
    int ERROR_INVOCATION_FAILED = 1;

    /** The migration request failed due to a timeout. */
    int ERROR_TIMEOUT = 2;

    /**
     * Error codes for {@link #onError(int, String)}.
     * @hide
     */
    @IntDef(prefix = { "ERROR_" }, value = {
            ERROR_INVOCATION_FAILED,
            ERROR_TIMEOUT
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface MigrationError {}

    /** Called when the non-PCC process provides a result (Accepted/Rejected). */
    void onResult(@NonNull MigrationRequestResult result);

    /**
     * Called if the system fails to start the non-PCC process or it times out.
     *
     * @param errorCode The error code.
     * @param errorMessage A description of the error.
     */
    void onError(int errorCode, @Nullable String errorMessage);
}
