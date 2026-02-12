/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Specialized {@link TextView} used to display {@link android.app.Notification.Metric.MetricValue}.
 * It will choose one the text options supplied to {@link #setTextVariants(List)} according
 * to their fit in the provided space without ellipsizing. If the first variant fits, it will
 * always be preferred.
 *
 * @hide
 */
@RemoteViews.RemoteView
public class NotificationMetricAdaptiveTextView extends NotificationMetricTextView {
    public NotificationMetricAdaptiveTextView(Context context) {
        super(context);
    }

    public NotificationMetricAdaptiveTextView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationMetricAdaptiveTextView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NotificationMetricAdaptiveTextView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
