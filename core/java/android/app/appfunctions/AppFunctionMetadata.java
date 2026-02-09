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
package android.app.appfunctions;

import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.GenericDocument;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents an app function's metadata, providing the essential information for its invocation.
 *
 * <p>This class provides a representation of the app function's metadata resulting from searching
 * for app functions on the device.
 *
 * <p>To make functions discoverable:
 *
 * <ol>
 *   <li>Define them in an XML file within the app's {@code assets/} directory.
 *   <li>Reference the XML file in {@code AndroidManifest.xml} as a property of the corresponding
 *       app function service.
 * </ol>
 *
 * <p><b>Example {@code AndroidManifest.xml} declaration:</b>
 *
 * <pre>{@code
 *  <application>
 *      <service android:name=".MyAppFunctionService">
 *          <property
 *              android:name="android.app.appfunctions"
 *              android:value="app_functions.xml" />
 *          <intent-filter>
 *              <action android:name="android.app.appfunctions.AppFunctionService"/>
 *          </intent-filter>
 *      </service>
 * <application>
 * }</pre>
 *
 * <p>The XML schema consists of a root {@code <appfunctions>} element that contains one or more
 * {@code <appfunction>} elements. The XML tags used within the {@code <appfunction>} element
 * directly correspond to the property names defined by the {@code PROPERTY_*} constants in this
 * class.
 *
 * <p><b>Example {@code app_functions.xml} declaration:</b>
 *
 * <pre>{@code
 * <appfunctions>
 *     <appfunction>
 *         <id>com.example.notes/createNote</id>
 *         <enabledByDefault>true</enabledByDefault>
 *         ...
 *     </appfunction>
 * </appfunctions>
 * }</pre>
 */
