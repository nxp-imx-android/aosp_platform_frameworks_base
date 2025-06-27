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

import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfiguration;
import android.hardware.camera2.params.StreamConfigurationDuration;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;

import java.util.List;

/**
 * Builder class for creating {@link CameraCharacteristics} instances to be used in a
 * {@link VirtualCameraConfig}.
 *
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
     * Builder for creating {@link CameraCharacteristics} starting from a preset list of
     * {@link CameraCharacteristics.Key}s and values necessary to create a complete and valid
     * {@link CameraCharacteristics} instance. Once built, the {@link CameraCharacteristics}
     * cover the mandatory keys from the
     * <a href="https://android.googlesource.com/platform/hardware/libhardware/+/refs/heads/main/include_all/hardware/camera3.h">Camera HAL specification</a>.
     * and can be used to create a functional {@link VirtualCamera} that can be queried and opened
     * by camera apps.
     * <p>
     * The filled in keys and values are opinionated and can be further overwritten with the
     * desired values.
     */
    public CameraCharacteristicsBuilder() {
        mNativeMetadata = new CameraMetadataNative();
        setDefaults();
    }

    /**
     * Builder for creating {@link CameraCharacteristics} starting from a copy of
     * the passed characteristics.
     * <p>
     * It doesn't add any default keys, so it's the responsibility of the caller to add all the
     * mandatory {@link CameraCharacteristics.Key}s required by the Camera HAL for instantiating
     * and opening the camera.
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
     * Sets the {@link CameraCharacteristics.Key}s available for the {@link CameraCharacteristics}.
     * Any key not listed here won't be queryable by the application using the camera.
     */
    @SuppressLint("MissingGetterMatchingBuilder") // the getter method is getKeys()
    @NonNull
    public <T> CameraCharacteristicsBuilder setAvailableCharacteristicsKeys(
            @Nullable List<CameraCharacteristics.Key<?>> availableCharacteristicsKeys) {
        int[] characteristicsTags = null;
        if (availableCharacteristicsKeys != null) {
            characteristicsTags = new int[availableCharacteristicsKeys.size()];
            for (int i = 0; i < availableCharacteristicsKeys.size(); i++) {
                characteristicsTags[i] =
                        availableCharacteristicsKeys.get(i).getNativeKey().getTag();
            }
        }
        mNativeMetadata.set(CameraCharacteristics.REQUEST_AVAILABLE_CHARACTERISTICS_KEYS,
                characteristicsTags);

        return this;
    }

    /**
     * Sets the {@link CaptureRequest.Key}s available for the {@link CaptureRequest}.
     */
    @NonNull
    public <T> CameraCharacteristicsBuilder setAvailableCaptureRequestKeys(
            @Nullable List<CaptureRequest.Key<?>> availableCaptureRequestKeys) {
        int[] captureRequestTags = null;
        if (availableCaptureRequestKeys != null) {
            captureRequestTags = new int[availableCaptureRequestKeys.size()];
            for (int i = 0; i < availableCaptureRequestKeys.size(); i++) {
                captureRequestTags[i] = availableCaptureRequestKeys.get(i).getNativeKey().getTag();
            }
        }
        mNativeMetadata.set(CameraCharacteristics.REQUEST_AVAILABLE_REQUEST_KEYS,
                captureRequestTags);

        return this;
    }

    /**
     * Sets the {@link CaptureResult.Key}s available for the {@link CaptureResult}.
     */
    @NonNull
    public <T> CameraCharacteristicsBuilder setAvailableCaptureResultKeys(
            @Nullable List<CaptureResult.Key<?>> availableCaptureResultKeys) {
        int[] captureResultTags = null;
        if (availableCaptureResultKeys != null) {
            captureResultTags = new int[availableCaptureResultKeys.size()];
            for (int i = 0; i < availableCaptureResultKeys.size(); i++) {
                captureResultTags[i] = availableCaptureResultKeys.get(i).getNativeKey().getTag();
            }
        }
        mNativeMetadata.set(CameraCharacteristics.REQUEST_AVAILABLE_RESULT_KEYS, captureResultTags);

        return this;
    }

    /**
     * Sets the {@link CaptureRequest.Key}s available for the {@link SessionConfiguration}.
     */
    @NonNull
    public <T> CameraCharacteristicsBuilder setAvailableSessionKeys(
            @Nullable List<CaptureRequest.Key<?>> availableSessionKeys) {
        int[] sessionTags = null;
        if (availableSessionKeys != null) {
            sessionTags = new int[availableSessionKeys.size()];
            for (int i = 0; i < availableSessionKeys.size(); i++) {
                sessionTags[i] = availableSessionKeys.get(i).getNativeKey().getTag();
            }
        }
        mNativeMetadata.set(CameraCharacteristics.REQUEST_AVAILABLE_SESSION_KEYS, sessionTags);

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

    // set the default keys and values necessary for a valid and usable CameraCharacteristics
    private void setDefaults() {
        List<CameraCharacteristics.Key<?>> availableCharacteristicsKeys = List.of(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL,
                CameraCharacteristics.FLASH_INFO_AVAILABLE, CameraCharacteristics.LENS_FACING,
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
                CameraCharacteristics.SENSOR_ORIENTATION,
                CameraCharacteristics.SENSOR_READOUT_TIMESTAMP,
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE,
                CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
                CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES,
                CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES,
                CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,
                CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES,
                CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES,
                CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,
                CameraCharacteristics.CONTROL_AVAILABLE_MODES,
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES,
                CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS,
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES,
                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES,
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES,
                CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE,
                CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP,
                CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE,
                CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE,
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES,
                CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE,
                CameraCharacteristics.SCALER_CROPPING_TYPE,
                CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES,
                CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT,
                CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION,
                CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT,
                CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH,
                CameraCharacteristics.SYNC_MAX_LATENCY,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE,
                CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);

        List<CaptureRequest.Key<?>> availableCaptureRequestKeys = List.of(
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.SCALER_CROP_REGION,
                CaptureRequest.CONTROL_EFFECT_MODE,
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_SCENE_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_ZOOM_RATIO,
                CaptureRequest.FLASH_MODE,
                CaptureRequest.JPEG_THUMBNAIL_SIZE,
                CaptureRequest.JPEG_ORIENTATION,
                CaptureRequest.JPEG_QUALITY,
                CaptureRequest.JPEG_THUMBNAIL_QUALITY,
                CaptureRequest.JPEG_THUMBNAIL_SIZE,
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.STATISTICS_FACE_DETECT_MODE);

        List<CaptureResult.Key<?>> availableCaptureResultKeys = List.of(
                CaptureResult.COLOR_CORRECTION_ABERRATION_MODE,
                CaptureResult.CONTROL_AE_ANTIBANDING_MODE,
                CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION,
                CaptureResult.CONTROL_AE_LOCK,
                CaptureResult.CONTROL_AE_MODE,
                CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureResult.CONTROL_AE_STATE,
                CaptureResult.CONTROL_AE_TARGET_FPS_RANGE,
                CaptureResult.CONTROL_AF_MODE,
                CaptureResult.CONTROL_AF_STATE,
                CaptureResult.CONTROL_AF_TRIGGER,
                CaptureResult.CONTROL_AWB_LOCK,
                CaptureResult.CONTROL_AWB_MODE,
                CaptureResult.CONTROL_AWB_STATE,
                CaptureResult.CONTROL_CAPTURE_INTENT,
                CaptureResult.CONTROL_EFFECT_MODE,
                CaptureResult.CONTROL_MODE,
                CaptureResult.CONTROL_SCENE_MODE,
                CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureResult.STATISTICS_FACE_DETECT_MODE,
                CaptureResult.FLASH_MODE,
                CaptureResult.FLASH_STATE,
                CaptureResult.JPEG_THUMBNAIL_SIZE,
                CaptureResult.JPEG_QUALITY,
                CaptureResult.JPEG_THUMBNAIL_QUALITY,
                CaptureResult.LENS_FOCAL_LENGTH,
                CaptureResult.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureResult.NOISE_REDUCTION_MODE,
                CaptureResult.REQUEST_PIPELINE_DEPTH,
                CaptureResult.SENSOR_TIMESTAMP,
                CaptureResult.STATISTICS_HOT_PIXEL_MAP_MODE,
                CaptureResult.STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureResult.STATISTICS_SCENE_FLICKER);

        int cameraWidth = 640;
        int cameraHeight = 480;
        int minFps = 4;
        int maxFps = 30;
        int streamFormat = YUV_420_888;
        long minFrameDuration = 1_000_000_000L / maxFps;
        long minStallDuration = 0L;

        Size supportedSize = new Size(cameraWidth, cameraHeight);
        Range<Integer>[] supportedFpsRange = new Range[]{new Range<>(minFps, maxFps)};

        StreamConfiguration streamConfig = new StreamConfiguration(streamFormat, cameraWidth,
                cameraHeight, false);
        StreamConfigurationDuration streamMinFrameConfig = new StreamConfigurationDuration(
                streamFormat, cameraWidth, cameraHeight, minFrameDuration);
        StreamConfigurationDuration streamStallConfig = new StreamConfigurationDuration(
                streamFormat, cameraWidth, cameraHeight, minStallDuration);

        set(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL,
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL);
        set(CameraCharacteristics.FLASH_INFO_AVAILABLE, false);
        set(CameraCharacteristics.LENS_FACING, LENS_FACING_FRONT);
        set(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS, new float[]{43.0f});
        set(CameraCharacteristics.SENSOR_ORIENTATION, SENSOR_ORIENTATION_0);
        set(CameraCharacteristics.SENSOR_READOUT_TIMESTAMP,
                CameraCharacteristics.SENSOR_READOUT_TIMESTAMP_NOT_SUPPORTED);
        set(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE,
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN);
        set(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE, new SizeF(36.0f, 24.0f));
        set(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES,
                new int[]{CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_OFF});
        set(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES,
                new int[]{CameraCharacteristics.NOISE_REDUCTION_MODE_OFF});
        set(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,
                new int[]{CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF});
        set(CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES,
                new long[]{CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT,
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW,
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE,
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD,
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL,
                        CameraCharacteristics.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL});
        set(CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES,
                new int[]{CameraCharacteristics.SENSOR_TEST_PATTERN_MODE_OFF});
        set(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM, 1.0f);
        set(CameraCharacteristics.CONTROL_AVAILABLE_MODES,
                new int[]{CameraCharacteristics.CONTROL_MODE_AUTO});
        set(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                new int[]{CameraCharacteristics.CONTROL_AF_MODE_OFF});
        set(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES,
                new int[]{CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED});
        set(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS,
                new int[]{CameraCharacteristics.CONTROL_EFFECT_MODE_OFF});
        set(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES,
                new int[]{CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF});
        set(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES,
                new int[]{CameraCharacteristics.CONTROL_AE_MODE_ON});
        set(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES,
                new int[]{CameraCharacteristics.CONTROL_AE_ANTIBANDING_MODE_AUTO});
        set(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES, supportedFpsRange);
        set(CameraCharacteristics.CONTROL_MAX_REGIONS, new int[]{0, 0, 0});
        set(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE, new Range<>(0, 0));
        set(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP, new Rational(0, 0));
        set(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE, false);
        set(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE, false);
        set(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES,
                new int[]{CameraCharacteristics.CONTROL_AWB_MODE_AUTO});
        set(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE, new Range<>(1.0f, 1.0f));
        set(CameraCharacteristics.SCALER_CROPPING_TYPE,
                CameraCharacteristics.SCALER_CROPPING_TYPE_CENTER_ONLY);
        set(CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES, new Size[]{supportedSize});
        set(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT, 0);
        set(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION, 1_000_000_000L);
        set(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_STREAMS, new int[]{0, 3, 1});
        set(CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT, 1);
        set(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH, (byte) 2);
        set(CameraCharacteristics.SYNC_MAX_LATENCY, CameraCharacteristics.SYNC_MAX_LATENCY_UNKNOWN);
        set(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, new int[]{
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE});
        set(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE,
                new Rect(0, 0, cameraWidth, cameraHeight));
        set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE, supportedSize);
        // stream configurations
        set(CameraCharacteristics.SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                new StreamConfiguration[]{streamConfig});
        set(CameraCharacteristics.SCALER_AVAILABLE_MIN_FRAME_DURATIONS,
                new StreamConfigurationDuration[]{streamMinFrameConfig});
        set(CameraCharacteristics.SCALER_AVAILABLE_STALL_DURATIONS,
                new StreamConfigurationDuration[]{streamStallConfig});
        setAvailableCharacteristicsKeys(availableCharacteristicsKeys);
        setAvailableCaptureRequestKeys(availableCaptureRequestKeys);
        setAvailableCaptureResultKeys(availableCaptureResultKeys);
    }
}
