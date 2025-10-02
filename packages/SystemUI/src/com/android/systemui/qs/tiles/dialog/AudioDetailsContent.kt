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

package com.android.systemui.qs.tiles.dialog

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel.ContentViewModel.SwitcherPageViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.shared.model.VolumePanelComponents
import com.android.systemui.volume.panel.ui.composable.ComposeVolumePanelUiComponent
import com.android.systemui.volume.panel.ui.composable.VolumePanelComposeScope

@Composable
fun AudioDetailsContent(audioDetailsViewModel: AudioDetailsViewModel) {
    LaunchedEffect(Unit) { audioDetailsViewModel.activate() }
    when (val currentViewModel = audioDetailsViewModel.contentViewModel) {
        is AudioDetailsDefaultPageViewModel -> {
            val accessibilityTitle = stringResource(R.string.accessibility_volume_settings)
            val volumePanelState = currentViewModel.volumePanelState

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(600.dp)
                        .semantics { paneTitle = accessibilityTitle }
                        .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                if (volumePanelState != null) {
                    with(
                        VolumePanelComposeScope(
                            volumePanelState.collectAsStateWithLifecycle().value
                        )
                    ) {
                        AudioContentsDefaultPage(viewModel = currentViewModel)
                    }
                }
            }
        }
        is SwitcherPageViewModel ->
            AndroidView(
                factory = { context ->
                    // TODO(b/378513663): Implement the switcher page view
                    LayoutInflater.from(context).inflate(R.layout.media_output_dialog, null)
                }
            )
    }
}

@Composable
fun VolumePanelComposeScope.AudioContentsDefaultPage(
    viewModel: AudioDetailsDefaultPageViewModel,
    modifier: Modifier = Modifier,
) {
    val volumeComponentsFactory = viewModel.volumeComponentsFactory
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        volumeComponentsFactory?.let { factory ->
            SectionTitle(R.string.quick_settings_audio_output_section_title)

            val outputComponent = factory.createComponent(VolumePanelComponents.MEDIA_OUTPUT)
            with(outputComponent as ComposeVolumePanelUiComponent) { Content(Modifier) }

            // TODO(b/448199358): Use customized slider for audio details view instead.
            val sliderComponent = factory.createComponent(VolumePanelComponents.VOLUME_SLIDERS)
            with(sliderComponent as ComposeVolumePanelUiComponent) { Content(Modifier) }

            SectionTitle(R.string.quick_settings_audio_input_section_title)

            val inputComponent = factory.createComponent(VolumePanelComponents.MEDIA_INPUT)
            with(inputComponent as ComposeVolumePanelUiComponent) { Content(Modifier) }
        }

        Text(
            modifier = modifier.basicMarquee().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            text = stringResource(R.string.quick_settings_audio_input_disclaimer_text),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        SectionTitle(R.string.quick_settings_audio_effects_section_title)

        AnimatedContent(
            targetState = viewModel.footerComponents,
            label = "FooterComponentAnimation",
        ) { footerComponents ->
            footerComponents?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    val visibleComponentsCount =
                        footerComponents.fastSumBy { if (it.isVisible) 1 else 0 }

                    // Center footer component if there is only one present
                    if (visibleComponentsCount == 1) {
                        Spacer(modifier = Modifier.weight(0.5f))
                    }

                    for (component in footerComponents) {
                        if (component.isVisible) {
                            with(component.component as ComposeVolumePanelUiComponent) {
                                Content(Modifier.weight(1f))
                            }
                        }
                    }

                    if (visibleComponentsCount == 1) {
                        Spacer(modifier = Modifier.weight(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(textId: Int, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.basicMarquee().padding(horizontal = 18.dp),
        text = stringResource(textId),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
