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

package com.android.systemui.keyguard.ui.composable.element

import android.content.res.Resources
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.padding
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.ui.composable.blueprint.ClockElementKeys
import com.android.systemui.keyguard.ui.composable.modifier.burnInAware
import com.android.systemui.keyguard.ui.composable.modifier.onTopPlacementChanged
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.BurnInParameters
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject

class SmartSpaceElement
@Inject
constructor(
    private val lockscreenSmartspaceController: LockscreenSmartspaceController,
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
    private val aodBurnInViewModel: AodBurnInViewModel,
) {
    @Composable
    fun ContentScope.SmartSpace(
        burnInParams: BurnInParameters,
        onTopChanged: (top: Float?) -> Unit,
        onBottomChanged: ((Float) -> Unit)?,
        smartSpacePaddingTop: (Resources) -> Int,
        modifier: Modifier = Modifier,
    ) {
        val resources = LocalResources.current

        Element(key = ClockElementKeys.smartspaceElementKey, modifier = modifier) {
            Column(
                modifier =
                    modifier
                        .onTopPlacementChanged(onTopChanged)
                        .padding(
                            top = { smartSpacePaddingTop(resources) },
                            bottom = {
                                resources.getDimensionPixelSize(
                                    R.dimen.keyguard_status_view_bottom_margin
                                )
                            },
                        )
            ) {
                if (!keyguardSmartspaceViewModel.isSmartspaceEnabled) {
                    return@Column
                }

                val paddingCardHorizontal = dimensionResource(R.dimen.below_clock_padding_end)
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = paddingCardHorizontal, end = paddingCardHorizontal)
                            .burnInAware(viewModel = aodBurnInViewModel, params = burnInParams)
                            .onGloballyPositioned { coordinates ->
                                onBottomChanged?.invoke(coordinates.boundsInWindow().bottom)
                            }
                )
            }
        }
    }

    @Composable
    private fun Card(modifier: Modifier = Modifier) {
        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    addView(
                        lockscreenSmartspaceController.buildAndConnectView(this).apply {
                            layoutParams =
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                )

                            keyguardUnlockAnimationController.lockscreenSmartspace = this
                        }
                    )
                }
            },
            onRelease = { keyguardUnlockAnimationController.lockscreenSmartspace = null },
            modifier = modifier,
        )
    }
}
