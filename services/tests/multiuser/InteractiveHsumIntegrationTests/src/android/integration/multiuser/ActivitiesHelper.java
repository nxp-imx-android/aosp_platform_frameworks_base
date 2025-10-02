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
package android.integration.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

/** Helper for activities-related operations (like launching them). */
public final class ActivitiesHelper {

    private static final String TAG = ActivitiesHelper.class.getSimpleName();

    private static final long ACTIVITY_TIMEOUT_MS = 5_000;

    /** Launches the given activity in a new task. */
    public static Activity launchActivity(Class<? extends Activity> activityClass) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent intent = new Intent(context, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Log.d(TAG, "Launching activity with intent " + intent);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                activityClass.getName(), /* result= */ null, /* block= */ false);
        context.startActivity(intent);

        Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_TIMEOUT_MS);
        Log.d(TAG, "Activity: " + activity);
        try {
            assertWithMessage("activity launched in %sms", ACTIVITY_TIMEOUT_MS).that(activity)
                    .isNotNull();
            assertWithMessage("monitor hits").that(monitor.getHits()).isEqualTo(1);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        return activity;
    }

    /** Finishes the given activity. */
    public static void finishActivity(@Nullable Activity activity) {
        if (activity == null) {
            Log.w(TAG, "Not finishing null activitiy");
            return;
        }
        Log.d(TAG, "Finishing " + activity);
        activity.finish();
    }


    private ActivitiesHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
