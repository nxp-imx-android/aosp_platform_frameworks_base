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
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A client object that wraps a {@link SurfaceView} on order to negotiate an insight surface to be
 * installed in that {@link SurfaceView}. Apps that need to receive insight surfaces from the
 * personal context engine must instantiate this class and pass in the {@link SurfaceView} that
 * will receive the insight surfaces.
 * <p>
 * The client can also send extra information back to the personal context engine to assist in
 * refining the insight surfaces it receives. This is accomplished by adding {@link ContextHint}s to
 * the client via its builder. For example, if the client app wanted to receive a surface containing
 * a confirmation number, it could add a custom {@link ContextHint}, like this:
 * <pre>{@code
 * final BundleHint hint = new BundleHint();
 * hint.getDataBundle().putString("field-to-fill", "confirmation-number");
 * final InsightSurfaceClient client =
 *          new InsightSurfaceClient.Builder(context, surfaceView).addHint(hint).build();
 * }</pre>
 * This is an example. In practice, a specific {@link ContextHint} would be defined for this
 * purpose.
 *
 * @hide
 */
@SystemApi(client = PRIVILEGED_APPS)
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public class InsightSurfaceClient {
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

    private InsightSurfaceClientInfo mClientInfo;

    private final Context mContext;

    private final SurfaceView mSurfaceView;

    @NonNull
    private final List<InsightReceiver> mReceivers;

    @NonNull
    private final List<ContextHint> mHints;

    private final IEmbeddedInsightSurfaceCallback mCallback =
            new IEmbeddedInsightSurfaceCallback.Stub() {
                @Override
                public void onSurfaceCreated(SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceCreated [" + surfacePackage + "]");
                    }
                    mSurfaceView.post(() -> {
                        mSurfaceView.setChildSurfacePackage(surfacePackage);
                        mSurfaceView.setZOrderOnTop(true);
                        mSurfaceView.postInvalidate();
                    });
                }

                @Override
                public void onReceiveInsight(ContextInsightWrapper insightWrapper) {
                    final ContextInsight insight = insightWrapper.getContextInsight();
                    if (DEBUG) {
                        Log.d(TAG, "onInsightReceived [" + insight + "]");
                    }
                    mSurfaceView.post(() -> {
                        mReceivers.forEach((receiver) -> {
                            receiver.onReceive(insight);
                        });
                    });
                }
            };

    private InsightSurfaceClient(
            Context context,
            @NonNull SurfaceView surfaceView,
            @NonNull List<ContextHint> hints,
            @NonNull List<InsightReceiver> receivers) {
        mContext = context;
        mSurfaceView = surfaceView;
        mHints = List.copyOf(hints);
        mReceivers = List.copyOf(receivers);

        mSurfaceView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                InsightSurfaceClient.this.onViewAttachedToWindow();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                InsightSurfaceClient.this.onViewDetachedFromWindow();
            }
        });
    }

    /**
     * Return the context hints for this client.
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
     * @return a list of {@link ContextInsight} receivers.
     */
    @NonNull
    public List<InsightReceiver> getReceivers() {
        return mReceivers;
    }

    private void onViewAttachedToWindow() {
        mSurfaceView.post(this::registerClient);
    }

    private void onViewDetachedFromWindow() {
        unregisterClient();
    }

    private void registerClient() {
        if (DEBUG) {
            Log.d(TAG, "registering client...");
        }

        if (mClientInfo != null) {
            // Already registered.
            Log.w(TAG, "client is already registered");
            return;
        }

        final AttachedSurfaceControl rootSurfaceControl =
                mSurfaceView.getRootView().getRootSurfaceControl();
        if (rootSurfaceControl == null) {
            Log.w(TAG, "rootSurfaceControl is null");
            return;
        }

        final Display display = mSurfaceView.getDisplay();
        if (display == null) {
            Log.w(TAG, "display is null");
            return;
        }

        mClientInfo = new InsightSurfaceClientInfo.Builder(
                display.getDisplayId(),
                View.MeasureSpec.makeMeasureSpec(mSurfaceView.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                        mSurfaceView.getHeight(), View.MeasureSpec.EXACTLY),
                mSurfaceView.getResources().getConfiguration(),
                mCallback)
                .setInputTransferToken(rootSurfaceControl.getInputTransferToken())
                .build();
        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);
        personalContextManager.registerInsightSurfaceClient(mClientInfo, mHints);
    };

    private void unregisterClient() {
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

    /** Builder used to build a new {@link InsightSurfaceClient}. */
    public static final class Builder {
        private final Context mContext;
        private final SurfaceView mSurfaceView;
        private final List<InsightReceiver> mReceivers = new ArrayList<>();
        private final List<ContextHint> mHints = new ArrayList<>();

        /**
         * Constructor a new builder.
         *
         * @param context a {@link Context} used to fetch system services
         * @param surfaceView the {@link SurfaceView} that this client wraps
         */
        public Builder(@NonNull Context context, @NonNull SurfaceView surfaceView) {
            mContext = context;
            mSurfaceView = surfaceView;
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
         * Add a context hint to be sent to the context engine from the app embedding the
         * {@link SurfaceView}.
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
                    mContext, mSurfaceView, mHints, mReceivers);
        }
    }
}
