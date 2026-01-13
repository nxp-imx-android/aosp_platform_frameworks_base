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

#pragma once

#include <stdint.h>

namespace android::adpf::api {

enum class PerformanceHintFeature : int32_t {
    /**
     * This value represents all APerformanceHintSession functionality. Using the Performance Hint
     * API at all if this is not enabled will likely result in either
     * {@link APerformanceHintManager} or {@link APerformanceHintSession} failing to create, or the
     * session having little to no benefit even if creation succeeds.
     */
    APERF_HINT_SESSIONS,

    /**
     * This value represents the power efficiency mode, as exposed by
     * {@link ASessionCreationConfig_setPreferPowerEfficiency} and
     * {@link APerformanceHint_setPreferPowerEfficiency}.
     */
    APERF_HINT_POWER_EFFICIENCY,

    /**
     * This value the ability for sessions to bind to surfaces using
     * {@link APerformanceHint_setNativeSurfaces} or
     * {@link ASessionCreationConfig_setNativeSurfaces}
     */
    APERF_HINT_SURFACE_BINDING,

    /**
     * This value represents the "graphics pipeline" mode, as exposed by
     * {@link ASessionCreationConfig_setGraphicsPipeline}.
     */
    APERF_HINT_GRAPHICS_PIPELINE,

    /**
     * This value represents the automatic CPU timing feature, as exposed by
     * {@link ASessionCreationConfig_setUseAutoTiming}.
     */
    APERF_HINT_AUTO_CPU,

    /**
     * This value represents the automatic GPU timing feature, as exposed by
     * {@link ASessionCreationConfig_setUseAutoTiming}.
     */
    APERF_HINT_AUTO_GPU,

    /**
     * This value represents the "audio performance" mode, as exposed by
     * {@link ASessionCreationConfig_setAudioPerformance}.
     */
    APERF_HINT_AUDIO_PERFORMANCE,
};

}
