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

package android.companion.datatransfer.continuity;

import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class facilitates task continuity between devices owned by the same user.
 * This includes synchronizing lists of open tasks between a user's devices, as well as requesting
 * to hand off a task from one device to another. Handing a task off to a device will resume the
 * application on the receiving device, preserving the state of the task.
 *
 * @hide
 */
@FlaggedApi(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
@SystemService(Context.TASK_CONTINUITY_SERVICE)
@SystemApi
public class TaskContinuityManager {
    private final Context mContext;
    private final ITaskContinuityManager mService;

    private final RemoteTaskListenerHolder mListenerHolder;

    /** @hide */
    public TaskContinuityManager(
        @NonNull Context context,
        @NonNull ITaskContinuityManager service) {

        mContext = context;
        mService = service;
        mListenerHolder = new RemoteTaskListenerHolder(service);
    }

    /**
     * Listener to be notified when the list of remote tasks changes.
    */
    public interface RemoteTaskListener {
        /**
         * Invoked when the list of remote tasks changes.
         *
         * @param remoteTasks The list of remote tasks.
         */
        void onRemoteTasksChanged(@NonNull List<RemoteTask> remoteTasks);
    }

    /**
     * Returns a list of tasks currently running on the remote devices owned by the user.
     */
    @NonNull
    public List<RemoteTask> getRemoteTasks() {
        // TODO: joeantonetti - Optimize this call by caching the most recent state pushed to
        // mListenerHolder.
        try {
            return mService.getRemoteTasks();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers a listener to be notified when the list of remote tasks changes.
     *
     * @param executor The executor to be used to invoke the listener.
     * @param listener The listener to be registered.
     */
    public void registerRemoteTaskListener(
        @NonNull Executor executor,
        @NonNull RemoteTaskListener listener) {

        try {
            mListenerHolder.registerListener(executor, listener);
            // TODO: joeantonetti - Send an initial notification to the listener after it's
            // attached.
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters a listener previously registered with
     * {@link #registerRemoteTaskListener}.
     *
     * @param listener The listener to be unregistered.
     */
    public void unregisterRemoteTaskListener(@NonNull RemoteTaskListener listener) {
        try {
            mListenerHolder.unregisterListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Helper class which manages registered listeners and proxies them behind a single
     * IRemoteTaskListener, which is lazily registered with ITaskContinuityManager if there is
     * a single registered listener.
     */
    private final class RemoteTaskListenerHolder extends IRemoteTaskListener.Stub {

        @GuardedBy("mListeners")
        private final Map<RemoteTaskListener, Executor> mListeners = new ArrayMap<>();

        @GuardedBy("mListeners")
        private boolean mRegistered = false;

        public RemoteTaskListenerHolder(ITaskContinuityManager service) {}

        /**
         * Registers a listener to be notified of remote task changes.
         *
         * @param executor The executor on which the listener should be invoked.
         * @param listener The listener to register.
         */
        public void registerListener(
            @NonNull Executor executor,
            @NonNull RemoteTaskListener listener) throws RemoteException {

            synchronized(mListeners) {
                if (!mRegistered) {
                    mService.registerRemoteTaskListener(this);
                    mRegistered = true;
                }

                mListeners.put(listener, executor);
            }
        }

        /**
         * Unregisters a previously registered listener.
         *
         * @param listener The listener to unregister.
         */
        public void unregisterListener(
            @NonNull RemoteTaskListener listener) throws RemoteException {

            synchronized(mListeners) {
                mListeners.remove(listener);
                if (mListeners.isEmpty() && mRegistered) {
                    mRegistered = false;
                    mService.unregisterRemoteTaskListener(this);
                }
            }
        }

        @Override
        public void onRemoteTasksChanged(List<RemoteTask> remoteTasks) throws RemoteException {
            synchronized(mListeners) {
                for (Map.Entry<RemoteTaskListener, Executor> entry : mListeners.entrySet()) {
                    RemoteTaskListener listener = entry.getKey();
                    Executor executor = entry.getValue();
                    executor.execute(() -> listener.onRemoteTasksChanged(remoteTasks));
                }
            }
        }
    }
}
