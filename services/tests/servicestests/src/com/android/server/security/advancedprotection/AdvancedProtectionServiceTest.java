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

package com.android.server.security.advancedprotection;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.test.FakePermissionEnforcer;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.security.advancedprotection.AdvancedProtectionManager.FeatureId;
import android.security.advancedprotection.IAdvancedProtectionCallback;

import androidx.annotation.NonNull;

import com.android.server.pm.UserManagerInternal;
import com.android.server.security.advancedprotection.features.AdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.AdvancedProtectionProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("VisibleForTests")
@RunWith(JUnit4.class)
public class AdvancedProtectionServiceTest {
    private static final int HOOK_ID = AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G;
    private static final String HOOK_NAME = "DISALLOW_CELLULAR_2G";
    private static final int PROVIDER_ID =
            AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;
    private static final String PROVIDER_NAME = "DISALLOW_INSTALL_UNKNOWN_SOURCES";

    private FakePermissionEnforcer mPermissionEnforcer;
    private UserManagerInternal mUserManager;
    private Context mContext;
    private TestLooper mLooper;
    private AdvancedProtectionConfigLoader.Injector mInjector;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setup() throws Settings.SettingNotFoundException {
        mContext = mock(Context.class);
        mUserManager = mock(UserManagerInternal.class);
        mInjector = new AdvancedProtectionConfigLoader.Injector();
        mLooper = new TestLooper();
        mPermissionEnforcer = new FakePermissionEnforcer();
        mPermissionEnforcer.grant(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        mPermissionEnforcer.grant(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        Mockito.when(mUserManager.getUserInfo(ArgumentMatchers.anyInt()))
                .thenReturn(new UserInfo(0, "user", UserInfo.FLAG_ADMIN));
    }

    @Test
    public void testToggleProtection() {
        AdvancedProtectionService service = createService(null, null);
        service.setAdvancedProtectionEnabled(true);
        assertTrue(service.isAdvancedProtectionEnabled());

        service.setAdvancedProtectionEnabled(false);
        assertFalse(service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testDisableProtection_byDefault() {
        AdvancedProtectionService service = createService(null, null);
        assertFalse(service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testSetProtection_nonAdminUser() {
        Mockito.when(mUserManager.getUserInfo(ArgumentMatchers.anyInt()))
                .thenReturn(new UserInfo(1, "user2", UserInfo.FLAG_FULL));
        AdvancedProtectionService service = createService(null, null);

        assertThrows(SecurityException.class, () -> service.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testEnableProtection_withHook() {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCaptor);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertTrue(callbackCaptor.get());
    }

    @Test
    public void testEnableProtection_withFeature_notAvailable() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ false, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertFalse(callbackCalledCaptor.get());
    }

    @Test
    public void testEnableProtection_withFeature_notCalledIfModeNotChanged() {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, callbackCalledCaptor);
        AdvancedProtectionService service = createService(hook, null);

        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchNext();

        assertTrue(callbackCalledCaptor.get());

        callbackCalledCaptor.set(false);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        assertFalse(callbackCalledCaptor.get());
    }

    @Test
    public void testRegisterCallback() throws RemoteException {
        AtomicBoolean callbackCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback = createCallback(callbackCaptor);
        AdvancedProtectionService service = createService(null, null);
        service.setAdvancedProtectionEnabled(true);
        mLooper.dispatchAll();

        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchNext();

        assertTrue(callbackCaptor.get());

        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchNext();

        assertFalse(callbackCaptor.get());
    }

    @Test
    public void testUnregisterCallback() throws RemoteException {
        AtomicBoolean callbackCalledCaptor = new AtomicBoolean(false);
        IAdvancedProtectionCallback callback = createCallback(callbackCalledCaptor);
        AdvancedProtectionService service = createService(null, null);

        service.setAdvancedProtectionEnabled(true);
        service.registerAdvancedProtectionCallback(callback);
        mLooper.dispatchAll();
        callbackCalledCaptor.set(false);

        service.unregisterAdvancedProtectionCallback(callback);
        service.setAdvancedProtectionEnabled(false);
        mLooper.dispatchNext();

        assertFalse(callbackCalledCaptor.get());
    }

    @DisableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures() {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();

        assertContainsInAnyOrder(features, HOOK_ID, PROVIDER_ID);
    }

    @DisableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureNotAvailable() {
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();

        assertContainsInAnyOrder(features, PROVIDER_ID);
        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featuresAvailableInHookAndConfig() throws IOException {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures(HOOK_NAME, PROVIDER_NAME);
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();

        assertContainsInAnyOrder(features, HOOK_ID, PROVIDER_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureAvailableInHookButNotAvailableInConfig() throws IOException {
        AdvancedProtectionHook hook = createHook(/* isAvailable */ true, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures(PROVIDER_NAME);
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();

        assertContainsInAnyOrder(features, PROVIDER_ID);
        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featureNotAvailableInHookButAvailableInConfig() throws IOException {
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures(HOOK_NAME, PROVIDER_NAME);
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();

        assertContainsInAnyOrder(features, PROVIDER_ID);
        assertDoesNotContain(features, HOOK_ID);
    }

    @EnableFlags(Flags.FLAG_AAPM_API_V2)
    @Test
    public void testGetFeatures_featuresNotAvailableInHookAndConfig() throws IOException {
        AdvancedProtectionHook hook =
                createHook(/* isAvailable */ false, /* callbackCaptor */ null);
        AdvancedProtectionProvider provider = createProvider();
        mockSystemConfigWithFeatures();
        AdvancedProtectionService service = createService(hook, provider);

        List<AdvancedProtectionFeature> features = service.getAdvancedProtectionFeatures();

        assertTrue(features.isEmpty());
    }

    @Test
    public void testSetProtection_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
        assertThrows(SecurityException.class, () -> service.setAdvancedProtectionEnabled(true));
    }

    @Test
    public void testGetProtection_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(SecurityException.class, () -> service.isAdvancedProtectionEnabled());
    }

    @Test
    public void testRegisterCallback_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.registerAdvancedProtectionCallback(
                                new IAdvancedProtectionCallback.Default()));
    }

    @Test
    public void testUnregisterCallback_withoutPermission() {
        AdvancedProtectionService service = createService(null, null);
        mPermissionEnforcer.revoke(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE);
        assertThrows(
                SecurityException.class,
                () ->
                        service.unregisterAdvancedProtectionCallback(
                                new IAdvancedProtectionCallback.Default()));
    }

    private void assertContainsInAnyOrder(
            List<AdvancedProtectionFeature> features, Integer... featureIds) {
        assertThat(
                features.stream().map(AdvancedProtectionFeature::getId).toList(),
                containsInAnyOrder(featureIds));
    }

    private void assertDoesNotContain(List<AdvancedProtectionFeature> features, int featureId) {
        assertThat(
                features.stream().map(AdvancedProtectionFeature::getId).toList(),
                not(containsInAnyOrder(featureId)));
    }

    private IAdvancedProtectionCallback createCallback(AtomicBoolean callbackCaptor) {
        return new IAdvancedProtectionCallback.Stub() {
            @Override
            public void onAdvancedProtectionChanged(boolean enabled) {
                callbackCaptor.set(enabled);
            }
        };
    }

    private AdvancedProtectionHook createHook(
            boolean isAvailable, @Nullable AtomicBoolean callbackCaptor) {
        return createHook(HOOK_ID, isAvailable, callbackCaptor);
    }

    private AdvancedProtectionHook createHook(
            @FeatureId int featureId, boolean isAvailable, @Nullable AtomicBoolean callbackCaptor) {
        return new AdvancedProtectionHook(mContext, true) {
            @Override
            public @FeatureId int getFeatureId() {
                return featureId;
            }

            @Override
            public boolean isAvailable() {
                return isAvailable;
            }

            @Override
            public void onAdvancedProtectionChanged(boolean enabled) {
                if (callbackCaptor != null) {
                    callbackCaptor.set(enabled);
                }
            }
        };
    }

    private AdvancedProtectionProvider createProvider() {
        return new AdvancedProtectionProvider() {
            @Override
            public List<Integer> getFeatureIds(Context context) {
                return List.of(PROVIDER_ID);
            }
        };
    }

    private void mockSystemConfigWithFeatures(String... featureNames) throws IOException {
        String configContent =
                """
                <advanced-protection-config>
                    <available-protections>\
                """;
        for (String featureName : featureNames) {
            configContent += String.format("<protection id=\"%s\" />", featureName);
        }
        configContent +=
                """
                    </available-protections>
                </advanced-protection-config>\
                """;
        mockSystemConfig(configContent);
    }

    private void mockSystemConfig(String configContent) throws IOException {
        mInjector = mock(AdvancedProtectionConfigLoader.Injector.class);
        Mockito.when(mInjector.readSystemConfig())
                .thenReturn(
                        new ByteArrayInputStream(configContent.getBytes(StandardCharsets.UTF_8)));
    }

    private AdvancedProtectionService createService(
            AdvancedProtectionHook hook, AdvancedProtectionProvider provider) {

        AdvancedProtectionStore store =
                new AdvancedProtectionStore(mContext) {
                    private boolean mAdvancedProtectionModeEnabled = false;
                    private boolean mUsbDataProtectionEnabled = false;

                    @Override
                    void saveAdvancedProtectionModeEnabled(boolean enabled) {
                        mAdvancedProtectionModeEnabled = enabled;
                    }

                    @Override
                    boolean retrieveAdvancedProtectionModeEnabled() {
                        return mAdvancedProtectionModeEnabled;
                    }

                    @Override
                    void saveUsbDataProtectionEnabled(boolean enabled) {
                        mUsbDataProtectionEnabled = enabled;
                    }

                    @Override
                    boolean retrieveUsbDataProtectionEnabled() {
                        return mUsbDataProtectionEnabled;
                    }

                    @Override
                    void saveEnabledChangeTime(long value) {
                        // Do nothing
                    }

                    @Override
                    long retrieveEnabledChangeTime() {
                        return 0;
                    }

                    @Override
                    void saveDialogShown(
                            int featureId,
                            int type,
                            boolean learnMoreClicked,
                            int hoursSinceEnabled) {
                        // Do nothing
                    }

                    @Override
                    int retrieveLastDialogFeatureId() {
                        return -1;
                    }

                    @Override
                    int retrieveLastDialogType() {
                        return -1;
                    }

                    @Override
                    boolean retrieveLastDialogLearnMoreClicked() {
                        return false;
                    }

                    @Override
                    int retrieveLastDialogHoursSinceEnabled() {
                        return -1;
                    }
                };

        return new AdvancedProtectionService(
                mContext,
                store,
                mUserManager,
                mLooper.getLooper(),
                mPermissionEnforcer,
                hook,
                provider,
                mInjector);
    }
}
