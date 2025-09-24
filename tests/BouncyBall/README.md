# BouncyBall test app

This is a simple graphics app which draws a ball bouncing around the screen.

This app's primary use is in automated testing, to make sure no frames are
dropped while running.

The graphics tested here are quite simple.  This app is not just intended to
assure that very basic graphics work, but that the system does not drop frames
as CPUs/GPU get turned off and downclocked while in the steady state.

See https://source.android.com/docs/core/tests/debug/eval_perf#touchlatency
for more details.

## Manual usage basics

This app can be used outside of automation to check and debug this behavior.

This app fundamentally assumes that it is the only foreground app running on
the device while testing.  If that assumption is broken, this app will log
to logcat, at the "E"rror level, noting an "ASSUMPTION FAILURE".

On a properly set up device, it is expected that this app never drops a frame.

### Helpful "flags" to flip

The source code (in
`app/src/main/java/com/android/test/bouncyball/BouncyBallActivity.java`) has a
few constants which can be changed to help with debugging and testing.  The
app needs to be recompiled after any of these have been changed.

* `LOG_DROPPED_FRAMES`  If changed to `true`, the app will log, at the "E"rror
level, every frame drop detected in-app.  Note that trace-based frame drop
detection is the golden standard; this is best effort.
* `LOG_EVERY_FRAME`  If changed to `true`, the app will log, at the "D"ebug
level, every (non-dropped) frame.
* `FORCE_DROPPED_FRAMES`  If changed to `true`, the app will drop every 64th
frame.  This can be helpful for debugging automation pipelines and confirming
app behavior.
* `ASSUMPTION_FAILURE_FORCES_EXIT`  If changed to `false`, if the app fails
the assumption that it is always in foreground focus, then the app will
keep running (even though we know the results will be wrong).


## Local build and install/run/uninstall

From the top of tree, in a shell that has been set up for building, compile
the app with:

```
$ mmma -j frameworks/base/tests/BouncyBall
```

Install it to an adb-connected device with:

```
$ adb install ${ANDROID_PRODUCT_OUT}/system/app/BouncyBallTest/BouncyBallTest.apk
```

This can be launched with:

```
$ adb shell am start -W com.android.test.bouncyball/com.android.test.bouncyball.BouncyBallActivity
```

It can be stopped with:

```
$ adb shell am force-stop com.android.test.bouncyball
```

And it can be uninstalled with:

```
$ adb uninstall com.android.test.bouncyball
```

## Debugging frame drops

See https://developer.android.com/topic/performance/tracing/on-device for
detailed information on how to evaluate and debug performance issues with this,
including any frame drops.

This is the recommended approach not just for this example app, but real world
apps and device performance in general.


## GPU composition settings

In automation, we test this both with the default GPU composition settings,
and with GPU composition forced on.  GPU composition is a critical part of a
device's graphics path which we want to test, but BouncyBall's simplicity has
some devices avoid GPU composition by default.

For local runs, force GPU composition on with:

```
$ adb shell sfdo force-client-composition enabled
```

Put GPU composition back in its default state with:

```
$ adb shell sfdo force-client-composition disabled
```

Note this latter command does not force GPU composition off.  There is no such
standard command for that.  This just returns back to the default composition
setup.


## How automation analyzes the test

Automation uses a stripped down version of tracing.  It is only interested in
whether or not frames were dropped (and how close we came to dropping them).

In the interest of efficiency, it minimizes the information collected.

Thus, it is not helpful for debugging why a frame was dropped.  See the section
above for the best approach for that.

However, in the interest of transparency, we give some information about the
commands the automated testing setup uses.

First, make sure the app is installed on the device (see sections above), but
also make sure it's been stopped:

```
$ adb shell am force-stop com.android.test.bouncyball
```

From this directory do:

```
# Put perfetto config on the device
$ adb push automation_config.pbtx /data/misc/perfetto-configs/

# Launch perfetto in the background
$ adb shell /system/bin/perfetto --background --config /data/misc/perfetto-configs/automation_config.pbtx --txt --out /data/misc/perfetto-traces/bouncy_trace
[This command will output the process ID for Perfetto (PERFETTO_PID)]

# Now immediately launch the app, i.e.:
$ adb shell am start -W com.android.test.bouncyball/com.android.test.bouncyball.BouncyBallActivity

# Wait until Perfetto is done running.  Substitute "PERFETTO_PID" with the
# value from above.
$ adb shell 'while ps -p PERFETTO_PID 2> /dev/null > /dev/null; do sleep 1; done'
[This will take a bit over two minutes]

# Grab the trace results to the local machine
$ adb pull /data/misc/perfetto-traces/bouncy_trace

# Analyze the trace results
$ ../../../../prebuilts/tools/linux-x86_64/perfetto/trace_processor_shell  --summary --summary-metrics-v2 all --summary-spec trace_metrics_v2_spec.pbtx bouncy_trace
```

Look for `missed_app_frames_bouncyball` and confirm that its value is 0 (if
it's non-zero, this is a failed run).  You'll also see various `frame_dur`
values, in nanoseconds, of the frame duration for the app at various
percentiles (where P50 is the median duration time).  (Note that automation
actually passes `--summary-format binary` to `trace_processor_shell` and parses
the output programatically).

Clean up afterwards with:

```
# Clean up the device
$ adb shell rm /data/misc/perfetto-configs/automation_config.pbtx /data/misc/perfetto-traces/bouncy_trace

# Clean up the local directory
$ rm bouncy_trace
```

