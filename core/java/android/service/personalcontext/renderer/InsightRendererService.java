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

package android.service.personalcontext.renderer;

import static android.annotation.SystemApi.Client.PRIVILEGED_APPS;

import android.annotation.FlaggedApi;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.service.personalcontext.Flags;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The base insight renderer service, which is a service responsible for rendering
 * {@link ContextInsight}s to the user as suggestions or other actionable items. This is an abstract
 * class intended to be subclassed by concrete renderer services. Subclasses need to implement
 * {@link #onRegistered()} (which should return a {@link RendererFilter} indicating what types of
 * hints and insights the renderer will accept), and {@link #onRender(List, boolean)} (which the
 * personal context engine will call with a list of {@link ContextInsight}s to be rendered).
 *
 * <p>You must declare the service in the AndroidManifest of the app hosting the service with the
 * {@link android.Manifest.permission#BIND_INSIGHT_RENDERER_SERVICE} permission,
 * and include an intent filter with the necessary action indicating that it is an
 * {@link InsightRendererService} ({@link #SERVICE_INTERFACE}).
 *
 * <p>For example:
 * <pre>
 *     &lt;service android:name=".ExampleInsightRendererService"
 *             android:exported="true"
 *             android:permission="android.permission.BIND_INSIGHT_RENDERER_SERVICE"&gt;
 *         &lt;intent-filter&gt;
 *             &lt;action
 *             android:name="android.service.personalcontext.renderer.InsightRendererService"
 *             /&gt;
 *         &lt;/intent-filter&gt;
 *     &lt;/service&gt;
 * </pre>
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class InsightRendererService extends Service {
    private static final String TAG = "InsightRendererService";

    /**
     * The {@link Intent} that must be declared as handled by the service.
     *
     * <p>To be supported, the service must also require the
     * {@link android.Manifest.permission#BIND_INSIGHT_RENDERER_SERVICE} permission.
     */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.service.personalcontext.renderer.InsightRendererService";

    private final BinderService mCurrentBinder;

    /**
     * Construct a new {@link InsightRendererService}.
     */
    public InsightRendererService() {
        mCurrentBinder = new BinderService(this);
    }

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mCurrentBinder;
        }
        Log.w(
                TAG,
                "Tried to bind to wrong intent (should be " + SERVICE_INTERFACE + "): " + intent);
        return null;
    }

    /**
     * Mint and return a new {@link RenderToken}.
     *
     * A {@link RenderToken} uniquely identifies this renderer to the personal context engine.
     * Triggers can include a {@link RenderToken} in hints to indicate that insights containing
     * those hints are meant to be rendered by the renderer that minted that token.
     *
     * @return a token that uniquely identifies this renderer
     */
    @NonNull
    @SuppressWarnings("OnNameExpected")
    public final RenderToken mintRenderToken() {
        return mCurrentBinder.mintRenderToken();
    }

    /**
     * This method is called to inform the renderer subclass that it has been registered. The
     * renderer should return a {@link RendererFilter} that will be used to filter the insights that
     * this renderer's {@link #onRender(List, boolean)} method will be called with.
     *
     * @return a filter that restricts the {@link ContextInsight}s this renderer will receive
     */
    @NonNull
    public abstract RendererFilter onRegistered();

    /**
     * This method will be called when the given list of {@link ContextInsight}s needs to be
     * rendered. isFirst will be true if this is the first renderer service to render these
     * insights.
     *
     * @param insights the list of {@link ContextInsight}s to renderer
     * @param isFirst true if this renderer is the first renderer to receive these insights
     */
    public abstract void onRender(@NonNull List<ContextInsight> insights, boolean isFirst);

    private static final class BinderService extends IInsightRenderer.Stub {
        private UUID mComponentId;
        private final WeakReference<InsightRendererService> mService;

        BinderService(InsightRendererService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void render(List<ContextInsightWrapper> insights, boolean alreadyRendered) {
            final InsightRendererService service = mService.get();
            if (service == null) {
                return;
            }

            final List<ContextInsight> unwrappedInsights = new ArrayList<>();
            insights.forEach(wrappedInsight ->
                    unwrappedInsights.add(wrappedInsight.getContextInsight()));
            service.onRender(unwrappedInsights, alreadyRendered);
        }

        @Override
        public RendererFilter onRegister(String componentId) {
            final InsightRendererService service = mService.get();
            if (service == null) {
                return null;
            }

            mComponentId = UUID.fromString(componentId);
            return service.onRegistered();
        }

        @Override
        public RenderToken mintRenderToken() {
            return new RenderToken.RenderTokenBuilder()
                    .setRendererComponentId(mComponentId).build();
        }
    }
}
