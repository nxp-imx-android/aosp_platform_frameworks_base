/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.telephony;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.mbms.InternalStreamingSessionCallback;
import android.telephony.mbms.InternalStreamingServiceCallback;
import android.telephony.mbms.MbmsException;
import android.telephony.mbms.MbmsStreamingSessionCallback;
import android.telephony.mbms.MbmsUtils;
import android.telephony.mbms.StreamingService;
import android.telephony.mbms.StreamingServiceCallback;
import android.telephony.mbms.StreamingServiceInfo;
import android.telephony.mbms.vendor.IMbmsStreamingService;
import android.util.ArraySet;
import android.util.Log;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

/**
 * This class provides functionality for streaming media over MBMS.
 */
public class MbmsStreamingSession implements AutoCloseable {
    private static final String LOG_TAG = "MbmsStreamingSession";

    /**
     * Service action which must be handled by the middleware implementing the MBMS streaming
     * interface.
     * @hide
     */
    @SystemApi
    @SdkConstant(SdkConstant.SdkConstantType.SERVICE_ACTION)
    public static final String MBMS_STREAMING_SERVICE_ACTION =
            "android.telephony.action.EmbmsStreaming";

    private static AtomicBoolean sIsInitialized = new AtomicBoolean(false);

    private AtomicReference<IMbmsStreamingService> mService = new AtomicReference<>(null);
    private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            sIsInitialized.set(false);
            sendErrorToApp(MbmsException.ERROR_MIDDLEWARE_LOST, "Received death notification");
        }
    };

    private InternalStreamingSessionCallback mInternalCallback;
    private Set<StreamingService> mKnownActiveStreamingServices = new ArraySet<>();

    private final Context mContext;
    private int mSubscriptionId = INVALID_SUBSCRIPTION_ID;

    /** @hide */
    private MbmsStreamingSession(Context context, MbmsStreamingSessionCallback callback,
                    int subscriptionId, Handler handler) {
        mContext = context;
        mSubscriptionId = subscriptionId;
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        mInternalCallback = new InternalStreamingSessionCallback(callback, handler);
    }

    /**
     * Create a new {@link MbmsStreamingSession} using the given subscription ID.
     *
     * Note that this call will bind a remote service. You may not call this method on your app's
     * main thread.
     *
     * You may only have one instance of {@link MbmsStreamingSession} per UID. If you call this
     * method while there is an active instance of {@link MbmsStreamingSession} in your process
     * (in other words, one that has not had {@link #close()} called on it), this method will
     * throw an {@link IllegalStateException}. If you call this method in a different process
     * running under the same UID, an error will be indicated via
     * {@link MbmsStreamingSessionCallback#onError(int, String)}.
     *
     * Note that initialization may fail asynchronously. If you wish to try again after you
     * receive such an asynchronous error, you must call {@link #close()} on the instance of
     * {@link MbmsStreamingSession} that you received before calling this method again.
     *
     * @param context The {@link Context} to use.
     * @param callback A callback object on which you wish to receive results of asynchronous
     *                 operations.
     * @param subscriptionId The subscription ID to use.
     * @param handler The handler you wish to receive callbacks on.
     * @return An instance of {@link MbmsStreamingSession}, or null if an error occurred.
     */
    public static @Nullable MbmsStreamingSession create(@NonNull Context context,
            final @NonNull MbmsStreamingSessionCallback callback, int subscriptionId,
            @NonNull Handler handler) {
        if (!sIsInitialized.compareAndSet(false, true)) {
            throw new IllegalStateException("Cannot create two instances of MbmsStreamingSession");
        }
        MbmsStreamingSession session = new MbmsStreamingSession(context, callback,
                subscriptionId, handler);

        final int result = session.bindAndInitialize();
        if (result != MbmsException.SUCCESS) {
            sIsInitialized.set(false);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(result, null);
                }
            });
            return null;
        }
        return session;
    }

    /**
     * Create a new {@link MbmsStreamingSession} using the system default data subscription ID.
     * See {@link #create(Context, MbmsStreamingSessionCallback, int, Handler)}.
     */
    public static MbmsStreamingSession create(@NonNull Context context,
            @NonNull MbmsStreamingSessionCallback callback, @NonNull Handler handler) {
        return create(context, callback, SubscriptionManager.getDefaultSubscriptionId(), handler);
    }

    /**
     * Terminates this instance. Also terminates
     * any streaming services spawned from this instance as if
     * {@link StreamingService#stopStreaming()} had been called on them. After this method returns,
     * no further callbacks originating from the middleware will be enqueued on the provided
     * instance of {@link MbmsStreamingSessionCallback}, but callbacks that have already been
     * enqueued will still be delivered.
     *
     * It is safe to call {@link #create(Context, MbmsStreamingSessionCallback, int, Handler)} to
     * obtain another instance of {@link MbmsStreamingSession} immediately after this method
     * returns.
     *
     * May throw an {@link IllegalStateException}
     */
    public void close() {
        try {
            IMbmsStreamingService streamingService = mService.get();
            if (streamingService == null) {
                // Ignore and return, assume already disposed.
                return;
            }
            streamingService.dispose(mSubscriptionId);
            for (StreamingService s : mKnownActiveStreamingServices) {
                s.getCallback().stop();
            }
            mKnownActiveStreamingServices.clear();
        } catch (RemoteException e) {
            // Ignore for now
        } finally {
            mService.set(null);
            sIsInitialized.set(false);
            mInternalCallback.stop();
        }
    }

    /**
     * An inspection API to retrieve the list of streaming media currently be advertised.
     * The results are returned asynchronously via
     * {@link MbmsStreamingSessionCallback#onStreamingServicesUpdated(List)} on the callback
     * provided upon creation.
     *
     * Multiple calls replace the list of service classes of interest.
     *
     * May throw an {@link IllegalArgumentException} or an {@link IllegalStateException}.
     *
     * @param serviceClassList A list of streaming service classes that the app would like updates
     *                         on. The exact names of these classes should be negotiated with the
     *                         wireless carrier separately.
     */
    public void requestUpdateStreamingServices(List<String> serviceClassList) {
        IMbmsStreamingService streamingService = mService.get();
        if (streamingService == null) {
            throw new IllegalStateException("Middleware not yet bound");
        }
        try {
            int returnCode = streamingService.requestUpdateStreamingServices(
                    mSubscriptionId, serviceClassList);
            if (returnCode != MbmsException.SUCCESS) {
                sendErrorToApp(returnCode, null);
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Remote process died");
            mService.set(null);
            sIsInitialized.set(false);
            sendErrorToApp(MbmsException.ERROR_MIDDLEWARE_LOST, null);
        }
    }

    /**
     * Starts streaming a requested service, reporting status to the indicated callback.
     * Returns an object used to control that stream. The stream may not be ready for consumption
     * immediately upon return from this method -- wait until the streaming state has been
     * reported via
     * {@link android.telephony.mbms.StreamingServiceCallback#onStreamStateUpdated(int, int)}
     *
     * May throw an {@link IllegalArgumentException} or an {@link IllegalStateException}
     *
     * Asynchronous errors through the callback include any of the errors in
     * {@link android.telephony.mbms.MbmsException.GeneralErrors} or
     * {@link android.telephony.mbms.MbmsException.StreamingErrors}.
     *
     * @param serviceInfo The information about the service to stream.
     * @param callback A callback that'll be called when something about the stream changes.
     * @param handler A handler that calls to {@code callback} should be called on.
     * @return An instance of {@link StreamingService} through which the stream can be controlled.
     *         May be {@code null} if an error occurred.
     */
    public @Nullable StreamingService startStreaming(StreamingServiceInfo serviceInfo,
            StreamingServiceCallback callback, @NonNull Handler handler) {
        IMbmsStreamingService streamingService = mService.get();
        if (streamingService == null) {
            throw new IllegalStateException("Middleware not yet bound");
        }

        InternalStreamingServiceCallback serviceCallback = new InternalStreamingServiceCallback(
                callback, handler);

        StreamingService serviceForApp = new StreamingService(
                mSubscriptionId, streamingService, this, serviceInfo, serviceCallback);
        mKnownActiveStreamingServices.add(serviceForApp);

        try {
            int returnCode = streamingService.startStreaming(
                    mSubscriptionId, serviceInfo.getServiceId(), serviceCallback);
            if (returnCode != MbmsException.SUCCESS) {
                sendErrorToApp(returnCode, null);
                return null;
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Remote process died");
            mService.set(null);
            sIsInitialized.set(false);
            sendErrorToApp(MbmsException.ERROR_MIDDLEWARE_LOST, null);
            return null;
        }

        return serviceForApp;
    }

    /** @hide */
    public void onStreamingServiceStopped(StreamingService service) {
        mKnownActiveStreamingServices.remove(service);
    }

    private int bindAndInitialize() {
        return MbmsUtils.startBinding(mContext, MBMS_STREAMING_SERVICE_ACTION,
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        IMbmsStreamingService streamingService =
                                IMbmsStreamingService.Stub.asInterface(service);
                        int result;
                        try {
                            result = streamingService.initialize(mInternalCallback,
                                    mSubscriptionId);
                        } catch (RemoteException e) {
                            Log.e(LOG_TAG, "Service died before initialization");
                            sendErrorToApp(
                                    MbmsException.InitializationErrors.ERROR_UNABLE_TO_INITIALIZE,
                                    e.toString());
                            sIsInitialized.set(false);
                            return;
                        } catch (RuntimeException e) {
                            Log.e(LOG_TAG, "Runtime exception during initialization");
                            sendErrorToApp(
                                    MbmsException.InitializationErrors.ERROR_UNABLE_TO_INITIALIZE,
                                    e.toString());
                            sIsInitialized.set(false);
                            return;
                        }
                        if (result != MbmsException.SUCCESS) {
                            sendErrorToApp(result, "Error returned during initialization");
                            sIsInitialized.set(false);
                            return;
                        }
                        try {
                            streamingService.asBinder().linkToDeath(mDeathRecipient, 0);
                        } catch (RemoteException e) {
                            sendErrorToApp(MbmsException.ERROR_MIDDLEWARE_LOST,
                                    "Middleware lost during initialization");
                            sIsInitialized.set(false);
                            return;
                        }
                        mService.set(streamingService);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        sIsInitialized.set(false);
                        mService.set(null);
                    }
                });
    }

    private void sendErrorToApp(int errorCode, String message) {
        try {
            mInternalCallback.onError(errorCode, message);
        } catch (RemoteException e) {
            // Ignore, should not happen locally.
        }
    }
}
