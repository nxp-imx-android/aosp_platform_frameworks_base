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
import static android.app.Notification.Metric.MEANING_CELESTIAL;
import static android.app.Notification.Metric.MEANING_CELESTIAL_TIDE;
import static android.app.Notification.Metric.MEANING_CHRONOMETER;
import static android.app.Notification.Metric.MEANING_CHRONOMETER_STOPWATCH;
import static android.app.Notification.Metric.MEANING_CHRONOMETER_TIMER;
import static android.app.Notification.Metric.MEANING_EVENT_TIME;
import static android.app.Notification.Metric.MEANING_HEALTH;
import static android.app.Notification.Metric.MEANING_HEALTH_ACTIVE_TIME;
import static android.app.Notification.Metric.MEANING_HEALTH_CALORIES;
import static android.app.Notification.Metric.MEANING_HEALTH_READINESS;
import static android.app.Notification.Metric.MEANING_TRAVEL;
import static android.app.Notification.Metric.MEANING_TRAVEL_TERMINAL;
import static android.app.Notification.Metric.MEANING_UNKNOWN;
import static android.app.Notification.Metric.MEANING_WEATHER_TEMPERATURE_OUTDOOR;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification.Metric;
import android.app.Notification.Metric.FixedFloat;
import android.app.Notification.Metric.FixedInstant;
import android.app.Notification.Metric.FixedInt;
import android.app.Notification.Metric.FixedString;
import android.app.Notification.Metric.TimeDifference;
import android.app.Notification.MetricStyle;
import android.os.Bundle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
@Presubmit
@EnableFlags(Flags.FLAG_API_METRIC_STYLE)
public class NotificationMetricStyleTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void addExtras_writesExtras() {
        MetricStyle style = new MetricStyle()
                .addMetric(new Metric(new FixedInt(4, "birds"), "4", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(5, "rings"), "5", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(6, "geese"), "6", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(7, "swans"), "7", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(8, "maids"), "8", MEANING_UNKNOWN));

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
                        "Time:", MEANING_CHRONOMETER_TIMER))
                .addMetric(new Metric(
                        TimeDifference.forPausedStopwatch(Duration.ofHours(4),
                                TimeDifference.FORMAT_CHRONOMETER_MINUTES),
                        "Stopwatch:", MEANING_CHRONOMETER_STOPWATCH))
                .addMetric(new Metric(
                        new FixedInstant(Instant.ofEpochMilli(55),
                                FixedInstant.FORMAT_SHORT_DATE,
                                TimeZone.getTimeZone("America/Montevideo")),
                        "Event time:", MEANING_EVENT_TIME))
                .addMetric(new Metric(
                        new FixedInt(12, "drummers"), "Label", MEANING_UNKNOWN))
                .addMetric(new Metric(
                        new FixedInt(42), "Answer", MEANING_CELESTIAL))
                .addMetric(new Metric(
                        new FixedFloat(0.75f), "Readiness", MEANING_HEALTH_READINESS))
                .addMetric(new Metric(
                        new FixedFloat(273f, "°K"),
                        "Temp", MEANING_WEATHER_TEMPERATURE_OUTDOOR))
                .addMetric(new Metric(
                        new FixedFloat(12.345f, null, 0, 3),
                        "Active time", MEANING_HEALTH_ACTIVE_TIME))
                .addMetric(new Metric(
                        new FixedString("This is the last"), "Last", MEANING_UNKNOWN));

        original.addExtras(bundle);
        MetricStyle recovered = new MetricStyle();
        recovered.restoreFromExtras(bundle);

        assertThat(recovered).isEqualTo(original);
    }

    @Test
    public void areNotificationsVisiblyDifferent_sameMetrics_false() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "Cal", MEANING_HEALTH_CALORIES))
                .addMetric(new Metric(new FixedInt(2), "Cal", MEANING_HEALTH_CALORIES));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "Cal", MEANING_HEALTH_CALORIES))
                .addMetric(new Metric(new FixedInt(2), "Cal", MEANING_HEALTH_CALORIES));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isFalse();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isFalse();
    }

    @Test
    public void areNotificationsVisiblyDifferent_differentMetrics_true() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "thingies"), "a", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(2, "widgets"), "b", MEANING_UNKNOWN));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "c", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "d", MEANING_UNKNOWN));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isTrue();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isTrue();
    }

    @Test
    public void areNotificationsVisiblyDifferent_differentMetricCounts_true() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "a", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "b", MEANING_UNKNOWN));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "a", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "b", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(3, "whatsits"), "c", MEANING_UNKNOWN));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isTrue();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isTrue();
    }

    @Test
    public void areNotificationsVisiblyDifferent_firstThreeEqual_false() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "a", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(2), "b", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(3), "c", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedString("Ignored thing"), "d", MEANING_UNKNOWN));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "a", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(2), "b", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedInt(3), "c", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedString("Also ignored"), "d", MEANING_UNKNOWN))
                .addMetric(new Metric(new FixedString("And this too"), "e", MEANING_UNKNOWN));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isFalse();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isFalse();
    }

    @Test
    public void getMeaningCategory_concreteMeaning_returnsCategory() {
        assertThat(Metric.getMeaningCategory(MEANING_CHRONOMETER_TIMER)).isEqualTo(
                MEANING_CHRONOMETER);
        assertThat(Metric.getMeaningCategory(MEANING_CELESTIAL_TIDE)).isEqualTo(MEANING_CELESTIAL);
        assertThat(Metric.getMeaningCategory(MEANING_HEALTH_ACTIVE_TIME)).isEqualTo(MEANING_HEALTH);
        assertThat(Metric.getMeaningCategory(MEANING_TRAVEL_TERMINAL)).isEqualTo(MEANING_TRAVEL);
    }

    @Test
    public void getMeaningCategory_categoryMeaning_returnsCategory() {
        assertThat(Metric.getMeaningCategory(MEANING_UNKNOWN)).isEqualTo(MEANING_UNKNOWN);
        assertThat(Metric.getMeaningCategory(MEANING_CHRONOMETER)).isEqualTo(MEANING_CHRONOMETER);
        assertThat(Metric.getMeaningCategory(MEANING_CELESTIAL)).isEqualTo(MEANING_CELESTIAL);
        assertThat(Metric.getMeaningCategory(MEANING_HEALTH)).isEqualTo(MEANING_HEALTH);
        assertThat(Metric.getMeaningCategory(MEANING_TRAVEL)).isEqualTo(MEANING_TRAVEL);
    }

    @Test
    public void getMeaningCategory_invalidCategory_returnsUnknown() {
        assertThat(Metric.getMeaningCategory(0xaaaa0001)).isEqualTo(MEANING_UNKNOWN);
    }
}
