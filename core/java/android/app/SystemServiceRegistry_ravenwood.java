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
package android.app;

import android.annotation.NonNull;
import android.app.SystemServiceRegistry.CachedServiceFetcher;
import android.app.SystemServiceRegistry.ServiceFetcher;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PermissionEnforcer;
import android.os.ServiceManager.ServiceNotFoundException;
import android.platform.test.ravenwood.RavenwoodPermissionEnforcer;
import android.ravenwood.example.BlueManager;
import android.ravenwood.example.RedManager;

public class SystemServiceRegistry_ravenwood {
    private SystemServiceRegistry_ravenwood() {
    }

    /**
     * Wrapper method for registerServiceForRavenwood(), just so we can use the name
     * "registerService" in this class.
     */
    static <T> void registerService(@NonNull String serviceName,
            @NonNull Class<T> serviceClass, @NonNull ServiceFetcher<T> serviceFetcher) {
        SystemServiceRegistry.registerServiceForRavenwood(
                serviceName, serviceClass, serviceFetcher);
    }

    /**
     * RavenwoodEquivalent to {@link SystemServiceRegistry_ravenwood#registerServices}.
     *
     * TODO: Extract the common part between this and the other method to reuse the
     * same code on Ravenwood. (For now, we don't want to touch the production code
     * for Ravenwood.)
     */
    static void registerServices() {
        // This is the same as the real one.
        registerService(Context.CLIPBOARD_SERVICE, ClipboardManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public ClipboardManager createService(ContextImpl ctx)
                            throws ServiceNotFoundException {
                        return new ClipboardManager(ctx.getOuterContext(),
                                ctx.mMainThread.getHandler());
                    }});

        // For now, we use a custom version of PermissionEnforcer on Ravenwood,
        // which skips all permission checks.
        registerService(Context.PERMISSION_ENFORCER_SERVICE, PermissionEnforcer.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public PermissionEnforcer createService(ContextImpl ctx) {
                        return new RavenwoodPermissionEnforcer();
                    }});

        registerRavenwoodSpecificServices();
    }

    private static void registerRavenwoodSpecificServices() {
        // Additional services we provide for testing purposes
        registerService(BlueManager.SERVICE_NAME, BlueManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public BlueManager createService(ContextImpl ctx) {
                        return new BlueManager();
                    }
                });
        registerService(RedManager.SERVICE_NAME, RedManager.class,
                new CachedServiceFetcher<>() {
                    @Override
                    public RedManager createService(ContextImpl ctx) {
                        return new RedManager();
                    }
                });
    }
}
