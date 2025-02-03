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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Environment;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertPathValidatorException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Manages the revocation status of certificates used in remote attestation. */
class CertificateRevocationStatusManager {
    private static final String TAG = "AVF_CRL";
    // Must be unique within system server
    private static final int JOB_ID = 1737671340;
    private static final String REVOCATION_STATUS_FILE_NAME = "certificate_revocation_status.txt";
    private static final String REVOCATION_STATUS_FILE_FIELD_DELIMITER = ",";

    /**
     * The number of days since last update for which a stored revocation status can be accepted.
     */
    @VisibleForTesting static final int MAX_DAYS_SINCE_LAST_CHECK = 30;

    /**
     * The number of days since issue date for an intermediary certificate to be considered fresh
     * and not require a revocation list check.
     */
    private static final int FRESH_INTERMEDIARY_CERT_DAYS = 70;

    /**
     * The expected number of days between a certificate's issue date and notBefore date. Used to
     * infer a certificate's issue date from its notBefore date.
     */
    private static final int DAYS_BETWEEN_ISSUE_AND_NOT_BEFORE_DATES = 2;

    private static final String TOP_LEVEL_JSON_PROPERTY_KEY = "entries";
    private static final Object sFileLock = new Object();

    private final Context mContext;
    private final String mTestRemoteRevocationListUrl;
    private final File mTestRevocationStatusFile;
    private final boolean mShouldScheduleJob;

    CertificateRevocationStatusManager(Context context) {
        this(context, null, null, true);
    }

    @VisibleForTesting
    CertificateRevocationStatusManager(
            Context context,
            String testRemoteRevocationListUrl,
            File testRevocationStatusFile,
            boolean shouldScheduleJob) {
        mContext = context;
        mTestRemoteRevocationListUrl = testRemoteRevocationListUrl;
        mTestRevocationStatusFile = testRevocationStatusFile;
        mShouldScheduleJob = shouldScheduleJob;
    }

    /**
     * Check the revocation status of the provided {@link X509Certificate}s.
     *
     * <p>The provided certificates should have been validated and ordered from leaf to a
     * certificate issued by the trust anchor, per the convention specified in the javadoc of {@link
     * java.security.cert.CertPath}.
     *
     * @param certificates List of certificates to be checked
     * @throws CertPathValidatorException if the check failed
     */
    void checkRevocationStatus(List<X509Certificate> certificates)
            throws CertPathValidatorException {
        if (!needToCheckRevocationStatus(certificates)) {
            return;
        }
        List<String> serialNumbers = new ArrayList<>();
        for (X509Certificate certificate : certificates) {
            String serialNumber = certificate.getSerialNumber().toString(16);
            if (serialNumber == null) {
                throw new CertPathValidatorException("Certificate serial number cannot be null.");
            }
            serialNumbers.add(serialNumber);
        }
        try {
            JSONObject revocationList = fetchRemoteRevocationList();
            Map<String, Boolean> areCertificatesRevoked = new HashMap<>();
            for (String serialNumber : serialNumbers) {
                areCertificatesRevoked.put(serialNumber, revocationList.has(serialNumber));
            }
            updateLastRevocationCheckData(areCertificatesRevoked);
            for (Map.Entry<String, Boolean> entry : areCertificatesRevoked.entrySet()) {
                if (entry.getValue()) {
                    throw new CertPathValidatorException(
                            "Certificate " + entry.getKey() + " has been revoked.");
                }
            }
        } catch (IOException | JSONException ex) {
            Slog.d(TAG, "Fallback to check stored revocation status", ex);
            if (ex instanceof IOException && mShouldScheduleJob) {
                scheduleJobToUpdateStoredDataWithRemoteRevocationList(serialNumbers);
            }
            for (X509Certificate certificate : certificates) {
                // Assume recently issued certificates are not revoked.
                if (isIssuedWithinDays(certificate, MAX_DAYS_SINCE_LAST_CHECK)) {
                    String serialNumber = certificate.getSerialNumber().toString(16);
                    serialNumbers.remove(serialNumber);
                }
            }
            Map<String, LocalDateTime> lastRevocationCheckData;
            try {
                lastRevocationCheckData = getLastRevocationCheckData();
            } catch (IOException ex2) {
                throw new CertPathValidatorException(
                        "Unable to load stored revocation status", ex2);
            }
            for (String serialNumber : serialNumbers) {
                if (!lastRevocationCheckData.containsKey(serialNumber)
                        || lastRevocationCheckData
                                .get(serialNumber)
                                .isBefore(
                                        LocalDateTime.now().minusDays(MAX_DAYS_SINCE_LAST_CHECK))) {
                    throw new CertPathValidatorException(
                            "Unable to verify the revocation status of certificate "
                                    + serialNumber);
                }
            }
        }
    }

