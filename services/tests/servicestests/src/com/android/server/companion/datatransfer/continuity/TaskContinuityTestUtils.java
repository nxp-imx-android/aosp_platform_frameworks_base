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

import android.companion.AssociationInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.mockito.Mockito;

public final class TaskContinuityTestUtils {

    public static Context createMockContext() {
        return Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));
    }

    public static AssociationInfo createAssociationInfo(int id, String displayName) {
        return new AssociationInfo.Builder(id, 0, "com.android.test")
            .setDisplayName(displayName)
            .build();
    }
}