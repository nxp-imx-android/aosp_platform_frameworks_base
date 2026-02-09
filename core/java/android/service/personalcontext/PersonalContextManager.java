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

import static java.util.Objects.requireNonNull;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.UserHandleAware;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.hint.AutofillInlineRequestHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.service.personalcontext.insight.interaction.ReturnHintReport;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client facing access to the PersonalContext service.
 */
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@SystemService(Context.PERSONAL_CONTEXT_SERVICE)
public final class PersonalContextManager {
    /** The name of the Personal Context service. */
    public static final String PERSONAL_CONTEXT_SERVICE = "personal_context";

    private static final String TAG = "PersonalContextManager";

    /**
     * Intent that is broadcast when the state of {@link #isEnabled()} changes.
     * This broadcast is only sent to registered receivers.
     */
    @SdkConstant(SdkConstant.SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PERSONAL_CONTEXT_ENABLED_CHANGED =
            "android.service.personalcontext.action.PERSONAL_CONTEXT_ENABLED_CHANGED";

    private final Context mContext;
    private final IPersonalContextManager mService;

    /** @hide */
    public PersonalContextManager(
            @NonNull Context context, @NonNull IPersonalContextManager service) {
        mContext = context;
        mService = service;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Set up service: " + mService);
        }
    }

