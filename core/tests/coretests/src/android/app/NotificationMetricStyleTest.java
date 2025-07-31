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

package android.app;

import static android.app.Notification.EXTRA_METRICS;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;

import android.app.Notification.Metric;
import android.app.Notification.Metric.FixedDate;
import android.app.Notification.Metric.FixedFloat;
import android.app.Notification.Metric.FixedInt;
import android.app.Notification.Metric.FixedString;
import android.app.Notification.Metric.FixedTime;
import android.app.Notification.Metric.MetricValue.ValueString;
import android.app.Notification.Metric.TimeDifference;
import android.app.Notification.MetricStyle;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
@Presubmit
@EnableFlags(Flags.FLAG_API_METRIC_STYLE)
public class NotificationMetricStyleTest {

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    // May 30, 2025 14:26:25 UTC
    private static final Instant NOW = Instant.ofEpochMilli(1748615185000L);
    // May 30, 2025
    private static final LocalDate TODAY = LocalDate.of(2025, 5, 30);
    // May 29, 2025 -> less than 4 months have passed
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    // May 31, 2025 -> less than 4 months away
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    // December 18, 2025 -> more than 4 months have passed
    private static final LocalDate LONG_AGO = LocalDate.of(2025, 1, 1);
    // December 18, 2025 -> more than 4 months away
    private static final LocalDate FAR_AWAY = LocalDate.of(2025, 12, 18);

    private static final long ELAPSED_REALTIME = 300_000;

    private static final String NNBSP = "\u202f";

