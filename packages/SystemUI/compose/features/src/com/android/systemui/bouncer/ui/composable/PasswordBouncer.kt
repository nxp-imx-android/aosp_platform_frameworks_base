/*
 * Copyright (C) 2023 The Android Open Source Project
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

@file:OptIn(ExperimentalComposeUiApi::class)

package com.android.systemui.bouncer.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformIconButton
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.bouncer.ui.viewmodel.PasswordBouncerViewModel
import com.android.systemui.common.ui.compose.SelectedUserAwareInputConnection
import com.android.systemui.common.ui.compose.SelectedUserAwareLocalContext
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import kotlinx.coroutines.awaitCancellation

/** UI for the input part of a password-requiring version of the bouncer. */
@Composable
internal fun ContentScope.PasswordBouncer(
    viewModel: PasswordBouncerViewModel,
    alphaOnEntry: () -> Float,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    RequestFocus(focusRequester = focusRequester, viewModel = viewModel)

    val isInputEnabled: Boolean by viewModel.isInputEnabled.collectAsStateWithLifecycle()
    val animateFailure: Boolean by viewModel.animateFailure.collectAsStateWithLifecycle()
    val isImeSwitcherButtonVisible by
        viewModel.isImeSwitcherButtonVisible.collectAsStateWithLifecycle()
    val isPasswordRevealed by viewModel.isPasswordRevealed.collectAsStateWithLifecycle()
    val selectedUserId by viewModel.selectedUserId.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { viewModel.resetTextFieldFocus() } }

    LaunchedEffect(animateFailure) {
        if (animateFailure) {
            // We don't currently have a failure animation for password, just consume it:
            viewModel.onFailureAnimationShown()
        }
    }

    val color = MaterialTheme.colorScheme.onSurfaceVariant

    DisableSoftKeyboardWhenNotVisible(alphaOnEntry = alphaOnEntry) {
        SelectedUserAwareInputConnection(selectedUserId) {
            SelectedUserAwareLocalContext(selectedUserId) {
                OutlinedSecureTextField(
                    state = viewModel.textFieldState,
                    enabled = isInputEnabled,
                    textStyle =
                        LocalTextStyle.current.copy(
                            textAlign =
                                if (hasAnyTrailingIcons(viewModel, isImeSwitcherButtonVisible))
                                    TextAlign.Start
                                else TextAlign.Center
                        ),
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    onKeyboardAction = { viewModel.onAuthenticateKeyPressed() },
                    textObfuscationMode =
                        if (isPasswordRevealed) TextObfuscationMode.Visible
                        // Note that [TextObfuscationMode.RevealLastTyped] is a misleading name.
                        // On Android it means "briefly reveal last typed character *if and only if*
                        // the System.TEXT_SHOW_PASSWORD setting is enabled, otherwise it behaves as
                        // [TextObfuscationMode.Hidden].
                        // With this being in a [SelectedUserAwareLocalContext] block, the
                        // setting will be read from the user identified by [selectedUserId].
                        else TextObfuscationMode.RevealLastTyped,
                    modifier =
                        modifier
                            .width(dimensionResource(id = R.dimen.keyguard_password_field_width))
                            .sysuiResTag("bouncer_text_entry")
                            .focusRequester(focusRequester)
                            .onFocusChanged { viewModel.onTextFieldFocusChanged(it.isFocused) }
                            .onInterceptKeyBeforeSoftKeyboard { keyEvent ->
                                if (keyEvent.key == Key.Back) {
                                    viewModel.onImeDismissed()
                                    true
                                } else {
                                    false
                                }
                            },
                    trailingIcon =
                        if (hasAnyTrailingIcons(viewModel, isImeSwitcherButtonVisible)) {
                            {
                                trailingIcons(
                                    viewModel,
                                    color,
                                    isImeSwitcherButtonVisible,
                                    isPasswordRevealed,
                                )
                            }
                        } else null,
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = color,
                            unfocusedBorderColor = color,
                        ),
                )
            }
        }
    }
}

private fun hasAnyTrailingIcons(
    viewModel: PasswordBouncerViewModel,
    isImeSwitcherButtonVisible: Boolean,
): Boolean {
    if (!viewModel.isMoreIndicatorsAndButtonsEnabled) {
        return isImeSwitcherButtonVisible
    }

    return true
}

@Composable
private fun trailingIcons(
    viewModel: PasswordBouncerViewModel,
    color: Color,
    isImeSwitcherButtonVisible: Boolean,
    isPasswordRevealed: Boolean,
) {
    if (!viewModel.isMoreIndicatorsAndButtonsEnabled) {
        if (isImeSwitcherButtonVisible) {
            ImeSwitcherButton(viewModel, color)
        }
        return
    }

    Row() {
        if (isImeSwitcherButtonVisible) {
            ImeSwitcherButton(viewModel, color)
        }
        IconButton(
            onClick = {
                if (isPasswordRevealed) {
                    viewModel.onHidePasswordButtonClicked()
                } else {
                    viewModel.onRevealPasswordButtonClicked()
                }
            },
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    contentColor = color,
                    containerColor = Color.Transparent,
                ),
        ) {
            Icon(
                imageVector =
                    if (isPasswordRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
                // 24p matches the size of ImeSwitcherButton's icon
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Button for changing the password input method (IME). */
@Composable
private fun ImeSwitcherButton(viewModel: PasswordBouncerViewModel, color: Color) {
    val context = LocalContext.current
    PlatformIconButton(
        onClick = { viewModel.onImeSwitcherButtonClicked(context.displayId) },
        iconResource = R.drawable.ic_lockscreen_ime,
        contentDescription = stringResource(R.string.accessibility_ime_switch_button),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                contentColor = color,
                containerColor = Color.Transparent,
            ),
    )
}

/**
 * (Re)requests focus as needed. Done as a separate `@Composable` function to make sure that the
 * caller doesn't need to recompose every time the state in the view-model is changed.
 */
@Composable
private fun RequestFocus(focusRequester: FocusRequester, viewModel: PasswordBouncerViewModel) {
    val isTextFieldFocusRequested by
        viewModel.isTextFieldFocusRequested.collectAsStateWithLifecycle()
    LaunchedEffect(isTextFieldFocusRequested) {
        if (isTextFieldFocusRequested) {
            focusRequester.requestFocus()
        }
    }
}

/** Disables the visibility of the IME when the bouncer is not visible. */
@Composable
fun ContentScope.DisableSoftKeyboardWhenNotVisible(
    alphaOnEntry: () -> Float,
    content: @Composable () -> Unit,
) {
    val contentVisible by remember {
        derivedStateOf { contentVisible(alphaOnEntry, layoutState.currentTransition) }
    }

    DisableSoftKeyboard(!contentVisible) { content() }
}

/** Disables the visibility of the IME without affecting the text field focus. */
@Composable
fun DisableSoftKeyboard(disabled: Boolean, content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            if (disabled) {
                awaitCancellation()
            } else {
                nextHandler.startInputMethod(request)
            }
        }
    ) {
        content()
    }
}

/**
 * Calculates whether the content of the bouncer is visible based on the alpha value from the entry
 * animation and the current transition state.
 */
private fun contentVisible(
    alphaOnEntry: () -> Float,
    currentTransition: TransitionState.Transition?,
): Boolean {
    if (currentTransition == null || currentTransition.isTransitioning(to = Overlays.Bouncer)) {
        return alphaOnEntry() > 0.5
    }

    if (currentTransition.isTransitioning(from = Overlays.Bouncer)) {
        return currentTransition.progress <= 0.5
    }

    return false
}
