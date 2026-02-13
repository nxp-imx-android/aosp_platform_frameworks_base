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

package com.android.server.uilatencystats;

import android.annotation.NonNull;
import android.content.Context;
import android.uilatencystats.Event;
import android.uilatencystats.EventType;
import android.uilatencystats.UiLatencyEventListener;
import android.uilatencystats.UiLatencyStatsManagerInternal;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.SystemService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service to record and report UI latency related statistics.
 *
 * @hide
 */
public class UiLatencyStatsService extends SystemService {
    private static final String TAG = "UiLatencyStatsService";

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<CopyOnWriteArrayList<UiLatencyEventListener>> mEventListeners =
            new SparseArray<>();

    public UiLatencyStatsService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishLocalService(UiLatencyStatsManagerInternal.class, new LocalService());
    }

    @Override
    public void onUserSwitching(@NonNull TargetUser from, @NonNull TargetUser to) {
        final EventType eventType = new EventType.UserSwitch(to.getUserIdentifier());
        final Event event = new Event(eventType, from.getUserIdentifier());
        publishEvent(event);
    }

    /** Registers a UiLatencyEventListener */
    public void registerUiLatencyEventListener(UiLatencyEventListener listener) {
        for (int eventTypeId : listener.getEventIdsToListen()) {
            synchronized (mLock) {
                var currentListeners = mEventListeners.get(eventTypeId);
                if (currentListeners == null) {
                    currentListeners = new CopyOnWriteArrayList<>();
                }
                currentListeners.add(listener);
                mEventListeners.put(eventTypeId, currentListeners);
            }
        }
    }

    private void publishEvent(Event event) {
        final int id = event.getType().getId();
        final List<UiLatencyEventListener> listeners;
        synchronized (mLock) {
            listeners = mEventListeners.get(id);
        }
        if (listeners != null) {
            for (UiLatencyEventListener listener : listeners) {
                listener.onEvent(event);
            }
        }
    }

    private final class LocalService extends UiLatencyStatsManagerInternal {
        @Override
        public void registerUiLatencyEventListener(UiLatencyEventListener listener) {
            UiLatencyStatsService.this.registerUiLatencyEventListener(listener);
        }

        @Override
        public void publishEvent(Event event) {
            UiLatencyStatsService.this.publishEvent(event);
        }
    }
}
