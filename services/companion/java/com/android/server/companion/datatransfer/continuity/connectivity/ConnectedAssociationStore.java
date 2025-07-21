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

package com.android.server.companion.datatransfer.continuity.connectivity;

import android.annotation.NonNull;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class ConnectedAssociationStore {

    private static final String TAG = "ConnectedAssociationStore";

    private final CompanionDeviceManager mCompanionDeviceManager;
    private final Map<Integer, AssociationInfo> mConnectedAssociations = new HashMap<>();
    private final List<Observer> mObservers = new ArrayList<>();

    public interface Observer {
        void onTransportConnected(AssociationInfo associationInfo);
        void onTransportDisconnected(int associationId);
    }

    public ConnectedAssociationStore(
        @NonNull CompanionDeviceManager companionDeviceManager,
        @NonNull Executor executor) {

        mCompanionDeviceManager = companionDeviceManager;
        mCompanionDeviceManager.addOnTransportsChangedListener(
                executor,
                this::onTransportsChanged);
   }

    public void addObserver(@NonNull Observer observer) {
        mObservers.add(observer);
    }

    public void removeObserver(@NonNull Observer observer) {
        mObservers.remove(observer);
    }

    public Collection<AssociationInfo> getConnectedAssociations() {
        return mConnectedAssociations.values();
    }

    public AssociationInfo getConnectedAssociationById(int associationId) {
        return mConnectedAssociations.get(associationId);
    }

    private void onTransportsChanged(List<AssociationInfo> associationInfos) {
        Set<Integer> removedAssociations
            = new HashSet<>(mConnectedAssociations.keySet());

        Set<AssociationInfo> addedAssociations = new HashSet<>();
        for (AssociationInfo associationInfo : associationInfos) {
            if (!mConnectedAssociations.containsKey(associationInfo.getId())) {
                addedAssociations.add(associationInfo);
            }

            if (removedAssociations.contains(associationInfo.getId())) {
                removedAssociations.remove(associationInfo.getId());
            }
        }

        for (Integer associationId : removedAssociations) {
            Log.i(
                TAG,
                "Transport disconnected for association: " + associationId);

            mConnectedAssociations.remove(associationId);

            for (Observer observer : mObservers) {
                observer.onTransportDisconnected(associationId);
            }
        }

        for (AssociationInfo associationInfo : addedAssociations) {
            Log.i(
                TAG,
                "Transport connected for association: " + associationInfo.getId());

            mConnectedAssociations.put(associationInfo.getId(), associationInfo);

            for (Observer observer : mObservers) {
                observer.onTransportConnected(associationInfo);
            }
        }
    }
}