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
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ConnectedAssociationStore {

    private static final String TAG = "ConnectedAssociationStore";

    private final CompanionDeviceManager mCompanionDeviceManager;
    private final Set<Integer> mConnectedAssociations = new HashSet<>();
    private final List<Observer> mObservers = new ArrayList<>();

    public interface Observer {
        void onTransportConnected(int associationId);
        void onTransportDisconnected(int associationId);
    }

    public ConnectedAssociationStore(
            @NonNull Context context) {
        mCompanionDeviceManager = context
            .getSystemService(CompanionDeviceManager.class);

        mCompanionDeviceManager.addOnTransportsChangedListener(
                context.getMainExecutor(),
                this::onTransportsChanged);
   }

    public void addObserver(@NonNull Observer observer) {
        mObservers.add(observer);
    }

    public void removeObserver(@NonNull Observer observer) {
        mObservers.remove(observer);
    }

    public Set<Integer> getConnectedAssociations() {
        return mConnectedAssociations;
    }

    private void onTransportsChanged(List<AssociationInfo> associationInfos) {
        Set<Integer> removedAssociations
            = new HashSet<>(mConnectedAssociations);

        Set<Integer> addedAssociations = new HashSet<>();
        for (AssociationInfo associationInfo : associationInfos) {
            if (!mConnectedAssociations.contains(associationInfo.getId())) {
                addedAssociations.add(associationInfo.getId());
            }

            if (removedAssociations.contains(associationInfo.getId())) {
                removedAssociations.remove(associationInfo.getId());
            }
        }

        mConnectedAssociations.addAll(addedAssociations);
        mConnectedAssociations.removeAll(removedAssociations);

        for (Integer associationId : removedAssociations) {
            Log.i(
                TAG,
                "Transport disconnected for association: " + associationId);

            for (Observer observer : mObservers) {
                observer.onTransportDisconnected(associationId);
            }
        }

        for (Integer associationId : addedAssociations) {
            Log.i(TAG, "Transport connected for association: " + associationId);
            for (Observer observer : mObservers) {
                observer.onTransportConnected(associationId);
            }
        }
    }
}