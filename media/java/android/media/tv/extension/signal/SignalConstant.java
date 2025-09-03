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
package android.media.tv.extension.signal;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants for Signal Extension Package.
 *
 * @hide
 */
final class SignalConstant {
    @IntDef({FRONTEND_STATUS_UNTUNED, FRONTEND_STATUS_TUNING,
            FRONTEND_STATUS_LOCKED, FRONTEND_STATUS_UNLOCKED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FrontendStatus {}
    public static final int FRONTEND_STATUS_UNTUNED = 0;
    public static final int FRONTEND_STATUS_TUNING = 1;
    public static final int FRONTEND_STATUS_LOCKED = 2;
    public static final int FRONTEND_STATUS_UNLOCKED = 3;
}
