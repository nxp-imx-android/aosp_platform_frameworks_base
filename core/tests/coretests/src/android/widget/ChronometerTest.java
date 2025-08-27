/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;

import android.app.Activity;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import androidx.test.filters.LargeTest;

import com.android.frameworks.coretests.R;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link Chronometer} counting up and down.
 */
@SuppressWarnings("deprecation")
@LargeTest
public class ChronometerTest extends ActivityInstrumentationTestCase2<ChronometerActivity> {

    private Activity mActivity;
    private Chronometer mChronometer;

    public ChronometerTest() {
        super(ChronometerActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mActivity = getActivity();
        mChronometer = mActivity.findViewById(R.id.chronometer);
    }

    @UiThreadTest
    public void testSystemClockChronometer() {
        var clocks = new Object() {
            public Instant systemNow = Instant.ofEpochMilli(1748615185000L);
            public long elapsedRealtime = 1000L;
        };
        Chronometer chronometer = new Chronometer(mActivity, () -> clocks.elapsedRealtime,
                () -> clocks.systemNow, null, 0, 0);
        mActivity.setContentView(chronometer);

        // Start state: timer for 2 minutes.
        Instant base = clocks.systemNow.plus(2, MINUTES);
        chronometer.setBase(base);
        chronometer.setCountDown(true);
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("02:00");

        // Clocks advance normally for 20 seconds.
        clocks.systemNow = clocks.systemNow.plus(20, SECONDS);
        clocks.elapsedRealtime = clocks.elapsedRealtime + Duration.ofSeconds(20).toMillis();
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("01:40");

        // After 1 realtime seconds, clock is adjusted and jumps forward by 4 additional seconds!
        clocks.systemNow = clocks.systemNow.plus(5, SECONDS);
        clocks.elapsedRealtime = clocks.elapsedRealtime + Duration.ofSeconds(1).toMillis();
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("01:35");
    }

    @UiThreadTest
    public void testChronometerStartingFromPausedDuration() {
        var clocks = new Object() {
            public Instant systemNow = Instant.ofEpochMilli(1748615185000L);
            public long elapsedRealtime = 10_000L;
        };
        Chronometer chronometer = new Chronometer(mActivity, () -> clocks.elapsedRealtime,
                () -> clocks.systemNow, null, 0, 0);
        mActivity.setContentView(chronometer);

        // Starts paused at 5 seconds.
        chronometer.setCountDown(true);
        chronometer.setPausedDuration(Duration.ofSeconds(5));
        assertThat(chronometer.getText().toString()).isEqualTo("00:05");

        // "Continue countdown" for 3 seconds.
        clocks.elapsedRealtime = clocks.elapsedRealtime + Duration.ofSeconds(3).toMillis();
        chronometer.updateText();
        assertThat(chronometer.getText().toString()).isEqualTo("00:02");
    }

    public void testChronometerTicksSequentially() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(6);
        ArrayList<String> ticks = new ArrayList<>();
        runOnUiThread(() -> {
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.setOnChronometerTickListener((chronometer) -> {
                ticks.add(chronometer.getText().toString());
                latch.countDown();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                }
            });
            mChronometer.start();
        });
        assertTrue(latch.await(5500, TimeUnit.MILLISECONDS));
        assertEquals("00:00", ticks.get(0));
        assertEquals("00:01", ticks.get(1));
        assertEquals("00:02", ticks.get(2));
        assertEquals("00:03", ticks.get(3));
        assertEquals("00:04", ticks.get(4));
        assertEquals("00:05", ticks.get(5));
    }

    public void testChronometerCountDown() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(12);
        ArrayList<String> ticks = new ArrayList<>();
        runOnUiThread(() -> {
            mChronometer.setBase(SystemClock.elapsedRealtime() + 3_000);
            mChronometer.setCountDown(true);
            mChronometer.setOnChronometerTickListener((chronometer) -> {
                ticks.add(chronometer.getText().toString());
                latch.countDown();
            });

            // start in the next frame so that it is more than 1 ms below 3 seconds
            mChronometer.post(() -> mChronometer.start());
        });
        assertTrue(latch.await(12500, TimeUnit.MILLISECONDS));
        assertEquals("00:02", ticks.get(0));
        assertEquals("00:01", ticks.get(1));
        assertEquals("00:00", ticks.get(2));
        assertEquals("−00:01", ticks.get(3));
        assertEquals("−00:02", ticks.get(4));
        assertEquals("−00:03", ticks.get(5));
        assertEquals("−00:04", ticks.get(6));
        assertEquals("−00:05", ticks.get(7));
        assertEquals("−00:06", ticks.get(8));
        assertEquals("−00:07", ticks.get(9));
        assertEquals("−00:08", ticks.get(10));
        assertEquals("−00:09", ticks.get(11));
    }

    private void runOnUiThread(Runnable runnable) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> {
            runnable.run();
            latch.countDown();
        });
        latch.await();
    }
}
