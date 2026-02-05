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
import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;

import com.android.input.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * AttentionManager allows privileged system apps or services to register a listener to subscribe
 * to user-activity events.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
@SystemService(Context.ATTENTION_SERVICE)
public final class AttentionManager {
    /**
     * An empty bit set of interaction types.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    public static final int INTERACTION_TYPE_NONE  = InteractionState.INTERACTION_TYPE_NONE;

    /**
     * Key-press with any supporting device such as keyboard, gamepad, tv-remote etc.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    public static final int INTERACTION_TYPE_KEY = InteractionState.INTERACTION_TYPE_KEY;

    /**
     * Hover with any supporting device such as mouse, touchpad etc.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    public static final int INTERACTION_TYPE_HOVER = InteractionState.INTERACTION_TYPE_HOVER;

    /**
     * A touchscreen, mouse or touchpad gesture such as drag, fling, tap, click etc.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    public static final int INTERACTION_TYPE_GESTURE = InteractionState.INTERACTION_TYPE_GESTURE;

    /**
     * A bit set of all available interaction types.
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_ENABLE_ATTENTION_SERVICE_APIS)
    public static final int INTERACTION_TYPE_ALL = InteractionState.INTERACTION_TYPE_ALL;

    /**
     * @hide
     */
    @IntDef(flag = true, prefix = { "INTERACTION_TYPE_" }, value = {
            INTERACTION_TYPE_NONE,
            INTERACTION_TYPE_KEY,
            INTERACTION_TYPE_HOVER,
            INTERACTION_TYPE_GESTURE,
            INTERACTION_TYPE_ALL,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InteractionType {}

    /**
     * @hide
     */
    public AttentionManager() {
    }
}