@FlaggedApi(FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
public final class AppFunctionMetadata implements AbstractAppFunctionMetadata, Parcelable {

    /**
     * Property name for the app function's ID, which is used in an {@link
     * ExecuteAppFunctionRequest} to refer to the app function.
     *
     * <p>This name identifies the app function's ID property in the XML file that declares app
     * functions.
     */
    public static final String PROPERTY_FUNCTION_ID = "id";

    /**
     * Property name for the app function's schema category.
     *
     * <p>This name identifies the app function's schema category property in the XML file that
     * declares app functions.
     */
    public static final String PROPERTY_SCHEMA_CATEGORY = "schemaCategory";

    /**
     * Property name for the app function's schema name.
     *
     * <p>This name identifies the app function's schema name property in the XML file that declares
     * app functions.
     */
    public static final String PROPERTY_SCHEMA_NAME = "schemaName";

    /**
     * Property name for the app function's schema version.
     *
     * <p>This name identifies the app function's schema version property in the XML file that
     * declares app functions.
     */
    public static final String PROPERTY_SCHEMA_VERSION = "schemaVersion";

    /**
     * Property name for whether the function is enabled by default.
     *
     * <p>This name identifies the enabled-by-default property in the XML file that declares app
     * functions.
     */
    public static final String PROPERTY_ENABLED_BY_DEFAULT = "enabledByDefault";

    /**
     * Property name for the scope of this function.
     *
     * <p>This name identifies the app function's scope property in the XML file that declares app
     * functions.
     *
     * @see #getScope()
     */
    public static final String PROPERTY_SCOPE = "scope";

    /**
     * The value for {@link #PROPERTY_SCOPE} in the XML representing a global scope.
     *
     * @see #getScope()
     * @see #SCOPE_GLOBAL
     */
    public static final String PROPERTY_VALUE_SCOPE_GLOBAL = "global";

    /**
     * The value for {@link #PROPERTY_SCOPE} in the XML representing an activity scope.
     *
     * @see #getScope()
     * @see #SCOPE_ACTIVITY
     */
    public static final String PROPERTY_VALUE_SCOPE_ACTIVITY = "activity";

    /**
     * A value returned from {@link #getScope()} that indicates it is a globally scoped app
     * function.
     *
     * <p>There can be at most one function with the same {@link AppFunctionName} available with
     * this scope.
     *
     * <p>The function remains registered until it is explicitly unregistered or the process
     * terminates.
     */
    public static final int SCOPE_GLOBAL = 0;

    /**
     * A value returned from {@link #getScope()} that indicates it is a activity scoped app
     * function.
     *
     * <p>Multiple instances of the same function (with the same {@link AppFunctionName}) can
     * exist simultaneously, each associated with a different activity instance identified by an
     * {@link AppFunctionActivityId}.
     *
     * <p>To discover the specific activities where an activity-scoped function is currently
     * registered, see {@link AppFunctionManager#getAppFunctionStates} and {@link
     * AppFunctionManager#getAppFunctionActivityStates}.
     *
     * <p>To execute an activity-scoped function, see {@link
     * ExecuteAppFunctionRequest#setActivityId}.
     *
     * <p>The function remains registered until it is explicitly unregistered or the activity is
     * destroyed.
     *
     * @see AppFunctionActivityId
     */
    public static final int SCOPE_ACTIVITY = 1;

    @IntDef({SCOPE_GLOBAL, SCOPE_ACTIVITY})
    @Retention(RetentionPolicy.SOURCE)
    @interface Scope {}

    /**
     * Internal property which stores service name which should be used to execute App Function.
     * {@link #DYNAMIC_APP_FUNCTIONS_SERVICE_NAME} is set for dynamic app functions.
     *
     * @hide
     */
    public static final String PROPERTY_SERVICE_NAME = "serviceName";

    /**
     * Expected value for {@link #PROPERTY_SERVICE_NAME} in case AppFunction is dynamic.
     *
     * @hide
     */
    public static final String DYNAMIC_APP_FUNCTIONS_SERVICE_NAME = "@null";

    @NonNull
    public static final Creator<AppFunctionMetadata> CREATOR =
            new Creator<>() {
                @Override
                public AppFunctionMetadata createFromParcel(Parcel in) {
                    return new AppFunctionMetadata(in);
                }

                @Override
                public AppFunctionMetadata[] newArray(int size) {
                    return new AppFunctionMetadata[size];
                }
            };

    @NonNull private final GenericDocumentWrapper mAppFunctionMetadataDocumentWrapper;
    @NonNull private final AppFunctionName mAppFunctionName;
    @Nullable private final AppFunctionSchemaMetadata mAppFunctionSchemaMetadata;
    @NonNull private final AppFunctionPackageMetadata mAppFunctionPackageMetadata;

    private AppFunctionMetadata(
            @NonNull AppFunctionName appFunctionName,
            @Nullable AppFunctionSchemaMetadata appFunctionSchemaMetadata,
            @NonNull AppFunctionPackageMetadata appFunctionPackageMetadata,
            @NonNull GenericDocument appFunctionMetadataDocument) {
        mAppFunctionName = appFunctionName;
        mAppFunctionSchemaMetadata = appFunctionSchemaMetadata;
        mAppFunctionPackageMetadata = appFunctionPackageMetadata;
        mAppFunctionMetadataDocumentWrapper =
                new GenericDocumentWrapper(appFunctionMetadataDocument);
    }

    private AppFunctionMetadata(Parcel in) {
        mAppFunctionName = Objects.requireNonNull(AppFunctionName.CREATOR.createFromParcel(in));
        mAppFunctionSchemaMetadata = in.readTypedObject(AppFunctionSchemaMetadata.CREATOR);
        mAppFunctionPackageMetadata =
                Objects.requireNonNull(AppFunctionPackageMetadata.CREATOR.createFromParcel(in));
        mAppFunctionMetadataDocumentWrapper =
                Objects.requireNonNull(GenericDocumentWrapper.CREATOR.createFromParcel(in));
    }

    /**
     * Returns the qualified name of the app function.
     *
     * <p>The {@link AppFunctionName} is composed of the app's package name and the function's ID.
     * The ID is specified by the {@link PROPERTY_FUNCTION_ID} tag in the app function XML.
     */
    @NonNull
    public AppFunctionName getName() {
        return mAppFunctionName;
    }

    /**
     * Returns the identifying info for a pre-defined schema which this app function implements.
     *
     * <p>The schema metadata properties are specified by the {@link PROPERTY_SCHEMA_CATEGORY},
     * {@link PROPERTY_SCHEMA_NAME} and {@link PROPERTY_SCHEMA_VERSION} tags in the app function
     * XML.
     */
    @Nullable
    public AppFunctionSchemaMetadata getSchemaMetadata() {
        return mAppFunctionSchemaMetadata;
    }

    /** Returns the {@link AppFunctionPackageMetadata} of the enclosing package. */
    @NonNull
    public AppFunctionPackageMetadata getPackageMetadata() {
        return mAppFunctionPackageMetadata;
    }

    /**
     * Returns the scope of the app function.
     *
     * <p>The scope determines the function's lifecycle and uniqueness rules. Depending on the
     * scope, there could be at most one or multiple functions registered in the system with the
     * same {@link AppFunctionName}.
     */
    @Scope
    public int getScope() {
        String xmlValue = getMetadataDocument().getPropertyString(PROPERTY_SCOPE);
        return scopeXmlValueToScope(xmlValue);
    }

    @Scope
    static int scopeXmlValueToScope(@NonNull String xmlValue) {
        return switch (xmlValue) {
            case PROPERTY_VALUE_SCOPE_GLOBAL -> SCOPE_GLOBAL;
            case PROPERTY_VALUE_SCOPE_ACTIVITY -> SCOPE_ACTIVITY;
            default -> throw new IllegalStateException("Unexpected value: " + xmlValue);
        };
    }

    static String scopeToScopeXmlValue(@Scope int scope) {
        return switch (scope) {
            case SCOPE_GLOBAL -> PROPERTY_VALUE_SCOPE_GLOBAL;
            case SCOPE_ACTIVITY -> PROPERTY_VALUE_SCOPE_ACTIVITY;
            default -> throw new IllegalStateException("Unexpected value: " + scope);
        };
    }

    /**
     * Returns the app function's metadata as a {@link GenericDocument}.
     *
     * <p>Client-defined properties can be retrieved using the {@link GenericDocument} property
     * getters.
     *
     * <p>Properties that are not defined in this class (see {@code PROPERTY_*} constants) are not
     * guaranteed to be available or consistent across versions.
     */
    @Override
    @NonNull
    public GenericDocument getMetadataDocument() {
        return mAppFunctionMetadataDocumentWrapper.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppFunctionMetadata that)) return false;
        return getMetadataDocument().equals(that.getMetadataDocument())
                && getPackageMetadata().equals(that.getPackageMetadata());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMetadataDocument(), getPackageMetadata());
    }

    @Override
    public String toString() {
        return "AppFunctionMetadata("
                + "appFunctionName="
                + getName()
                + ", "
                + "schemaMetadata="
                + getSchemaMetadata()
                + ", "
                + "packageMetadata="
                + getPackageMetadata()
                + ", "
                + "metadataDocument="
                + mAppFunctionMetadataDocumentWrapper.getValue()
                + ")";
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        mAppFunctionName.writeToParcel(dest, flags);
        dest.writeTypedObject(mAppFunctionSchemaMetadata, flags);
        final boolean prev = dest.allowSquashing();
        try {
            mAppFunctionPackageMetadata.writeToParcel(dest, flags);
        } finally {
            dest.restoreAllowSquashing(prev);
        }
        mAppFunctionMetadataDocumentWrapper.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Nullable
    private static AppFunctionSchemaMetadata getAppFunctionSchemaMetadataOrNull(
            GenericDocument appFunctionMetadataDocument) {
        String category = appFunctionMetadataDocument.getPropertyString(PROPERTY_SCHEMA_CATEGORY);
        String name = appFunctionMetadataDocument.getPropertyString(PROPERTY_SCHEMA_NAME);
        long version = appFunctionMetadataDocument.getPropertyLong(PROPERTY_SCHEMA_VERSION);

        if (category == null || name == null) {
            return null;
        }

        return new AppFunctionSchemaMetadata(category, name, version);
    }

    /** @hide */
    public static final class Builder {
        private final GenericDocument mStaticDocument;
        private final AppFunctionPackageMetadata mPackageMetadata;

        /** The builder to create {@link AppFunctionMetadata}. */
        public Builder(
                @NonNull GenericDocument staticDocument,
                @NonNull AppFunctionPackageMetadata packageMetadata) {
            mStaticDocument = Objects.requireNonNull(staticDocument);
            mPackageMetadata = Objects.requireNonNull(packageMetadata);
        }

        /**
         * Builds {@link AppFunctionPackageMetadata}.
         *
         * @throws IllegalArgumentException If unable to build metadata from the provided arguments.
         */
        public AppFunctionMetadata build() throws IllegalArgumentException {
            Objects.requireNonNull(mStaticDocument);
            Objects.requireNonNull(mPackageMetadata);

            AppFunctionName appFunctionName =
                    AppFunctionName.fromQualifiedId(mStaticDocument.getId());
            AppFunctionSchemaMetadata schemaMetadata =
                    getAppFunctionSchemaMetadataOrNull(mStaticDocument);
            return new AppFunctionMetadata(
                    appFunctionName, schemaMetadata, mPackageMetadata, mStaticDocument);
        }
    }
}
