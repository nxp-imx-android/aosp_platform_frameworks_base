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

package android.service.personalcontext;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;

import java.util.List;

/**
 * Client facing access to the PersonalContext service.
 * @hide
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemApi
@SystemService(Context.PERSONAL_CONTEXT_SERVICE)
public final class PersonalContextManager {
    /**
     * The name of the Personal Context service.
     */
    public static final String PERSONAL_CONTEXT_SERVICE = "personal_context";

    private static final String TAG = "PersonalContextManager";

    private final IPersonalContextManager mService;

    /**
     * @hide
     */
    public PersonalContextManager(@NonNull IPersonalContextManager service)  {
        mService = service;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Set up service: " + mService);
        }
    }

    /**
     * Triggers a Personal Context service flow with raw data in the form of hints.
     *
     * @param hints raw data to be injected into the context flow
     * @param renderToken optional token indicating which renderer should be used to render results
     *                    of this flow to the user; if {@code null} then this flow can be rendered
     *                    by any Personal Context renderer
     * @hide
     */
    @SystemApi
    public void publishTriggeringHint(
            @NonNull List<ContextHint> hints, @Nullable RenderToken renderToken) {
        try {
            mService.publishTriggeringHint(ContextHintWrapper.wrapList(hints), renderToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Triggers a Personal Context service flow with data that has been understood in the form of
     * insights.
     *
     * This method may deliver multiple new insights at once. All insights must be derived from
     * hints previously supplied. All hints used to generate an insight must have the same
     * {@link RenderToken}, or a {@code null} {@link RenderToken}; non-confirming insights will be
     * ignored.
     *
     * @param insights new insights to be injected into the context flow
     * @hide
     */
    @SystemApi
    public void publishInsight(@NonNull List<ContextInsight> insights) {
        try {
            mService.publishInsight(ContextInsightWrapper.wrapList(insights));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
