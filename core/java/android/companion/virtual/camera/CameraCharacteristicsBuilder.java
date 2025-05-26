/*
 * Copyright 2025 The Android Open Source Project
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

package android.companion.virtual.camera;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.impl.CameraMetadataNative;

/**
 * Builder class for creating {@link CameraCharacteristics} instances to be used in a
 * {@link VirtualCameraConfig}.
 * @hide
 */
// There is no CameraCharacteristics.Builder for now, so this helps VDM clients to build
// CameraCharacteristics instances for their VirtualCameraConfig.
@SuppressLint("TopLevelBuilder")
@SystemApi
@FlaggedApi(android.companion.virtualdevice.flags.Flags.FLAG_VIRTUAL_CAMERA_METADATA)
public final class CameraCharacteristicsBuilder {
    private final CameraMetadataNative mNativeMetadata;

    /**
     * Builder for creating {@link CameraCharacteristics} starting from an empty list of keys.
     */
    public CameraCharacteristicsBuilder() {
        mNativeMetadata = new CameraMetadataNative();
    }

    /**
     * Builder for creating {@link CameraCharacteristics} starting from a copy of
     * the passed characteristics.
     */
    public CameraCharacteristicsBuilder(@NonNull CameraCharacteristics characteristics) {
        mNativeMetadata = new CameraMetadataNative(characteristics.getNativeMetadata());
    }

    /**
     * Set a camera characteristics field to a value. The field definitions can be found in
     * {@link CameraCharacteristics}.
     *
     * <p>Setting a field to {@code null} will remove that field from the camera
     * characteristics.
     * Unless the field is optional, removing it will likely produce an error from the camera
     * device when the camera characteristics are set.</p>
     *
     * @param key   The metadata field to write.
     * @param value The value to set the field to, which must be of a matching type to the key.
     */
    @SuppressLint("KotlinOperator")
    @NonNull
    public <T> CameraCharacteristicsBuilder set(@NonNull CameraCharacteristics.Key<T> key,
            T value) {
        mNativeMetadata.set(key, value);
        return this;
    }

    /**
     * Builds the {@link CameraCharacteristics} object with the set
     * {@link CameraCharacteristics.Key}s.
     *
     * @return A new {@link CameraCharacteristics} instance to be set in the
     * {@link VirtualCameraConfig} of a Virtual Camera.
     */
    public @NonNull CameraCharacteristics build() {
        return new CameraCharacteristics(mNativeMetadata);
    }
}
