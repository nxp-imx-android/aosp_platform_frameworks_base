/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.volume.panel.component.mediaoutput.domain.interactor

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.dialog.MediaOutputDialog
import com.android.systemui.media.dialog.MediaOutputDialogManager
import com.android.systemui.media.dialog.MediaSwitchingType
import com.android.systemui.qs.panels.data.repository.QSPanelAppearanceRepository
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.panel.component.mediaoutput.domain.model.MediaOutputComponentModel
import com.android.systemui.volume.panel.dagger.scope.VolumePanelScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** User actions interactor for Media Output Volume Panel component. */
@VolumePanelScope
class MediaOutputActionsInteractor
@Inject
constructor(
    @Application private val context: Context,
    @VolumePanelScope private val coroutineScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
    private val mediaOutputDialogManager: MediaOutputDialogManager,
    private val qsPanelAppearanceRepository: QSPanelAppearanceRepository,
    private val expandedAudioTileDetailsFeatureInteractor: ExpandedAudioTileDetailsFeatureInteractor,
) {
    private val mDesktopDialogWidth =
        context.getResources().getDimensionPixelSize(R.dimen.shade_panel_width)
    private val mDesktopDialogHeight = 650

    fun onBarClick(
        model: MediaOutputComponentModel?,
        expandable: Expandable?,
        mediaSwitchingType: MediaSwitchingType?,
    ) {
        val onDialogEventListener =
            if (expandedAudioTileDetailsFeatureInteractor.isEnabled()) {
                object : MediaOutputDialog.OnDialogEventListener {
                    private var job: Job? = null

                    override fun onCreate(dialog: Dialog) {
                        job?.cancel()
                        job =
                            coroutineScope.launch {
                                updateDialogBounds(
                                    dialog,
                                    qsPanelAppearanceRepository.qsPanelShape.value,
                                )
                                // Update the dialog bounds when the QS panel shape changes.
                                qsPanelAppearanceRepository.qsPanelShape.collect { shape ->
                                    updateDialogBounds(dialog, shape)
                                }
                            }
                    }

                    override fun onStop(dialog: Dialog) {
                        job?.cancel()
                    }
                }
            } else {
                null
            }

        coroutineScope.launch {
            if (model is MediaOutputComponentModel.MediaSession) {
                suspendCancellableCoroutine { continuation ->
                    mediaOutputDialogManager.createAndShowWithController(
                        packageName = model.session.packageName,
                        aboveStatusBar = false,
                        controller = expandable?.dialogController(),
                        onDialogEventListener = onDialogEventListener,
                        mediaSwitchingType = mediaSwitchingType,
                    )
                    continuation.invokeOnCancellation { mediaOutputDialogManager.dismiss() }
                }
            } else {
                suspendCancellableCoroutine { continuation ->
                    mediaOutputDialogManager.createAndShowForSystemRouting(
                        expandable?.dialogController(),
                        onDialogEventListener,
                        mediaSwitchingType,
                    )
                    continuation.invokeOnCancellation { mediaOutputDialogManager.dismiss() }
                }
            }
        }
    }

    private suspend fun updateDialogBounds(dialog: Dialog, shape: ShadeScrimShape?) {
        // Update the dialog UI on main thread.
        withContext(mainDispatcher) {
            if (shape == null) {
                return@withContext
            }
            val qsPanelBounds = shape.bounds
            val lp: WindowManager.LayoutParams = dialog.window!!.attributes
            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.width = mDesktopDialogWidth
            lp.height = mDesktopDialogHeight
            // Position the dialog at the center of the qsPanelBounds
            lp.x = (qsPanelBounds.left + qsPanelBounds.right - mDesktopDialogWidth).toInt() / 2
            lp.y = (qsPanelBounds.top + qsPanelBounds.bottom - mDesktopDialogHeight).toInt() / 2
            dialog.window!!.attributes = lp
        }
    }

    private fun Expandable.dialogController(): DialogTransitionAnimator.Controller? {
        if (expandedAudioTileDetailsFeatureInteractor.isEnabled()) {
            return null
        }
        return dialogTransitionController(
            cuj =
                DialogCuj(
                    InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                    MediaOutputDialogManager.INTERACTION_JANK_TAG,
                )
        )
    }
}
