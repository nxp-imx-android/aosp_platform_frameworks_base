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

package com.android.server.security;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Slog;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A {@link JobService} that fetches the certificate revocation list from a remote location. */
public class UpdateCertificateRevocationStatusJobService extends JobService {

    static final String EXTRA_KEY_CERTIFICATES_TO_CHECK =
            "com.android.server.security.extra.CERTIFICATES_TO_CHECK";
    private static final String TAG = "AVF_CRL";
    private ExecutorService mExecutorService;

    @Override
    public void onCreate() {
        super.onCreate();
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mExecutorService.execute(
                () -> {
                    try {
                        CertificateRevocationStatusManager certificateRevocationStatusManager =
                                new CertificateRevocationStatusManager(this);
                        Slog.d(TAG, "Starting to fetch remote CRL from job service.");
                        JSONObject revocationList =
                                certificateRevocationStatusManager.fetchRemoteRevocationList();
                        String[] certificatesToCheckFromJobParams =
                                params.getExtras().getStringArray(EXTRA_KEY_CERTIFICATES_TO_CHECK);
                        if (certificatesToCheckFromJobParams == null) {
                            Slog.e(TAG, "Extras not found: " + EXTRA_KEY_CERTIFICATES_TO_CHECK);
                            return;
                        }
                        certificateRevocationStatusManager
                                .updateLastRevocationCheckDataForAllPreviouslySeenCertificates(
                                        revocationList,
                                        Arrays.asList(certificatesToCheckFromJobParams));
                    } catch (Throwable t) {
                        Slog.e(TAG, "Unable to update the stored revocation status.", t);
                    }
                    jobFinished(params, false);
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdown();
    }
}
