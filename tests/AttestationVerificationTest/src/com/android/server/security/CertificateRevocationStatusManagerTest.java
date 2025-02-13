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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class CertificateRevocationStatusManagerTest {

    private static final String TEST_CERTIFICATE_FILE_1 = "test_attestation_with_root_certs.pem";
    private static final String TEST_CERTIFICATE_FILE_2 = "test_attestation_wrong_root_certs.pem";
    private static final String TEST_REVOCATION_LIST_FILE_NAME = "test_revocation_list.json";
    private static final String REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST =
            "test_revocation_list_no_test_certs.json";
    private static final String REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST =
            "test_revocation_list_with_test_certs.json";
    private static final String TEST_REVOCATION_STATUS_FILE_NAME = "test_revocation_status.txt";
    private static final String FILE_URL_PREFIX = "file://";
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private CertificateFactory mFactory;
    private List<X509Certificate> mCertificates1;
    private List<X509Certificate> mCertificates2;
    private File mRevocationListFile;
    private String mRevocationListUrl;
    private String mNonExistentRevocationListUrl;
    private File mRevocationStatusFile;
    private CertificateRevocationStatusManager mCertificateRevocationStatusManager;

    @Before
    public void setUp() throws Exception {
        mFactory = CertificateFactory.getInstance("X.509");
        mCertificates1 = getCertificateChain(TEST_CERTIFICATE_FILE_1);
        mCertificates2 = getCertificateChain(TEST_CERTIFICATE_FILE_2);
        mRevocationListFile = new File(mContext.getFilesDir(), TEST_REVOCATION_LIST_FILE_NAME);
        mRevocationListUrl = FILE_URL_PREFIX + mRevocationListFile.getAbsolutePath();
        File noSuchFile = new File(mContext.getFilesDir(), "file_does_not_exist");
        mNonExistentRevocationListUrl = FILE_URL_PREFIX + noSuchFile.getAbsolutePath();
        mRevocationStatusFile = new File(mContext.getFilesDir(), TEST_REVOCATION_STATUS_FILE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        mRevocationListFile.delete();
        mRevocationStatusFile.delete();
    }

    @Test
    public void checkRevocationStatus_doesNotExistOnRemoteRevocationList_noException()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_existsOnRemoteRevocationList_throwsException()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void
            checkRevocationStatus_cannotReachRemoteRevocationList_noStoredStatus_throwsException()
                    throws Exception {
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mRevocationStatusFile, false);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void checkRevocationStatus_savesRevocationStatus() throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);

        assertThat(mRevocationStatusFile.length()).isGreaterThan(0);
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_certsSaved_noException()
            throws Exception {
        // call checkRevocationStatus once to save the revocation status
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // call checkRevocationStatus again with mNonExistentRevocationListUrl
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mRevocationStatusFile, false);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_someCertsNotSaved_exception()
            throws Exception {
        // call checkRevocationStatus once to save the revocation status for mCertificates2
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates2);
        // call checkRevocationStatus again with mNonExistentRevocationListUrl, this time for
        // mCertificates1
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mRevocationStatusFile, false);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_someCertsStatusTooOld_exception()
            throws Exception {
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mRevocationStatusFile, false);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredStatusDate =
                now.minusDays(CertificateRevocationStatusManager.MAX_DAYS_SINCE_LAST_CHECK + 1);
        Map<String, LocalDateTime> lastRevocationCheckData = new HashMap<>();
        lastRevocationCheckData.put(getSerialNumber(mCertificates1.get(0)), expiredStatusDate);
        for (int i = 1; i < mCertificates1.size(); i++) {
            lastRevocationCheckData.put(getSerialNumber(mCertificates1.get(i)), now);
        }
        mCertificateRevocationStatusManager.storeLastRevocationCheckData(lastRevocationCheckData);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void checkRevocationStatus_cannotReachRemoteList_allCertResultsFresh_noException()
            throws Exception {
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mRevocationStatusFile, false);
        LocalDateTime bearlyNotExpiredStatusDate =
                LocalDateTime.now()
                        .minusDays(
                                CertificateRevocationStatusManager.MAX_DAYS_SINCE_LAST_CHECK - 1);
        Map<String, LocalDateTime> lastRevocationCheckData = new HashMap<>();
        for (X509Certificate certificate : mCertificates1) {
            lastRevocationCheckData.put(getSerialNumber(certificate), bearlyNotExpiredStatusDate);
        }
        mCertificateRevocationStatusManager.storeLastRevocationCheckData(lastRevocationCheckData);

        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void updateLastRevocationCheckData_correctlySavesStatus() throws Exception {
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mNonExistentRevocationListUrl, mRevocationStatusFile, false);
        Map<String, Boolean> areCertificatesRevoked = new HashMap<>();
        for (X509Certificate certificate : mCertificates1) {
            areCertificatesRevoked.put(getSerialNumber(certificate), false);
        }

        mCertificateRevocationStatusManager.updateLastRevocationCheckData(areCertificatesRevoked);

        // no exception
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // revoke one certificate and try again
        areCertificatesRevoked.put(getSerialNumber(mCertificates1.getLast()), true);
        mCertificateRevocationStatusManager.updateLastRevocationCheckData(areCertificatesRevoked);
        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    @Test
    public void updateLastRevocationCheckDataForAllPreviouslySeenCertificates_updatesCorrectly()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);
        // populate the revocation status file
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // Sleep for 2 second so that the current time changes
        SystemClock.sleep(2000);
        LocalDateTime timestampBeforeUpdate = LocalDateTime.now();
        JSONObject revocationList = mCertificateRevocationStatusManager.fetchRemoteRevocationList();
        List<String> otherCertificatesToCheck = new ArrayList<>();
        String serialNumber1 = "1234567"; // not revoked
        String serialNumber2 = "8350192447815228107"; // revoked
        String serialNumber3 = "987654"; // not revoked
        otherCertificatesToCheck.add(serialNumber1);
        otherCertificatesToCheck.add(serialNumber2);
        otherCertificatesToCheck.add(serialNumber3);

        mCertificateRevocationStatusManager
                .updateLastRevocationCheckDataForAllPreviouslySeenCertificates(
                        revocationList, otherCertificatesToCheck);

        Map<String, LocalDateTime> lastRevocationCheckData =
                mCertificateRevocationStatusManager.getLastRevocationCheckData();
        assertThat(lastRevocationCheckData.get(serialNumber1)).isAtLeast(timestampBeforeUpdate);
        assertThat(lastRevocationCheckData).doesNotContainKey(serialNumber2); // revoked
        assertThat(lastRevocationCheckData.get(serialNumber3)).isAtLeast(timestampBeforeUpdate);
        // validate that the existing certificates on the file got updated too
        for (X509Certificate certificate : mCertificates1) {
            assertThat(lastRevocationCheckData.get(getSerialNumber(certificate)))
                    .isAtLeast(timestampBeforeUpdate);
        }
    }

    @Test
    public void checkRevocationStatus_allCertificatesRecentlyChecked_doesNotFetchRemoteCrl()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITHOUT_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
        // indirectly verifies the remote list is not fetched by simulating a remote revocation
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);

        // no exception
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_allCertificatesBarelyRecentlyChecked_doesNotFetchRemoteCrl()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);
        Map<String, LocalDateTime> lastCheckedDates = new HashMap<>();
        LocalDateTime barelyRecently =
                LocalDateTime.now()
                        .minusHours(
                                CertificateRevocationStatusManager.NUM_HOURS_BEFORE_NEXT_CHECK - 1);
        for (X509Certificate certificate : mCertificates1) {
            lastCheckedDates.put(getSerialNumber(certificate), barelyRecently);
        }
        mCertificateRevocationStatusManager.storeLastRevocationCheckData(lastCheckedDates);

        // Indirectly verify the remote CRL is not checked by checking there is no exception despite
        // a certificate being revoked. This test differs from the next only in the lastCheckedDate,
        // one before the NUM_HOURS_BEFORE_NEXT_CHECK cutoff and one after
        mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1);
    }

    @Test
    public void checkRevocationStatus_certificatesRevokedAfterCheck_throwsException()
            throws Exception {
        copyFromAssetToFile(
                REVOCATION_LIST_WITH_CERTIFICATES_USED_IN_THIS_TEST, mRevocationListFile);
        mCertificateRevocationStatusManager =
                new CertificateRevocationStatusManager(
                        mContext, mRevocationListUrl, mRevocationStatusFile, false);
        Map<String, LocalDateTime> lastCheckedDates = new HashMap<>();
        // To save network use, we do not check the remote CRL if all the certificates are recently
        // checked, so we set the lastCheckDate to some time not recent.
        LocalDateTime notRecently =
                LocalDateTime.now()
                        .minusHours(
                                CertificateRevocationStatusManager.NUM_HOURS_BEFORE_NEXT_CHECK + 1);
        for (X509Certificate certificate : mCertificates1) {
            lastCheckedDates.put(getSerialNumber(certificate), notRecently);
        }
        mCertificateRevocationStatusManager.storeLastRevocationCheckData(lastCheckedDates);

        assertThrows(
                CertPathValidatorException.class,
                () -> mCertificateRevocationStatusManager.checkRevocationStatus(mCertificates1));
    }

    private List<X509Certificate> getCertificateChain(String fileName) throws Exception {
        Collection<? extends Certificate> certificates =
                mFactory.generateCertificates(mContext.getResources().getAssets().open(fileName));
        ArrayList<X509Certificate> x509Certs = new ArrayList<>();
        for (Certificate cert : certificates) {
            x509Certs.add((X509Certificate) cert);
        }
        return x509Certs;
    }

    private void copyFromAssetToFile(String assetFileName, File targetFile) throws Exception {
        byte[] data;
        try (InputStream in = mContext.getResources().getAssets().open(assetFileName)) {
            data = in.readAllBytes();
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
            fileOutputStream.write(data);
        }
    }

    private String getSerialNumber(X509Certificate certificate) {
        return certificate.getSerialNumber().toString(16);
    }
}
