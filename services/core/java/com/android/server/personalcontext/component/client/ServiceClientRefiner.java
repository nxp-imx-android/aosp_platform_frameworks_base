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
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWithSignatureWrapper;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.service.personalcontext.refiner.IGetFilterCallback;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Refiner;

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
    private HintFilter mFilter = null;

    public ServiceClientRefiner(Context context, UUID componentId, ServiceInfo serviceInfo,
            UserHandle userHandle) {
        super(context, componentId, serviceInfo, userHandle);

        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.getFilter(getParcelComponentId(), new IGetFilterCallback.Stub() {
                    @PermissionManuallyEnforced
                    @Override
                    public void updateFilter(HintFilter filter) {
                        mFilter = filter;
                    }
                }, opCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get renderer filter", e);
            }
        });
    }

    @Override
    public Set<Set<ContextHintWithSignature>> getInterestedHintClusters(
            Set<ContextHintWithSignature> allContextHints, Set<UUID> seenIDs, boolean isFirstRun) {
        if (mFilter == null) {
            return null;
        } else {
            final Set<ContextHintWithSignature> hints = mFilter.getInterestedHintClusters(
                    allContextHints, seenIDs);
            return hints == null || hints.isEmpty() ? null : Set.of(hints);
        }
    }

    @Override
    protected IRefiner getServiceWrapper(IBinder binder) {
        return IRefiner.Stub.asInterface(binder);
    }

    @Override
    protected void initializeClient(IRefiner client) {
    }

    @Override
    public void refine(
            @NonNull Set<ContextHintWithSignature> inputHints,
            @NonNull Consumer<Set<ContextHint>> callback) {
        final IRefineCallback.Stub binderCallback = new IRefineCallback.Stub() {
            @PermissionManuallyEnforced
            @Override
            public void onHintsRefined(List<ContextHintWrapper> hints) {
                callback.accept(ContextHintWrapper.unwrapInto(hints, new HashSet<>()));
            }
        };

        final List<ContextHintWithSignatureWrapper> hints =
                ContextHintWithSignatureWrapper.wrapList(inputHints);

        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.refine(getParcelComponentId(), hints, binderCallback, opCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, this + " refine() failed", e);
                callback.accept(Collections.emptySet());
            }
        });
    }

    @Override
    public void handleEvent(@NonNull String packageName, @NonNull InsightEvent event) {
        runWithScopedBinder((binder, opCallback) -> {
            try {
                binder.handleEvent(getParcelComponentId(), packageName, event, opCallback);
            } catch (RemoteException e) {
                Slog.w(TAG, this + " handleEvent() failed", e);
            }
        });
    }

    @Override
    public void handleFeedback(PublishedContextInsight insight, Bundle feedback) {
        throw new IllegalStateException("Refiners do not support feedback");
    }
}
