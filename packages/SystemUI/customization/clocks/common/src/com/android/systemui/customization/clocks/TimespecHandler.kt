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

package com.android.systemui.customization.clocks

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import com.android.systemui.customization.clocks.DigitalTimespec.DATE_FORMAT
import com.android.systemui.customization.clocks.DigitalTimespec.DIGIT_PAIR
import com.android.systemui.customization.clocks.DigitalTimespec.FIRST_DIGIT
import com.android.systemui.customization.clocks.DigitalTimespec.SECOND_DIGIT
import com.android.systemui.customization.clocks.DigitalTimespec.TIME_FULL_FORMAT
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.util.Date
import java.util.Locale

interface TimeKeeper {
    val callbacks: MutableList<Callback>
    var timeZone: TimeZone
    val time: Date

    fun updateTime()

    fun <T> queryCalendar(calTimespec: Int, callback: (time: Int, min: Int, max: Int) -> T): T

    interface Callback {
        fun onTimeChanged() {}

        fun onTimeZoneChanged() {}
    }
}

open class TimeKeeperImpl(private val calendar: Calendar = Calendar.getInstance()) : TimeKeeper {
    override var timeZone: TimeZone
        get() = calendar.timeZone
        set(value) {
            if (calendar.timeZone != value) {
                calendar.timeZone = value
                callbacks.forEach { it.onTimeZoneChanged() }
            }
        }

    override val callbacks = mutableListOf<TimeKeeper.Callback>()

    override val time: Date
        get() = calendar.time

    override fun updateTime() = updateTime(System.currentTimeMillis())

    override fun <T> queryCalendar(
        calTimespec: Int,
        callback: (time: Int, min: Int, max: Int) -> T,
    ): T {
        return callback(
            calendar.get(calTimespec),
            calendar.getActualMinimum(calTimespec),
            calendar.getActualMaximum(calTimespec),
        )
    }

    protected fun updateTime(timeMillis: Long) {
        calendar.timeInMillis = timeMillis
        callbacks.forEach { it.onTimeChanged() }
    }
}

class TestTimeKeeper(var currentTimeMillis: Long = 0L) : TimeKeeperImpl() {
    override fun updateTime() {
        updateTime(currentTimeMillis)
    }
}

class DigitalFormatter
private constructor(
    val timeKeeper: TimeKeeper,
    private val patterns: PatternProvider,
    private val enableContentDescription: Boolean,
    private val textModifier: (String) -> String = { it },
) : TimeKeeper.Callback {
    interface PatternProvider {
        fun getTextPattern(formatKind: TimeFormatKind): String

        fun getDescriptionPattern(formatKind: TimeFormatKind): String
    }

    val textPattern: String
        get() = patterns.getTextPattern(formatKind)

    val descriptionPattern: String
        get() = patterns.getDescriptionPattern(formatKind)

    var formatKind = TimeFormatKind.HALF_DAY
        set(value) {
            if (field != value) {
                field = value
                applyPattern()
            }
        }

    var locale: Locale = Locale.getDefault()
        set(value) {
            if (field != value) {
                field = value
                onLocaleChanged()
            }
        }

    private lateinit var textFormat: SimpleDateFormat
    private var descriptionFormat: SimpleDateFormat? = null

    init {
        timeKeeper.callbacks.add(this)
        onLocaleChanged()
    }

    fun onLocaleChanged() {
        textFormat = getTextFormat(locale)
        descriptionFormat = getDescriptionFormat(locale)
        onTimeZoneChanged()
    }

    override fun onTimeZoneChanged() {
        textFormat.timeZone = timeKeeper.timeZone
        descriptionFormat?.timeZone = timeKeeper.timeZone
        applyPattern()
    }

    @SuppressLint("SimpleDateFormat")
    private fun getTextFormat(locale: Locale): SimpleDateFormat {
        return if (locale.language.equals(Locale.ENGLISH.language)) {
            // force date format in English, and time format to use defined format
            SimpleDateFormat(textPattern, textPattern, ULocale.forLocale(locale))
        } else {
            SimpleDateFormat.getInstanceForSkeleton(textPattern, locale) as SimpleDateFormat
        }
    }

    private fun getDescriptionFormat(locale: Locale): SimpleDateFormat? {
        if (!enableContentDescription) return null
        return SimpleDateFormat.getInstanceForSkeleton(descriptionPattern, locale)
            as SimpleDateFormat
    }

    private fun applyPattern() {
        textFormat.applyPattern(textPattern)
        descriptionFormat?.applyPattern(descriptionPattern)
    }

    fun getText(): String {
        return textModifier(textFormat.format(timeKeeper.time))
    }

    fun getContentDescription(): String? {
        return descriptionFormat?.format(timeKeeper.time)
    }

    companion object {
        fun Date(
            pattern: String,
            timeKeeper: TimeKeeper,
            enableContentDescription: Boolean = false,
            textModifier: (String) -> String = { it },
        ): DigitalFormatter {
            return DigitalFormatter(
                timeKeeper,
                object : PatternProvider {
                    override fun getTextPattern(formatKind: TimeFormatKind) = pattern

                    override fun getDescriptionPattern(formatKind: TimeFormatKind) = "EEEE MMMM d"
                },
                enableContentDescription,
                textModifier,
            )
        }

        fun Time(
            pattern: String,
            timeKeeper: TimeKeeper,
            enableContentDescription: Boolean = false,
            textModifier: (String) -> String = { it },
        ): DigitalFormatter {
            return DigitalFormatter(
                timeKeeper,
                object : PatternProvider {
                    override fun getTextPattern(formatKind: TimeFormatKind): String {
                        return when (formatKind) {
                            TimeFormatKind.HALF_DAY -> pattern
                            TimeFormatKind.FULL_DAY -> pattern.replace("hh", "h").replace("h", "HH")
                        }
                    }

                    override fun getDescriptionPattern(formatKind: TimeFormatKind): String {
                        return when (formatKind) {
                            TimeFormatKind.HALF_DAY -> "hh:mm"
                            TimeFormatKind.FULL_DAY -> "HH:mm"
                        }
                    }
                },
                enableContentDescription,
                textModifier,
            )
        }
    }
}

