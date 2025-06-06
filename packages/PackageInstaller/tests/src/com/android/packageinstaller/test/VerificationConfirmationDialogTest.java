/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.packageinstaller.test;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;

import static com.android.packageinstaller.ConfirmDeveloperVerification.FLAG_VERIFICATION_FAILED_MAY_BYPASS;
import static com.android.packageinstaller.ConfirmDeveloperVerification.FLAG_VERIFICATION_FAILED_MAY_RETRY;
import static com.android.packageinstaller.ConfirmDeveloperVerification.FLAG_VERIFICATION_FAILED_MAY_RETRY_MAY_BYPASS;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.PackageInstaller.DeveloperVerificationUserConfirmationInfo;

import com.android.packageinstaller.ConfirmDeveloperVerification;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VerificationConfirmationDialogTest {

    @Test
    public void policyOpen_packageBlocked_onlyAck() {
        int flag = ConfirmDeveloperVerification.getUserConfirmationDialogFlag(
                new DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED));

        assertThat(flag).isEqualTo(0);
    }

    @Test
    public void policyOpen_noNetwork_mayRetryMayBypass() {
        int flag = ConfirmDeveloperVerification.getUserConfirmationDialogFlag(
                new DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE));

        assertThat(flag).isEqualTo(FLAG_VERIFICATION_FAILED_MAY_RETRY_MAY_BYPASS);
    }

    @Test
    public void policyOpen_unknown_mayBypass() {
        int flag = ConfirmDeveloperVerification.getUserConfirmationDialogFlag(
                new DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN));

        assertThat(flag).isEqualTo(FLAG_VERIFICATION_FAILED_MAY_BYPASS);
    }

    @Test
    public void policyClosed_packageBlocked_onlyAck() {
        int flag = ConfirmDeveloperVerification.getUserConfirmationDialogFlag(
                new DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                        DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED));

        assertThat(flag).isEqualTo(0);
    }

    @Test
    public void policyClosed_noNetwork_mayRetry() {
        int flag = ConfirmDeveloperVerification.getUserConfirmationDialogFlag(
                new DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                        DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE));

        assertThat(flag).isEqualTo(FLAG_VERIFICATION_FAILED_MAY_RETRY);
    }

    @Test
    public void policyClosed_unknown_onlyAck() {
        int flag = ConfirmDeveloperVerification.getUserConfirmationDialogFlag(
                new DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                        DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN));

        assertThat(flag).isEqualTo(0);
    }
}
