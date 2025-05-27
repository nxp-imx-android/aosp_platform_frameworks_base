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

package com.android.server.connectivity;

import static android.net.IpSecAlgorithm.AUTH_AES_CMAC;
import static android.net.IpSecAlgorithm.AUTH_AES_XCBC;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_AES_GCM;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305;
import static android.net.IpSecAlgorithm.AUTH_HMAC_MD5;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA1;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA256;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA384;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA512;
import static android.net.IpSecAlgorithm.CRYPT_AES_CBC;
import static android.net.IpSecAlgorithm.CRYPT_AES_CTR;

import static com.android.testutils.Cleanup.testAndCleanup;
import static com.android.server.connectivity.VpnConnectivityMetrics.buildAllowedAlgorithmsBitmask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Ikev2VpnProfile;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class VpnConnectivityMetricsTest {
    @Test
    public void testBuildAllowedAlgorithmsBitmask() {
        assertEquals(1536, buildAllowedAlgorithmsBitmask(List.of(CRYPT_AES_CBC, CRYPT_AES_CTR)));
        assertEquals(496, buildAllowedAlgorithmsBitmask(
                List.of(AUTH_HMAC_MD5, AUTH_HMAC_SHA1, AUTH_HMAC_SHA256, AUTH_HMAC_SHA384,
                        AUTH_HMAC_SHA512)));
        assertEquals(3, buildAllowedAlgorithmsBitmask(List.of(AUTH_AES_XCBC, AUTH_AES_CMAC)));
        assertEquals(12, buildAllowedAlgorithmsBitmask(
                List.of(AUTH_CRYPT_AES_GCM, AUTH_CRYPT_CHACHA20_POLY1305)));
        assertEquals(1999, buildAllowedAlgorithmsBitmask(Ikev2VpnProfile.DEFAULT_ALGORITHMS));
    }

    @Test
    public void testBuildAllowedAlgorithmsBitmask_UnknownAlgorithm() {
        final AtomicBoolean hasFailed = new AtomicBoolean(false);
        final Log.TerribleFailureHandler originalHandler =
                Log.setWtfHandler((tag, what, system) -> hasFailed.set(true));
        testAndCleanup(() -> {
            buildAllowedAlgorithmsBitmask(List.of("unknown"));
            assertTrue(hasFailed.get());
        }, () -> Log.setWtfHandler(originalHandler));
    }
}
