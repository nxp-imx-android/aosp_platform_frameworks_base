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

package com.android.server.companion.datatransfer.continuity;

import static android.Manifest.permission.READ_REMOTE_TASKS;
import static android.Manifest.permission.REQUEST_TASK_HANDOFF;
import static android.Manifest.permission.MODIFY_HANDOFF_SETTINGS;
import static android.Manifest.permission.READ_HANDOFF_SETTINGS;
import static com.android.server.companion.utils.PermissionsUtils.enforceCallerIsSystemOrCanInteractWithUserId;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.ITaskContinuityManager;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.IHandoffFeatureStateListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.content.Context;
import android.os.Binder;
import android.util.Slog;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.tasks.TaskSyncController;
import com.android.server.companion.datatransfer.continuity.handoff.HandoffController;

import com.android.server.SystemService;

import java.util.Collection;
import java.util.Objects;

/**
 * Service to handle task continuity features
 *
 * @hide
 */
public final class TaskContinuityManagerService extends SystemService {

    private static final String TAG = "TaskContinuityManagerService";

    private TaskSyncController mTaskSyncController;
    private HandoffController mHandoffController;
    private TaskContinuityManagerServiceImpl mTaskContinuityManagerService;
    private TaskContinuityMessenger mTaskContinuityMessenger;

    public TaskContinuityManagerService(Context context) {
        super(context);

        mTaskContinuityMessenger = new TaskContinuityMessenger(context);
        mTaskSyncController = new TaskSyncController(context, mTaskContinuityMessenger);
        mHandoffController =
                new HandoffController(context, mTaskContinuityMessenger, mTaskSyncController);
    }

    @Override
    public void onStart() {
        mTaskContinuityManagerService = new TaskContinuityManagerServiceImpl();
        mTaskSyncController.enable();
        mHandoffController.enable();
        publishBinderService(Context.TASK_CONTINUITY_SERVICE, mTaskContinuityManagerService);
    }

    private final class TaskContinuityManagerServiceImpl extends ITaskContinuityManager.Stub {
        @Override
        @EnforcePermission(READ_REMOTE_TASKS)
        public void registerRemoteTaskListener(int userId, @NonNull IRemoteTaskListener listener) {
            registerRemoteTaskListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);
            mTaskSyncController.registerTaskListener(Objects.requireNonNull(listener));
        }

        @Override
        @EnforcePermission(READ_REMOTE_TASKS)
        public void unregisterRemoteTaskListener(
                int userId, @NonNull IRemoteTaskListener listener) {
            unregisterRemoteTaskListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);
            mTaskSyncController.unregisterTaskListener(Objects.requireNonNull(listener));
        }

        @Override
        @EnforcePermission(REQUEST_TASK_HANDOFF)
        public void requestHandoff(
                int userId,
                int associationId,
                int remoteTaskId,
                @NonNull IHandoffRequestCallback callback) {
            requestHandoff_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            Objects.requireNonNull(callback);

            final long ident = Binder.clearCallingIdentity();
            try {
                mHandoffController.requestHandoff(associationId, remoteTaskId, callback);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        @EnforcePermission(MODIFY_HANDOFF_SETTINGS)
        public void enableHandoffForDevice(int userId, boolean enabled) {
            enableHandoffForDevice_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            // TODO: Implement this method.
        }

        @Override
        @EnforcePermission(READ_HANDOFF_SETTINGS)
        public void registerHandoffFeatureStateListener(
                int userId, @NonNull IHandoffFeatureStateListener listener) {
            registerHandoffFeatureStateListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            // TODO: Implement this method.
        }

        @Override
        @EnforcePermission(READ_HANDOFF_SETTINGS)
        public void unregisterHandoffFeatureStateListener(
                int userId, @NonNull IHandoffFeatureStateListener listener) {
            unregisterHandoffFeatureStateListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            // TODO: Implement this method.
        }
    }
}
