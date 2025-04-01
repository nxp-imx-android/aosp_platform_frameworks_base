/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.wm;

import static android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS;

import static com.android.server.wm.ActivityTaskManagerService.enforceTaskPermission;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;
import android.window.IMultitaskingController;
import android.window.IMultitaskingDelegate;

import androidx.annotation.RequiresPermission;

import com.android.server.am.ActivityManagerService;

import java.util.Objects;

/**
 * Stores a reference to the multitasking functions delegate in WM Shell and serves as a proxy
 * that applies the policy restrictions and passes the calls from the clients.
 */
class MultitaskingController extends IMultitaskingController.Stub {

    private static final String TAG = MultitaskingController.class.getSimpleName();

    private static final boolean DEBUG = true;

    private MultitaskingControllerProxy mProxy;

    @Override
    public void registerMultitaskingDelegate(IMultitaskingDelegate delegate) {
        if (DEBUG) {
            Slog.d(TAG, "registerMultitaskingDelegate: " + delegate);
        }
        enforceTaskPermission("registerMultitaskingDelegate()");
        Objects.requireNonNull(delegate);
        if (mProxy != null) {
            throw new IllegalStateException("Cannot register more than one MultitaskingDelegate.");
        }
        // TODO(b/407149510): Handle the case when SysUI crashes and remove the delegate or the
        // proxy, then init again when it comes back.
        mProxy = new MultitaskingControllerProxy(delegate);
    }

    @Override
    public IMultitaskingDelegate getClientInterface() {
        if (DEBUG) {
            Slog.d(TAG, "getClientInterface");
        }
        enforceMultitaskingControlPermission("getClientInterface()");
        if (mProxy == null) {
            throw new IllegalStateException("WM Shell multitasking delegate not registered.");
        }
        return mProxy;
    }

    /**
     * A proxy class that applies the policy restrictions to the calls coming from the app clients
     * and passes to the registered WM Shell delegate.
     */
    private static class MultitaskingControllerProxy extends IMultitaskingDelegate.Stub {
        @NonNull
        private final IMultitaskingDelegate mDelegate;

        MultitaskingControllerProxy(@NonNull IMultitaskingDelegate delegate) {
            Objects.requireNonNull(delegate);
            mDelegate = delegate;
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void createBubble(@NonNull IBinder token, @NonNull Intent intent,
                boolean collapsed) {
            if (DEBUG) {
                Slog.d(TAG, "createBubble token: " + token + " intent: " + intent
                        + " collapsed: " + collapsed);
            }
            enforceMultitaskingControlPermission("createBubble()");
            Objects.requireNonNull(token);
            Objects.requireNonNull(intent);
            final ComponentName componentName = intent.getComponent();
            if (componentName == null) {
                throw new IllegalArgumentException(
                        "Component name must be set to launch into a Bubble.");
            }

            final long origId = Binder.clearCallingIdentity();
            try {
                // TODO: sanitize the incoming intent?
                mDelegate.createBubble(token, intent, collapsed);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception creating bubble", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void updateBubbleState(IBinder token, boolean collapsed) {
            if (DEBUG) {
                Slog.d(TAG, "updateBubbleState token: " + token + " collapsed: " + collapsed);
            }
            enforceMultitaskingControlPermission("updateBubbleState()");
            Objects.requireNonNull(token);

            final long origId = Binder.clearCallingIdentity();
            try {
                mDelegate.updateBubbleState(token, collapsed);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception updating bubble state", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void updateBubbleMessage(IBinder token, String message) {
            if (DEBUG) {
                Slog.d(TAG, "updateBubbleMessage token: " + token + " message: " + message);
            }
            enforceMultitaskingControlPermission("updateBubbleMessage()");
            Objects.requireNonNull(token);

            final long origId = Binder.clearCallingIdentity();
            try {
                mDelegate.updateBubbleMessage(token, message);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception updating bubble message", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void removeBubble(IBinder token) {
            if (DEBUG) {
                Slog.d(TAG, "removeBubble token: " + token);
            }
            enforceMultitaskingControlPermission("removeBubble()");
            Objects.requireNonNull(token);

            final long origId = Binder.clearCallingIdentity();
            try {
                mDelegate.removeBubble(token);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception removing bubble", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    static void enforceMultitaskingControlPermission(String func) {
        if (ActivityManagerService.checkComponentPermission(
                REQUEST_SYSTEM_MULTITASKING_CONTROLS, Binder.getCallingPid(),
                Binder.getCallingUid(), PackageManager.PERMISSION_GRANTED, -1 /* owningUid */,
                true /* exported */) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid() + ", uid="
                + Binder.getCallingUid()
                + " requires android.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS";
        Slog.w(TAG, msg);
        throw new SecurityException(msg);
    }
}
