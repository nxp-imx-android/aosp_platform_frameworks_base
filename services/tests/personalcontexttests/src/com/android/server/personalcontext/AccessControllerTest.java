/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.personalcontext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.PermissionEnforcer;
import android.os.Process;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.personalcontext.Flags;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests covering the functionality of {@link AccessController}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AccessControllerTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testHintPublishingAllowList() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedHintPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_PUBLISH_HINTS_ALLOWLIST)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_PUBLISH_HINTS_ALLOWLIST)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testInsightPublishingAllowList() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedInsightPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_PUBLISH_INSIGHTS_ALLOWLIST)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_PUBLISH_INSIGHTS_ALLOWLIST)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testHintReceivingAllowList() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedHintReceivers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_RECEIVE_HINTS_ALLOWLIST)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_RECEIVE_HINTS_ALLOWLIST)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testInsightReceivingAllowList() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedInsightReceivers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_RECEIVE_INSIGHTS_ALLOWLIST)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_RECEIVE_INSIGHTS_ALLOWLIST)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testInsightFilteringAllowList() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedInsightFilterers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_FILTER_INSIGHTS_ALLOWLIST)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_FILTER_INSIGHTS_ALLOWLIST)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testHintPublishingPermission() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setPermittedHintPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_PUBLISH_HINTS_PERMISSION)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_PUBLISH_HINTS_PERMISSION)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testInsightPublishingPermission() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setPermittedInsightPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testHintReceivingPermission() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setPermittedHintReceivers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_RECEIVE_HINTS_PERMISSION)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_RECEIVE_HINTS_PERMISSION)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testInsightReceivingPermission() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setPermittedInsightReceivers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(
                allowedPackage, AccessController.ACCESS_RECEIVE_INSIGHTS_PERMISSION)).isTrue();

        assertThat(controller.isPackageAllowed(
                deniedPackage, AccessController.ACCESS_RECEIVE_INSIGHTS_PERMISSION)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testsRegisterVisualizerPackage() {
        final String allowedPackage = "com.foo.baz";
        final String deniedPackage = "com.foo.bar";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedVisualizerRegistrars(Set.of(allowedPackage))
                .build();

        assertThat(
                controller.isPackageAllowed(
                        allowedPackage, AccessController.ACCESS_REGISTER_VISUALIZER)).isTrue();

        assertThat(
                controller.isPackageAllowed(
                        deniedPackage, AccessController.ACCESS_REGISTER_VISUALIZER)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testMultipleAllowedPackage() {
        final String allowedPackage = "com.foo.baz";

        final AccessController controller = new AccessControllerBuilder()
                .setAllowedHintReceivers(Set.of(allowedPackage))
                .setAllowedInsightPublishers(Set.of(allowedPackage))
                .build();

        assertThat(controller.isPackageAllowed(allowedPackage,
                AccessController.ACCESS_PUBLISH_INSIGHTS_ALLOWLIST
                        | AccessController.ACCESS_RECEIVE_HINTS_ALLOWLIST))
                .isTrue();

        assertThat(controller.isPackageAllowed(allowedPackage,
                AccessController.ACCESS_PUBLISH_INSIGHTS_ALLOWLIST
                        | AccessController.ACCESS_RECEIVE_HINTS_ALLOWLIST
                        | AccessController.ACCESS_RECEIVE_INSIGHTS_ALLOWLIST))
                .isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    @Test
    public void testServiceBindContextPermissionEnforced() {
        final ServiceInfo allowedService = serviceInfo("com.foo.baz", "good_service", true);
        final ServiceInfo deniedService = serviceInfo("com.foo.bar", "bad_service", false);

        final AccessController controller = new AccessControllerBuilder().build();

        assertThat(controller.isServiceAllowed(allowedService,
                AccessController.ACCESS_BIND_CONTEXT_PERMISSION)).isTrue();

        assertThat(controller.isServiceAllowed(deniedService,
                AccessController.ACCESS_BIND_CONTEXT_PERMISSION)).isFalse();
    }

    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    @Test
    public void testIsPackageAllowed_SystemPackage() {
        final AccessController controller = new AccessControllerBuilder().build();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_PUBLISH_HINTS_ALLOWLIST)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_PUBLISH_INSIGHTS_ALLOWLIST)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_RECEIVE_HINTS_ALLOWLIST)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_RECEIVE_INSIGHTS_ALLOWLIST)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_FILTER_INSIGHTS_ALLOWLIST)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_REGISTER_VISUALIZER)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_PUBLISH_HINTS_PERMISSION)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_RECEIVE_HINTS_PERMISSION)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_RECEIVE_INSIGHTS_PERMISSION)).isTrue();
        assertThat(controller.isPackageAllowed(
            PersonalContextManagerService.SYSTEM_PACKAGE,
            AccessController.ACCESS_BIND_CONTEXT_PERMISSION)).isTrue();
    }

    private static ServiceInfo serviceInfo(
            String packageName,
            String name,
            boolean permitBindContext
    ) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.enabled = true;

        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.applicationInfo = applicationInfo;
        serviceInfo.packageName = packageName;
        serviceInfo.name = name;
        if (permitBindContext) {
            serviceInfo.permission = Manifest.permission.BIND_CONTEXT_COMPONENT_SERVICE;
        }
        serviceInfo.enabled = true;

        return serviceInfo;
    }

    @Test
    public void testEnforcePermissions() {
        checkPermission(AccessController.ACCESS_PUBLISH_HINTS_PERMISSION,
                Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        checkPermission(AccessController.ACCESS_RECEIVE_HINTS_PERMISSION,
                Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS);
        checkPermission(AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION,
                Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);
        checkPermission(AccessController.ACCESS_RECEIVE_INSIGHTS_PERMISSION,
                Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS);
        checkPermission(AccessController.ACCESS_HOST_INSIGHT_SURFACE_PERMISSION,
                Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);
    }

    @Test
    public void testBypassAllowlistCheckForSystemUid() {
        final AccessController controller = new AccessControllerBuilder()
                .build();
        assertThat(controller.isAnyPackageForUidAllowed(12345,
                AccessController.ACCESS_FILTER_INSIGHTS_ALLOWLIST)).isFalse();
        assertThat(controller.isAnyPackageForUidAllowed(
                    Process.SYSTEM_UID,
                    AccessController.ACCESS_FILTER_INSIGHTS_ALLOWLIST)).isTrue();
    }

    @Test
    public void testBypassPermissionsCheckForSystemUid() {
        final AccessController controller = new AccessControllerBuilder()
                .revokePermission(Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS)
                .build();

        assertThrows(SecurityException.class, () -> controller.enforcePermissions(0, 12345,
                AccessController.ACCESS_RECEIVE_HINTS_PERMISSION));
        try {
            controller.enforcePermissions(0, Process.SYSTEM_UID,
                    AccessController.ACCESS_RECEIVE_HINTS_PERMISSION);
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
    }

    private void checkPermission(@AccessController.Access int access, String permission) {
        {
            final AccessController controller = new AccessControllerBuilder()
                    .revokePermission(permission)
                    .build();
            assertThrows(SecurityException.class, () -> {
                controller.enforcePermissions(0, 0, access);
            });
        }

        {
            final AccessController controller = new AccessControllerBuilder()
                    .build();
            try {
                controller.enforcePermissions(0, 0, access);
            } catch (Throwable e) {
                fail("should not have thrown");
            }
        }
    }

    private static class AccessControllerBuilder {
        private final Resources mResources = mock(Resources.class);
        private final PermissionManager mPermissionManager = mock(PermissionManager.class);

        private final PermissionEnforcer mPermissionEnforcer = mock(PermissionEnforcer.class);

        private final HashSet<String> mRevokedPermissions = new HashSet<>();

        AccessControllerBuilder() {
            when(mPermissionManager.checkPackageNamePermission(any(), any(), anyInt(), anyInt()))
                    .thenReturn(PackageManager.PERMISSION_DENIED);

            setAllowedHintPublishers(Collections.emptySet());
            setAllowedHintReceivers(Collections.emptySet());
            setAllowedInsightPublishers(Collections.emptySet());
            setAllowedInsightReceivers(Collections.emptySet());
            setAllowedVisualizerRegistrars(Collections.emptySet());
        }

        private void setStringsToResource(Set<String> strings, int resourceId) {
            final String[] stringArray = new String[strings.size()];
            strings.toArray(stringArray);
            when(mResources.getStringArray(eq(resourceId))).thenReturn(stringArray);
        }

        private void allowPermission(Set<String> packageNames, String permission) {
            for (String packageName : packageNames) {
                doReturn(PackageManager.PERMISSION_GRANTED)
                        .when(mPermissionManager).checkPackageNamePermission(
                                eq(permission), eq(packageName), anyInt(), anyInt());
            }
        }

        public AccessControllerBuilder revokePermission(String permission) {
            mRevokedPermissions.add(permission);
            return this;
        }

        public AccessControllerBuilder setAllowedHintPublishers(Set<String> publishers) {
            setStringsToResource(publishers,
                    R.array.config_allowlistPersonalContextHintPublishing);

            return this;
        }

        public AccessControllerBuilder setAllowedHintReceivers(Set<String> receivers) {
            setStringsToResource(receivers,
                    R.array.config_allowlistPersonalContextHintReceiving);

            return this;
        }

        public AccessControllerBuilder setAllowedInsightPublishers(Set<String> publishers) {
            setStringsToResource(publishers,
                    R.array.config_allowlistPersonalContextInsightPublishing);

            return this;
        }

        public AccessControllerBuilder setAllowedInsightReceivers(Set<String> receivers) {
            setStringsToResource(receivers,
                    R.array.config_allowlistPersonalContextInsightReceiving);

            return this;
        }

        public AccessControllerBuilder setAllowedVisualizerRegistrars(Set<String> receivers) {
            setStringsToResource(receivers,
                    R.array.config_allowlistPersonalContextVisualizers);

            return this;
        }

        public AccessControllerBuilder setAllowedInsightFilterers(Set<String> receivers) {
            setStringsToResource(receivers,
                    R.array.config_allowlistPersonalContextInsightFiltering);

            return this;
        }

        public AccessControllerBuilder setPermittedHintPublishers(Set<String> packageNames) {
            allowPermission(packageNames, Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
            return this;
        }

        public AccessControllerBuilder setPermittedHintReceivers(Set<String> packageNames) {
            allowPermission(packageNames, Manifest.permission.PERSONAL_CONTEXT_RECEIVE_HINTS);
            return this;
        }

        public AccessControllerBuilder setPermittedInsightPublishers(Set<String> packageNames) {
            allowPermission(packageNames, Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);
            return this;
        }

        public AccessControllerBuilder setPermittedInsightReceivers(Set<String> packageNames) {
            allowPermission(packageNames, Manifest.permission.PERSONAL_CONTEXT_RECEIVE_INSIGHTS);
            return this;
        }

        public AccessController build() {
            doAnswer(invocation -> {
                for (String permission : (String[]) invocation.getArgument(0)) {
                    if (mRevokedPermissions.contains(permission)) {
                        throw new SecurityException();
                    }
                }
                return null;
            }).when(mPermissionEnforcer).enforcePermissionAllOf(any(), anyInt(), anyInt());
            return new AccessController(
                    new AccessController.Injector() {
                        @Override
                        public Resources getResources() {
                            return mResources;
                        }

                        @Override
                        public PackageManager getPackageManager() {
                            return mock(PackageManager.class);
                        }

                        @Override
                        public PermissionEnforcer getPermissionEnforcer() {
                            return mPermissionEnforcer;
                        }

                        @Override
                        public PermissionManager getPermissionManager() {
                            return mPermissionManager;
                        }

                        @Override
                        public RoleManager getRoleManager() {
                            return mock(RoleManager.class);
                        }

                        @Override
                        public AccessController.EventListener getEventListener() {
                            return mock(AccessController.EventListener.class);
                        }
                    },
                    mock(UserHandle.class));
        }
    }
}
