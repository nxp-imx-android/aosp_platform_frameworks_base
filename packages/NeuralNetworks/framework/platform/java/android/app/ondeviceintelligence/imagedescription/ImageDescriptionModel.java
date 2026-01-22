/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app.ondeviceintelligence.imagedescription;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.OnDeviceModel;
import android.app.ondeviceintelligence.FeatureDetails;
import android.app.ondeviceintelligence.flags.Flags;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Represents a specific on-device image description model.
 *
 * <p>This class provides methods to query model information, check availability, download the
 * model, and perform inference to generate descriptions for images.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
public final class ImageDescriptionModel implements OnDeviceModel, Parcelable {
    private OnDeviceIntelligenceManager mManager;
    private final Feature mFeature;
    private final String mModelSignature;

    /**
     * Constructs a new {@link ImageDescriptionModel}.
     *
     * @param feature The internal feature.
     * @param modelSignature The signature of the model.
     */
    public ImageDescriptionModel(@NonNull Feature feature, @NonNull String modelSignature) {
        mFeature = Objects.requireNonNull(feature);
        mModelSignature = Objects.requireNonNull(modelSignature);
    }

    /**
     * Sets the manager for this model.
     *
     * @hide
     */
    public void setOnDeviceIntelligenceManager(@NonNull OnDeviceIntelligenceManager manager) {
        mManager = manager;
    }

    /** Returns the unique signature of the model. */
    @Override
    @NonNull
    public String getModelSignature() {
        return mModelSignature;
    }

    /** Returns the name of the model. */
    @Override
    @NonNull
    public String getName() {
        String name = mFeature.getName();
        return name != null ? name : mModelSignature;
    }

    /**
     * Retrieves the current status of the model.
     *
     * @param executor The executor on which to invoke the callback.
     * @param callback The callback to receive the model status (e.g., DOWNLOADABLE, AVAILABLE).
     */
    @Override
    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void getStatus(
            @NonNull Executor executor,
            @NonNull OutcomeReceiver<Integer, OnDeviceIntelligenceException> callback) {
        Objects.requireNonNull(
                mManager, "OnDeviceIntelligenceManager instance not attached to this Model.");
        mManager.getFeatureDetails(
                mFeature,
                executor,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(FeatureDetails result) {
                        callback.onResult(result.getFeatureStatus());
                    }

                    @Override
                    public void onError(OnDeviceIntelligenceException error) {
                        callback.onError(error);
                    }
                });
    }

    /**
     * Requests the download of the model files.
     *
     * @param signal A {@link CancellationSignal} to cancel the download operation.
     * @param executor The executor on which to invoke the callback.
     * @param callback The callback to receive download progress and status updates.
     */
    @Override
    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void download(
            @Nullable CancellationSignal signal,
            @NonNull Executor executor,
            @NonNull DownloadCallback callback) {
        Objects.requireNonNull(
                mManager, "OnDeviceIntelligenceManager instance not attached to this Model.");
        mManager.requestFeatureDownload(mFeature, signal, executor, callback);
    }

    /**
     * Generates a description for the provided image.
     *
     * @param request The request containing the input image and optional parameters.
     * @param signal A {@link CancellationSignal} to cancel the operation.
     * @param executor The executor on which to invoke the callback.
     * @param callback The callback to receive the generated {@link ImageDescriptionResponse} or an
     *     error.
     */
    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void generateImageDescription(
            @NonNull ImageDescriptionRequest request,
            @Nullable CancellationSignal signal,
            @NonNull Executor executor,
            @NonNull
                    OutcomeReceiver<ImageDescriptionResponse, OnDeviceIntelligenceException>
                            callback) {
        Objects.requireNonNull(
                mManager, "OnDeviceIntelligenceManager instance not attached to this Model.");
        mManager.generateImageDescription(mFeature, request, signal, executor, callback);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mFeature, flags);
        dest.writeString8(mModelSignature);
    }

    public static final @NonNull Creator<ImageDescriptionModel> CREATOR =
            new Creator<ImageDescriptionModel>() {
                @Override
                public ImageDescriptionModel createFromParcel(Parcel in) {
                    return new ImageDescriptionModel(in);
                }

                @Override
                public ImageDescriptionModel[] newArray(int size) {
                    return new ImageDescriptionModel[size];
                }
            };

    private ImageDescriptionModel(Parcel in) {
        mFeature = in.readTypedObject(Feature.CREATOR);
        mModelSignature = in.readString8();
    }
}
