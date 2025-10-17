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

package android.service.personalcontext.embedded;

import static android.annotation.SystemApi.Client.PRIVILEGED_APPS;

import android.annotation.FlaggedApi;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.Looper;
import android.service.personalcontext.Flags;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The base class for insight surface visualizer implementations. Implementations should subclass
 * this class and override the createVisualizationForClient() method to return a {@link View} to be
 * installed in the client surface. Subclasses will be notified that a client has connected or
 * disconnected via the {@link #onClientConnected} and {@link #onClientDisconnected} methods.
 *
 * <p>You must declare the service in the AndroidManifest of the app hosting the service with the
 * {@link android.Manifest.permission#BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE} permission,
 * and include an intent filter with the necessary action indicating that it is an
 * {@link InsightSurfaceVisualizerService} ({@link #SERVICE_INTERFACE}).
 *
 * <p>For example:
 * <pre>
 *     &lt;service android:name=".ExampleInsightSurfaceVisualizerService"
 *             android:exported="true"
 *             android:permission="android.permission.BIND_INSIGHT_SURFACE_VISUALIZER_SERVICE"&gt;
 *         &lt;intent-filter&gt;
 *             &lt;action
 *           android:name="android.service.personalcontext.embedded.InsightSurfaceVisualizerService"
 *             /&gt;
 *         &lt;/intent-filter&gt;
 *     &lt;/service&gt;
 * </pre>
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class InsightSurfaceVisualizerService extends Service {
    private static final String TAG = "InsightSurfaceVisualizr";

    /** The {@link Intent} that must be declared as handled by the service. */
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String SERVICE_INTERFACE =
            "android.service.personalcontext.embedded.InsightSurfaceVisualizerService";

    private final Injector mInjector;
    private BinderService mBinder;

    /**
     * A helper object to inject dependencies into {@link InsightSurfaceVisualizerService}.
     * @hide
     */
    @VisibleForTesting
    public interface Injector {
        /**
         * Create the display context that will be used when instantiating the {@link View}
         * hierarchy to be returned from the visualizer.
         * @return the created display {@link Context}
         */
        Context createDisplayContext();

        /**
         * Get the default display that will be used when instantiating the {@link View} hierarchy
         * to be returned from the visualizer.
         * @return the default {@link Display}
         */
        Display getDisplay();

        /**
         * Create the executor on which binder work will be performed.
         * @return the {@link Executor}
         */
        Executor createExecutor();
    }

    private static final class DefaultInjector implements Injector {
        private final Context mContext;

        DefaultInjector(Context context) {
            mContext = context;
        }

        @Override
        public Context createDisplayContext() {
            return mContext.getApplicationContext().createDisplayContext(getDisplay())
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION, null);
        }

        @Override
        public Display getDisplay() {
            return mContext.getApplicationContext().getDisplay();
        }

        @Override
        public Executor createExecutor() {
            return new HandlerExecutor(new Handler(Looper.getMainLooper()));
        }
    }

    /**
     * Create an {@link InsightSurfaceVisualizerService}.
     */
    public InsightSurfaceVisualizerService() {
        mInjector = new DefaultInjector(this);
    }

    /**
     * Create an {@link InsightSurfaceVisualizerService} for test purposes.
     *
     * @param injector an {@link Injector} to be provided by tests
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceVisualizerService(Injector injector) {
        mInjector = injector;
    }

    @Override
    public void onCreate() {
        mBinder =
                new BinderService(
                        this,
                        mInjector.createDisplayContext(),
                        mInjector.getDisplay(),
                        mInjector.createExecutor());
    }

    @Nullable
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        Log.w(
                TAG,
                "Tried to bind to wrong intent (should be " + SERVICE_INTERFACE + "): " + intent);
        return null;
    }

    /**
     * An insight surface client has connected to the visualizer.
     *
     * @param client the {@link InsightSurfaceClientInfo} of the connecting client
     */
    public abstract void onClientConnected(@NonNull InsightSurfaceClientInfo client);

    /**
     * An insight surface client has disconnected from the visualizer.
     *
     * @param client the {@link InsightSurfaceClientInfo} of the disconnecting client.
     */
    public abstract void onClientDisconnected(@NonNull InsightSurfaceClientInfo client);

    /**
     * Create and return a {@link View} for the given {@link InsightSurfaceClientInfo} based on the
     * list of {@link ContextInsight}s.
     *
     * @param context a {@link Context} suitable for creating {@link View}s in the current display
     * @param insights the list of {@link ContextInsight}s that subclasses can use to create the
     *                 embedded {@link View}
     * @param info the {@link InsightSurfaceClientInfo} containing information about the client
     * @return the {@link View} that will be passed to the client
     */
    @Nullable
    public abstract View onCreateEmbeddedView(
            @NonNull Context context,
            @NonNull List<ContextInsight> insights,
            @NonNull InsightSurfaceClientInfo info);

    private static final class BinderService extends IEmbeddedInsightSurfaceVisualizer.Stub {
        private final WeakReference<InsightSurfaceVisualizerService> mService;
        private final Context mContext;
        private final Display mDisplay;
        private final Executor mExecutor;

        BinderService(
                InsightSurfaceVisualizerService service,
                Context context,
                Display display,
                Executor executor) {
            mService = new WeakReference<>(service);
            mContext = context;
            mDisplay = display;
            mExecutor = executor;
        }

        @Override
        public void createVisualizationForClient(
                List<ContextInsightWrapper> insights, InsightSurfaceClientInfo clientInfo) {
            post(service -> {
                final View view = service.onCreateEmbeddedView(
                        mContext, ContextInsightWrapper.unwrapList(insights), clientInfo);
                if (view == null) {
                    Log.e(TAG, "onCreateEmbeddedView returned null for client: " + clientInfo);
                    return;
                }

                final SurfaceControlViewHost surfaceControlViewHost =
                        new SurfaceControlViewHost(
                                mContext, mDisplay, clientInfo.getInputTransferToken());
                surfaceControlViewHost.setView(
                        view,
                        MeasureSpec.getSize(clientInfo.getWidthMeasureSpec()),
                        MeasureSpec.getSize(clientInfo.getHeightMeasureSpec()));
                clientInfo.onSurfaceCreated(surfaceControlViewHost.getSurfacePackage());
            });
        }

        @Override
        public void onClientConnected(InsightSurfaceClientInfo client) {
            post(service -> service.onClientConnected(client));
        }

        @Override
        public void onClientDisconnected(InsightSurfaceClientInfo client) {
            post(service -> service.onClientDisconnected(client));
        }

        private void post(Consumer<InsightSurfaceVisualizerService> consumer) {
            final InsightSurfaceVisualizerService service = mService.get();

            if (service == null) {
                return;
            }

            mExecutor.execute(() -> consumer.accept(service));
        }
    }
}
