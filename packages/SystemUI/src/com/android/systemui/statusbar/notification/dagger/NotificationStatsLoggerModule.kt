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

package com.android.systemui.statusbar.notification.dagger

import com.android.systemui.statusbar.notification.stack.ui.view.NotificationRowStatsLogger
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationStatsLogger
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationStatsLoggerImpl
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationLoggerViewModel
import dagger.Binds
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider

@Module
interface NotificationStatsLoggerModule {

    /** Binds an implementation to the [NotificationStatsLogger]. */
    @Binds fun bindsStatsLoggerImpl(impl: NotificationStatsLoggerImpl): NotificationStatsLogger

    companion object {

        // TODO(b/424001722) no need to keep it optional anymore
        /** Provides a [NotificationStatsLogger] if the refactor flag is on. */
        @Provides
        fun provideStatsLogger(
            provider: Provider<NotificationStatsLogger>
        ): Optional<NotificationStatsLogger> {
            return Optional.of(provider.get())
        }

        // TODO(b/424001722) no need to keep it optional anymore
        /** Provides a [NotificationLoggerViewModel] if the refactor flag is on. */
        @Provides
        fun provideViewModel(
            provider: Provider<NotificationLoggerViewModel>
        ): Optional<NotificationLoggerViewModel> {
            return Optional.of(provider.get())
        }

        /**
         * Provides a the legacy [NotificationLogger] or the new [NotificationStatsLogger] to the
         * notification row.
         *
         * TODO(b/308623704) remove the [NotificationRowStatsLogger] interface, and provide a
         *   [NotificationStatsLogger] to the row directly.
         */
        @Provides
        fun provideRowStatsLogger(
            provider: Provider<NotificationStatsLogger>
        ): NotificationRowStatsLogger {
            return provider.get()
        }
    }
}
