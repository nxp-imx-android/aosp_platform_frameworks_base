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

package com.android.server.security.authenticationpolicy.agent;

import android.content.Context;
import android.provider.Settings;
import android.util.Slog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A Map wrapper of {@link AgentSession} entries. Each entry in the map is also exposed
 * to via a secure settings if the session is <b>not</b> authorized. This allows other system
 * components to check for that case even if they do not have permission to use the other agent
 * APIs in this service. This should only be used for convenience, like showing a UI
 * affordance to the user before they attempt to try an action that will fail when the session
 * state is checked during execution (i.e. it's a hint).
 *
 * The map is backed by a {@link java.util.concurrent.ConcurrentHashMap} and offers the
 * same read/write behavior for map entries. There are no ordering guarantees about the state of
 * the values exposed via settings.
 */
public class AgentSessionMap<K> {

    private static final String TAG = "AgentSessionMap";
    public static final String SETTINGS_KEY = "xdev-ai-agent-missing-session";

    private final Context mContext;
    private final int mUserId;
    private final Map<K, AgentSession> mAgentSessionList = new ConcurrentHashMap<>();

    /**
     * Create a new session map.
     *
     * @param context system_server context
     * @param userId user id
     */
    AgentSessionMap(Context context, int userId) {
        mContext = context;
        mUserId = userId;
        updateSetting("" /* value */);
    }

    public AgentSession get(K key) {
        return mAgentSessionList.get(key);
    }

    public void put(K key, AgentSession value) {
        try {
            mAgentSessionList.put(key, value);
        } finally {
            updateSetting();
        }
    }

    public AgentSession remove(K key) {
        try {
            return mAgentSessionList.remove(key);
        } finally {
            updateSetting();
        }
    }

    public void clear() {
        try {
            mAgentSessionList.clear();
        } finally {
            updateSetting();
        }
    }

    private void updateSetting() {
        updateSetting(null /* value */);
    }

    private void updateSetting(String overrideValue) {
        final String value = overrideValue != null ? overrideValue : mAgentSessionList.values()
                .stream()
                .filter(s -> (s.getUserId() == mUserId) && !s.isAllowed())
                .map(s -> String.valueOf(s.getId()))
                .collect(Collectors.joining(","));
        final boolean ok = Settings.Secure.putStringForUser(mContext.getContentResolver(),
                SETTINGS_KEY, value, mUserId);
        if (!ok) {
            Slog.w(TAG, "Unable to shadow invalid connections");
        }
    }
}
