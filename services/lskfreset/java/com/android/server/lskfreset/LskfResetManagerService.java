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

package com.android.server.lskfreset;

import static android.app.lskfreset.flags.Flags.enableLskfResetManager;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.app.lskfreset.EscrowToken;
import android.app.lskfreset.ILskfResetManager;
import android.app.lskfreset.ILskfResetSession;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LskfResetManagerService extends SystemService {
    private static final String TAG = "LskfResetManagerSvc";
    private LskfResetManagerImpl mBinder;
    private LskfResetKeyManager mLskfResetKeyManager;

    public LskfResetManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        if (enableLskfResetManager()) {
            Slog.i(TAG, "Starting LskfResetManagerService");
            mLskfResetKeyManager = new LskfResetKeyManager(getContext());
            mBinder = new LskfResetManagerImpl(getContext(), mLskfResetKeyManager);
            Slog.i(TAG, "Registering binder for " + Context.LSKF_RESET_SERVICE);
            try {
                publishBinderService(Context.LSKF_RESET_SERVICE, mBinder);
            } catch (Throwable t) {
                Slog.e(TAG, "Could not start the LskfResetManagerService.", t);
            }
        } else {
            Slog.i(TAG, "LskfResetManagerService not enabled");
        }
    }

    @VisibleForTesting
    ILskfResetManager getBinderService() {
        return mBinder;
    }

    private static class LskfResetManagerImpl extends ILskfResetManager.Stub {
        private static final String STUB_TAG = "LskfResetManagerImpl";

        @SuppressWarnings("unused")
        private final Context mContext;

        private final LskfResetKeyManager mLskfResetKeyManager;

        // Map to keep track of active sessions.
        private final Map<IBinder, LskfResetSessionImpl> mActiveSessions =
                Collections.synchronizedMap(new HashMap<>());

        LskfResetManagerImpl(Context context, LskfResetKeyManager keyManager) {
            mContext = context;
            mLskfResetKeyManager = keyManager;
        }

        @Override
        @RequiresNoPermission
        public ILskfResetSession createLskfResetSession(int userId) {
            Slog.d(STUB_TAG, "createLskfResetSession for user " + userId);
            // TODO: Permission checks for the caller
            LskfResetSessionImpl session =
                    new LskfResetSessionImpl(
                            mContext, userId, mLskfResetKeyManager, this::removeSession);
            IBinder sessionBinder = session.asBinder();
            mActiveSessions.put(sessionBinder, session);

            try {
                sessionBinder.linkToDeath(
                        () -> {
                            Slog.w(
                                    STUB_TAG,
                                    "Client for session died, cleaning up for userId: " + userId);
                            session.close(); // This will also call removeSession
                        },
                        0);
            } catch (RemoteException e) {
                Slog.e(STUB_TAG, "Failed to link to death for session, cleaning up", e);
                session.close();
                return null;
            }

            Slog.d(STUB_TAG, "Created session: " + session.getSessionId() + " for user " + userId);
            return session;
        }

        // Called by LskfResetSessionImpl to remove itself from the map
        private void removeSession(IBinder sessionBinder) {
            if (mActiveSessions.remove(sessionBinder) != null) {
                Slog.d(STUB_TAG, "Session removed. Active sessions: " + mActiveSessions.size());
            }
        }
    }

    // --- Implementation of ILskfResetSession ---
    private static class LskfResetSessionImpl extends ILskfResetSession.Stub {
        private static final String SESSION_TAG = "LskfResetSessionImpl";

        @SuppressWarnings("unused")
        private final int mUserId;

        @SuppressWarnings("unused")
        private final Context mContext;

        @SuppressWarnings("unused")
        private final LskfResetKeyManager mLskfResetKeyManager;

        private final String mSessionId;
        private final SessionRemover mSessionRemover;

        interface SessionRemover {
            void removeSession(IBinder binder);
        }

        LskfResetSessionImpl(
                Context context,
                int userId,
                LskfResetKeyManager keyManager,
                SessionRemover remover) {
            mContext = context;
            mUserId = userId;
            mLskfResetKeyManager = keyManager;
            mSessionId = UUID.randomUUID().toString();
            mSessionRemover = remover;
        }

        String getSessionId() {
            return mSessionId;
        }

        @Override
        @RequiresNoPermission
        public void saveEscrowToken(@NonNull EscrowToken escrowToken) {
            Slog.d(SESSION_TAG, "saveEscrowToken for session " + mSessionId + ", user " + mUserId);
            // TODO: Permission checks
            // TODO: Validate token
            // TODO: mLskfResetKeyManager.storeEscrowToken(mUserId, mSessionId, escrowToken);
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        @RequiresNoPermission
        public void close() {
            Slog.d(SESSION_TAG, "close called for session " + mSessionId + ", user " + mUserId);
            // Implement lock and close mechanism.
            // Remove from the active sessions map in the parent
            mSessionRemover.removeSession(this.asBinder());
        }
    }
}
