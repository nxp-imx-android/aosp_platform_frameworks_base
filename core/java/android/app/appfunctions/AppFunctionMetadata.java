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

import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
import static android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
import static android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_ENABLED;
import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_CONTEXTUAL_APP_FUNCTIONS;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.GenericDocument;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Represents an app function's metadata, providing the essential information for its invocation.
 *
 * <p>This class provides a representation of the app function's metadata resulting from searching
 * for app functions on the device.
 *
 * <p>To make functions discoverable:
 * <ol>
 * <li>Define them in an XML file within the app's {@code assets/} directory.
 * <li>Reference the XML file in {@code AndroidManifest.xml} as a property of the corresponding
 * app function service.
 * </ol>
 *
 * <p><b>Example {@code AndroidManifest.xml} declaration:</b>
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
@FlaggedApi(FLAG_ENABLE_CONTEXTUAL_APP_FUNCTIONS)
public class AppFunctionMetadata implements AbstractAppFunctionMetadata {

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

    @NonNull
    private final GenericDocument mAppFunctionMetadataDocument;
    @NonNull
    private final AppFunctionName mAppFunctionName;
    @Nullable
    private final AppFunctionSchemaMetadata mAppFunctionSchemaMetadata;
    @NonNull
    private final AppFunctionPackageMetadata mAppFunctionPackageMetadata;
    private final boolean mIsEnabled;

    private AppFunctionMetadata(
            @NonNull AppFunctionName appFunctionName,
            @Nullable AppFunctionSchemaMetadata appFunctionSchemaMetadata,
            @NonNull AppFunctionPackageMetadata appFunctionPackageMetadata,
            @NonNull GenericDocument appFunctionMetadataDocument,
            boolean isEnabled
    ) {
        this.mAppFunctionName = appFunctionName;
        this.mAppFunctionSchemaMetadata = appFunctionSchemaMetadata;
        this.mAppFunctionPackageMetadata = appFunctionPackageMetadata;
        this.mAppFunctionMetadataDocument = appFunctionMetadataDocument;
        this.mIsEnabled = isEnabled;
    }

    /**
     * Creates a new instance of AppFunctionMetadata using the given function and package metadata.
     *
     * @hide
     */
    @VisibleForTesting
    public static AppFunctionMetadata create(
            @NonNull GenericDocument appFunctionStaticMetadataDocument,
            @NonNull GenericDocument appFunctionRuntimeMetadataDocument,
            @NonNull AppFunctionPackageMetadata appFunctionPackageMetadata) {
        requireNonNull(appFunctionStaticMetadataDocument);
        requireNonNull(appFunctionRuntimeMetadataDocument);
        requireNonNull(appFunctionPackageMetadata);
        String qualifiedFunctionId = requireNonNull(appFunctionStaticMetadataDocument.getId());
        AppFunctionName appFunctionName =
                new AppFunctionName(appFunctionPackageMetadata.getPackageName(),
                        qualifiedFunctionId.substring(qualifiedFunctionId.indexOf('/') + 1));
        AppFunctionSchemaMetadata schemaMetadata =
                getAppFunctionSchemaMetadataOrNull(appFunctionStaticMetadataDocument);
        boolean isEnabled = isEnabled(appFunctionStaticMetadataDocument,
                appFunctionRuntimeMetadataDocument);

        return new AppFunctionMetadata(
                appFunctionName,
                schemaMetadata,
                appFunctionPackageMetadata,
                appFunctionStaticMetadataDocument,
                isEnabled
        );
    }

    /**
     * Returns the qualified name of the app function.
     *
     * <p>The {@link AppFunctionName} is composed of the app's package name and the function's ID.
     * The ID is specified by the {@link PROPERTY_FUNCTION_ID} tag in the
     * the app function XML.
     */
    @NonNull
    public AppFunctionName getName() {
        return mAppFunctionName;
    }

    /**
     * Returns the identifying info for a pre-defined schema which this app function implements.
     *
     * <p>The schema metadata properties are specified by the {@link PROPERTY_SCHEMA_CATEGORY},
     * {@link PROPERTY_SCHEMA_NAME} and {@link PROPERTY_SCHEMA_VERSION} tags in the
     * app function XML.
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
     * Whether the app function is enabled.
     *
     * <p>The default enabled status is specified by the {@link PROPERTY_ENABLED_BY_DEFAULT} tag
     * in the app function XML. Apps can change this status at runtime using
     * {@link AppFunctionManager#setAppFunctionEnabled}.
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * Returns the app function's metadata as a {@link GenericDocument}.
     *
     * <p>Client-defined properties can be retrieved using the {@link GenericDocument}
     * property getters.
     *
     * <p>Properties that are not defined in this class (see {@code PROPERTY_*} constants) are
     * not guaranteed to be available or consistent across versions.
     */
    @Override
    @NonNull
    public GenericDocument getMetadataDocument() {
        return mAppFunctionMetadataDocument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppFunctionMetadata that)) return false;
        return mAppFunctionMetadataDocument.equals(that.mAppFunctionMetadataDocument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAppFunctionMetadataDocument);
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
                + mAppFunctionMetadataDocument
                + ")";
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

    private static boolean isEnabled(
            @NonNull GenericDocument appFunctionStaticMetadataDocument,
            @NonNull GenericDocument appFunctionRuntimeMetadataDocument) {
        long enabled = appFunctionRuntimeMetadataDocument.getPropertyLong(PROPERTY_ENABLED);
        if (enabled != APP_FUNCTION_STATE_DEFAULT) {
            return enabled == APP_FUNCTION_STATE_ENABLED;
        }
        return appFunctionStaticMetadataDocument.getPropertyBoolean(
                PROPERTY_ENABLED_BY_DEFAULT);
    }
}
