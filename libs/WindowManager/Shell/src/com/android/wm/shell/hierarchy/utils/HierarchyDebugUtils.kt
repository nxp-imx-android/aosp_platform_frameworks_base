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
package com.android.wm.shell.hierarchy.utils

import android.window.WindowContainerToken

/**
 * Utility functions to support debugging the hierarchy.
 */
class HierarchyDebugUtils {
    companion object {
        val UNDERLINE = "\u001b[4m"
        val RED = "\u001b[31m"
        val GREEN = "\u001b[32m"
        val YELLOW = "\u001b[33m"
        val PURPLE = "\u001b[35m"
        val WHITE = "\u001b[37m"
        val NONE = "\u001b[0m"

        /**
         * Returns a string representation for a token.
         */
        fun tokenToString(token: WindowContainerToken): String {
            val windowTokenStr =
                token.toString().removePrefix("WCT{android.os.BinderProxy@").removeSuffix("}")
            return "Token=${windowTokenStr}"
        }
    }
}
