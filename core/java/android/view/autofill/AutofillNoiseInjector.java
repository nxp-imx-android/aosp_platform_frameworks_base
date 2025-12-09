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

package android.view.autofill;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.assist.AssistStructure.ViewNode;
import android.content.ComponentName;

/**
 * The class containing the implementation of the noise injection algorithm.
 *
 * @hide
 */
public final class AutofillNoiseInjector {

    private String mMasterSeed;

    private ComponentName mActivityComponent;

    /**
     * Constructs a new AutofillNoiseInjector.
     *
     * @param masterSeed The master seed for the noise injection algorithm. It's expected to be
     *     unique per device.
     * @param activityComponent the componentName of the current assistStructure, which contains the
     *     package name and activity name. Needed for calculating seed.
     * @hide
     */
    public AutofillNoiseInjector(
            @NonNull String masterSeed, @NonNull ComponentName activityComponent) {
        this.mMasterSeed = masterSeed;
        this.mActivityComponent = activityComponent;
    }

    /**
     * Injects noise into the given view node's text.
     *
     * @param viewNode the node to inject noise into.
     * @return returns the payload and metadata of the view node's text after noise-injection.
     * @hide
     */
    @Nullable
    public AutofillNoiseInjectedData injectNoise(@NonNull ViewNode viewNode) {
        // TODO(b/456535350): implement the algorithm.
        return null;
    }
}
