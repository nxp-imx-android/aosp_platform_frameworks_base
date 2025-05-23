/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.data.repository

import android.app.ActivityOptions
import android.app.ActivityTaskManager
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.app.assist.ActivityId
import android.app.smartspace.SmartspaceConfig
import android.app.smartspace.SmartspaceManager
import android.app.smartspace.SmartspaceSession.OnTargetsAvailableListener
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.annotation.VisibleForTesting
import androidx.tracing.trace
import com.android.systemui.LauncherProxyService
import com.android.systemui.LauncherProxyService.LauncherProxyListener
import com.android.systemui.ambientcue.shared.model.ActionModel
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.display.data.repository.FocusedDisplayRepository
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Source of truth for ambient actions and visibility of their system space. */
interface AmbientCueRepository {
    /** Chips that should be visible on the UI. */
    val actions: StateFlow<List<ActionModel>>

    /** If the root view is attached to the WindowManager. */
    val isRootViewAttached: StateFlow<Boolean>

    /** If IME is visible or not. */
    val isImeVisible: MutableStateFlow<Boolean>

    /** Task Id which is globally focused on display. */
    val globallyFocusedTaskId: StateFlow<Int>

    /** If the UI is deactivated, such as closed by user or not used for a long period. */
    val isDeactivated: MutableStateFlow<Boolean>

    /** If the taskbar is fully visible and not stashed. */
    val isTaskBarVisible: StateFlow<Boolean>

    /** True if in gesture nav mode, false when in 3-button navbar. */
    val isGestureNav: StateFlow<Boolean>

    val recentsButtonPosition: StateFlow<Rect?>
}

