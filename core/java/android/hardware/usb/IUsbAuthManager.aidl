/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.usb;

import android.hardware.usb.UsbAuthDeviceInfo;
import android.hardware.usb.UsbAuthorizationStatus;
import android.hardware.usb.UsbAuthorizationSystemState;
import java.util.List;

/** @hide */
interface IUsbAuthManager {
    /**
     * Gets a list of all currently authorized USB devices.
     *
     * @return A List of UsbAuthDeviceInfo objects that are authorized.
     */
    List<UsbAuthDeviceInfo> getAuthorizedUsbDevices();

    /**
     * Gets a list of USB devices that are currently in a deferred state.
     * These devices might be waiting for user input or further policy evaluation.
     * A deferred device has the state `DENIED_AND_DEFERRED`.
     *
     * @return A List of UsbAuthDeviceInfo objects that are deferred.
     */
    List<UsbAuthDeviceInfo> getDeferredUsbDevices();

    /**
     * Gets a list of USB devices that require explicit authorization from the user
     * (i.e., "ask devices"). These are devices that are not yet
     * authorized and are not internal or automatically handled.
     * These devices are in DENIED state.
     *
     * @return A List of UsbAuthDeviceInfo objects awaiting explicit authorization.
     */
    List<UsbAuthDeviceInfo> getDevicesAwaitingAuthorization();

    /**
     * Gets the authorization status for a specific USB device.
     *
     * @param device The UsbAuthDeviceInfo to check.
     * @return The authorization status of the device.
     */
    UsbAuthorizationStatus getAuthorizationStatus(in UsbAuthDeviceInfo device);

    /**
     * Sets the authorization status for a specific USB device.
     *
     * @param device The UsbAuthDeviceInfo to update.
     * @param status The new authorization status to set.
     */
    void setAuthorizationStatus(in UsbAuthDeviceInfo device, in UsbAuthorizationStatus status);

    /**
     * Inform {@link IUsbAuthManager} about the current system state. This is a stateful
     * event that will cause policy evaluations.
     */
    void setSystemState(in UsbAuthorizationSystemState state);
}
