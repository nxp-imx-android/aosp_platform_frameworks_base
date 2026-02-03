/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.attention;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import com.android.input.flags.Flags;

/**
 * Listener for interaction state changes.
 * @see AttentionManager
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
public interface InteractionListener {
    /**
     * Called when the interaction state changes.
     * @hide
     * @param interactionInfo current interaction info.
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    void onInteraction(@NonNull InteractionInfo interactionInfo);
}
