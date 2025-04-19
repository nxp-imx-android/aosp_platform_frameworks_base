/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.selectiontoolbar;

import static android.permission.flags.Flags.useSystemSelectionToolbarInSysui;

import android.content.ComponentName;
import android.content.Context;
import android.service.selectiontoolbar.DefaultSelectionToolbarRenderService;

import com.android.internal.R;
import com.android.server.infra.ServiceNameResolver;

import java.io.PrintWriter;

final class SelectionToolbarServiceNameResolver implements ServiceNameResolver {
    private String mSystemSelectionToolbarServiceName;

    SelectionToolbarServiceNameResolver(Context context) {
        if (useSystemSelectionToolbarInSysui()) {
            mSystemSelectionToolbarServiceName = context.getResources()
                            .getString(R.string.config_systemUiSelectionToolbarRenderService);
        } else {
            mSystemSelectionToolbarServiceName = new ComponentName(
                    "android", DefaultSelectionToolbarRenderService.class.getName())
                    .flattenToString();
        }
    }

    private String getDefaultServiceName() {
        return mSystemSelectionToolbarServiceName;
    }

    @Override
    public String getDefaultServiceName(int userId) {
        return getDefaultServiceName();
    }

    @Override
    public void dumpShort(PrintWriter pw) {
        pw.print("service="); pw.print(getDefaultServiceName());
    }

    @Override
    public void dumpShort(PrintWriter pw, int userId) {
        pw.print("defaultService="); pw.print(getDefaultServiceName(userId));
    }
}
