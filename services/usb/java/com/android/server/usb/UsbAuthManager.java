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

package com.android.server.usb;

import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.hardware.usb.IUsbAuthEventsListener;
import android.hardware.usb.IUsbAuthManager;
import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationStatus;
import android.hardware.usb.UsbAuthorizationSystemState;
import android.hardware.usb.UsbDevice;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.FgThread;
import com.android.server.SystemServerInitThreadPool;

public class UsbAuthManager implements IBinder.DeathRecipient {
    private static final String TAG = "UsbAuthManager";
    private IUsbAuthManager mService;
    private final Context mContext;
    private final UserManager mUserManager;
    private final UsbHostManager mHostManager;
    private final UsbAuthEventsListener mAuthEventsListener;
    private static final String USB_AUTH_SERVICE_NAME = "usb_auth";

    @GuardedBy("mLock")
    private @UserIdInt int mCurrentUserId;

    @GuardedBy("mLock")
    private boolean mIsScreenLocked;

    @GuardedBy("mLock")
    private SparseBooleanArray mFullUsersLoggedIn = new SparseBooleanArray();

    @GuardedBy("mLock")
    private @UsbAuthorizationSystemState int mCurrentState = -1;

    // Class keeping track of authorized devices and whether host manager
    // has been notified of an authorized device.
    private static class AuthorizationState {
        // Auth service provided device info.
        UsbAuthDeviceInfo mDeviceInfo;

        // Authorization status for this device.
        @UsbAuthorizationStatus int mAuthorizationStatus;

        // Host manager has seen device and is ready for notification.
        boolean mHostReady;

        // Currently communicated authorized state with host manager once ready.
        boolean mHostAuthorized;

        AuthorizationState(
                UsbAuthDeviceInfo deviceInfo,
                int authorizationStatus,
                boolean hostReady,
                boolean hostAuthorized) {
            mDeviceInfo = deviceInfo;
            mAuthorizationStatus = authorizationStatus;
            mHostReady = hostReady;
            mHostAuthorized = hostAuthorized;
        }
    }

    // Map of currently connected usb devices. Updated via usbDeviceAdded and
    // usbDeviceRemoved. Key is device address (UsbDevice.getDeviceName) and
    // value is whether we have called usbDeviceAuthorized in host manager.
    @GuardedBy("mLock")
    private ArrayMap<String, AuthorizationState> mConnectedDeviceForHostMgr = new ArrayMap<>();

    // Set of authorized devices that are persisted to disk.
    @GuardedBy("mLock")
    private ArraySet<UsbDeviceFingerprint> mPersistedAuthorizedDevices = new ArraySet<>();

    // Set of devices that are waiting to ask for user input.
    @GuardedBy("mLock")
    private ArrayMap<String, UsbAuthDeviceInfo> mPendingAskDevices = new ArrayMap<>();

    // Set of devices that are waiting to check for persisted devices.
    @GuardedBy("mLock")
    private ArrayMap<String, UsbAuthDeviceInfo> mPendingAllowPersistedDevices = new ArrayMap<>();

    private final Object mLock = new Object();

    public UsbAuthManager(Context context, UserManager userManager, UsbHostManager hostManager) {
        mHostManager = hostManager;
        mUserManager = userManager;
        mContext = context;
        mAuthEventsListener = new UsbAuthEventsListener();
        listenForService();
    }

    @VisibleForTesting
    public UsbAuthManager(
            Context context,
            UserManager userManager,
            UsbHostManager hostManager,
            IUsbAuthManager service) {
        mHostManager = hostManager;
        mUserManager = userManager;
        mContext = context;
        mAuthEventsListener = new UsbAuthEventsListener();
        setAndLinkService(service);
    }

    private void listenForService() {
        SystemServerInitThreadPool.submit(
                () -> {
                    IBinder serviceBinder = ServiceManager.waitForService(USB_AUTH_SERVICE_NAME);
                    FgThread.getExecutor()
                            .execute(
                                    () -> {
                                        Slog.d(TAG, "UsbAuthService arrived");
                                        setAndLinkService(
                                                IUsbAuthManager.Stub.asInterface(serviceBinder));
                                    });
                },
                "UsbAuthManager#listenForService");
    }

    private void setAndLinkService(IUsbAuthManager service) {
        synchronized (mLock) {
            if (mService != null) {
                mService.asBinder().unlinkToDeath(this, 0);
            }
            mService = service;
            if (mService != null) {
                try {
                    mService.asBinder().linkToDeath(this, 0);
                    mService.registerForUsbAuthorizationEvents(mAuthEventsListener);
                    updateSystemStateInternal(); // Update system state after linking to service
                } catch (RemoteException e) {
                    Slog.e(TAG, "linkToDeath failed", e);
                    mService = null;
                }
            }
        }
    }