    private Context mContext;
    private Locale mPreviousLocale;
    private TimeZone mPreviousTimeZone;
    private String mPrevious24HourSetting;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        // Force some values that can depend on device current settings to a known state.
        mPreviousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        mPreviousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Montevideo")); // (not UTC!)
        mPrevious24HourSetting = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.TIME_12_24);
        Settings.System.putString(mContext.getContentResolver(), Settings.System.TIME_12_24, "12");

        Notification.sSystemClock = () -> NOW;
        Notification.sElapsedRealtimeClock = () -> ELAPSED_REALTIME;
    }

    @After
    public void tearDown() {
        if (mPreviousLocale != null) {
            Locale.setDefault(mPreviousLocale);
        }
        if (mPreviousTimeZone != null) {
            TimeZone.setDefault(mPreviousTimeZone);
        }
        Settings.System.putString(mContext.getContentResolver(), Settings.System.TIME_12_24,
                mPrevious24HourSetting);
        Notification.sSystemClock = InstantSource.system();
        Notification.sElapsedRealtimeClock = () -> SystemClock.elapsedRealtime();
    }

    @Test
    public void addExtras_writesExtras() {
        MetricStyle style = new MetricStyle()
                .addMetric(new Metric(new FixedInt(4, "birds"), "4"))
                .addMetric(new Metric(new FixedInt(5, "rings"), "5"))
                .addMetric(new Metric(new FixedInt(6, "geese"), "6"))
                .addMetric(new Metric(new FixedInt(7, "swans"), "7"))
                .addMetric(new Metric(new FixedInt(8, "maids"), "8"));

        Bundle bundle = new Bundle();
        style.addExtras(bundle);

        ArrayList<Bundle> storedBundles = bundle.getParcelableArrayList(EXTRA_METRICS,
                Bundle.class);

        assertThat(storedBundles).isNotNull();
        assertThat(storedBundles).hasSize(5);
    }

    @Test
    public void restoreFromExtras_restoresWrittenMetrics() {
        Bundle bundle = new Bundle();
        MetricStyle original = new MetricStyle()
                .addMetric(new Metric(
                        TimeDifference.forTimer(Instant.ofEpochMilli(1),
                                TimeDifference.FORMAT_ADAPTIVE),
                        "Time:"))
                .addMetric(new Metric(
                        TimeDifference.forPausedStopwatch(Duration.ofHours(4),
                                TimeDifference.FORMAT_CHRONOMETER),
                        "Stopwatch:"))
                .addMetric(new Metric(
                        new FixedDate(LocalDate.of(2025, 6, 2), FixedDate.FORMAT_SHORT_DATE),
                        "Event date:"))
                .addMetric(new Metric(
                        new FixedTime(LocalTime.of(10, 30)),
                        "Event time:"))
                .addMetric(new Metric(
                        new FixedInt(12, "drummers"), "Label"))
                .addMetric(new Metric(
                        new FixedInt(42), "Answer"))
                .addMetric(new Metric(
                        new FixedFloat(0.75f), "Readiness"))
                .addMetric(new Metric(
                        new FixedFloat(273f, "°K"),
                        "Temp"))
                .addMetric(new Metric(
                        new FixedFloat(12.345f, null, 0, 3),
                        "Active time"))
                .addMetric(new Metric(
                        new FixedString("This is the last"), "Last"));

        original.addExtras(bundle);
        MetricStyle recovered = new MetricStyle();
        recovered.restoreFromExtras(bundle);

        assertThat(recovered).isEqualTo(original);
    }

    @Test
    public void areNotificationsVisiblyDifferent_sameMetrics_false() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "Cal"))
                .addMetric(new Metric(new FixedInt(2), "Cal"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "Cal"))
                .addMetric(new Metric(new FixedInt(2), "Cal"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isFalse();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isFalse();
    }

    @Test
    public void areNotificationsVisiblyDifferent_differentMetrics_true() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "thingies"), "a"))
                .addMetric(new Metric(new FixedInt(2, "widgets"), "b"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "c"))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "d"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isTrue();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isTrue();
    }

    @Test
    public void areNotificationsVisiblyDifferent_differentMetricCounts_true() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "a"))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "b"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "a"))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "b"))
                .addMetric(new Metric(new FixedInt(3, "whatsits"), "c"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isTrue();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isTrue();
    }

    @Test
    public void areNotificationsVisiblyDifferent_firstThreeEqual_false() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "a"))
                .addMetric(new Metric(new FixedInt(2), "b"))
                .addMetric(new Metric(new FixedInt(3), "c"))
                .addMetric(new Metric(new FixedString("Ignored thing"), "d"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "a"))
                .addMetric(new Metric(new FixedInt(2), "b"))
                .addMetric(new Metric(new FixedInt(3), "c"))
                .addMetric(new Metric(new FixedString("Also ignored"), "d"))
                .addMetric(new Metric(new FixedString("And this too"), "e"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isFalse();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isFalse();
    }

    @Test
    public void valueToString_timeDifferenceRunning() {
        TimeDifference runningTimer = TimeDifference.forTimer(
                NOW.plusSeconds(90), // Rings in 90 seconds
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("1:30"));

        TimeDifference overrunSeconds = TimeDifference.forTimer(
                NOW.minusSeconds(10), // Rang 10 seconds ago
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(overrunSeconds.toValueString(mContext)).isEqualTo(new ValueString("−0:10"));

        TimeDifference overrunMinutes = TimeDifference.forTimer(
                NOW.minus(2, MINUTES).minus(10, SECONDS), // Rang 2 minutes 10 seconds ago
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(overrunMinutes.toValueString(mContext)).isEqualTo(new ValueString("−2:10"));

        TimeDifference overrunHours = TimeDifference.forTimer(
                NOW.minus(3, HOURS).minus(2, MINUTES).minus(10, SECONDS), // Are you asleep?
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(overrunHours.toValueString(mContext)).isEqualTo(new ValueString("−3:02:10"));

        TimeDifference runningStopwatch = TimeDifference.forStopwatch(
                NOW.minusSeconds(120), // Started 2 minutes ago
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(runningStopwatch.toValueString(mContext)).isEqualTo(new ValueString("2:00"));

        TimeDifference longRunningStopwatch = TimeDifference.forStopwatch(
                NOW.minus(500, HOURS).minus(40, MINUTES), // Started looooong ago
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(longRunningStopwatch.toValueString(mContext)).isEqualTo(
                new ValueString("500:40:00"));
    }

    @Test
    public void valueToString_timeDifferenceInstant_updatesWithSystemClock() {
        TimeDifference runningTimer = TimeDifference.forTimer(
                NOW.plusSeconds(60), // Rings in 60 seconds
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("1:00"));

        Notification.sElapsedRealtimeClock =
                () -> ELAPSED_REALTIME + Duration.ofSeconds(10).toMillis();

        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("1:00"));

        Notification.sSystemClock = () -> NOW.plusSeconds(3);

        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("0:57"));
    }

    @Test
    public void valueToString_timeDifferenceElapsedRealtime_updatesWithElapsedRealtime() {
        TimeDifference runningTimer = TimeDifference.forTimer(
                ELAPSED_REALTIME + Duration.ofSeconds(60).toMillis(), // Rings in 60 seconds
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("1:00"));

        Notification.sSystemClock = () -> NOW.plusSeconds(3);

        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("1:00"));

        Notification.sElapsedRealtimeClock =
                () -> ELAPSED_REALTIME + Duration.ofSeconds(10).toMillis();

        expect.that(runningTimer.toValueString(mContext)).isEqualTo(new ValueString("0:50"));
    }

    @Test
    public void valueToString_timeDifferencePaused() {
        TimeDifference pausedTimer = TimeDifference.forPausedTimer(
                Duration.ofHours(2).plusMinutes(5),
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(pausedTimer.toValueString(mContext)).isEqualTo(
                new ValueString("2:05:00"));

        TimeDifference pausedStopWatch = TimeDifference.forPausedStopwatch(
                Duration.ofMinutes(12),
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(pausedStopWatch.toValueString(mContext)).isEqualTo(
                new ValueString("12:00"));
    }

    @Test
    public void valueToString_timeDifferenceAdaptive() {
        TimeDifference diffHms = TimeDifference.forPausedTimer(
                Duration.ofHours(2).plusMinutes(30).plusSeconds(58),
                TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffHms.toValueString(mContext)).isEqualTo(new ValueString("2h 30m 58s"));

        TimeDifference diffH = TimeDifference.forPausedTimer(
                Duration.ofHours(2), TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffH.toValueString(mContext)).isEqualTo(new ValueString("2h"));

        TimeDifference diffHm = TimeDifference.forPausedTimer(
                Duration.ofHours(2).plusMinutes(30), TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffHm.toValueString(mContext)).isEqualTo(new ValueString("2h 30m"));

        TimeDifference diffHs = TimeDifference.forPausedTimer(
                Duration.ofHours(2).plusSeconds(10), TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffHs.toValueString(mContext)).isEqualTo(new ValueString("2h 10s"));

        TimeDifference diffMs = TimeDifference.forPausedTimer(
                Duration.ofMinutes(30).plusSeconds(58), TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffMs.toValueString(mContext)).isEqualTo(new ValueString("30m 58s"));

        TimeDifference diffZero = TimeDifference.forPausedTimer(
                Duration.ofSeconds(0), TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffZero.toValueString(mContext)).isEqualTo(new ValueString("0s"));

        TimeDifference diffNegative = TimeDifference.forPausedTimer(
                Duration.ZERO.minusHours(2).minusMinutes(30).minusSeconds(58),
                TimeDifference.FORMAT_ADAPTIVE);
        expect.that(diffNegative.toValueString(mContext)).isEqualTo(new ValueString("−2h 30m 58s"));
    }

    @Test
    public void valueToString_timeDifferenceChronometer() {
        TimeDifference formatAutoAboveHour = TimeDifference.forPausedTimer(
                Duration.ofHours(2).plusMinutes(30),
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(formatAutoAboveHour.toValueString(mContext)).isEqualTo(
                new ValueString("2:30:00"));

        TimeDifference formatAutoBelowHour = TimeDifference.forPausedTimer(
                Duration.ofMinutes(8),
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(formatAutoBelowHour.toValueString(mContext)).isEqualTo(
                new ValueString("8:00"));

        TimeDifference formatChrono = TimeDifference.forPausedTimer(
                Duration.ofHours(2).plusMinutes(30),
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(formatChrono.toValueString(mContext)).isEqualTo(new ValueString("2:30:00"));

        TimeDifference pausedNegative = TimeDifference.forPausedTimer(
                Duration.ZERO.minusHours(2).minusMinutes(30).minusSeconds(10),
                TimeDifference.FORMAT_CHRONOMETER);
        expect.that(pausedNegative.toValueString(mContext)).isEqualTo(
                new ValueString("−2:30:10"));
    }

    @Test
    public void valueToString_fixedDateAutomatic() {
        FixedDate today = new FixedDate(TODAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(today.toValueString(mContext)).isEqualTo(new ValueString("5/30"));

        FixedDate tomorrow = new FixedDate(TOMORROW, FixedDate.FORMAT_AUTOMATIC);
        expect.that(tomorrow.toValueString(mContext)).isEqualTo(new ValueString("5/31"));

        FixedDate yesterday = new FixedDate(YESTERDAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(yesterday.toValueString(mContext)).isEqualTo(new ValueString("5/29"));

        FixedDate farAway = new FixedDate(FAR_AWAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(farAway.toValueString(mContext)).isEqualTo(new ValueString("12/18/2025"));

        FixedDate longAgo = new FixedDate(LONG_AGO, FixedDate.FORMAT_AUTOMATIC);
        expect.that(longAgo.toValueString(mContext)).isEqualTo(new ValueString("1/1/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(today.toValueString(mContext)).isEqualTo(new ValueString("30/05"));
            expect.that(tomorrow.toValueString(mContext)).isEqualTo(new ValueString("31/05"));
            expect.that(farAway.toValueString(mContext)).isEqualTo(new ValueString("18/12/2025"));
        });
    }

    @Test
    public void valueToString_fixedDateFormats() {
        FixedDate soonAuto = new FixedDate(TOMORROW, FixedDate.FORMAT_AUTOMATIC);
        expect.that(soonAuto.toValueString(mContext)).isEqualTo(new ValueString("5/31"));

        FixedDate soonLong = new FixedDate(TOMORROW, FixedDate.FORMAT_LONG_DATE);
        expect.that(soonLong.toValueString(mContext)).isEqualTo(
                new ValueString("May 31, 2025"));

        FixedDate soonShort = new FixedDate(TOMORROW, FixedDate.FORMAT_SHORT_DATE);
        expect.that(soonShort.toValueString(mContext)).isEqualTo(
                new ValueString("5/31/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(soonAuto.toValueString(mContext)).isEqualTo(new ValueString("31/05"));
            expect.that(soonLong.toValueString(mContext)).isEqualTo(
                    new ValueString("31 mai 2025"));
            expect.that(soonShort.toValueString(mContext)).isEqualTo(
                    new ValueString("31/05/2025"));
        });

        FixedDate farAwayAuto = new FixedDate(FAR_AWAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(farAwayAuto.toValueString(mContext)).isEqualTo(new ValueString("12/18/2025"));

        FixedDate farAwayLong = new FixedDate(FAR_AWAY, FixedDate.FORMAT_LONG_DATE);
        expect.that(farAwayLong.toValueString(mContext)).isEqualTo(
                new ValueString("Dec 18, 2025"));

        FixedDate farAwayShort = new FixedDate(FAR_AWAY, FixedDate.FORMAT_SHORT_DATE);
        expect.that(farAwayShort.toValueString(mContext)).isEqualTo(
                new ValueString("12/18/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(farAwayAuto.toValueString(mContext)).isEqualTo(
                    new ValueString("18/12/2025"));
            expect.that(farAwayLong.toValueString(mContext)).isEqualTo(
                    new ValueString("18 déc. 2025"));
            expect.that(farAwayShort.toValueString(mContext)).isEqualTo(
                    new ValueString("18/12/2025"));
        });
    }

    @Test
    public void valueToString_fixedDate_notAffectedByTimeZone() {
        FixedDate today = new FixedDate(TODAY, FixedDate.FORMAT_AUTOMATIC);

        withTimeZone(TimeZone.getTimeZone("Etc/GMT+12"),
                () -> expect.that(today.toValueString(mContext)).isEqualTo(
                        new ValueString("5/30")));

        withTimeZone(TimeZone.getTimeZone("Etc/GMT-14"),
                () -> expect.that(today.toValueString(mContext)).isEqualTo(
                        new ValueString("5/30")));
    }

    @Test
    public void valueToString_fixedTime() {
        FixedTime time = new FixedTime(LocalTime.of(14, 30));
        expect.that(time.toValueString(mContext)).isEqualTo(
                new ValueString("2:30" + NNBSP + "PM"));

        FixedTime secondsIgnored = new FixedTime(LocalTime.of(14, 30, 59));
        expect.that(secondsIgnored.toValueString(mContext)).isEqualTo(
                new ValueString("2:30" + NNBSP + "PM"));

        FixedTime subsecondIgnored = new FixedTime(LocalTime.of(14, 30, 59, 999_999_999));
        expect.that(subsecondIgnored.toValueString(mContext)).isEqualTo(
                new ValueString("2:30" + NNBSP + "PM"));

        FixedTime closeToMidnight = new FixedTime(LocalTime.of(23, 59));
        expect.that(closeToMidnight.toValueString(mContext)).isEqualTo(
                new ValueString("11:59" + NNBSP + "PM"));

        FixedTime afterMidnight = new FixedTime(LocalTime.of(0, 1));
        expect.that(afterMidnight.toValueString(mContext)).isEqualTo(
                new ValueString("12:01" + NNBSP + "AM"));
    }

    @Test
    public void valueToString_fixedTime_respects24HourFormat() {
        FixedTime time = new FixedTime(LocalTime.of(14, 30));

        withTimeFormat("12", () -> {
            // User's choice wins over locale's default 12/24 setting.
            withLocale(Locale.US, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("2:30" + NNBSP + "PM"));
            });
            withLocale(Locale.FRANCE, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("2:30" + NNBSP + "PM"));
            });
        });

        withTimeFormat("24", () -> {
            // User's choice wins over locale's default 12/24 setting.
            withLocale(Locale.US, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(new ValueString("14:30"));
            });
            withLocale(Locale.FRANCE, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(new ValueString("14:30"));
            });
        });
    }

    @Test
    public void valueToString_fixedTime_notAffectedByTimeZone() {
        FixedTime time = new FixedTime(LocalTime.of(12, 30));

        withTimeZone(TimeZone.getTimeZone("Etc/GMT+12"),
                () -> expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("12:30" + NNBSP + "PM")));

        withTimeZone(TimeZone.getTimeZone("Etc/GMT-14"),
                () -> expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("12:30" + NNBSP + "PM")));
    }

    @Test
    public void valueToString_fixedInt() {
        FixedInt withUnit = new FixedInt(42, "km");
        assertThat(withUnit.toValueString(mContext)).isEqualTo(new ValueString("42", "km"));

        FixedInt noUnit = new FixedInt(42);
        assertThat(noUnit.toValueString(mContext)).isEqualTo(new ValueString("42", null));
    }

    @Test
    public void valueToString_fixedFloat() {
        FixedFloat defaultDigits = new FixedFloat(1612.3456789f);
        assertThat(defaultDigits.toValueString(mContext)).isEqualTo(
                new ValueString("1,612.346", null));

        FixedFloat minDigits = new FixedFloat(42, "km", 2, 4);
        assertThat(minDigits.toValueString(mContext)).isEqualTo(new ValueString("42.00", "km"));

        FixedFloat maxDigits = new FixedFloat(42.1111111f, "km", 2, 4);
        assertThat(maxDigits.toValueString(mContext)).isEqualTo(new ValueString("42.1111", "km"));
    }

    @Test
    public void valueToString_fixedString() {
        FixedString str = new FixedString("Boring");
        assertThat(str.toValueString(mContext)).isEqualTo(new ValueString("Boring", null));
    }

    private void withLocale(Locale locale, Runnable r) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            r.run();
        } finally {
            Locale.setDefault(previous);
        }
    }

    private void withTimeZone(TimeZone tz, Runnable r) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(tz);
            r.run();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private void withTimeFormat(String fmt, Runnable r) {
        ContentResolver cr = mContext.getContentResolver();
        String previous = Settings.System.getString(cr, Settings.System.TIME_12_24);
        try {
            Settings.System.putString(cr, Settings.System.TIME_12_24, fmt);
            r.run();
        } finally {
            Settings.System.putString(cr, Settings.System.TIME_12_24, previous);
        }
    }
}