    private static boolean needToCheckRevocationStatus(
            List<X509Certificate> certificatesOrderedLeafFirst) {
        if (certificatesOrderedLeafFirst.isEmpty()) {
            return false;
        }
        // A certificate isn't revoked when it is first issued, so we treat it as checked on its
        // issue date.
        if (!isIssuedWithinDays(certificatesOrderedLeafFirst.get(0), MAX_DAYS_SINCE_LAST_CHECK)) {
            return true;
        }
        for (int i = 1; i < certificatesOrderedLeafFirst.size(); i++) {
            if (!isIssuedWithinDays(
                    certificatesOrderedLeafFirst.get(i), FRESH_INTERMEDIARY_CERT_DAYS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIssuedWithinDays(X509Certificate certificate, int days) {
        LocalDate notBeforeDate =
                LocalDate.ofInstant(certificate.getNotBefore().toInstant(), ZoneId.systemDefault());
        LocalDate expectedIssueData =
                notBeforeDate.plusDays(DAYS_BETWEEN_ISSUE_AND_NOT_BEFORE_DATES);
        return LocalDate.now().minusDays(days + 1).isBefore(expectedIssueData);
    }

    void updateLastRevocationCheckDataForAllPreviouslySeenCertificates(
            JSONObject revocationList, Collection<String> otherCertificatesToCheck) {
        Set<String> allCertificatesToCheck = new HashSet<>(otherCertificatesToCheck);
        try {
            allCertificatesToCheck.addAll(getLastRevocationCheckData().keySet());
        } catch (IOException ex) {
            Slog.e(TAG, "Unable to update last check date of stored data.", ex);
        }
        Map<String, Boolean> areCertificatesRevoked = new HashMap<>();
        for (String serialNumber : allCertificatesToCheck) {
            areCertificatesRevoked.put(serialNumber, revocationList.has(serialNumber));
        }
        updateLastRevocationCheckData(areCertificatesRevoked);
    }

    /**
     * Update the last revocation check data stored on this device.
     *
     * @param areCertificatesRevoked A Map whose keys are certificate serial numbers and values are
     *     whether that certificate has been revoked
     */
    void updateLastRevocationCheckData(Map<String, Boolean> areCertificatesRevoked) {
        LocalDateTime now = LocalDateTime.now();
        synchronized (sFileLock) {
            Map<String, LocalDateTime> lastRevocationCheckData;
            try {
                lastRevocationCheckData = getLastRevocationCheckData();
            } catch (IOException ex) {
                Slog.e(TAG, "Unable to updateLastRevocationCheckData", ex);
                return;
            }
            for (Map.Entry<String, Boolean> entry : areCertificatesRevoked.entrySet()) {
                if (entry.getValue()) {
                    lastRevocationCheckData.remove(entry.getKey());
                } else {
                    lastRevocationCheckData.put(entry.getKey(), now);
                }
            }
            storeLastRevocationCheckData(lastRevocationCheckData);
        }
    }

    Map<String, LocalDateTime> getLastRevocationCheckData() throws IOException {
        Map<String, LocalDateTime> data = new HashMap<>();
        File dataFile = getLastRevocationCheckDataFile();
        synchronized (sFileLock) {
            if (!dataFile.exists()) {
                return data;
            }
            String dataString;
            try (FileInputStream in = new FileInputStream(dataFile)) {
                dataString = new String(in.readAllBytes(), UTF_8);
            }
            for (String line : dataString.split(System.lineSeparator())) {
                String[] elements = line.split(REVOCATION_STATUS_FILE_FIELD_DELIMITER);
                if (elements.length != 2) {
                    continue;
                }
                try {
                    data.put(elements[0], LocalDateTime.parse(elements[1]));
                } catch (DateTimeParseException ex) {
                    Slog.e(
                            TAG,
                            "Unable to parse last checked LocalDateTime from file. Deleting the"
                                    + " potentially corrupted file.",
                            ex);
                    dataFile.delete();
                    return data;
                }
            }
        }
        return data;
    }

    @VisibleForTesting
    void storeLastRevocationCheckData(Map<String, LocalDateTime> lastRevocationCheckData) {
        StringBuilder dataStringBuilder = new StringBuilder();
        for (Map.Entry<String, LocalDateTime> entry : lastRevocationCheckData.entrySet()) {
            dataStringBuilder
                    .append(entry.getKey())
                    .append(REVOCATION_STATUS_FILE_FIELD_DELIMITER)
                    .append(entry.getValue())
                    .append(System.lineSeparator());
        }
        synchronized (sFileLock) {
            try (FileOutputStream fileOutputStream =
                    new FileOutputStream(getLastRevocationCheckDataFile())) {
                fileOutputStream.write(dataStringBuilder.toString().getBytes(UTF_8));
                Slog.d(TAG, "Successfully stored revocation status data.");
            } catch (IOException ex) {
                Slog.e(TAG, "Failed to store revocation status data.", ex);
            }
        }
    }

    private File getLastRevocationCheckDataFile() {
        if (mTestRevocationStatusFile != null) {
            return mTestRevocationStatusFile;
        }
        return new File(Environment.getDataSystemDirectory(), REVOCATION_STATUS_FILE_NAME);
    }

    private void scheduleJobToUpdateStoredDataWithRemoteRevocationList(List<String> serialNumbers) {
        JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            Slog.e(TAG, "Unable to get job scheduler.");
            return;
        }
        Slog.d(TAG, "Scheduling job to fetch remote CRL.");
        PersistableBundle extras = new PersistableBundle();
        extras.putStringArray(
                UpdateCertificateRevocationStatusJobService.EXTRA_KEY_CERTIFICATES_TO_CHECK,
                serialNumbers.toArray(new String[0]));
        jobScheduler.schedule(
                new JobInfo.Builder(
                                JOB_ID,
                                new ComponentName(
                                        mContext,
                                        UpdateCertificateRevocationStatusJobService.class))
                        .setExtras(extras)
                        .setRequiredNetwork(
                                new NetworkRequest.Builder()
                                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                        .build())
                        .build());
    }

    /**
     * Fetches the revocation list from the URL specified in
     * R.string.vendor_required_attestation_revocation_list_url
     *
     * @return The remote revocation list entries in a JSONObject
     * @throws CertPathValidatorException if the URL is not defined or is malformed.
     * @throws IOException if the URL is valid but the fetch failed.
     * @throws JSONException if the revocation list content cannot be parsed
     */
    JSONObject fetchRemoteRevocationList()
            throws CertPathValidatorException, IOException, JSONException {
        String urlString = getRemoteRevocationListUrl();
        if (urlString == null || urlString.isEmpty()) {
            throw new CertPathValidatorException(
                    "R.string.vendor_required_attestation_revocation_list_url is empty.");
        }
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException ex) {
            throw new CertPathValidatorException("Unable to parse the URL " + urlString, ex);
        }
        byte[] revocationListBytes;
        try (InputStream inputStream = url.openStream()) {
            revocationListBytes = inputStream.readAllBytes();
        }
        JSONObject revocationListJson = new JSONObject(new String(revocationListBytes, UTF_8));
        return revocationListJson.getJSONObject(TOP_LEVEL_JSON_PROPERTY_KEY);
    }

    private String getRemoteRevocationListUrl() {
        if (mTestRemoteRevocationListUrl != null) {
            return mTestRemoteRevocationListUrl;
        }
        return mContext.getResources()
                .getString(R.string.vendor_required_attestation_revocation_list_url);
    }
}
