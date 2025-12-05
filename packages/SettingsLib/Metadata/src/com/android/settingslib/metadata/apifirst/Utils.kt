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

package com.android.settingslib.metadata.apifirst

/**
 * A utility object for creating standardized exception messages for the ApiFirst approach.
 *
 * This object centralizes common error message templates to ensure that validation errors
 * (e.g. incorrect block order, duplicate definitions) are reported consistently across the module.
 */
internal object ExceptionMessagesFormatter {
    private const val EXCEPTION_MESSAGE_WRONG_ORDER = "%s defined in wrong order"
    private const val EXCEPTION_MESSAGE_MULTIPLE_DEFINES =
        "%s should be only defined once per screen"

    fun getExceptionMessageWrongOrder(item: String) =
        String.format(EXCEPTION_MESSAGE_WRONG_ORDER, item)

    fun getExceptionMessageMultipleDefines(item: String) =
        String.format(EXCEPTION_MESSAGE_MULTIPLE_DEFINES, item)
}