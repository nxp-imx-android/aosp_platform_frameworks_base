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

package com.android.server.personalcontext.component.client;

import android.annotation.PermissionManuallyEnforced;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Refiner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client for refiner services.
 *
 * @hide
 */
public class ServiceClientRefiner extends BaseServiceClientComponent<IRefiner> implements Refiner {
    public ServiceClientRefiner(Context context, UUID componentId, ServiceInfo serviceInfo) {
        super(context, componentId, serviceInfo);
    }

    @Override
    public Set<Set<ContextHintWithSignature>> getInterestedHintClusters(
            Set<ContextHintWithSignature> allContextHints, Set<UUID> seenIDs, boolean isFirstRun) {
        // TODO(b/452425564): Implement this to use a filter in the package's manifest.
        // For now this runs hints through the refiner one-by-one.
        final Set<Set<ContextHintWithSignature>> eachHint = new HashSet<>();
        for (ContextHintWithSignature hint : allContextHints) {
            if (!seenIDs.contains(hint.getContextHint().getHintId())) eachHint.add(Set.of(hint));
        }
        return eachHint;
    }

    @Override
    protected IRefiner getServiceWrapper(IBinder binder) {
        return IRefiner.Stub.asInterface(binder);
    }

    @Override
    protected void initializeClient(IRefiner client) throws RemoteException {
        client.configure(new ParcelUuid(getComponentId()));
    }

    @Override
    public void refine(
            @NonNull Set<ContextHintWithSignature> inputHints,
            @NonNull Consumer<Set<ContextHint>> callback) {
        final List<ContextHintWithSignature> hints = new ArrayList<>(inputHints);

        final IRefineCallback.Stub binderCallback = new IRefineCallback.Stub() {
            @PermissionManuallyEnforced
            @Override
            public void onHintsRefined(List<ContextHintWrapper> hints) {
                callback.accept(ContextHintWrapper.unwrapInto(hints, new HashSet<>()));
            }
        };

        runWithBinder(binder -> {
            try {
                binder.refine(hints, binderCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, this + " refine() failed", e);
                callback.accept(Collections.emptySet());
            }
        });
    }
}
