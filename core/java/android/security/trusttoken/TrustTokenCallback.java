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

package android.security.trusttoken;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.security.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Callback invoked by the {@link TrustTokenService} when received trust tokens from the remote
 * Trust Anchor server.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_TALISMAN_SERVICE_API)
@SystemApi
public abstract class TrustTokenCallback {
    /**
     * Error codes returned by the remote Trust Anchor server.
     *
     * @hide
     */
    @IntDef(
            prefix = {"ERROR_"},
            value = {ERROR_UNKNOWN, ERROR_INVALID_ARGUMENT, ERROR_INTERNAL, ERROR_UNAVAILABLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {}

    /** Unknown error. */
    public static final int ERROR_UNKNOWN = 0;

    /** The request parameters are invalid. */
    public static final int ERROR_INVALID_ARGUMENT = 1;

    /** The request failed due to an internal error. */
    public static final int ERROR_INTERNAL = 2;

    /** The request failed due to an unavailable error (such as network unavailable). */
    public static final int ERROR_UNAVAILABLE = 3;

    /**
     * Invoked when successfully received trust tokens and other data from the remote server.
     *
     * @param response the response from the remote server.
     */
    public abstract void onSuccess(@NonNull TrustTokenResponse response);

    /**
     * Invoked when encountered a permanent error, or too many retry attempts for transient errors
     * (such as network unavailable).
     *
     * <p>For permanent errors, the system should stop retrying. For transient errors, the service
     * has internally retried for certain times but still failed, so the system should wait for a
     * while then retry.
     *
     * @param code the error code returned by the remote server.
     */
    public abstract void onFailure(@ErrorCode int code);
}
