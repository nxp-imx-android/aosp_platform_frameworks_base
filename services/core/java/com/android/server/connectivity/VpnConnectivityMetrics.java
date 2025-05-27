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

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.List;

/**
 * Class to record the VpnConnectionReported into statsd, facilitating the logging of independent
 * VPN connection details per user.
 */
public class VpnConnectivityMetrics {
    private static final String TAG = VpnConnectivityMetrics.class.getSimpleName();
    public static final int VPN_TYPE_UNKNOWN = 0;
    public static final int VPN_PROFILE_TYPE_UNKNOWN = 0;
    private static final int UNKNOWN_UNDERLYING_NETWORK_TYPE = -1;
    private static final SparseArray<String> sAlgorithms = new SparseArray<>();
    private final int mUserId;
    @NonNull
    private final ConnectivityManager mConnectivityManager;
    private int mVpnType = VPN_TYPE_UNKNOWN;
    private int mVpnProfileType = VPN_PROFILE_TYPE_UNKNOWN;
    private int mMtu = 0;
    /**
     * A bitmask representing the set of currently allowed algorithms.
     * Each bit in this integer corresponds to an algorithm defined in {@code sAlgorithms}.
     * If a bit at a certain position (index) is set, the algorithm corresponding to that
     * index in {@code sAlgorithms} is considered allowed.
     */
    private int mAllowedAlgorithms = 0;
    /**
     * An array representing the transport types of the underlying networks for the VPN.
     * Each element in this array corresponds to a specific underlying network.
     * The value of each element is the primary transport type of the network
     * (e.g., {@link NetworkCapabilities#TRANSPORT_WIFI},
     * {@link NetworkCapabilities#TRANSPORT_CELLULAR}).
     * If the transport type of a network cannot be determined, the value will be
     * {@code UNKNOWN_UNDERLYING_NETWORK_TYPE}.
     */
    @NonNull
    private int[] mUnderlyingNetworkTypes;

    // Static initializer block to populate the sAlgorithms mapping. It associates integer keys
    // (which also serve as bit positions for the mAllowedAlgorithms bitmask) with their
    // respective algorithm string constants.
    static {
        sAlgorithms.put(0, AUTH_AES_CMAC);
        sAlgorithms.put(1, AUTH_AES_XCBC);
        sAlgorithms.put(2, AUTH_CRYPT_AES_GCM);
        sAlgorithms.put(3, AUTH_CRYPT_CHACHA20_POLY1305);
        sAlgorithms.put(4, AUTH_HMAC_MD5);
        sAlgorithms.put(5, AUTH_HMAC_SHA1);
        sAlgorithms.put(6, AUTH_HMAC_SHA256);
        sAlgorithms.put(7, AUTH_HMAC_SHA384);
        sAlgorithms.put(8, AUTH_HMAC_SHA512);
        sAlgorithms.put(9, CRYPT_AES_CBC);
        sAlgorithms.put(10, CRYPT_AES_CTR);
    }

    public VpnConnectivityMetrics(int userId, ConnectivityManager connectivityManager) {
        mUserId = userId;
        mConnectivityManager = connectivityManager;
    }

    /**
     * Sets the VPN type.
     *
     * @param type The type of the VPN, as defined in {@link VpnManager.VpnType}.
     */
    public void setVpnType(@VpnManager.VpnType int type) {
        mVpnType = type;
    }

    /**
     * Sets the MTU for the VPN connection.
     *
     * @param mtu The MTU value in bytes.
     */
    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    /**
     * Sets the VPN profile type.
     *
     * @param vpnProfile The integer value representing the VPN profile.
     */
    public void setVpnProfileType(int vpnProfile) {
        // There is a shift (+1) between VpnProfileType and VpnProfile.
        mVpnProfileType = vpnProfile + 1;
    }

    /**
     * Sets the allowed algorithms based on a provided list of algorithm names.
     * This method converts the list of string names into a bitmask representation
     * which is then stored in {@code mAllowedAlgorithms}.
     *
     * @param allowedAlgorithms A list of strings, where each string is the name of a algorithm to
     *                          be allowed.
     */
    public void setAllowedAlgorithms(@NonNull List<String> allowedAlgorithms) {
        mAllowedAlgorithms = buildAllowedAlgorithmsBitmask(allowedAlgorithms);
    }

    /**
     * Constructs a bitmask representing the set of allowed algorithms from a list of
     * algorithm names.
     * <p>
     * Each known algorithm name in the input list corresponds to a specific bit
     * in the returned integer. If an algorithm name from the list is found in
     * {@link #sAlgorithms}, the bit at the index of that algorithm in {@link #sAlgorithms}
     * is set in the bitmask.
     * </p>
     *
     * @param allowedAlgorithms A list of strings, where each string is the name of a algorithm.
     * @return An integer bitmask where each set bit indicates an allowed algorithm based on its
     *         index in {@link #sAlgorithms}. Returns 0 if the input list is empty, or contains only
     *         unknown algorithms.
     */
    @VisibleForTesting
    static int buildAllowedAlgorithmsBitmask(@NonNull List<String> allowedAlgorithms) {
        int bitmask = 0;
        for (String ac : allowedAlgorithms) {
            final int index = sAlgorithms.indexOfValue(ac);
            if (index < 0) {
                Log.wtf(TAG, "Unknown allowed algorithm: " + ac);
                continue;
            }
            bitmask |= 1 << index;
        }
        return bitmask;
    }

    /**
     * Sets the transport types of the underlying networks for the VPN.
     * <p>
     * This method processes an array of {@link android.net.Network} objects. For each network,
     * it attempts to retrieve its {@link android.net.NetworkCapabilities} and extracts the
     * primary transport type (e.g., Wi-Fi, cellular). If capabilities cannot be retrieved
     * for a specific network, a predefined {@code UNKNOWN_UNDERLYING_NETWORK_TYPE} is
     * used for that entry.
     *
     * @param networks An array of {@link android.net.Network} objects representing the underlying
     *                 networks currently in use.
     */
    public void setUnderlyingNetwork(@NonNull Network[] networks) {
        if (networks.length != 0) {
            int[] types = new int[networks.length];
            for (int i = 0; i < networks.length; i++) {
                final NetworkCapabilities capabilities =
                        mConnectivityManager.getNetworkCapabilities(networks[i]);
                if (capabilities != null) {
                    // Get the primary transport type of the network.
                    types[i] = capabilities.getTransportTypes()[0];
                } else {
                    types[i] = UNKNOWN_UNDERLYING_NETWORK_TYPE;
                }
            }
            mUnderlyingNetworkTypes = Arrays.copyOf(types, types.length);
        } else {
            mUnderlyingNetworkTypes = new int[0];
        }
    }
}
