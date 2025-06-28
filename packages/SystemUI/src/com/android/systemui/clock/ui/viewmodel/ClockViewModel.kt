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

package com.android.systemui.clock.ui.viewmodel

import android.icu.text.DateTimePatternGenerator
import androidx.compose.runtime.getValue
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.clock.domain.interactor.ClockInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.util.time.DateFormatUtil
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

/** Models UI state for the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClockViewModel
@AssistedInject
constructor(clockInteractor: ClockInteractor, private val dateFormatUtil: DateFormatUtil) :
    ExclusiveActivatable() {
    private val hydrator = Hydrator("ClockViewModel.hydrator")
    private lateinit var dateTimePatternGenerator: DateTimePatternGenerator

    private val formatString: Flow<String> =
        clockInteractor.onTimezoneOrLocaleChanged.mapLatest { getFormatString() }

    private val contentDescriptionFormat: Flow<SimpleDateFormat> =
        formatString.mapLatest { format ->
            // We already provide the locale value in `DateTimePatternGenerator`, so it is okay to
            // not provide the locale in `SimpleDateFormat` here.
            @Suppress("SimpleDateFormat") SimpleDateFormat(format)
        }

    private val _contentDescriptionText: Flow<String> =
        combine(contentDescriptionFormat, clockInteractor.currentTime) {
            contentDescriptionFormat,
            time ->
            contentDescriptionFormat.format(time)
        }

    val contentDescriptionText: String by
        hydrator.hydratedStateOf(
            traceName = "clockContentDescriptionText",
            initialValue = clockInteractor.currentTime.value.toString(),
            source = _contentDescriptionText,
        )

    private val clockTextFormat: Flow<SimpleDateFormat> =
        formatString.mapLatest { format -> getClockTextFormat(format) }

    private val _clockText: Flow<String> =
        combine(clockTextFormat, clockInteractor.currentTime) { clockTextFormat, time ->
            clockTextFormat.format(time)
        }

    val clockText: String by
        hydrator.hydratedStateOf(
            traceName = "clockText",
            initialValue = clockInteractor.currentTime.value.toString(),
            source = _clockText,
        )

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            awaitCancellation()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(): ClockViewModel
    }

    private fun getFormatString(): String {
        dateTimePatternGenerator = DateTimePatternGenerator.getInstance(Locale.getDefault())

        // TODO(b/390204943): use different value depending on if the system want to show seconds.
        val formatSkeleton = if (dateFormatUtil.is24HourFormat) "Hm" else "hm"

        return dateTimePatternGenerator.getBestPattern(formatSkeleton)
    }

    private fun getClockTextFormat(format: String): SimpleDateFormat {
        // TODO(b/390204943): handle AM/PM style
        // We already provide the locale value in `DateTimePatternGenerator` above, so it is okay
        // to not provide the locale in `SimpleDateFormat` here.
        @Suppress("SimpleDateFormat")
        return SimpleDateFormat(format)
    }
}
