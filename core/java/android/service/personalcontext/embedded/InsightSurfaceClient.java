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
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.content.Context;
import android.graphics.Color;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

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
         * @deprecated Use {@link #onSessionCreated(InsightSurfaceSession)} instead.
         */
        @Deprecated
        void onSurfaceCreated(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage);

        /**
         * The {@link SurfaceControlViewHost.SurfacePackage} returned by onSurfaceCreated has been
         * released. This is an opportunity for the client owner to release any resources
         * associated with the surface.
         * @deprecated Use {@link #onSessionDestroyed(InsightSurfaceSession)} instead.
         */
        @Deprecated
        void onSurfaceReleased(@NonNull SurfaceControlViewHost.SurfacePackage surfacePackage);

        /**
         * The size of the embedded surface has changed. Subclasses can override this method to be
         * informed of the size change.
         * @param width the new width of the surface
         * @param height the new height of the surface
         */
        default void onSizeChanged(int width, int height) {
        }

        /**
         * The given {@link InsightSurfaceSession} has been created. Subclasses can override this
         * method to be informed when a new session has been created. This method will only be
         * called once for a newly created session. Subsequent session updates will call
         * {@link #onSessionUpdated(InsightSurfaceSession)}. If a session is destroyed, then this
         * method will be called again if/when a new session is created.
         */
        default void onSessionCreated(@NonNull InsightSurfaceSession session) {
        }

        /**
         * The given {@link InsightSurfaceSession} has been updated. Subclasses can override this
         * method to be informed when a session has been updated. This method will only be called
         * when a session already exists. If no session yet exists, then
         * {@link #onSessionCreated(InsightSurfaceSession)} will be called instead. The session
         * passed to this method will be the same session that was passed to
         * {@link #onSessionCreated(InsightSurfaceSession)}.
         */
        default void onSessionUpdated(@NonNull InsightSurfaceSession session) {
        }

        /**
         * The given {@link InsightSurfaceSession} has been destroyed. Subclasses can override this
         * method to be informed when a session has been destroyed.
         */
        default void onSessionDestroyed(@NonNull InsightSurfaceSession session) {
        }

        /**
         * An error has occurred. Subclasses can override this method to be informed of errors.
         * @param exception an {@link Exception} representing the error
         */
        default void onError(@NonNull Exception exception) {
        }
    }

    private InsightSurfaceClientInfo mClientInfo;
    private InsightSurfaceSession mSession;

    private final Context mContext;
    @NonNull
    private final List<InsightReceiver> mInsightReceivers;
    @NonNull
    private final ClientCallback mCallbacks;
    @NonNull
    private final Executor mCallbacksExecutor;
    @NonNull
    private final List<ContextHint> mHints;
    private boolean mIsRegistered;

    private final IInsightSurfaceClient mClient =
            new IInsightSurfaceClient.Stub() {
                @Override
                public void onSurfaceCreated(
                        SurfaceControlViewHost.SurfacePackage surfacePackage,
                        IInsightSurfaceSession session) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceCreated [" + surfacePackage + "]");
                    }
                    mCallbacksExecutor.execute(() -> {
                        if (mSession != null) {
                            mSession.close();
                            mCallbacks.onSessionDestroyed(mSession);
                        }
                        mSession = new InsightSurfaceSession(
                                InsightSurfaceClient.this, surfacePackage, session);
                        mCallbacks.onSessionCreated(mSession);

                        mCallbacks.onSurfaceCreated(surfacePackage);
                    });
                }

                @Override
                public void onSurfaceReleased(
                        SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceReleased [" + surfacePackage + "]");
                    }
                    mCallbacksExecutor.execute(() -> {
                        mCallbacks.onSurfaceReleased(surfacePackage);

                        if (mSession != null) {
                            Preconditions.checkState(
                                    mSession.getSurfacePackage() == surfacePackage);
                            mCallbacks.onSessionDestroyed(mSession);
                            mSession.close();
                            mSession = null;
                        }
                    });
                }

                @Override
                public void onSurfaceUpdated(SurfaceControlViewHost.SurfacePackage surfacePackage) {
                    if (DEBUG) {
                        Log.d(TAG, "onSurfaceUpdated [" + surfacePackage + "]");
                    }
                    mCallbacksExecutor.execute(() -> {
                        Preconditions.checkState(
                                mSession != null && mSession.getSurfacePackage() == surfacePackage);
                        mCallbacks.onSessionUpdated(mSession);
                    });
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

                @Override
                public void onSizeChanged(int width, int height) {
                    if (DEBUG) {
                        Log.d(TAG, "onSizeChanged [width=" + width + ", height=" + height + "]");
                    }
                    mCallbacksExecutor.execute(() ->
                            mCallbacks.onSizeChanged(width, height));
                }
            };

    private InsightSurfaceClient(
            Context context,
            int widthMeasureSpec,
            int heightMeasureSpec,
            @NonNull Color backgroundColor,
            int nestedScrollAxes,
            boolean nestedScrollAxisLocked,
            @NonNull ClientCallback callbacks,
            @NonNull Executor callbacksExecutor,
            @NonNull List<ContextHint> hints,
            @NonNull List<InsightReceiver> receivers) {
        mContext = context;
        mHints = List.copyOf(hints);

        mCallbacks = callbacks;
        mCallbacksExecutor = callbacksExecutor;
        mInsightReceivers = List.copyOf(receivers);

        mClientInfo = new InsightSurfaceClientInfo(
                mContext.getDisplay().getDisplayId(),
                widthMeasureSpec,
                heightMeasureSpec,
                backgroundColor,
                nestedScrollAxes,
                nestedScrollAxisLocked,
                mContext.getResources().getConfiguration(),
                mClient);
    }

    /**
     * Publish new hints to the context engine. This method can be called any time after the client
     * has been created to send new hints to the context engine.
     *
     * @param hints a list of {@link ContextHint}s
     *
     * @hide
     */
    @VisibleForTesting
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
     * Return the width {@link View.MeasureSpec} for this client.
     */
    public int getMeasureSpecWidth() {
        return mClientInfo.getMeasureSpecWidth();
    }

    /**
     * Return the height {@link View.MeasureSpec} for this client.
     */
    public int getMeasureSpecHeight() {
        return mClientInfo.getMeasureSpecHeight();
    }

    /**
     * Return the background {@link Color} for this client.
     */
    @NonNull
    public Color getBackgroundColor() {
        return mClientInfo.getBackgroundColor();
    }

    /**
     * Return a bitmask indicating the nested scroll axes supported by the client. This ensures
     * that an embedded surface will only send these nested scroll events back to the client when
     * nested scroll axis is locked. Possible values are
     * {@link View#SCROLL_AXIS_HORIZONTAL},
     * {@link View#SCROLL_AXIS_VERTICAL}, or
     * {@link View#SCROLL_AXIS_NONE}.
     */
    public int getNestedScrollAxes() {
        return mClientInfo.getNestedScrollAxes();
    }

    /**
     * Return whether an embedded surface should report a specific axis when a nested scroll gesture
     * is detected, and whether that axis should be locked such that subsequent nested scroll events
     * are only reported for that axis. A value of {@code true} is typical for Android UIs where
     * scroll axes are locked during a gesture, while a value of {@code false} can be used to give
     * the illusion of a 2D canvas. Only applicable when nested scroll axes is set to
     * {@link View#SCROLL_AXIS_HORIZONTAL} or
     * {@link View#SCROLL_AXIS_VERTICAL}.
     */
    public boolean isNestedScrollAxisLocked() {
        return mClientInfo.getNestedScrollAxisLocked();
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
     * @deprecated Use {@link #register()} instead.
     */
    @Deprecated
    public void register(int widthMeasureSpec, int heightMeasureSpec) {
        if (mIsRegistered) {
            // Already registered.
            Log.w(TAG, "client is already registered");
            return;
        }

        // Re-create client info to update measure specs for backward compatibility. All other
        // properties are preserved.
        mClientInfo = new InsightSurfaceClientInfo(
                mClientInfo.getDisplayId(),
                widthMeasureSpec,
                heightMeasureSpec,
                mClientInfo.getBackgroundColor(),
                mClientInfo.getNestedScrollAxes(),
                mClientInfo.getNestedScrollAxisLocked(),
                mClientInfo.getConfiguration(),
                mClient);
        register();
    }

    /**
     * Register with the personal context engine. Once registered, the client can receive a
     * {@link SurfaceControlViewHost.SurfacePackage} via {@link ClientCallback}.
     */
    public void register() {
        if (DEBUG) {
            Log.d(TAG, "registering client...");
        }

        if (mIsRegistered) {
            // Already registered.
            Log.w(TAG, "client is already registered");
            return;
        }

        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);
        personalContextManager.registerInsightSurfaceClient(mClientInfo, mHints);

        mIsRegistered = true;
    }

    /**
     * Unregister from the personal context engine.
     */
    public void unregister() {
        if (DEBUG) {
            Log.d(TAG, "unregistering client...");
        }

        if (!mIsRegistered) {
            Log.w(TAG, "client not registered");
            return;
        }

        final PersonalContextManager personalContextManager =
                mContext.getSystemService(PersonalContextManager.class);
        personalContextManager.unregisterInsightSurfaceClient(mClientInfo);

        if (mSession != null) {
            mSession.close();
            mSession = null;
        }

        mIsRegistered = false;
    }

    @Override
    public void close() {
        if (mIsRegistered) {
            unregister();
        }
    }

    /**
     * Return the {@link InsightSurfaceClientInfo} for this client.
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo getClientInfo() {
        return mClientInfo;
    }

    /**
     * Update the {@link InsightSurfaceClientInfo} with the given
     * {@link InsightSurfaceClientUpdate}. Returns the old {@link InsightSurfaceClientInfo}.
     * @hide
     */
    @VisibleForTesting
    public InsightSurfaceClientInfo updateClientInfo(InsightSurfaceClientUpdate update) {
        final InsightSurfaceClientInfo oldClientInfo = mClientInfo;
        mClientInfo = mClientInfo.createInfoFromUpdate(update);
        return oldClientInfo;
    }

    /** Builder used to build a new {@link InsightSurfaceClient}. */
    public static final class Builder {
        private final Context mContext;
        private final ClientCallback mCallbacks;
        private final Executor mCallbacksExecutor;
        private final List<InsightReceiver> mReceivers = new ArrayList<>();
        private final List<ContextHint> mHints = new ArrayList<>();
        private int mWidthMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        private int mHeightMeasureSpec =
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        private Color mBackgroundColor = Color.valueOf(Color.TRANSPARENT);
        private int mNestedScrollAxes = View.SCROLL_AXIS_NONE;
        private boolean mNestedScrollAxisLocked = false;

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
         * Set the width {@link View.MeasureSpec} of the client surface
         *
         * @param widthMeasureSpec the width {@link View.MeasureSpec} of the client surface
         * @param heightMeasureSpec the height {@link View.MeasureSpec} of the client surface
         * @throws IllegalArgumentException when the {@link View.MeasureSpec} is not one of
         * {@code View.MeasureSpec.UNSPECIFIED}, {@code View.MeasureSpec.EXACTLY}, or
         * {@code View.MeasureSpec.At_MOST}
         */
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        public Builder setMeasureSpecs(int widthMeasureSpec, int heightMeasureSpec) {
            Preconditions.checkArgument(isValidMeasureSpec(widthMeasureSpec));
            Preconditions.checkArgument(isValidMeasureSpec(heightMeasureSpec));
            mWidthMeasureSpec = widthMeasureSpec;
            mHeightMeasureSpec = heightMeasureSpec;
            return this;
        }

        /**
         * Set the surface background color. Transparent is the default background color when no
         * background color is set.
         *
         * @param backgroundColor the background color of the client
         */
        @NonNull
        public Builder setBackgroundColor(@NonNull Color backgroundColor) {
            mBackgroundColor = backgroundColor;
            return this;
        }

        /**
         * Sets a bitmask indicating the nested scroll axes supported by the client. This ensures
         * that an embedded surface will only send these nested scroll events for the specified axes
         * back to the client. Possible values are {@link View#SCROLL_AXIS_HORIZONTAL},
         * {@link View#SCROLL_AXIS_VERTICAL}, or {@link View#SCROLL_AXIS_NONE}.
         *
         * @param nestedScrollAxes the axes that the client supports
         * @throws IllegalArgumentException when nestedScrollAxes is not a bitmask of the possible
         * values
         */
        @NonNull
        public Builder setNestedScrollAxes(int nestedScrollAxes) {
            Preconditions.checkArgument(isValidNestedScrollAxes(nestedScrollAxes));
            mNestedScrollAxes = nestedScrollAxes;
            return this;
        }

        /**
         * Sets whether nested scrolling is locked (based on the bitmask past to
         * {@link #setNestedScrollAxes(int)}). A value of {@code true} is typical for Android UIs
         * where scroll axes are locked during a gesture, while a value of {@code false} can be
         * used to give the illusion of a 2D canvas. Only applicable when nested scroll axes is
         * set to {@link View#SCROLL_AXIS_HORIZONTAL} or
         * {@link View#SCROLL_AXIS_VERTICAL}.
         *
         * @param nestedScrollAxisLocked {@code true} if the embedded surface should only send
         *                               nested scroll events for the axes the client supports
         */
        @NonNull
        public Builder setNestedScrollAxisLocked(boolean nestedScrollAxisLocked) {
            mNestedScrollAxisLocked = nestedScrollAxisLocked;
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
                    mWidthMeasureSpec,
                    mHeightMeasureSpec,
                    mBackgroundColor,
                    mNestedScrollAxes,
                    mNestedScrollAxisLocked,
                    mCallbacks,
                    mCallbacksExecutor,
                    mHints,
                    mReceivers);
        }
    }

    private static boolean isValidNestedScrollAxes(int nestedScrollAxes) {
        return switch (nestedScrollAxes) {
            case View.SCROLL_AXIS_NONE, View.SCROLL_AXIS_VERTICAL,
                 View.SCROLL_AXIS_HORIZONTAL + View.SCROLL_AXIS_VERTICAL -> true;
            default -> false;
        };
    }

    private static boolean isValidMeasureSpec(int measureSpec) {
        return switch (View.MeasureSpec.getMode(measureSpec)) {
            case View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST
                    -> true;
            default -> false;
        };
    }
}
