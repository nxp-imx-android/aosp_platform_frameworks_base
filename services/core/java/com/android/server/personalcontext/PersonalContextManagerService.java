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

package com.android.server.personalcontext;

import android.annotation.RequiresNoPermission;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.R;
import com.android.internal.os.BackgroundThread;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public class PersonalContextManagerService extends SystemService {
    private static final String TAG = "PersonalContext";

    private boolean mRegisteredComponents = false;

    private List<ComponentName> mTriggerComponents;
    private List<ComponentName> mRefinerComponents;
    private ComponentName mUnderstanderComponent;
    private List<ComponentName> mTransformerComponents;
    private List<ComponentName> mRendererComponents;

    public PersonalContextManagerService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishBinderService(PersonalContextManager.PERSONAL_CONTEXT_SERVICE, new BinderService());
        Log.d(TAG, "Service started");

        registerBroadcastReceiver();
    }

    @SuppressLint("MissingPermission")
    private void registerBroadcastReceiver() {
        // user change and unlock
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_REMOVED);
        intentFilter.addAction(Intent.ACTION_USER_SWITCHED);
        intentFilter.addAction(Intent.ACTION_USER_UNLOCKED);

        Handler receiverHandler = BackgroundThread.getHandler();
        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_REMOVED.equals(action)) {
                    handleUserRemoved();
                } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    handleUserSwitched();
                } else if (Intent.ACTION_USER_UNLOCKED.equals(action)) {
                    handleUserUnlocked();
                }
            }
        };

        getContext().registerReceiverAsUser(broadcastReceiver, UserHandle.ALL, intentFilter, null,
                receiverHandler);
    }

    private void handleUserRemoved() {
        // TODO: Handle user being removed
    }

    private void handleUserSwitched() {
        // TODO: Handle user switching
    }

    private void handleUserUnlocked() {
        if (!mRegisteredComponents) {
            mRegisteredComponents = true;
            registerComponents(
                    resourceToComponentList(R.array.config_personalContextTriggerComponents),
                    resourceToComponentList(R.array.config_personalContextRefinerComponents),
                    resourceToComponent(R.string.config_personalContextUnderstanderComponent),
                    resourceToComponentList(R.array.config_personalContextTransformerComponents),
                    resourceToComponentList(R.array.config_personalContextRendererComponents)
            );
        }
    }

    private void registerComponents(
            List<ComponentName> triggerComponents,
            List<ComponentName> refinerComponents,
            ComponentName understanderComponent,
            List<ComponentName> transformerComponents,
            List<ComponentName> rendererComponents) {
        mTriggerComponents = triggerComponents;
        mRefinerComponents = refinerComponents;
        mUnderstanderComponent = understanderComponent;
        mTransformerComponents = transformerComponents;
        mRendererComponents = rendererComponents;

        // TODO: Do actual registration.
    }

    private ComponentName resourceToComponent(int resId) {
        return ComponentName.unflattenFromString(getContext().getResources().getString(resId));
    }

    private List<ComponentName> resourceToComponentList(int resId) {
        List<ComponentName> result = new ArrayList<>();
        for (String rawComponentName : getContext().getResources().getStringArray(resId)) {
            result.add(ComponentName.unflattenFromString(rawComponentName));
        }
        return result;
    }

    private final class BinderService extends IPersonalContextManager.Stub {
        @Override
        @RequiresNoPermission
        protected void dump(
                @NonNull FileDescriptor fd,
                @NonNull PrintWriter fout,
                @Nullable String[] args) {
            fout.write("Triggers\n");
            fout.write("========\n");
            dumpComponentList(fout, mTriggerComponents);

            fout.write("Refiners\n");
            fout.write("========\n");
            dumpComponentList(fout, mRefinerComponents);

            fout.write("Understander\n");
            fout.write("============\n");
            if (mUnderstanderComponent != null) {
                fout.write("  " + mUnderstanderComponent.flattenToString() + "\n");
            } else {
                fout.write("  (No component configured)\n");
            }
            fout.write("\n");

            fout.write("Transformers\n");
            fout.write("============\n");
            dumpComponentList(fout, mTransformerComponents);

            fout.write("Renderers\n");
            fout.write("=========\n");
            dumpComponentList(fout, mRendererComponents);
        }

        private void dumpComponentList(@NonNull PrintWriter fout, List<ComponentName> components) {
            for (ComponentName component : components) {
                fout.write("  " + component.flattenToString() + "\n");
            }
            fout.write(String.format("  (%s configured components)\n", components.size()));
            fout.write("\n");
        }

    }
}
