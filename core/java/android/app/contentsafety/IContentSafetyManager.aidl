package android.app.contentsafety;

import android.os.Bundle;
import com.android.internal.infra.AndroidFuture;
import android.app.contentsafety.ICheckContentCallback;
import android.app.contentsafety.IIsFeatureEnabledCallback;
import android.app.contentsafety.SupportedTypesResult;

/**
* Interface for ContentSafetyManager for managing ContentSafetyService.
*
* @hide
*/
interface IContentSafetyManager {

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    void checkContent(in int featureType, in Bundle input, in  AndroidFuture cancellationSignalFuture, in ICheckContentCallback callback) = 1;

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    String getRemoteServicePackageName() = 2;

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    void isFeatureEnabled(in int featureType, in  AndroidFuture cancellationSignalFuture, in IIsFeatureEnabledCallback callback) = 3;

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    String getRemoteSandboxedServicePackageName() = 4;

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
    String getRemoteSettingsServicePackageName() = 5;

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.CHECK_CONTENT_SAFETY)")
     SupportedTypesResult getSupportedInputTypes(in int featureType) = 6;

}
