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

package com.android.server.contentrestriction;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.app.contentrestriction.Content;
import android.app.contentrestriction.IContentRestrictionCallback;
import android.app.contentrestriction.IContentRestrictionManager;
import android.content.Context;

import com.android.server.SystemService;

/** Service for handling content restrictions. */
public class ContentRestrictionService extends IContentRestrictionManager.Stub {
    private final Context mContext;

    public ContentRestrictionService(Context context) {
        mContext = context.createAttributionContext("ContentRestrictionService");
    }

    @Override
    @RequiresNoPermission
    public boolean isContentRestrictionEnabled(@UserIdInt int userId) {
        // TODO(b/441599653): Implement content restriction enablement check.
        return true;
    }

    @Override
    @RequiresNoPermission
    public void isContentAllowed(@UserIdInt int userId, Content content,
            IContentRestrictionCallback callback) {
        // TODO(b/441599653): Implement content restriction check.
    }

    public static class Lifecycle extends SystemService {
        private final ContentRestrictionService mContentRestrictionService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mContentRestrictionService = new ContentRestrictionService(context);
        }

        @Override
        public void onStart() {
            publishBinderService(Context.CONTENT_RESTRICTION_SERVICE, mContentRestrictionService);
        }
    }
}