class DigitalTimespecHandler(val timespec: DigitalTimespec, val formatter: DigitalFormatter) {
    fun getViewId(): Int = timespec.getViewId("h" in formatter.textPattern)

    fun getText(): String {
        val text = formatter.getText()

        return when (timespec) {
            // These directly return the result from the ICU time formatter
            DIGIT_PAIR,
            TIME_FULL_FORMAT,
            DATE_FORMAT -> text

            // We expect a digit pair string to extract FIRST_DIGIT/SECOND_DIGIT, but some languages
            // produce numerals at utf-8 code points that are not representable by a single char. To
            // account for this, we break the string in half instead.
            FIRST_DIGIT -> text.substring(0, text.length / 2)
            SECOND_DIGIT -> text.substring(text.length / 2, text.length)
        }
    }

    fun getContentDescription(): String? {
        return when (timespec) {
            TIME_FULL_FORMAT,
            DATE_FORMAT -> formatter.getContentDescription()
            DIGIT_PAIR,
            FIRST_DIGIT,
            SECOND_DIGIT -> null
        }
    }
}

class AnalogTimespecHandler(
    val timespec: AnalogTimespec,
    val tickMode: AnalogTickMode,
    val timeKeeper: TimeKeeper,
) {
    val isSweeping: Boolean
        get() = tickMode == AnalogTickMode.SWEEP

    fun getRotation(): Float {
        return when (timespec) {
            AnalogTimespec.SECONDS -> query(Calendar.SECOND)
            AnalogTimespec.MINUTES -> query(Calendar.MINUTE)
            AnalogTimespec.HOURS -> query(Calendar.HOUR)
            AnalogTimespec.HOURS_OF_DAY -> query(Calendar.HOUR_OF_DAY)
            AnalogTimespec.DAY_OF_WEEK -> query(Calendar.DAY_OF_WEEK)
            AnalogTimespec.DAY_OF_MONTH -> query(Calendar.DAY_OF_MONTH)
            AnalogTimespec.DAY_OF_YEAR -> query(Calendar.DAY_OF_YEAR)
            AnalogTimespec.WEEK -> query(Calendar.WEEK_OF_YEAR)
            AnalogTimespec.MONTH -> query(Calendar.MONTH)
        }
    }

    private fun query(calTimespec: Int, depth: Int = 0): Float {
        return timeKeeper.queryCalendar(calTimespec) { time, min, max ->
            val diff = (max - min + 1).toFloat()
            if (diff <= 0) return@queryCalendar 0f

            var result = (time - min) / diff
            if (isSweeping && depth < SWEEP_QUERY_DEPTH_LIMIT) {
                SWEEP_MAP[calTimespec]?.let { lowerTimespec ->
                    val lowerTimescale = query(lowerTimespec, depth + 1)
                    result += lowerTimescale / diff
                }
            }
            return@queryCalendar result
        }
    }

    companion object {
        private const val SWEEP_QUERY_DEPTH_LIMIT = 2
        private val SWEEP_MAP =
            mapOf(
                Calendar.SECOND to Calendar.MILLISECOND,
                Calendar.MINUTE to Calendar.SECOND,
                Calendar.HOUR to Calendar.MINUTE,
                Calendar.HOUR_OF_DAY to Calendar.MINUTE,
                Calendar.DAY_OF_WEEK to Calendar.HOUR_OF_DAY,
                Calendar.DAY_OF_MONTH to Calendar.HOUR_OF_DAY,
                Calendar.DAY_OF_YEAR to Calendar.HOUR_OF_DAY,
                Calendar.WEEK_OF_YEAR to Calendar.DAY_OF_WEEK,
                Calendar.MONTH to Calendar.DAY_OF_MONTH,
            )
    }
}
