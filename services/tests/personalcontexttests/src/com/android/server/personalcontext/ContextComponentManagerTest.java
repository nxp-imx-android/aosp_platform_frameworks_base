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

package com.android.server.personalcontext;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.component.Refiner;
import com.android.server.personalcontext.component.Renderer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextComponentManagerTest {

    @Test
    public void testRegistrationEmptyAtStart() {
        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                mock(UserHandle.class));

        assertThat(manager.getRefiners()).isEmpty();
        assertThat(manager.getRenderers()).isEmpty();
    }

    @Test
    public void testRegisterRefiner() {
        final Refiner refiner = mock(Refiner.class);
        when(refiner.getComponentId()).thenReturn(UUID.randomUUID());

        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                mock(UserHandle.class));
        manager.register(refiner);

        assertThat(manager.getRefiners()).containsExactly(refiner);
    }

    @Test
    public void testRegisterRenderer() {
        final Renderer renderer = mock(Renderer.class);
        when(renderer.getComponentId()).thenReturn(UUID.randomUUID());

        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                mock(UserHandle.class));
        manager.register(renderer);

        assertThat(manager.getRenderers()).containsExactly(renderer);
    }

    @Test
    public void testRendererFilter() {
        final Renderer nonMatchingRenderer = mock(Renderer.class);
        when(nonMatchingRenderer
                .hasProperties(anyInt()))
                .thenReturn(false);
        final UserHandle userHandle = mock(UserHandle.class);

        final Renderer matchingRenderer = mock(Renderer.class);
        when(matchingRenderer
                .hasProperties(eq(Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS)))
                .thenReturn(true);

        final ContextComponentManager manager = new ContextComponentManager(mock(Context.class),
                userHandle);
        manager.register(nonMatchingRenderer);
        manager.register(matchingRenderer);

        assertThat(manager.getRenderersWithProperties(
                Renderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS))
                .containsExactly(matchingRenderer);
    }

    @Test
    public void testRegisterAllComponentsIntent() {
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);

        when(context.getPackageManager()).thenReturn(pm);

        new ContextComponentManager(context, userHandle)
                .registerComponentsForAllPackages();

        final ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(pm, atLeastOnce()).queryIntentServices(intentCaptor.capture(), anyInt());

        assertThat(intentCaptor.getValue().getPackage()).isNull();
    }

    @Test
    public void testRegisterPackageComponentsIntent() {
        final String packageName = "com.whatever";
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);

        when(context.getPackageManager()).thenReturn(pm);

        new ContextComponentManager(context, userHandle)
                .registerComponentsForPackage(packageName);

        final ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(pm, atLeastOnce()).queryIntentServices(intentCaptor.capture(), anyInt());

        assertThat(intentCaptor.getValue().getPackage()).isEqualTo(packageName);
    }

    @Test
    public void testRegisterAndUnregisterPackageComponentsIntent() {
        final String packageName = "com.whatever";
        final Context context = mock(Context.class);
        final PackageManager pm = mock(PackageManager.class);
        final UserHandle userHandle = mock(UserHandle.class);
        final ResolveInfo resolve = new ResolveInfo();
        resolve.serviceInfo = new ServiceInfo();
        resolve.serviceInfo.packageName = packageName;
        resolve.serviceInfo.name = "WhateverService";

        when(context.getPackageManager()).thenReturn(pm);
        when(pm.queryIntentServices(any(), anyInt())).thenReturn(List.of(resolve));

        final ContextComponentManager manager = new ContextComponentManager(context, userHandle);
        manager.registerComponentsForAllPackages();

        // The above code reports the same service for all requested service types. Because
        // understanders are modeled as refiners this looks like 2 refiners, but 1 of all others.
        assertThat(manager.getRefiners().size()).isEqualTo(2);
        assertThat(manager.getRenderers().size()).isEqualTo(1);

        manager.unregisterComponentsForPackage(packageName);

        assertThat(manager.getRefiners()).isEmpty();
        assertThat(manager.getRenderers()).isEmpty();
    }
}