    /**
     * Returns whether the Personal Context service is enabled.
     * @hide
     */
    @SystemApi
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public boolean isEnabled() {
        // TODO(b/477958468): Correctly handle enabling/disabling the service and then make the
        // default "disabled".
        return Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_ENABLED,
                1, mContext.getUserId()) == 1;
    }

    /**
     * Set whether the Personal Context service is enabled.
     * @param enable whether to enable or disable the service
     * @hide
     */
    @SystemApi
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void setEnabled(boolean enable) {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_ENABLED,
                enable ? 1 : 0, mContext.getUserId());
    }

    /**
     * Returns true if personal context data collection is enabled for the given application.
     *
     * <p>When disabled, this setting stops all data collection sources for personal context for a
     * particular application, such as from the Content Capture API and notifications content.
     *
     * @param packageName package name of the application to read the setting for
     */
    @RequiresPermission(Manifest.permission.QUERY_ALL_PACKAGES)
    public boolean isPersonalContextModeEnabled(@NonNull String packageName) {
        try {
            return mService.isPersonalContextModeEnabled(packageName, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets the personal context data collection state for the given application.
     *
     * @param packageName package name of the application to change the setting for
     * @param enabled value for whether or not data collection is enabled
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE)
    public void setPersonalContextModeEnabled(@NonNull String packageName, boolean enabled) {
        try {
            mService.setPersonalContextModeEnabled(packageName, mContext.getUserId(), enabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Triggers a Personal Context service flow with raw data in the form of hints.
     *
     * @param hints raw data to be injected into the context flow
     * @param renderTokens optional tokens indicating which renderers should be used to render
     *                     results of this flow to the user; if empty, then this flow can be
     *                     rendered by any Personal Context renderer
     * @hide
     */
    @SystemApi
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void publishTriggeringHint(
            @NonNull List<ContextHint> hints, @Nullable List<RenderToken> renderTokens) {
        publishTriggeringHint(hints, renderTokens, null);
    }

    /**
     * Triggers a Personal Context service flow with raw data in the form of hints.
     *
     * <p>Each hint in {@code hints} will be attributed to each hint in {@code attributionHints}.
     *
     * @param hints raw data to be injected into the context flow
     * @param renderTokens optional tokens indicating which renderers should be used to render
     *     results of this flow to the user; if {@code null} or empty, then this flow can be
     *     rendered by any Personal Context renderer
     * @param attributionHints optional hints to use as attribution for {@code hints}
     * @hide
     */
    @SystemApi
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void publishTriggeringHint(
            @NonNull List<ContextHint> hints,
            @Nullable List<RenderToken> renderTokens,
            @Nullable List<ContextHint> attributionHints) {
        try {
            mService.publishTriggeringHint(
                    ContextHintWrapper.wrapList(hints),
                    renderTokens,
                    ContextHintWrapper.wrapList(attributionHints),
                    mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Triggers a Personal Context service flow with raw data in the form of hints.
     *
     * @throws IllegalArgumentException if the hints collection is null or empty
     *
     * @param returnHintReport Routing information from an insight that these hints are related to
     * @param hints raw data to be injected into the context flow
     */
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void publishTriggeringHint(
            @NonNull ReturnHintReport returnHintReport, @NonNull List<ContextHint> hints) {
        requireNonNull(returnHintReport, "returnHintReport must not be null");

        if (hints == null || hints.isEmpty()) {
            throw new IllegalArgumentException("hints collection must not be null or empty");
        }

        final List<ContextHint> allHints = new ArrayList<>();
        allHints.add(returnHintReport.getInsightReferenceHint());
        allHints.addAll(hints);

        publishTriggeringHint(allHints, returnHintReport.getRenderTokens(), null);
    }

    /**
     * Triggers a Personal Context service flow with data that has been understood in the form of
     * insights.
     *
     * <p>This method may deliver multiple new insights at once. All insights must be derived from
     * hints previously supplied. All hints used to generate an insight must have the same {@link
     * RenderToken}, or a {@code null} {@link RenderToken}; non-confirming insights will be ignored.
     *
     * @param insights new insights to be injected into the context flow
     * @hide
     */
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void publishInsight(@NonNull List<ContextInsight> insights) {
        try {
            mService.publishInsight(ContextInsightWrapper.wrapList(insights), mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Takes a {@link ContextHint}, attaches this application's package name, and signs it.
     *
     * <p>This can be used to sign a hint that your application has created, in order to attach it
     * to a new {@link ContextInsight}. Each hint in {@code hints} will be attributed to each
     * hint in {@code attributionHints}.
     *
     * @param hint raw data to be signed
     * @param attributionHints optional hints to use as attribution for {@code hint}
     * @return signed version of hint annotated with caller's package
     *
     * @hide
     */
    @SystemApi
    @NonNull
    public ContextHintWithSignature signHint(
            @NonNull ContextHint hint, @Nullable List<ContextHint> attributionHints) {
        try {
            return mService.signHint(
                    new ContextHintWrapper(hint), ContextHintWrapper.wrapList(attributionHints))
                    .getContextHintWithSignature();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers a client to receive embedded surfaces.
     *
     * @param clientInfo the {@link InsightSurfaceClientInfo} object for the client
     * @param hints a list of {@link ContextHint}s from the client used to trigger a new refiner
     *              workflow
     *
     * @hide
     */
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    @Nullable
    public void registerInsightSurfaceClient(
            @NonNull InsightSurfaceClientInfo clientInfo, @NonNull List<ContextHint> hints) {
        try {
            mService.registerInsightSurfaceClient(
                    ContextHintWrapper.wrapList(hints), clientInfo, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters an embedded insight surface client.
     *
     * @param clientInfo of the client to be unregistered
     *
     * @hide
     */
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void unregisterInsightSurfaceClient(@NonNull InsightSurfaceClientInfo clientInfo) {
        try {
            mService.unregisterInsightSurfaceClient(
                    new ParcelUuid(clientInfo.getId()), mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Mints a {@link Token} that can be attached to {@link ContextHint}, {@link ContextInsight}, or
     * used in filters to filter for hints and insights.
     *
     * @hide
     */
    @SystemApi
    @NonNull
    public Token mintToken() {
        try {
            return mService.mintToken();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Publish hints from an embedded insight surface.
     *
     * @param clientInfo of the client to be unregistered
     *
     * @hide
     */
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void publishInsightSurfaceHints(
            @NonNull Set<ContextHint> hints, @NonNull InsightSurfaceClientInfo clientInfo) {
        try {
            mService.publishInsightSurfaceHints(
                    ContextHintWrapper.wrapList(hints), clientInfo, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Instructs the understander to show attribution for the provided insight.
     *
     * @param insight Insight with attribution information that should be shown to the user.
     */
    @RequiresNoPermission
    public void showAttribution(@NonNull ContextInsight insight) {
        try {
            mService.showAttribution(new ContextInsightWrapper(insight));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Update the client info of an embedded client.
     *
     * @param oldClientInfo the old {@link InsightSurfaceClientInfo} that needs to be updated
     * @param newClientInfo the new {@link InsightSurfaceClientInfo} that has the updated info
     *
     * @hide
     */
    @UserHandleAware(
            requiresPermissionIfNotCaller = android.Manifest.permission.INTERACT_ACROSS_USERS)
    public void updateEmbeddedClientInfo(
            @NonNull InsightSurfaceClientInfo oldClientInfo,
            @NonNull InsightSurfaceClientInfo newClientInfo) {
        try {
            mService.updateEmbeddedClientInfo(oldClientInfo, newClientInfo, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Reports an InsightEvent back to the Understander that generated the Insight.
     *
     * @hide
     */
    public void reportEvent(@NonNull InsightEvent event) {
        try {
            mService.reportEvent(event, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the {@link AssistStructure.ViewNode} for the node focused by autofill in the given
     * {@link AutofillInlineRequestHint}.
     *
     * @hide
     */
    @SystemApi
    @NonNull
    public AssistStructure.ViewNode getFocusedViewNode(@NonNull AutofillInlineRequestHint hint) {
        return hint.getAugmentedAutofillProxy().getFocusedViewNode(hint.getFocusedId());
    }

    /**
     * Returns the coordinates of the input field view that autofill suggestions are being generated
     * for in the given {@link AutofillInlineRequestHint}.
     *
     * @hide
     */
    @SystemApi
    @NonNull
    public Rect getViewCoordinates(@NonNull AutofillInlineRequestHint hint) {
        return hint.getAugmentedAutofillProxy().getViewCoordinates(hint.getFocusedId());
    }

    /**
     * Reports that the user wants to provide feedback. A UI may be shown to the user to complete
     * filling out feedback (using initial user preferences from {@code feedbackPreview}, and the
     * resulting completed feedback is sent to the understander that generated the {@code insight}.
     *
     * @param insight the insight that feedback is being provided for
     * @param feedbackPreview initial user feedback values provided by the renderer
     */
    @RequiresNoPermission
    public void reportUserFeedback(
            @NonNull ContextInsight insight, @Nullable Bundle feedbackPreview) {
        try {
            mService.reportFeedback(
                    new ContextInsightWrapper(insight), feedbackPreview, mContext.getUserId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
