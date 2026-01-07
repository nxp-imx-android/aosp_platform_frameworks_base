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
package com.android.server.attention;

import android.annotation.DurationMillisLong;
import android.annotation.NonNull;
import android.attention.AttentionManager;
import android.attention.IInteractionListener;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.SparseArray;

import androidx.annotation.Keep;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Keep // Used from JNI code.
public class InteractionProviderServiceInternal extends InteractionProviderInternal {

    private final Object mInteractionProvidersLock = new Object();

    @GuardedBy("mInteractionProvidersLock")
    private final List<InteractionProvider> mInteractionProviders = new ArrayList<>();

    @GuardedBy("mPidToInteractionListeners")
    private final SparseArray<InteractionListenerRecord> mPidToInteractionListeners =
            new SparseArray<>();

    @Override
    public boolean registerInteractionProvider(@NonNull InteractionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        synchronized (mInteractionProvidersLock) {
            return mInteractionProviders.add(provider);
        }
    }

    @Override
    public boolean unregisterInteractionProvider(@NonNull InteractionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        synchronized (mInteractionProvidersLock) {
            return mInteractionProviders.remove(provider);
        }
    }

    /**
     * Internal implementation for the interaction listener register API.
     */
    public void registerListener(int callingPid,
            @AttentionManager.InteractionType int interactionTypes,
            @DurationMillisLong long debounceTimeMillis,
            IInteractionListener listener) {
        synchronized (mPidToInteractionListeners) {
            if (mPidToInteractionListeners.contains(callingPid)) {
                throw new RuntimeException(
                        "The calling process has already registered a listener.");
            }
            InteractionListenerRecord listenerRecord = new InteractionListenerRecord(callingPid,
                    listener);
            try {
                IBinder binder = listener.asBinder();
                binder.linkToDeath(listenerRecord, 0);
            } catch (RemoteException ex) {
                // give up
                throw new RuntimeException(ex);
            }
            mPidToInteractionListeners.put(callingPid, listenerRecord);
        }

    }

    /**
     * Internal implementation for the interaction listener unregister API.
     */
    public void unregisterListener(int callingPid) {
        synchronized (mPidToInteractionListeners) {
            if (!mPidToInteractionListeners.contains(callingPid)) {
                throw new RuntimeException("The calling process has not registered listener.");
            }
            mPidToInteractionListeners.remove(callingPid);
        }
    }

    private final class InteractionListenerRecord implements IBinder.DeathRecipient {
        private final int mPid;
        private final IInteractionListener mListener;

        InteractionListenerRecord(int pid, IInteractionListener listener) {
            this.mPid = pid;
            this.mListener = listener;
        }

        @Override
        public void binderDied() {
            synchronized (mPidToInteractionListeners) {
                mPidToInteractionListeners.remove(mPid);
            }
        }
    }

}
