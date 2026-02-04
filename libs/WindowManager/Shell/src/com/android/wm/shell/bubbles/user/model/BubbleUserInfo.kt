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

package com.android.wm.shell.bubbles.user.model

import com.android.launcher3.util.UserIconInfo
import com.android.users.UserType

/** User info needed for displaying bubbles for the user. */
data class BubbleUserInfo(val userId: Int, val userType: UserType) {

    val userIconInfoType: Int =
        when (userType) {
            UserType.MAIN -> UserIconInfo.TYPE_MAIN
            UserType.CLONED -> UserIconInfo.TYPE_CLONED
            UserType.WORK -> UserIconInfo.TYPE_WORK
            UserType.PRIVATE -> UserIconInfo.TYPE_PRIVATE
        }
}
