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
import android.annotation.SystemApi;
import android.content.Context;
import android.content.res.Configuration;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A client object that registers with the personal context engine in order to receive a
 * {@link SurfaceControlViewHost.SurfacePackage} that can be installed in a surface view. Apps that
 * need to receive insight surfaces from the personal context engine must instantiate this class and
 * call {@link #register}. If and when a surface is ready, the
 * {@link ClientCallback#onSurfaceCreated} method will be called with a
 * {@link SurfaceControlViewHost.SurfacePackage}. Call {@link #unregister} to unregister the client.
 * The {@link ClientCallback#onSurfaceReleased} method will be called if/when the surface has been
 * released by the personal context engine.
 * <p>
 * The client can also send extra information back to the personal context engine to assist in
 * refining the insight surfaces it receives. This is accomplished by adding {@link ContextHint}s to
 * the client via its builder. For example, if the client app wanted to receive a surface containing
 * a confirmation number, it could add a custom {@link ContextHint}, like this:
 * <pre>{@code
 * final BundleHint hint = new BundleHint.Builder().build();
 * hint.getDataBundle().putString("field-to-fill", "confirmation-number");
 * final InsightSurfaceClient client =
 *          new InsightSurfaceClient.Builder(context, callbacks).addHint(hint).build();
 * }</pre>
 * This is an example. In practice, a specific {@link ContextHint} would be defined for this
 * purpose.
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class InsightSurfaceClient implements AutoCloseable {
    private static final String TAG = "InsightSurfaceClient";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** A receiver that can receive {@link ContextInsight}s from an embedded surface. */
    public interface InsightReceiver {
        /**
         * Receive a ContextInsight from the context engine. For example, an insight with
         * relevant data could be sent to the client when the user interacts with the
         * embedded view. The context engine can choose to send insights to the client in
         * other situations as well. Returns true if the insight was used and false otherwise.
         */
        boolean onReceive(@NonNull ContextInsight insight);
    }

    /**
     * Callbacks that are to be implemented by the owner of the client to be notified when
     * certain events have occurred.
     */
    public interface ClientCallback {
        /**
         * A {@link SurfaceControlViewHost.SurfacePackage} has been created for the
         * client to embed in a SurfaceView. This callback will only be called after the client
         * has been registered with the personal context engine by calling the {@link #register}
         * method.
         */
        void onSurfaceCreated(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage);

        /**
         * The {@link SurfaceControlViewHost.SurfacePackage} returned by onSurfaceCreated has been
         * released. This is an opportunity for the client owner to release any resources
         * associated with the surface.
         */
        void onSurfaceReleased(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage);
    }

    private InsightSurfaceClientInfo mClientInfo;

    private final Context mContext;
    @NonNull
    private final List<InsightReceiver> mInsightReceivers;
    @NonNull
    private final ClientCallback mCallbacks;
    @NonNull
    private final Executor mCallbacksExecutor;
    @NonNull
    private final List<ContextHint> mHints;

    private final IEmbeddedInsightSurfaceCallback mInsightSurfaceCallback =
            new IEmbeddedInsightSurfaceCallback.Stub() {
                @Override
                public void onSurfaceCreated(SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceCreated [" + surfacePackage + "]");
                    }
                    mCallbacksExecutor.execute(() -> mCallbacks.onSurfaceCreated(surfacePackage));
                }

                @Override
                public void onSurfaceReleased(
                        SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceReleased [" + surfacePackage + "]");
                    }
                    mCallbacksExecutor.execute(() -> mCallbacks.onSurfaceReleased(surfacePackage));
                }

                @Override
                public void onReceiveInsight(ContextInsightWrapper insightWrapper) {
                    final ContextInsight insight = insightWrapper.getContextInsight();
                    if (DEBUG) {
                        Log.d(TAG, "onInsightReceived [" + insight + "]");
                    }
                    mCallbacksExecutor.execute(() ->
                            mInsightReceivers.forEach((receiver) -> receiver.onReceive(insight)));
                }
            };

    private InsightSurfaceClient(
            Context context,
            @NonNull ClientCallback callbacks,
            @NonNull Executor callbacksExecutor,
            @NonNull List<ContextHint> hints,
            @NonNull List<InsightReceiver> receivers) {
        mContext = context;
        mHints = List.copyOf(hints);

        mCallbacks = callbacks;
        mCallbacksExecutor = callbacksExecutor;
        mInsightReceivers = List.copyOf(receivers);
    }

    /**
     * Publish new hints to the context engine. This method can be called any time after the client
     * has been created to send new hints to the context engine.
     *
     * @param hints a list of {@link ContextHint}s
     */
    public void publishHints(@NonNull Set<ContextHint> hints) {
        Objects.requireNonNull(hints);
        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);
        personalContextManager.publishInsightSurfaceHints(hints, mClientInfo);
    }

    /**
     * Return the context hints this client was originally created with (using the
     * {@link Builder#addHint} method).
     *
     * @return the {@link ContextHint}s
     */
    @NonNull
    public List<ContextHint> getHints() {
        return mHints;
    }

    /**
     * Return the insight receivers for this client.
     *
     * @return a list of {@link ContextInsight} receivers
     */
    @NonNull
    public List<InsightReceiver> getReceivers() {
        return mInsightReceivers;
    }

    /**
     * Register with the personal context engine. Once registered, the client can receive a
     * {@link SurfaceControlViewHost.SurfacePackage} via {@link ClientCallback}.
     *
     * @param widthMeasureSpec a width measure spec indicating the desired width of the embedded
     *                         surface; the personal context engine will attempt to honor the spec
     * @param heightMeasureSpec a height measure spec indicating the desired height of the
     *                          embedded surface; the personal context engine will attempt to honor
     *                          the spec
     */
    public void register(int widthMeasureSpec, int heightMeasureSpec) {
        if (DEBUG) {
            Log.d(TAG, "registering client...");
        }

        if (mClientInfo != null) {
            // Already registered.
            Log.w(TAG, "client is already registered");
            return;
        }

        final int displayId = mContext.getDisplay().getDisplayId();
        final Configuration configuration = mContext.getResources().getConfiguration();

        mClientInfo = new InsightSurfaceClientInfo.Builder(
                displayId,
                widthMeasureSpec,
                heightMeasureSpec,
                configuration,
                mInsightSurfaceCallback).build();
        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);
        personalContextManager.registerInsightSurfaceClient(mClientInfo, mHints);
    };

    /**
     * Unregister from the personal context engine.
     */
    public void unregister() {
        if (DEBUG) {
            Log.d(TAG, "unregistering client...");
        }

        if (mClientInfo == null) {
            Log.w(TAG, "client not registered");
            return;
        }

        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);
        personalContextManager.unregisterInsightSurfaceClient(mClientInfo);

        mClientInfo = null;
    }

    @Override
    public void close() {
        if (mClientInfo != null) {
            unregister();
        }
    }

    /** Builder used to build a new {@link InsightSurfaceClient}. */
    public static final class Builder {
        private final Context mContext;
        private final ClientCallback mCallbacks;
        private final Executor mCallbacksExecutor;
        private final List<InsightReceiver> mReceivers = new ArrayList<>();
        private final List<ContextHint> mHints = new ArrayList<>();

        /**
         * Construct a new builder.
         *
         * @param context a {@link Context} used to fetch system services
         * @param callbacks {@link ClientCallback} to be notified of connection events
         */
        public Builder(@NonNull Context context, @NonNull ClientCallback callbacks) {
            this(context, context.getMainExecutor(), callbacks);
        }

        /**
         * Construct a new builder.
         *
         * @param context a {@link Context} used to fetch system services
         * @param callbacks {@link ClientCallback} to be notified of connection events
         * @param callbacksExecutor an {@link Executor} with which to execute callback methods
         */
        public Builder(
                @NonNull Context context,
                @NonNull Executor callbacksExecutor,
                @NonNull ClientCallback callbacks) {
            mContext = context;
            mCallbacksExecutor = callbacksExecutor;
            mCallbacks = callbacks;
        }

        /**
         * Construct a new builder.
         *
         * @param context a {@link Context} used to fetch system services
         * @param surfaceView the {@link SurfaceView} that this client wraps
         * @deprecated Use {@link #Builder(Context, ClientCallback)} instead.
         */
        @Deprecated
        public Builder(@NonNull Context context, @NonNull SurfaceView surfaceView) {
            this(context, new ClientCallback() {
                @Override
                public void onSurfaceCreated(
                        @NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
                }

                @Override
                public void onSurfaceReleased(
                        @NonNull SurfaceControlViewHost.SurfacePackage surfacePackage) {
                }
            });
        }

        /**
         * Add an insight receiver to the client.
         *
         * @param receiver the {@link InsightReceiver} to be added
         * @return the {@link Builder}
         */
        @NonNull
        public Builder addReceiver(@NonNull InsightReceiver receiver) {
            mReceivers.add(receiver);
            return this;
        }

        /**
         * Add a context hint to be sent to the context engine from the client app.
         *
         * @param hint the {@link ContextHint} to add
         * @return the {@link Builder}
         */
        @NonNull
        public Builder addHint(@NonNull ContextHint hint) {
            mHints.add(hint);
            return this;
        }

        /**
         * Build and return an {@link InsightSurfaceClient}.
         *
         * @return the {@link InsightSurfaceClient}
         */
        @NonNull
        public InsightSurfaceClient build() {
            return new InsightSurfaceClient(
                    mContext,
                    mCallbacks,
                    mCallbacksExecutor,
                    mHints,
                    mReceivers);
        }
    }
}
