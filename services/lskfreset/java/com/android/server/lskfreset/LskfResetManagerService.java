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
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LskfResetManagerService extends SystemService {
    private static final String TAG = "LskfResetManagerSvc";

    private LskfResetKeyManager mLskfResetKeyManager;
    private LskfResetManagerImpl mBinder;

    // Map to keep track of active sessions.
    private final Map<IBinder, LskfResetSessionImpl> mActiveSessions =
            Collections.synchronizedMap(new HashMap<>());

    public LskfResetManagerService(Context context) {
        super(context);
    }

    @VisibleForTesting
    ILskfResetManager getBinderService() {
        return mBinder;
    }

    @VisibleForTesting
    boolean isSessionActive(ILskfResetSession session) {
        return mActiveSessions.containsKey(session.asBinder());
    }

    @Override
    public void onStart() {
        if (enableLskfResetManager()) {
            Slog.i(TAG, "Starting LskfResetManagerService");
            mLskfResetKeyManager = new LskfResetKeyManager(getContext());
            mBinder = new LskfResetManagerImpl();
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

    private class LskfResetManagerImpl extends ILskfResetManager.Stub {
        private static final String STUB_TAG = "LskfResetManagerImpl";

        @Override
        @RequiresNoPermission
        public ILskfResetSession createLskfResetSession(UserHandle user) {
            // TODO: Permission checks for the caller
            Slog.d(STUB_TAG, "createLskfResetSession for user " + user);

            LskfResetSessionImpl session = new LskfResetSessionImpl(user);
            IBinder sessionBinder = session.asBinder();
            mActiveSessions.put(sessionBinder, session);

            try {
                sessionBinder.linkToDeath(
                        () -> {
                            Slog.w(
                                    STUB_TAG,
                                    "Client for session died, cleaning up for user: " + user);
                            session.terminate();
                        },
                        0);
            } catch (RemoteException e) {
                Slog.e(STUB_TAG, "Failed to link to death for session, cleaning up", e);
                session.close();
                return null;
            }

            Slog.d(STUB_TAG, "Created session: " + session.getSessionId() + " for user: " + user);
            return session;
        }
    }

    private class LskfResetSessionImpl extends ILskfResetSession.Stub {
        private static final String SESSION_TAG = "LskfResetSessionImpl";

        private final UserHandle mUser;
        private final String mSessionId;
        private boolean mClosed;

        LskfResetSessionImpl(UserHandle user) {
            mUser = user;
            mSessionId = UUID.randomUUID().toString();
            mClosed = false;
        }

        String getSessionId() {
            return mSessionId;
        }

        void terminate() {
            if (!mClosed) close();
        }

        @Override
        @RequiresNoPermission
        public void saveEscrowToken(@NonNull EscrowToken escrowToken) {
            Slog.d(SESSION_TAG, "saveEscrowToken for session " + mSessionId + ", user " + mUser);
            if (mClosed) throw new IllegalStateException("Session is already closed");
            // TODO: Permission checks
            // TODO: Validate token
            // TODO: mLskfResetKeyManager.storeEscrowToken(mUser, mSessionId, escrowToken);
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        @RequiresNoPermission
        public void close() {
            Slog.d(SESSION_TAG, "close called for session " + mSessionId + ", user " + mUser);
            if (mClosed) throw new IllegalStateException("Session is already closed");
            // TODO: Implement lock and close mechanism.
            // Remove from the active sessions map in the parent
            if (mActiveSessions.remove(asBinder()) != null) {
                Slog.d(SESSION_TAG, "Session removed. Active sessions: " + mActiveSessions.size());
            }
            mClosed = true;
        }
    }
}
