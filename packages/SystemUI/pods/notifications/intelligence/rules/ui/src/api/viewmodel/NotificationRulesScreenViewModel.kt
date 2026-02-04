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

package com.android.systemui.notifications.intelligence.rules.ui.viewmodel

import com.android.systemui.lifecycle.Activatable
import com.android.systemui.notifications.intelligence.rules.shared.model.DraftRuleModel

/** A view model for the notification rules screen. */
public interface NotificationRulesScreenViewModel : Activatable {
    /** The list of current saved rules for the user. */
    public val rules: List<DraftRuleModel>

    public interface Factory {
        public fun create(): NotificationRulesScreenViewModel
    }
}