@SysUISingleton
class AmbientCueRepositoryImpl
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val smartSpaceManager: SmartspaceManager?,
    private val autofillManager: AutofillManager?,
    private val activityStarter: ActivityStarter,
    private val navigationModeController: NavigationModeController,
    @Background executor: Executor,
    @Application applicationContext: Context,
    focusdDisplayRepository: FocusedDisplayRepository,
    launcherProxyService: LauncherProxyService,
) : AmbientCueRepository {

    init {
        val callback =
            object : LauncherProxyListener {
                override fun onTaskbarStatusUpdated(visible: Boolean, stashed: Boolean) {
                    _isTaskBarVisible.update { visible && !stashed }
                }

                override fun onRecentsButtonPositionChanged(position: Rect?) {
                    _recentsButtonPosition.update { position }
                }
            }
        launcherProxyService.addCallback(callback)
    }

    override val actions: StateFlow<List<ActionModel>> =
        conflatedCallbackFlow {
                if (smartSpaceManager == null) {
                    Log.i(TAG, "Cannot connect to SmartSpaceManager, it's null.")
                    return@conflatedCallbackFlow
                }

                val session =
                    smartSpaceManager.createSmartspaceSession(
                        SmartspaceConfig.Builder(applicationContext, AMBIENT_CUE_SURFACE).build()
                    )
                Log.i(TAG, "SmartSpace session created")

                val smartSpaceListener = OnTargetsAvailableListener { targets ->
                    val actions =
                        targets
                            .filter { it.smartspaceTargetId == AMBIENT_CUE_SURFACE }
                            .flatMap { target -> target.actionChips }
                            .map { chip ->
                                val title = chip.title.toString()
                                val activityId =
                                    chip.extras?.getParcelable<ActivityId>(EXTRA_ACTIVITY_ID)
                                ActionModel(
                                    icon =
                                        chip.icon?.loadDrawable(applicationContext)
                                            ?: applicationContext.getDrawable(
                                                R.drawable.ic_content_paste_spark
                                            )!!,
                                    label = title,
                                    attribution = chip.subtitle?.toString(),
                                    onPerformAction = {
                                        trace("performAmbientCueAction") {
                                            val intent = chip.intent
                                            val pendingIntent = chip.pendingIntent
                                            val activityId =
                                                chip.extras?.getParcelable<ActivityId>(
                                                    EXTRA_ACTIVITY_ID
                                                )
                                            val autofillId =
                                                chip.extras?.getParcelable<AutofillId>(
                                                    EXTRA_AUTOFILL_ID
                                                )
                                            val token = activityId?.token
                                            Log.v(
                                                TAG,
                                                "Performing action: $activityId, $autofillId, " +
                                                    "$pendingIntent, $intent",
                                            )
                                            if (token != null && autofillId != null) {
                                                autofillManager?.autofillRemoteApp(
                                                    autofillId,
                                                    title,
                                                    token,
                                                    activityId.taskId,
                                                )
                                            } else if (pendingIntent != null) {
                                                val options = BroadcastOptions.makeBasic()
                                                options.isInteractive = true
                                                options.pendingIntentBackgroundActivityStartMode =
                                                    ActivityOptions
                                                        .MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                                try {
                                                    pendingIntent.send(options.toBundle())
                                                } catch (e: PendingIntent.CanceledException) {
                                                    Log.e(
                                                        TAG,
                                                        "pending intent of $pendingIntent was canceled",
                                                    )
                                                }
                                            } else if (intent != null) {
                                                activityStarter.startActivity(intent, false)
                                            }
                                        }
                                    },
                                    taskId = activityId?.taskId ?: INVALID_TASK_ID,
                                )
                            }
                    if (DEBUG) {
                        Log.d(TAG, "SmartSpace OnTargetsAvailableListener $targets")
                    }
                    Log.v(TAG, "SmartSpace actions $actions")
                    trySend(actions)
                }

                session.addOnTargetsAvailableListener(executor, smartSpaceListener)
                Log.i(TAG, "SmartSpace session addOnTargetsAvailableListener")
                awaitClose {
                    session.removeOnTargetsAvailableListener(smartSpaceListener)
                    session.close()
                    Log.i(TAG, "SmartSpace session closed")
                }
            }
            .onEach { actions ->
                if (actions.isNotEmpty()) {
                    isDeactivated.update { false }
                    targetTaskId.update { actions[0].taskId }
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = emptyList(),
            )

    override val isGestureNav: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    NavigationModeController.ModeChangedListener { mode ->
                        trySend(QuickStepContract.isGesturalMode(mode))
                    }
                val navBarMode = navigationModeController.addListener(listener)
                listener.onNavigationModeChanged(navBarMode)
                awaitClose { navigationModeController.removeListener(listener) }
            }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), false)

    private val _isTaskBarVisible = MutableStateFlow(false)
    override val isTaskBarVisible: StateFlow<Boolean> = _isTaskBarVisible.asStateFlow()

    private val _recentsButtonPosition = MutableStateFlow<Rect?>(null)
    override val recentsButtonPosition: StateFlow<Rect?> = _recentsButtonPosition.asStateFlow()

    override val isImeVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val isDeactivated: MutableStateFlow<Boolean> = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    override val globallyFocusedTaskId: StateFlow<Int> =
        focusdDisplayRepository.globallyFocusedTask
            .map { it?.taskId ?: INVALID_TASK_ID }
            .distinctUntilChanged()
            // Filter out focused task quick change. For example, when user clicks ambient cue, the
            // click event will also be sent to NavBar, so it will cause a quick change of focused
            // task (Target App -> Launcher -> Target App).
            .debounce(DEBOUNCE_DELAY_MS)
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = INVALID_TASK_ID,
            )

    val targetTaskId: MutableStateFlow<Int> = MutableStateFlow(INVALID_TASK_ID)

    override val isRootViewAttached: StateFlow<Boolean> =
        combine(isDeactivated, globallyFocusedTaskId, actions) {
                isDeactivated,
                globallyFocusedTaskId,
                actions ->
                actions.isNotEmpty() &&
                    !isDeactivated &&
                    globallyFocusedTaskId == targetTaskId.value
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    companion object {
        // Surface that PCC wants to push cards into
        @VisibleForTesting const val AMBIENT_CUE_SURFACE = "ambientcue"
        @VisibleForTesting const val EXTRA_ACTIVITY_ID = "activityId"
        @VisibleForTesting const val EXTRA_AUTOFILL_ID = "autofillId"
        // Timeout to hide cuebar if it wasn't interacted with
        private const val TAG = "AmbientCueRepository"
        private const val DEBUG = false
        private const val INVALID_TASK_ID = ActivityTaskManager.INVALID_TASK_ID
        const val DEBOUNCE_DELAY_MS = 100L
    }
}