    @Override
    public void binderDied() {
        mService = null;
        listenForService();
    }

    private IUsbAuthManager getService() {
        synchronized (mLock) {
            if (mService == null) {
                IUsbAuthManager service =
                        IUsbAuthManager.Stub.asInterface(
                                ServiceManager.getService(USB_AUTH_SERVICE_NAME));
                setAndLinkService(service);
            }
            return mService;
        }
    }

    @VisibleForTesting
    UsbAuthEventsListener getEventsListenerForTest() {
        return mAuthEventsListener;
    }

    @VisibleForTesting
    ArraySet<UsbDeviceFingerprint> getPersistedFingerprintsCopyForTesting() {
        synchronized (mLock) {
            return new ArraySet<UsbDeviceFingerprint>(mPersistedAuthorizedDevices);
        }
    }

    @VisibleForTesting
    void addFingerprintToPersistedForTest(UsbDeviceFingerprint fingerprint) {
        mPersistedAuthorizedDevices.add(fingerprint);
    }

    public void setSystemState(int state) {
        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                service.setSystemState(state);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set system state, binder died", e);
            binderDied(); // Explicitly call binderDied to clean up and re-listen for the service
        }
    }

    private void setAuthorizationStatusInternal(
            UsbAuthDeviceInfo deviceInfo, @UsbAuthorizationStatus int status) {
        Slog.d(
                TAG,
                TextUtils.formatSimple(
                        "Setting /dev/bus/usb/%03d/%03d authorized = %d",
                        deviceInfo.busNumber, deviceInfo.deviceNumber, status));

        try {
            IUsbAuthManager service = getService();
            if (service != null) {
                service.setAuthorizationStatus(deviceInfo, status);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set system state, binder died", e);
            binderDied();
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Failed to set authorization: ", e);
        }
    }

    public void onUpdateScreenLockedState(boolean locked) {
        Slog.d(TAG, "updateLockState: " + locked);

        synchronized (mLock) {
            mIsScreenLocked = locked;
        }

        updateSystemStateInternal();
    }

    /**
     * Update the logged in state of a user. This method updates the internal state related to user
     * logins and logouts. It specifically tracks full or admin users: If a full or admin user logs
     * in, their ID is added to an internal map of logged-in full users. If a full or admin user
     * logs out, their ID is removed from this map.
     *
     * <p>When any user logs in, their ID is also set as the current user ID.
     *
     * @param loggedIn True when a user logs in, False when a user is stopped.
     * @param userId User that is the target of this state change.
     */
    public void onUpdateLoggedInState(boolean loggedIn, int userId) {
        Slog.d(TAG, "updateLoggedInState: " + loggedIn + " " + userId);

        synchronized (mLock) {
            // Update the current user id to the logged in user.
            if (loggedIn) {
                mCurrentUserId = userId;
            }

            UserInfo userInfo = mUserManager.getUserInfo(userId);
            if (userInfo == null) {
                Slog.e(TAG, "onUpdateLoggedInState: user not found");
                return;
            }
            // Update mFullUsersLoggedIn map
            if (userInfo.isFull() && !userInfo.isGuest()) {
                // Update the logged-in status for any full user, excluding guests.
                if (loggedIn) {
                    mFullUsersLoggedIn.put(userId, true);
                } else {
                    mFullUsersLoggedIn.delete(userId);
                }
            }
        }
        updateSystemStateInternal();
    }

    private void updateSystemStateInternal() {
        @UsbAuthorizationSystemState int state;
        synchronized (mLock) {
            if (mFullUsersLoggedIn.size() == 0) {
                // No full users logged in
                state = UsbAuthorizationSystemState.BOOTED;
            } else if (!mIsScreenLocked && mFullUsersLoggedIn.get(mCurrentUserId, false)) {
                // Active user is a full user, and screen is unlocked
                state = UsbAuthorizationSystemState.LOGGED_IN;
            } else {
                // There are full users logged in, but either screen is locked
                // or the active user is not a full user (e.g., guest) (I.e. guest user logs in)
                state = UsbAuthorizationSystemState.SCREEN_LOCKED;
            }
            if (mCurrentState == state) {
                return;
            }
            mCurrentState = state;

            // On every state change, clear any pending ask or allow-persisted.
            mPendingAskDevices.clear();
            mPendingAllowPersistedDevices.clear();
        }
        setSystemState(state);
    }

    @GuardedBy("mLock")
    AuthorizationState getOrCreateConnectedDeviceLocked(String deviceAddress) {
        AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
        if (state == null) {
            state =
                    new AuthorizationState(
                            new UsbAuthDeviceInfo(), UsbAuthorizationStatus.DENIED, false, false);
            mConnectedDeviceForHostMgr.put(deviceAddress, state);
        }

        return state;
    }

    // Synchronizing with usb devices getting added via HostManager.
    //
    // If the usb device is already authorized, then we notify HostManager.
    // If not, we initialize the map with a default auth state.
    void usbDeviceAdded(String deviceAddress) {
        boolean notifyHostManager = false;
        Slog.d(TAG, TextUtils.formatSimple("Host added device %s", deviceAddress));

        synchronized (mLock) {
            AuthorizationState state = getOrCreateConnectedDeviceLocked(deviceAddress);
            state.mHostReady = true;

            if (state.mAuthorizationStatus == UsbAuthorizationStatus.AUTHORIZED
                    && !state.mHostAuthorized) {
                state.mHostAuthorized = true;
                notifyHostManager = true;
            }
        }

        // Check any pending interactions as well.
        checkAllowPersistedDevice(deviceAddress);
        checkAskDevice(deviceAddress);

        if (notifyHostManager) {
            Slog.d(TAG, TextUtils.formatSimple("Notify host for authorized - %s", deviceAddress));
            mHostManager.usbDeviceAuthorized(deviceAddress);
        }
    }

    // Remove device from connected devices.
    void usbDeviceRemoved(String deviceAddress) {
        Slog.d(TAG, TextUtils.formatSimple("Host removed device %s", deviceAddress));

        synchronized (mLock) {
            mPendingAskDevices.remove(deviceAddress);
            mPendingAllowPersistedDevices.remove(deviceAddress);
            mConnectedDeviceForHostMgr.remove(deviceAddress);
        }
    }

    // Check for the existence of a persisted device and send authorization
    // response for it.
    void checkAllowPersistedDevice(String deviceAddress) {
        boolean sendAuthorization = false;
        int status = UsbAuthorizationStatus.DENIED;
        UsbAuthDeviceInfo deviceInfo = null;

        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);

        synchronized (mLock) {
            if (!mPendingAllowPersistedDevices.containsKey(deviceAddress)) {
                return;
            }

            if (fingerprint != null) {
                sendAuthorization = true;
                deviceInfo = mPendingAllowPersistedDevices.remove(deviceAddress);

                if (mPersistedAuthorizedDevices.contains(fingerprint)) {
                    status = UsbAuthorizationStatus.AUTHORIZED;
                }
            }
        }

        if (sendAuthorization) {
            setAuthorizationStatusInternal(deviceInfo, status);
        }
    }

    // Check if ask device can send immediate response (persisted) or queue up for
    // user interaction.
    void checkAskDevice(String deviceAddress) {
        boolean sendAuthorization = false;
        boolean sendAskRequest = false;
        int status = UsbAuthorizationStatus.DENIED;
        UsbAuthDeviceInfo deviceInfo = null;

        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);
        UsbDevice device = mHostManager.getConnectedDeviceForAddress(deviceAddress);

        synchronized (mLock) {
            if (!mPendingAskDevices.containsKey(deviceAddress)) {
                return;
            }

            // Send a persisted result directly or queue up a user dialog.
            if (fingerprint != null && mPersistedAuthorizedDevices.contains(fingerprint)) {
                sendAuthorization = true;
                deviceInfo = mPendingAskDevices.remove(deviceAddress);
                status = UsbAuthorizationStatus.AUTHORIZED;
            } else if (device != null) {
                sendAskRequest = true;
            }
        }

        if (sendAuthorization && deviceInfo != null) {
            setAuthorizationStatusInternal(deviceInfo, status);
        }

        if (sendAskRequest && device != null) {
            // TODO(b/432527670) - Send this up to UsbAuthorizationActivity for user interaction.
            //
            // For now, always respond with a persisted "authorized".
            setAuthorizationResponse(
                    device, UsbAuthorizationStatus.AUTHORIZED, /* isPersistent= */ true);
        }
    }

    // Set authorization response for a specific device. Valid for any currently connected device.
    //
    // This should be sent when the UI responds to an "Ask" request for a USB device.
    void setAuthorizationResponse(
            UsbDevice device, @UsbAuthorizationStatus int response, boolean isPersistent) {
        String deviceAddress = device.getDeviceName();
        UsbDeviceFingerprint fingerprint =
                mHostManager.getConnectedDeviceFingerprintForAddress(deviceAddress);
        UsbAuthDeviceInfo deviceInfo = null;

        if (mHostManager.getConnectedDeviceForAddress(deviceAddress) == null) {
            Slog.e(TAG, "Attempted to authorize device that's not connected: " + deviceAddress);
            return;
        }

        synchronized (mLock) {
            // Expect the device to be a pending Ask.
            deviceInfo = mPendingAskDevices.remove(deviceAddress);

            // If it's not a pending Ask, then also check connected devices list.
            if (deviceInfo == null) {
                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
                if (state != null && state.mHostReady) {
                    deviceInfo = state.mDeviceInfo;
                }
            }

            if (deviceInfo != null) {
                if (isPersistent && response == UsbAuthorizationStatus.AUTHORIZED) {
                    if (fingerprint != null) {
                        mPersistedAuthorizedDevices.add(fingerprint);
                    }
                }
            }
        }

        if (deviceInfo != null) {
            setAuthorizationStatusInternal(deviceInfo, response);
        }
    }

    /** Handle events received from UsbAuthService.
     *
     * This listener is registered directly with the usb_auth native service and is not accessible
     * to apps or other services. As a result, there is no need to enforce permissions on these
     * callbacks.
     */
    class UsbAuthEventsListener extends IUsbAuthEventsListener.Stub {
        UsbAuthEventsListener() {}

        @Override
        @RequiresNoPermission
        public void onDeviceAskForAuthorization(UsbAuthDeviceInfo device) {
            String deviceAddress;
            boolean readyForCheck = false;

            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "AuthEvents: received ASK request for bus %03d dev %03d",
                            device.busNumber, device.deviceNumber));

            synchronized (mLock) {
                deviceAddress = UsbDevice.getDeviceName(device.busNumber, device.deviceNumber);
                mPendingAskDevices.put(deviceAddress, device);

                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
                if (state != null) {
                    readyForCheck = state.mHostReady;
                }
            }

            if (readyForCheck) {
                checkAskDevice(deviceAddress);
            }
        }

        @Override
        @RequiresNoPermission
        public void onDeviceCheckPersistedAuthorization(UsbAuthDeviceInfo device) {
            String deviceAddress;
            boolean readyForCheck = false;

            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "AuthEvents: received ALLOW-PERSIST request for bus %03d dev %03d",
                            device.busNumber, device.deviceNumber));

            synchronized (mLock) {
                deviceAddress = UsbDevice.getDeviceName(device.busNumber, device.deviceNumber);
                mPendingAllowPersistedDevices.put(deviceAddress, device);

                AuthorizationState state = mConnectedDeviceForHostMgr.get(deviceAddress);
                if (state != null) {
                    readyForCheck = state.mHostReady;
                }
            }

            if (readyForCheck) {
                checkAllowPersistedDevice(deviceAddress);
            }
        }

        // Note: When USB devices disconnect, this callback will be called with
        // UsbAuthorizationStatus.DENIED if it was previously authorized. This is done in order to
        // avoid a race where authorization arrives after a device is removed and impacts the next
        // connection.
        //    device added (service starts processing), device removed,
        //    onDeviceAuthorizationStatusChanged(.., AUTHORIZED, ...)
        @Override
        @RequiresNoPermission
        public void onDeviceAuthorizationStatusChanged(
                UsbAuthDeviceInfo device,
                @UsbAuthorizationStatus int status,
                @UsbAuthorizationSystemState int systemState) {
            boolean notifyHostManager = false;
            boolean hostAuthorized = false;
            String deviceAddress = null;

            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "AuthEvents: received STATUS-CHANGE for bus %03d dev %03d to %d in"
                                    + " state %d",
                            device.busNumber, device.deviceNumber, status, systemState));

            synchronized (mLock) {
                deviceAddress = UsbDevice.getDeviceName(device.busNumber, device.deviceNumber);
                AuthorizationState state = getOrCreateConnectedDeviceLocked(deviceAddress);

                state.mDeviceInfo = device;
                state.mAuthorizationStatus = status;
                if (status == UsbAuthorizationStatus.AUTHORIZED
                        && state.mHostReady
                        && !state.mHostAuthorized) {
                    notifyHostManager = true;
                    state.mHostAuthorized = true;
                } else if (state.mHostReady
                        && state.mHostAuthorized
                        && status != UsbAuthorizationStatus.AUTHORIZED) {
                    notifyHostManager = true;
                    state.mHostAuthorized = false;
                }

                // Hold authorized state for what message to send to host manager.
                hostAuthorized = state.mHostAuthorized;
            }
            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "onDeviceAuthorizationStatusChange (%03d/%03d) for %s",
                            device.busNumber, device.deviceNumber, deviceAddress));

            if (notifyHostManager) {
                Slog.d(
                        TAG,
                        TextUtils.formatSimple(
                                "AuthEvents: Notify host for authorized(%b) - %s",
                                hostAuthorized, deviceAddress));

                if (hostAuthorized) {
                    mHostManager.usbDeviceAuthorized(deviceAddress);
                } else {
                    mHostManager.usbDeviceDeauthorized(deviceAddress);
                }
            }
        }
    }
}
