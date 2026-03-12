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

package com.android.systemui.notifications.intelligence.rules.shared

/**
 * A helper object to help manually test certain behaviors for contextual display during feature
 * development. This is temporary while we're building out the feature.
 */
interface NmContextualDisplayTestConfig {
    /**
     * Sets the amount of delay before returning a response for processing of freeform text into a
     * rule.
     */
    val delayOnRuleGenerationMs: Long
    /** Forces the processing of freeform text into a rule to return an error. */
    val forceErrorOnRuleGeneration: Boolean
}
