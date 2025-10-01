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

import android.annotation.PermissionManuallyEnforced;
import android.content.ComponentName;
import android.content.Context;
import android.service.personalcontext.IPersonalContextManager;
import android.service.personalcontext.PersonalContextManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.R;
import com.android.internal.util.DumpUtils;
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
    private final Context mContext;

    private boolean mRegisteredComponents = false;

    private List<ComponentName> mTriggerComponents;
    private List<ComponentName> mRefinerComponents;
    private ComponentName mUnderstanderComponent;
    private List<ComponentName> mTransformerComponents;
    private List<ComponentName> mRendererComponents;

    public PersonalContextManagerService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        publishBinderService(PersonalContextManager.PERSONAL_CONTEXT_SERVICE, new BinderService());
        Log.d(TAG, "Service started");
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        if (mRegisteredComponents) {
            return;
        }

        mRegisteredComponents = true;
        registerComponents(
                resourceToComponentList(R.array.config_personalContextTriggerComponents),
                resourceToComponentList(R.array.config_personalContextRefinerComponents),
                resourceToComponent(R.string.config_personalContextUnderstanderComponent),
                resourceToComponentList(R.array.config_personalContextTransformerComponents),
                resourceToComponentList(R.array.config_personalContextRendererComponents)
        );
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
        @PermissionManuallyEnforced
        @Override
        protected void dump(
                @NonNull FileDescriptor fd,
                @NonNull PrintWriter fout,
                @Nullable String[] args) {
            if (!DumpUtils.checkDumpPermission(mContext, TAG, fout)) {
                return;
            }

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
