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

package com.android.internal.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.internal.R;

public class MediaRouteChooserContentManager {
    Context mContext;

    private final boolean mShowProgressBarWhenEmpty;

    public MediaRouteChooserContentManager(Context context, boolean showProgressBarWhenEmpty) {
        mContext = context;
        mShowProgressBarWhenEmpty = showProgressBarWhenEmpty;
    }

    /**
     * Starts binding all the views (list view, empty view, etc.) using the
     * given container view.
     */
    public void bindViews(View containerView) {
        View emptyView = containerView.findViewById(android.R.id.empty);
        ListView listView = containerView.findViewById(R.id.media_route_list);
        listView.setEmptyView(emptyView);

        if (!mShowProgressBarWhenEmpty) {
            containerView.findViewById(R.id.media_route_progress_bar).setVisibility(View.GONE);

            // Center the empty view when the progress bar is not shown.
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) emptyView.getLayoutParams();
            params.gravity = Gravity.CENTER;
            emptyView.setLayoutParams(params);
        }
    }
}
