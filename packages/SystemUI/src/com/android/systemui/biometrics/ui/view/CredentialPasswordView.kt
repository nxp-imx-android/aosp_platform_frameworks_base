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

package com.android.systemui.biometrics.ui.view

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CredentialPasswordView(
    onVerify: suspend (CharSequence) -> ByteArray?,
    onSuccess: (ByteArray) -> Unit,
    isVisible: Boolean,
    error: String = "",
) {
    var text by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            text = ""
        }
    }

    val onSubmit = {
        if (text.isNotEmpty()) {
            scope.launch {
                val attestation = onVerify(text)
                if (attestation != null) {
                    onSuccess(attestation)
                } else {
                    text = ""
                }
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText -> text = newText },
        modifier =
            Modifier.fillMaxWidth()
                .testTag("lockPassword")
                .wrapContentHeight()
                .padding(bottom = 16.dp)
                .focusRequester(focusRequester),
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        isError = !error.isEmpty(),
        supportingText = { if (error.isEmpty()) Text(error) },
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}
