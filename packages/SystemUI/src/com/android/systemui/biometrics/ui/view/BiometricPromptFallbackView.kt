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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.biometrics.shared.model.IconType
import com.android.systemui.biometrics.ui.binder.Spaghetti
import com.android.systemui.biometrics.ui.viewmodel.PromptViewModel
import com.android.systemui.res.R

@Composable
fun BiometricPromptFallbackView(promptViewModel: PromptViewModel, callback: Spaghetti.Callback) {
    val fallbackViewModel = promptViewModel.promptFallbackViewModel
    val promptContent by fallbackViewModel.fallbackOptions.collectAsStateWithLifecycle(emptyList())
    val showCredential by fallbackViewModel.showCredential.collectAsStateWithLifecycle(false)
    val credentialText by fallbackViewModel.credentialKindText.collectAsStateWithLifecycle(-1)
    val credentialIcon by
        fallbackViewModel.credentialKindIcon.collectAsStateWithLifecycle(Icons.Filled.Password)

    val showManageIdentityCheck by
        fallbackViewModel.showManageIdentityCheck.collectAsStateWithLifecycle(false)
    val icCredentialButtonEnabled by
        fallbackViewModel.icCredentialButtonEnabled.collectAsStateWithLifecycle(false)
    val icCredentialSubtitle by
        fallbackViewModel.icCredentialSubtitle.collectAsStateWithLifecycle(null)
    val icShowFooter by fallbackViewModel.icShowFooter.collectAsStateWithLifecycle(false)

    val context = LocalContext.current

    val optionTotal = fallbackViewModel.optionCount.collectAsStateWithLifecycle(0)

    PlatformTheme {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        promptViewModel.onSwitchToAuth()
                        callback.onResumeAuthentication()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = stringResource(R.string.accessibility_back),
                    )
                }
                Text(
                    text = stringResource(R.string.biometric_dialog_fallback_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier.padding(start = 16.dp).semantics {
                            heading()
                            // TODO(391644182): Use paneTitle once prompt is moved to compose
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                var optionCount = 0
                // When credential is allowed and IC is disabled, credential should always be first
                if (showCredential) {
                    OptionItem(
                        icon = credentialIcon,
                        text = stringResource(credentialText),
                        index = optionCount++,
                        total = optionTotal.value,
                        modifier = Modifier.testTag("fallback_credential_button"),
                        onClick = {
                            promptViewModel.onSwitchToCredential()
                            callback.onUseDeviceCredential()
                        },
                    )
                }
                // If credential is allowed and IC is enabled, IC manage is presented first
                if (showManageIdentityCheck) {
                    OptionItem(
                        icon = Icons.Outlined.Settings,
                        text = stringResource(R.string.biometric_dialog_manage_identity_check),
                        index = optionCount++,
                        total = optionTotal.value,
                        onClick = {
                            fallbackViewModel.manageIdentityCheck(context)
                            callback.onUserCanceled()
                        },
                    )
                }
                for ((index, option) in promptContent.withIndex()) {
                    OptionItem(
                        icon = getIcon(option.iconType),
                        text = option.text.toString(),
                        index = optionCount++,
                        total = optionTotal.value,
                        onClick = { callback.onFallbackOptionPressed(index) },
                    )
                }
                // Credential when IC is enabled should always be at the bottom and disabled
                if (showManageIdentityCheck) {
                    OptionItem(
                        icon = credentialIcon,
                        text = stringResource(credentialText),
                        index = optionCount++,
                        total = optionTotal.value,
                        subtitle = icCredentialSubtitle,
                        enabled = icCredentialButtonEnabled,
                        onClick = {
                            promptViewModel.onSwitchToCredential()
                            callback.onUseDeviceCredential()
                        },
                    )
                    if (icShowFooter) {
                        Text(
                            stringResource(R.string.biometric_dialog_identity_check_pin_footer),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    text: String,
    index: Int,
    total: Int,
    subtitle: Int? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape =
        when (index) {
            0 -> RoundedCornerShape(16.dp, 16.dp, 4.dp, 4.dp)
            total - 1 -> RoundedCornerShape(4.dp, 4.dp, 16.dp, 16.dp)
            else -> RoundedCornerShape(4.dp)
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .clickable(onClick = onClick, enabled = enabled)
                .alpha(if (enabled) 1f else 0.4f),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = stringResource(subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun getIcon(iconType: IconType): ImageVector {
    return when (iconType) {
        IconType.ACCOUNT -> Icons.Outlined.AccountCircle
        IconType.SETTING -> Icons.Outlined.Settings
        IconType.QR_CODE -> Icons.Outlined.QrCode2
        IconType.PASSWORD -> Icons.Outlined.Password
        else -> Icons.Outlined.ViewStream // Generic Icon //TODO: This one is a placeholder
    }
}
