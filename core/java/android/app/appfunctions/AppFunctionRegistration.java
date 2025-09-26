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

package android.app.appfunctions;

import static android.app.appfunctions.flags.Flags.FLAG_ENABLE_CONTEXTUAL_APP_FUNCTIONS;

import android.annotation.FlaggedApi;

/**
 * Represents a registered {@link AppFunction}.
 *
 * <p>This object is returned from {@link AppFunctionManager#registerAppFunction(String, Executor,
 * AppFunction)}. The caller should retain a reference to this object and call {@link #unregister()}
 * when the app function is no longer needed, typically when the component's lifecycle ends (e.g.,
 * in {@code onStop()} or {@code onDestroy()}).
 *
 * <p>Failing to unregister the app function can lead to memory leaks and unexpected behavior, as
 * the system will continue to hold a reference to the {@link AppFunction} object.
 */
@FlaggedApi(FLAG_ENABLE_CONTEXTUAL_APP_FUNCTIONS)
public interface AppFunctionRegistration {
    /**
     * Removes the associated {@link AppFunction} from the system. If the app function was already
     * unregistered, this method is a no-op.
     */
    void unregister();
}
