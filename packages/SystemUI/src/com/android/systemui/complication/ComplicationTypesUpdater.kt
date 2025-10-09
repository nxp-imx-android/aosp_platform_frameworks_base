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

package com.android.systemui.complication

import android.database.ContentObserver
import android.os.UserHandle
import android.provider.Settings
import com.android.settingslib.dream.DreamBackend
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.SystemUser
import com.android.systemui.dreams.DreamOverlayStateController
import com.android.systemui.shared.condition.Monitor
import com.android.systemui.util.condition.ConditionalCoreStartable
import com.android.systemui.util.settings.SecureSettings
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * [ComplicationTypesUpdater] observes the state of available complication types set by the user,
 * and pushes updates to [DreamOverlayStateController].
 */
@SysUISingleton
class ComplicationTypesUpdater
@Inject
constructor(
    private val dreamBackend: DreamBackend,
    @Main private val executor: Executor,
    private val secureSettings: SecureSettings,
    private val dreamOverlayStateController: DreamOverlayStateController,
    @SystemUser monitor: Monitor,
) : ConditionalCoreStartable(monitor) {

    override fun onStart() {
        val settingsObserver =
            object : ContentObserver(null /*handler*/) {
                override fun onChange(selfChange: Boolean) {
                    executor.execute {
                        dreamOverlayStateController.availableComplicationTypes =
                            getAvailableComplicationTypes()
                    }
                }
            }

        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.SCREENSAVER_COMPLICATIONS_ENABLED,
            settingsObserver,
            UserHandle.myUserId(),
        )
        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.SCREENSAVER_HOME_CONTROLS_ENABLED,
            settingsObserver,
            UserHandle.myUserId(),
        )
        secureSettings.registerContentObserverForUserSync(
            Settings.Secure.LOCKSCREEN_SHOW_CONTROLS,
            settingsObserver,
            UserHandle.myUserId(),
        )
        settingsObserver.onChange(false)
    }

    /** Returns complication types that are currently available by user setting. */
    @Complication.ComplicationType
    private fun getAvailableComplicationTypes(): Int {
        return ComplicationUtils.convertComplicationTypes(dreamBackend.getEnabledComplications())
    }
}
