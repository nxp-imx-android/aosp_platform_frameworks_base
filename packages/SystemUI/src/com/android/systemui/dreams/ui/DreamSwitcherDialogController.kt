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

package com.android.systemui.dreams.ui

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dreams.domain.interactor.DreamInteractor
import com.android.systemui.dreams.domain.model.DreamSwitcherDialogRequestModel
import com.android.systemui.statusbar.phone.SystemUIDialog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * The [DreamSwitcherDialogController] is responsible for observing requests to show and dismiss the
 * dream switcher dialog and presenting it to the user.
 */
class DreamSwitcherDialogController
@Inject
constructor(
    @param:Main private val mainDispatcher: CoroutineDispatcher,
    private val delegate: DreamSwitcherDialogDelegate,
    private val dreamInteractor: DreamInteractor,
) {

    private companion object {
        const val TAG = "DreamSwitcherDialogController"
    }

    /** The currently active dialog, or null if no dialog is showing. */
    private var currentDialog: SystemUIDialog? = null

    /** Initializes the controller, which will begin observing for dialog requests. */
    fun init(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(mainDispatcher) {
            try {
                owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    dreamInteractor.switcherRequests.collect { request ->
                        when (request) {
                            is DreamSwitcherDialogRequestModel.Show -> {
                                if (currentDialog != null) {
                                    Log.d(TAG, "Dialog is already showing, ignoring show request")
                                    return@collect
                                }
                                currentDialog = delegate.createDialog()
                                currentDialog?.show()
                            }

                            is DreamSwitcherDialogRequestModel.Dismiss -> {
                                dismissDialog()
                            }
                        }
                    }
                }
            } finally {
                dismissDialog()
            }
        }
    }

    /** Dismisses the currently showing dialog, if any. */
    private fun dismissDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }
}
