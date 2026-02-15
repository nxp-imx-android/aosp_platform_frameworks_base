/**
 * Copyright (c) 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext;

import android.os.Bundle;
import android.os.ParcelUuid;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.Token;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.hint.ContextHintWithSignatureWrapper;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.insight.interaction.InsightEvent;

/**
 * {@link IPersonalContextManager} is the internal interface for accessing the
 * PersonalContextManagerService. Public usage should go through PersonalContextManager.
 * @hide
 */
interface IPersonalContextManager {
    @EnforcePermission("PERSONAL_CONTEXT_PUBLISH_HINTS")
    oneway void publishTriggeringHint(
            in List<ContextHintWrapper> hints,
            in List<RenderToken> renderTokens,
            in List<ContextHintWrapper> attributionHints,
            int userId);

    oneway void publishInsight(in List<ContextInsightWrapper> insights, in ParcelUuid componentId,
            int userId);

    ContextHintWithSignatureWrapper signHint(
            in ContextHintWrapper hint,
            in List<ContextHintWrapper> attributionHints);

    oneway void registerInsightSurfaceClient(
            in InsightSurfaceClientInfo clientInfo,
            int userId);

    oneway void unregisterInsightSurfaceClient(in ParcelUuid id, int userId);

    Token mintToken();

    oneway void publishInsightSurfaceHints(
        in List<ContextHintWrapper> hints, in InsightSurfaceClientInfo clientInfo, int userId);

    oneway void showAttribution(in ContextInsightWrapper insight);

    oneway void reportEvent(in InsightEvent event, int userId);

    boolean isPersonalContextModeEnabled(in String packageName, int userId);

    // Avoiding oneway so that get and set have a consistent ordering.
    @EnforcePermission("CHANGE_PERSONAL_CONTEXT_MODE")
    void setPersonalContextModeEnabled(in String packageName, int userId, boolean enabled);

    oneway void updateEmbeddedClientInfo(
        in InsightSurfaceClientInfo oldClientInfo,
        in InsightSurfaceClientInfo newClientInfo,
        int userId);
}