/**
 * Copyright (c) 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.refiner;

import android.os.ParcelUuid;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.refiner.IRefineCallback;

/**
 * Interface for refiner services.
 *
 * @hide
 */
interface IRefiner {
    /** Provides configuration information to the refiner. */
    oneway void configure(in ParcelUuid componentId);

    /**
     * Requests that a set of new hints be refined. All of the hints in inputHints will be
     * hints that this refiner hasn't seen before. The callback may be called exactly once,
     * with a new set of hints.
     */
    oneway void refine(in List<ContextHintWithSignature> inputHints, in IRefineCallback callback);
}