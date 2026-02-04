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

package com.android.server.privatecompute;

import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BINDER_PROXY_TRANSACTION_REPORTED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_FAILED;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_SUCCESS;
import static com.android.os.privatecompute.PrivateComputeAtomsLog.PCC_INPUT_SANITIZATION_REPORTED;

import com.android.os.privatecompute.PrivateComputeAtomsLog;

public class PrivateComputeStatsLogUtil {

    /** Logs the latency for performing bundle sanitization. */
    public static void logPccBundleInputSanitizationLatency(long elapsedTimeMillis,
            int sanitizationResult) {
        PrivateComputeAtomsLog.write(
                PCC_INPUT_SANITIZATION_REPORTED,
                elapsedTimeMillis,
                sanitizationResult
        );
    }

    /**
     * Logs a successful PCC binder proxy transaction.
     */
    public static void logPccBinderProxyTransactionSuccess() {
        PrivateComputeAtomsLog.write(
                PCC_BINDER_PROXY_TRANSACTION_REPORTED,
                PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_SUCCESS
        );
    }

    /**
     * Logs a failed PCC binder proxy transaction with a specific failure reason.
     */
    public static void logPccBinderProxyTransactionFailure(int failureReason) {
        PrivateComputeAtomsLog.write(
                PCC_BINDER_PROXY_TRANSACTION_REPORTED,
                PCC_BINDER_PROXY_TRANSACTION_REPORTED__TRANSACTION_STATUS__STATUS_FAILED,
                failureReason
        );
    }
}
