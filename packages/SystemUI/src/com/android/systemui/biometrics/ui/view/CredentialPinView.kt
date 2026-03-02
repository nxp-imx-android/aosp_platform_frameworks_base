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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.systemui.bouncer.ui.viewmodel.ActionButtonAppearance
import kotlinx.coroutines.launch

@Composable
fun CredentialPinView(
    onVerify: suspend (CharSequence) -> ByteArray?,
    onSuccess: (ByteArray) -> Unit,
    onPinPress: () -> Unit,
    isVisible: Boolean,
    error: String = "",
) {
    var pinText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        } else {
            pinText = ""
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("cred_pin_pad")
                .onPreviewKeyEvent { event ->
                    val digit = event.getDigitClicked()
                    if (digit != null) {
                        if (pinText.length < 16) pinText += digit
                        return@onPreviewKeyEvent true
                    }

                    if (event.isBackspace()) {
                        if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                        return@onPreviewKeyEvent true
                    }

                    if (event.isEnter()) {
                        // TODO: Pull this out
                        if (pinText.isNotEmpty()) {
                            scope.launch {
                                val attestation = onVerify(pinText)
                                if (attestation != null) {
                                    onSuccess(attestation)
                                } else {
                                    pinText = ""
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }

                    false
                },
    ) {
        Text(
            text = if (error.isNotEmpty()) error else " ",
            style = MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.error.copy(alpha = if (error.isNotEmpty()) 1f else 0f),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        PinDisplay(pinText = pinText, modifier = Modifier.padding(bottom = 16.dp))

        CredentialPinPad(
            onDigitClick = { digit ->
                if (pinText.length < 16) {
                    pinText += digit
                    onPinPress()
                }
            },
            onDeleteClick = {
                if (pinText.isNotEmpty()) {
                    pinText = pinText.dropLast(1)
                    onPinPress()
                }
            },
            onEnterClick = {
                if (pinText.isNotEmpty()) {
                    onPinPress()
                    scope.launch {
                        val attestation = onVerify(pinText)
                        if (attestation != null) {
                            onSuccess(attestation)
                        } else {
                            pinText = ""
                        }
                    }
                }
            },
            isInputEnabled = isVisible,
            deleteButtonAppearance = ActionButtonAppearance.Shown,
        )
    }
}

// Helper to extract digit string from a key event
// TODO: Is there a better way?
private fun KeyEvent.getDigitClicked(): String? {
    if (this.type != KeyEventType.KeyDown) return null
    val char = utf16CodePoint.toChar()
    return if (char.isDigit()) char.toString() else null
}

private fun KeyEvent.isEnter(): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return key == Key.Enter || key == Key.NumPadEnter
}

private fun KeyEvent.isBackspace(): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return key == Key.Backspace || key == Key.Delete
}
