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

package android.app.appfunctions;

import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppInteractionAttribution;
import android.app.appfunctions.flags.Flags;
import android.app.appsearch.GenericDocument;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * A request to execute an app function.
 *
 * <p>The {@link ExecuteAppFunctionRequest#getParameters()} contains the parameters for the function
 * to be executed in a GenericDocument. Structured classes defined in the AppFunction SDK can be
 * converted into GenericDocuments.
 *
 * <p>The {@link ExecuteAppFunctionRequest#getExtras()} provides any extra metadata for the request.
 * Structured APIs can be exposed in the SDK by packing and unpacking this Bundle.
 *
 * <p>If {@link AppFunctionMetadata#getScope()} is {@link AppFunctionMetadata#SCOPE_ACTIVITY}, the
 * target activity must be specified using {@link ExecuteAppFunctionRequest.Builder#setActivityId}.
 * If omitted, the request fails with {@link AppFunctionException#ERROR_FUNCTION_NOT_FOUND}.
 */
@FlaggedApi(FLAG_ENABLE_APP_FUNCTION_MANAGER)
public final class ExecuteAppFunctionRequest implements Parcelable {
    @NonNull
    public static final Creator<ExecuteAppFunctionRequest> CREATOR =
            new Creator<>() {
                @Override
                public ExecuteAppFunctionRequest createFromParcel(Parcel parcel) {
                    String targetPackageName = Objects.requireNonNull(parcel.readString8());
                    String functionIdentifier = Objects.requireNonNull(parcel.readString8());
                    GenericDocumentWrapper parameters =
                            Objects.requireNonNull(
                                    GenericDocumentWrapper.CREATOR.createFromParcel(parcel));
                    Bundle extras =
                            Objects.requireNonNull(
                                    parcel.readBundle(Bundle.class.getClassLoader()));
                    AppInteractionAttribution attribution = null;
                    if (Flags.enableAppInteractionApi()) {
                        attribution = parcel.readTypedObject(AppInteractionAttribution.CREATOR);
                    }
                    AppFunctionActivityId activityId = null;
                    if (Flags.enableDynamicAppFunctions()) {
                        activityId = parcel.readTypedObject(AppFunctionActivityId.CREATOR);
                    }
                    return new ExecuteAppFunctionRequest(
                            targetPackageName,
                            functionIdentifier,
                            extras,
                            parameters,
                            attribution,
                            activityId);
                }

                @Override
                public ExecuteAppFunctionRequest[] newArray(int size) {
                    return new ExecuteAppFunctionRequest[size];
                }
            };

    /** Returns the package name of the app that hosts/owns the function. */
    @NonNull private final String mTargetPackageName;

    /**
     * The unique string identifier of the app function to be executed. This identifier is used to
     * execute a specific app function.
     */
    @NonNull private final String mFunctionIdentifier;

    /** Returns additional metadata relevant to this function execution request. */
    @NonNull private final Bundle mExtras;

    /**
     * Returns the parameters required to invoke this function. Within this [GenericDocument], the
     * property names are the names of the function parameters and the property values are the
     * values of those parameters.
     *
     * <p>The document may have missing parameters. Developers are advised to implement defensive
     * handling measures.
     */
    @NonNull private final GenericDocumentWrapper mParameters;

    @Nullable private final AppInteractionAttribution mAttribution;

    @Nullable private final AppFunctionActivityId mActivityId;

    private ExecuteAppFunctionRequest(
            @NonNull String targetPackageName,
            @NonNull String functionIdentifier,
            @NonNull Bundle extras,
            @NonNull GenericDocumentWrapper parameters,
            @Nullable AppInteractionAttribution attribution,
            @Nullable AppFunctionActivityId activityId) {
        mTargetPackageName = Objects.requireNonNull(targetPackageName);
        mFunctionIdentifier = Objects.requireNonNull(functionIdentifier);
        mExtras = Objects.requireNonNull(extras);
        mParameters = Objects.requireNonNull(parameters);
        mAttribution = attribution;
        mActivityId = activityId;
    }

    /** Returns the package name of the app that hosts the function. */
    @NonNull
    public String getTargetPackageName() {
        return mTargetPackageName;
    }

    /**
     * Returns the unique string identifier of the app function to be executed.
     *
     * <p>When there is a package change or the device starts up, the metadata of available
     * functions is indexed by AppSearch. AppSearch stores the indexed information as {@code
     * AppFunctionStaticMetadata} document.
     *
     * <p>The ID can be obtained by querying the {@code AppFunctionStaticMetadata} documents from
     * AppSearch.
     *
     * <p>If the {@code functionId} provided is invalid, the caller will get an invalid argument
     * response.
     */
    @NonNull
    public String getFunctionIdentifier() {
        return mFunctionIdentifier;
    }

    /**
     * Returns the function parameters. The key is the parameter name, and the value is the
     * parameter value.
     *
     * <p>The {@link GenericDocument} may have missing parameters. Developers are advised to
     * implement defensive handling measures.
     *
     * @see AppFunctionManager on how to determine the expected parameters.
     */
    @NonNull
    public GenericDocument getParameters() {
        return mParameters.getValue();
    }

    /** Returns the additional metadata for this function execution request. */
    @NonNull
    public Bundle getExtras() {
        return mExtras;
    }

    /**
     * Returns the {@link AppInteractionAttribution} represents attribution information for the
     * {@link ExecuteAppFunctionRequest}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_APP_INTERACTION_API)
    @Nullable
    public AppInteractionAttribution getAttribution() {
        return mAttribution;
    }

    /**
     * Returns a copy of this {@link ExecuteAppFunctionRequest} without the {@link
     * AppInteractionAttribution}.
     *
     * @hide
     */
    @NonNull
    public ExecuteAppFunctionRequest copyWithoutAttribution() {
        return new ExecuteAppFunctionRequest(
                mTargetPackageName,
                mFunctionIdentifier,
                mExtras,
                mParameters,
                /* attribution= */ null,
                mActivityId);
    }

    /**
     * Returns the {@link AppFunctionActivityId} for this request.
     *
     * <p>This identifier is used to disambiguate between instances of the same app function running
     * in different activities when the function's {@link AppFunctionMetadata#getScope} is {@link
     * AppFunctionMetadata#SCOPE_ACTIVITY}.
     *
     * <p>If this returns {@code null}, the request targets an app function that is not {@link
     * AppFunctionMetadata#SCOPE_ACTIVITY}.
     *
     * @return The activity identifier, or {@code null} if the request targets an app function that
     *     is not {@link AppFunctionMetadata#SCOPE_ACTIVITY}.
     */
    @FlaggedApi(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
    @Nullable
    public AppFunctionActivityId getActivityId() {
        return mActivityId;
    }

    /**
     * Returns the size of the request in bytes.
     *
     * @hide
     */
    public int getRequestDataSize() {
        return mTargetPackageName.getBytes().length
                + mFunctionIdentifier.getBytes().length
                + mParameters.getDataSize()
                + mExtras.getSize();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString8(mTargetPackageName);
        dest.writeString8(mFunctionIdentifier);
        mParameters.writeToParcel(dest, flags);
        dest.writeBundle(mExtras);
        if (Flags.enableAppInteractionApi()) {
            dest.writeTypedObject(mAttribution, flags);
        }
        if (Flags.enableDynamicAppFunctions()) {
            dest.writeTypedObject(mActivityId, flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Builder for {@link ExecuteAppFunctionRequest}. */
    public static final class Builder {
        @NonNull private final String mTargetPackageName;
        @NonNull private final String mFunctionIdentifier;
        @NonNull private Bundle mExtras = Bundle.EMPTY;

        @NonNull
        private GenericDocument mParameters = new GenericDocument.Builder<>("", "", "").build();

        @Nullable private AppInteractionAttribution mAttribution = null;

        @Nullable private AppFunctionActivityId mActivityId = null;

        /**
         * Creates a new instance of this builder class.
         *
         * @param targetPackageName The package name of the target app providing the app function to
         *     invoke.
         * @param functionIdentifier The identifier used by the {@link AppFunctionService} from the
         *     target app to uniquely identify the function to be invoked.
         */
        public Builder(@NonNull String targetPackageName, @NonNull String functionIdentifier) {
            mTargetPackageName = Objects.requireNonNull(targetPackageName);
            mFunctionIdentifier = Objects.requireNonNull(functionIdentifier);
        }

        /**
         * Creates a new instance of this builder class.
         *
         * @param appFunctionName The {@link AppFunctionName} of the target app function.
         */
        @FlaggedApi(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
        public Builder(@NonNull AppFunctionName appFunctionName) {
            Objects.requireNonNull(appFunctionName);
            mTargetPackageName = appFunctionName.getPackageName();
            mFunctionIdentifier = appFunctionName.getFunctionId();
        }

        /** Sets the additional metadata for this function execution request. */
        @NonNull
        public Builder setExtras(@NonNull Bundle extras) {
            mExtras = Objects.requireNonNull(extras);
            return this;
        }

        /**
         * Sets the function parameters.
         *
         * @see ExecuteAppFunctionRequest#getParameters()
         */
        @NonNull
        public Builder setParameters(@NonNull GenericDocument parameters) {
            Objects.requireNonNull(parameters);
            mParameters = parameters;
            return this;
        }

        /**
         * Sets the {@link AppInteractionAttribution}.
         *
         * <p>Provides the attribution information for an {@link ExecuteAppFunctionRequest}. This
         * information can be used by the privacy setting to provide transparency to the user about
         * why an app function was invoked.
         *
         * <p>This is currently optional, but may become required for apps targeting a future
         * release.
         *
         * @see AppInteractionAttribution
         */
        @FlaggedApi(Flags.FLAG_ENABLE_APP_INTERACTION_API)
        @NonNull
        public Builder setAttribution(@NonNull AppInteractionAttribution attribution) {
            mAttribution = Objects.requireNonNull(attribution);
            return this;
        }

        /**
         * Sets the {@link AppFunctionActivityId}.
         *
         * <p>If the target app function's {@link AppFunctionMetadata#getScope()} is {@link
         * AppFunctionMetadata#SCOPE_ACTIVITY}, this field must be set to disambiguate between
         * instances of the same app function running in different activities.
         *
         * <p>The field must not be set if the target app function is not {@link
         * AppFunctionMetadata#SCOPE_ACTIVITY}.
         *
         * <p>The list of valid activity identifiers for a given function name can be obtained by
         * calling {@link AppFunctionManager#getAppFunctionStates}. The returned {@link
         * AppFunctionState} objects will contain the associated activity IDs via {@link
         * AppFunctionState#getActivityIds()}.
         *
         * @param activityId The activity identifier to associate with this request.
         * @return This builder.
         * @see AppFunctionState#getActivityIds
         * @see AppFunctionManager#getAppFunctionStates
         * @see AppFunctionMetadata#getScope
         */
        @FlaggedApi(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
        @NonNull
        public Builder setActivityId(@Nullable AppFunctionActivityId activityId) {
            mActivityId = activityId;
            return this;
        }

        /** Builds the {@link ExecuteAppFunctionRequest}. */
        @NonNull
        public ExecuteAppFunctionRequest build() {
            return new ExecuteAppFunctionRequest(
                    mTargetPackageName,
                    mFunctionIdentifier,
                    mExtras,
                    new GenericDocumentWrapper(mParameters),
                    mAttribution,
                    mActivityId);
        }
    }
}
