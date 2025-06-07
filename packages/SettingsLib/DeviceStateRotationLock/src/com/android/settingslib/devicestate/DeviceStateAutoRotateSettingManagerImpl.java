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
package com.android.settingslib.devicestate;

import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_IGNORED;
import static android.provider.Settings.Secure.DEVICE_STATE_ROTATION_LOCK_LOCKED;
import static android.provider.Settings.Secure.DeviceStateRotationLockKey;
import static android.provider.Settings.Secure.DeviceStateRotationLockSetting;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.R;
import com.android.internal.view.RotationPolicy;
import com.android.window.flags.Flags;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementation of {@link DeviceStateAutoRotateSettingManager}. This implementation is a part of
 * refactoring, it should be used when
 * {@link Flags#FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR}
 * is enabled.
 */
public class DeviceStateAutoRotateSettingManagerImpl implements
        DeviceStateAutoRotateSettingManager {
    // TODO: b/397928958 rename the fields and apis from rotationLock to autoRotate.

    private static final String TAG = "DSAutoRotateMngr";
    private static final String SEPARATOR_REGEX = ":";

    private final Set<DeviceStateAutoRotateSettingListener> mSettingListeners = new HashSet<>();
    private final SparseIntArray mFallbackPostureMap = new SparseIntArray();
    private final SparseIntArray mDefaultDeviceStateAutoRotateSetting = new SparseIntArray();
    private final List<SettableDeviceState> mSettableDeviceState = new ArrayList<>();
    private final SecureSettings mSecureSettings;
    private final Handler mMainHandler;
    private final PostureDeviceStateConverter mPostureDeviceStateConverter;

    public DeviceStateAutoRotateSettingManagerImpl(
            Context context,
            Executor backgroundExecutor,
            SecureSettings secureSettings,
            Handler mainHandler,
            PostureDeviceStateConverter postureDeviceStateConverter
    ) {
        mSecureSettings = secureSettings;
        mMainHandler = mainHandler;
        mPostureDeviceStateConverter = postureDeviceStateConverter;

        loadAutoRotateDeviceStates(context);
        final ContentObserver contentObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                notifyListeners();
            }
        };
        backgroundExecutor.execute(() ->
                mSecureSettings.registerContentObserver(
                        DEVICE_STATE_ROTATION_LOCK,
                        /* notifyForDescendants= */false,
                        contentObserver,
                        UserHandle.USER_CURRENT
                )
        );
    }

    @Override
    public void registerListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        mSettingListeners.add(settingListener);
    }

    @Override
    public void unregisterListener(@NonNull DeviceStateAutoRotateSettingListener settingListener) {
        if (!mSettingListeners.remove(settingListener)) {
            Log.w(TAG, "Attempting to unregister a listener hadn't been registered");
        }
    }

    @Override
    @DeviceStateRotationLockSetting
    public Integer getRotationLockSetting(int deviceState) {
        final int devicePosture = mPostureDeviceStateConverter.deviceStateToPosture(deviceState);
        final SparseIntArray deviceStateAutoRotateSetting = getRotationLockSetting();

        return extractSettingForDevicePosture(devicePosture, deviceStateAutoRotateSetting);
    }

    @Override
    public SparseIntArray getRotationLockSetting() {
        final String serializedSetting = mSecureSettings.getStringForUser(
                DEVICE_STATE_ROTATION_LOCK, UserHandle.USER_CURRENT);

        final SparseIntArray deviceStateAutoRotateSetting =
                deserializeSettingStringToMap(serializedSetting);

        if (!areAllDefaultsPresent(deviceStateAutoRotateSetting)) return null;

        final boolean allIgnoredStatesResolved = resolveIgnoredAutoRotateStates(
                deviceStateAutoRotateSetting);
        if (!allIgnoredStatesResolved) return null;

        return deviceStateAutoRotateSetting;
    }

    @Override
    public Boolean isRotationLocked(int deviceState) {
        final Integer autoRotateValue = getRotationLockSetting(deviceState);
        return autoRotateValue == null ? null
                : autoRotateValue == DEVICE_STATE_ROTATION_LOCK_LOCKED;
    }

    @Override
    public Boolean isRotationLockedForAllStates() {
        final SparseIntArray deviceStateAutoRotateSetting = getRotationLockSetting();
        if (deviceStateAutoRotateSetting == null) return null;
        for (int i = 0; i < deviceStateAutoRotateSetting.size(); i++) {
            if (deviceStateAutoRotateSetting.valueAt(i) != DEVICE_STATE_ROTATION_LOCK_LOCKED) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public List<SettableDeviceState> getSettableDeviceStates() {
        return mSettableDeviceState;
    }

    @Override
    public void updateSetting(int deviceState, boolean rotationLock) {
        RotationPolicy.requestDeviceStateAutoRotateSettingChange(deviceState, !rotationLock);
    }

    @NonNull
    @Override
    public SparseIntArray updateSetting(@NonNull SparseIntArray proposedSetting,
            @NonNull SparseIntArray currentSetting) {
        if (!areAllDefaultsPresent(proposedSetting) || !areAllDefaultsPresent(currentSetting)) {
            // Either the postures in proposed setting or current setting map do not match with
            // device postures defined in the default in configuration. We should still go ahead
            // with the update because after the write, the checks we have setup should be able
            // correct it.
            Log.w(TAG, "The postures in proposed setting or current setting map does not "
                    + "match with device postures defined in the default in configuration.\n"
                    + "proposedSetting=" + proposedSetting + "\ncurrentSetting="
                    + currentSetting);
        }

        for (int i = 0; i < mFallbackPostureMap.size(); i++) {
            final int devicePosture = mFallbackPostureMap.keyAt(i);
            final int fallbackPosture = mFallbackPostureMap.valueAt(i);

            final int proposedAutoRotateForDevicePosture =
                    getValueFromIntArray(devicePosture, proposedSetting);
            final int proposedAutoRotateForFallbackDevicePosture =
                    getValueFromIntArray(fallbackPosture, proposedSetting);
            final int currentAutoRotateForDevicePosture =
                    getValueFromIntArray(devicePosture, currentSetting);
            final int currentAutoRotateForFallbackDevicePosture =
                    getValueFromIntArray(fallbackPosture, currentSetting);

            if (proposedAutoRotateForDevicePosture != currentAutoRotateForDevicePosture
                    && proposedAutoRotateForFallbackDevicePosture
                    != currentAutoRotateForFallbackDevicePosture
                    && proposedAutoRotateForDevicePosture
                    != proposedAutoRotateForFallbackDevicePosture) {

                final StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("Auto-rotate setting for both device state and its fallback "
                                + "state are being updated to different values.")
                        .append("\nProposed setting value for device posture:")
                        .append(proposedAutoRotateForDevicePosture)
                        .append("\nCurrent setting value for device posture:")
                        .append(currentAutoRotateForDevicePosture)
                        .append("\nProposed setting value for fallback device posture:")
                        .append(proposedAutoRotateForFallbackDevicePosture)
                        .append("\nCurrent setting value for fallback device posture:")
                        .append(currentAutoRotateForFallbackDevicePosture)
                        .append("\nSetting value of ").append(fallbackPosture)
                        .append("will be set to ").append(proposedAutoRotateForDevicePosture);

                Log.w(TAG, errorMessage.toString());
            }
            if (proposedAutoRotateForDevicePosture != currentAutoRotateForDevicePosture
                    && proposedAutoRotateForDevicePosture != DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                proposedSetting.put(fallbackPosture, proposedAutoRotateForDevicePosture);
            }
            proposedSetting.put(devicePosture, DEVICE_STATE_ROTATION_LOCK_IGNORED);
        }

        final String serializedDeviceStateAutoRotateSetting =
                convertIntArrayToSerializedSetting(proposedSetting);
        mSecureSettings.putStringForUser(DEVICE_STATE_ROTATION_LOCK,
                serializedDeviceStateAutoRotateSetting, UserHandle.USER_CURRENT);

        resolveIgnoredAutoRotateStates(proposedSetting);
        return proposedSetting;
    }

    @Override
    public void dump(@NonNull PrintWriter writer, String[] args) {
        IndentingPrintWriter indentingWriter = new IndentingPrintWriter(writer, "  ");
        indentingWriter.println("DeviceStateAutoRotateSettingManagerImpl");
        indentingWriter.increaseIndent();
        indentingWriter.println("fallbackPostureMap: " + mFallbackPostureMap);
        indentingWriter.println("settableDeviceState: " + mSettableDeviceState);
        indentingWriter.decreaseIndent();
    }

    @NonNull
    @Override
    public SparseIntArray getDefaultRotationLockSetting() {
        final SparseIntArray defaultDeviceStateAutoRotateSetting =
                mDefaultDeviceStateAutoRotateSetting.clone();
        resolveIgnoredAutoRotateStates(defaultDeviceStateAutoRotateSetting);
        return defaultDeviceStateAutoRotateSetting;
    }

    private void notifyListeners() {
        for (DeviceStateAutoRotateSettingListener listener : mSettingListeners) {
            listener.onSettingsChanged();
        }
    }

    /**
     * Loads the {@link R.array#config_perDeviceStateRotationLockDefaults} array and populates
     * the {@link #mFallbackPostureMap}, {@link #mSettableDeviceState}, and
     * {@link #mDefaultDeviceStateAutoRotateSetting} fields.
     */
    private void loadAutoRotateDeviceStates(Context context) {
        final String[] perDeviceStateAutoRotateDefaults =
                context.getResources().getStringArray(
                        R.array.config_perDeviceStateRotationLockDefaults);
        for (String entry : perDeviceStateAutoRotateDefaults) {
            final PostureEntry parsedEntry = parsePostureEntry(entry);

            final int posture = parsedEntry.posture;
            final int autoRotateValue = parsedEntry.autoRotateValue;
            final Integer fallbackPosture = parsedEntry.fallbackPosture;
            final Integer deviceState = mPostureDeviceStateConverter.postureToDeviceState(posture);

            if (deviceState == null) {
                throw new IllegalStateException("No matching device state for posture: " + posture);
            }
            mSettableDeviceState.add(new SettableDeviceState(deviceState,
                    autoRotateValue != DEVICE_STATE_ROTATION_LOCK_IGNORED));

            if (autoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED
                    && fallbackPosture != null) {
                mFallbackPostureMap.put(posture, fallbackPosture);
            } else if (autoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                throw new IllegalStateException(
                        "Auto rotate setting is IGNORED for posture=" + posture
                                + ", but no fallback-posture defined");
            }
            mDefaultDeviceStateAutoRotateSetting.put(posture, autoRotateValue);
        }
    }

    @NonNull
    private PostureEntry parsePostureEntry(String entry) {
        final String[] values = entry.split(SEPARATOR_REGEX);
        if (values.length < 2 || values.length > 3) { // It should contain 2 or 3 values.
            throw new IllegalStateException("Invalid number of values in entry: " + entry);
        }
        try {
            final int posture = Integer.parseInt(values[0]);
            final int autoRotateValue = Integer.parseInt(values[1]);
            final Integer fallbackPosture = (values.length == 3) ? Integer.parseInt(values[2])
                    : null;

            return new PostureEntry(posture, autoRotateValue, fallbackPosture);

        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid number format in '" + entry + "'" + e.getMessage());
        }
    }


    /**
     * Deserializes the string value from {@link Settings.Secure#DEVICE_STATE_ROTATION_LOCK}
     * into a {@link SparseIntArray}.
     *
     * The expected format is a series of key-value pairs separated by {@link #SEPARATOR_REGEX},
     * e.g., "posture1:value1:posture2:value2".
     *
     * @param serializedSetting The string read from settings.
     * @return A {@link SparseIntArray} representing the settings, or null if the input string
     * is null, empty, or has an invalid format.
     */
    @Nullable
    private SparseIntArray deserializeSettingStringToMap(String serializedSetting) {
        if (serializedSetting == null || serializedSetting.isEmpty()) return null;
        final String[] deserializedSettings = serializedSetting.split(SEPARATOR_REGEX);
        if (deserializedSettings.length % 2 != 0) {
            Log.e(TAG, "Invalid format in serializedSetting=" + serializedSetting
                    + "\nOdd number of elements in the list");
            return null;
        }
        final SparseIntArray deviceStateAutoRotateSetting = new SparseIntArray(
                deserializedSettings.length / 2);

        for (int i = 0; i < deserializedSettings.length; i += 2) {
            try {
                final int key = Integer.parseInt(deserializedSettings[i]);
                final int value = Integer.parseInt(deserializedSettings[i + 1]);
                // Check if the value is within the expected range (0, 1, or 2).
                if (value < 0 || value > 2) {
                    Log.e(TAG, "Invalid format in serializedSetting=" + serializedSetting
                            + "\nInvalid value in pair: key=" + deserializedSettings[i] + ", value="
                            + deserializedSettings[i + 1]);
                    return null;
                }
                deviceStateAutoRotateSetting.put(key, value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid number format in serializedSetting=" + serializedSetting
                        + "\nError parsing pair: " + deserializedSettings[i] + ":"
                        + deserializedSettings[i + 1], e);
                return null;
            }
        }
        return deviceStateAutoRotateSetting;
    }

    /**
     * Return true if all device postures defined in the default configuration
     * ({@link #mDefaultDeviceStateAutoRotateSetting}) are present in the provided
     * {@code deviceStateAutoRotateSetting} map. Return false otherwise.
     *
     * @param deviceStateAutoRotateSetting The settings map to be tested.
     */
    private boolean areAllDefaultsPresent(SparseIntArray deviceStateAutoRotateSetting) {
        if (deviceStateAutoRotateSetting == null || deviceStateAutoRotateSetting.size()
                != mDefaultDeviceStateAutoRotateSetting.size()) {
            return false;
        }
        // Iterate through the default settings to find any postures that might be missing.
        for (int i = 0; i < mDefaultDeviceStateAutoRotateSetting.size(); i++) {
            final int devicePosture = mDefaultDeviceStateAutoRotateSetting.keyAt(i);
            final int indexOfDevicePosture = deviceStateAutoRotateSetting.indexOfKey(devicePosture);

            if (indexOfDevicePosture < 0) {
                // The posture is not found in the current settings.
                return false;
            }
        }
        return true;
    }

    /**
     * <p>
     * Applies the "resolved" logic to the provided settings map. For device postures
     * that are marked as {@link #DEVICE_STATE_ROTATION_LOCK_IGNORED} in the default
     * configuration, this method substitutes their value with the setting of their
     * designated fallback posture.
     * </p>
     * <p>
     * If a posture is marked as ignored in the default config but has a non-ignored
     * value in the current settings, it indicates a data inconsistency, and the
     * settings map is should be made null to trigger a reset.
     * </p>
     *
     * @param deviceStateAutoRotateSetting The settings map to resolve ignored states for.
     * @return True if ignored states were successfully resolved and false if resolution wasn't
     * successful.
     */
    private boolean resolveIgnoredAutoRotateStates(SparseIntArray deviceStateAutoRotateSetting) {
        if (deviceStateAutoRotateSetting == null) return false;
        // Iterate through the default settings to identify ignored states.
        for (int i = 0; i < mDefaultDeviceStateAutoRotateSetting.size(); i++) {
            final int devicePosture = mDefaultDeviceStateAutoRotateSetting.keyAt(i);
            final int defaultAutoRotateValue = mDefaultDeviceStateAutoRotateSetting.valueAt(i);
            final int indexOfDevicePosture = deviceStateAutoRotateSetting.indexOfKey(devicePosture);
            if (indexOfDevicePosture < 0) return false;
            final int autoRotateValue = deviceStateAutoRotateSetting.valueAt(indexOfDevicePosture);

            if (defaultAutoRotateValue == DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                // If the current setting for an ignored posture is NOT ignored, data is corrupt.
                if (autoRotateValue != DEVICE_STATE_ROTATION_LOCK_IGNORED) {
                    Log.w(TAG, "Data corruption: Ignored posture " + devicePosture
                            + " has non-ignored setting value " + autoRotateValue);
                    return false;
                }
                // If the setting is ignored for this posture, check the fallback posture
                // and use its resolved setting.
                final Integer fallbackAutoRotateValue = getFallbackAutoRotateSetting(devicePosture,
                        deviceStateAutoRotateSetting);
                if (fallbackAutoRotateValue != null) {
                    deviceStateAutoRotateSetting.put(devicePosture, fallbackAutoRotateValue);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Retrieves the value associated with the given key from the SparseIntArray.
     * <p>
     * This method is intended for use cases where the specified {@code key} is strongly expected to
     * exist within the {@code intArray}. If the key is not found, this method throws an
     * {@link IllegalStateException}. This behavior assumes that a missing key in this context
     * signifies a critical inconsistency or an unexpected program state, rather than a common
     * 'not found' scenario.
     * </p>
     */
    private int getValueFromIntArray(int key, SparseIntArray intArray) {
        final int indexOfKey = intArray.indexOfKey(key);
        if (indexOfKey < 0) {
            throw new IllegalStateException(
                    "Key " + key + " not found in SparseIntArray=" + intArray);
        }
        return intArray.valueAt(indexOfKey);
    }

    @Nullable
    private Integer getFallbackAutoRotateSetting(int devicePosture,
            SparseIntArray deviceStateAutoRotateSetting) {
        final int indexOfFallback = mFallbackPostureMap.indexOfKey(devicePosture);
        if (indexOfFallback < 0) {
            Log.w(TAG, "Setting is ignored, but no fallback was specified.");
            return null;
        }
        int fallbackPosture = mFallbackPostureMap.valueAt(indexOfFallback);
        return extractSettingForDevicePosture(fallbackPosture, deviceStateAutoRotateSetting);
    }

    /**
     * Safely extracts the setting value for a specific {@code devicePosture} from the
     * provided {@code deviceStateAutoRotateSetting} map.
     */
    @Nullable
    private Integer extractSettingForDevicePosture(
            @DeviceStateRotationLockKey int devicePosture,
            SparseIntArray deviceStateAutoRotateSetting
    ) {
        if (deviceStateAutoRotateSetting == null) return null;

        final int indexOfDevicePosture = deviceStateAutoRotateSetting.indexOfKey(devicePosture);
        if (indexOfDevicePosture < 0) {
            return null;
        } else {
            return deviceStateAutoRotateSetting.get(devicePosture);
        }
    }

    private static String convertIntArrayToSerializedSetting(
            SparseIntArray intArray) {
        return IntStream.range(0, intArray.size())
                .mapToObj(i -> intArray.keyAt(i) + SEPARATOR_REGEX + intArray.valueAt(i))
                .collect(Collectors.joining(SEPARATOR_REGEX));
    }

    private record PostureEntry(int posture, int autoRotateValue, Integer fallbackPosture) {
    }
}
