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

package com.android.systemui.notifications.ui.viewmodel

import android.graphics.drawable.Drawable

/** ViewModel representing the content of a notification view. */
public interface NotificationViewModel {
    /** Whether the notification should show its larger (expanded) content. */
    public val isExpanded: Boolean

    /**
     * The icon associated with the app that posted the notification, shown in a circle at the start
     * of the content.
     */
    public val appIcon: Drawable

    /** The title of the notification, emphasized in the content. */
    public val title: String
    /** The content text of the notification, shown below the title. */
    public val text: String
}
