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
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.impl.CameraMetadataNative;

import java.util.function.ObjLongConsumer;

/**
 * Builder class for creating {@link CaptureResult} instances to be passed to the Consumer from
 * {@link VirtualCameraCallback#onConfigureSession(VirtualCameraSessionConfig, ObjLongConsumer)}.
 *
 * @hide
 */
// There is no CaptureResult.Builder for now, so this helps VDM clients to build CaptureResult
// instances to be passed to the VirtualCameraService. Only the native metadata part is passed
// to the virtual camera service, the parent CaptureRequest is not used
@SuppressLint("TopLevelBuilder")
@SystemApi
@FlaggedApi(android.companion.virtualdevice.flags.Flags.FLAG_VIRTUAL_CAMERA_METADATA)
public final class CaptureResultBuilder {
    private final CameraMetadataNative mNativeMetadata;

    /**
     * Builder for creating a {@link CaptureResult}.
     *
     * @see CameraCharacteristics#getAvailableCaptureResultKeys
     */
    public CaptureResultBuilder() {
        mNativeMetadata = new CameraMetadataNative();
    }

    /**
     * Builder for creating a {@link CaptureResult} starting from a copy of an existing instance.
     */
    public CaptureResultBuilder(@NonNull CaptureResult captureResult) {
        mNativeMetadata = new CameraMetadataNative(captureResult.getNativeMetadata());
    }

    /**
     * Set a capture result field to a value. The field definitions can be found in
     * {@link CaptureResult}.
     * <p>
     * Setting a field to {@code null} will remove that field from the capture result metadata.
     *
     * @param key   The metadata field to write.
     * @param value The value to set the field to, which must be of a matching type to the key.
     */
    @SuppressLint("KotlinOperator")
    @NonNull
    public <T> CaptureResultBuilder set(@NonNull CaptureResult.Key<T> key, T value) {
        mNativeMetadata.set(key, value);
        return this;
    }

    /**
     * Builds the {@link CaptureResult} object with the set of {@link CaptureResult.Key}s.
     *
     * @return A new {@link CaptureResult} instance to be consumed by a Virtual Camera.
     */
    public @NonNull CaptureResult build() {
        // cameraId, captureRequest, requestId and frameNumber are not used
        return new CaptureResult(mNativeMetadata, 0 /* requestId */);
    }
}
