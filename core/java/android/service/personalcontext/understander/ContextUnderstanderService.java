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

package android.service.personalcontext.understander;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.refiner.HintFilter;
import android.service.personalcontext.refiner.IGetFilterCallback;
import android.service.personalcontext.refiner.IRefineCallback;
import android.service.personalcontext.refiner.IRefiner;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * This is the base class for understander services to implement, handling service details.
 *
 * <p>An understander takes hints provided to the Personal Context system and interprets them. This
 * usually means taking one or more hints, running them through models, and generating zero or more
 * {@link ContextInsight}s based on the hints.
 *
 * <p>The Personal Context service will manage the lifetime of this service, and this service may be
 * stopped if not utilized for some time. Services should start as rapidly as possible to minimize
 * latency in the Personal Context workflow.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public abstract class ContextUnderstanderService extends Service {
    private static final String TAG = "ContextUnderstanderSvc";

    private PersonalContextManager mPersonalContextManager;
    private UUID mComponentId = null;

    public ContextUnderstanderService() {
    }

    private void configure(UUID componentId) {
        mComponentId = componentId;
        onConnected();
    }

    /** @hide */
    @NonNull
    public final UUID getComponentId() {
        if (mComponentId == null) {
            throw new IllegalStateException("Service has not been configured yet");
        }
        return mComponentId;
    }

    @Override
    @Nullable
    public final IBinder onBind(@Nullable Intent intent) {
        return new Binder(this);
    }

    /** Called when the understander has been configured and is ready to receive insights. */
    public void onConnected() {
        // Default implementation does nothing.
    }

    /**
     * The understander should return a {@link HintFilter} that will be used to filter the
     * hints that this understander's {@link #onUnderstand} method will be called with.
     *
     * The result of this method will be cached and re-used between service bindings. If the filter
     * returned by this method changes, the changes will be ignored.
     *
     * @return a filter that restricts the {@link ContextHint}s this understander will receive
     */
    @NonNull
    public abstract HintFilter onInitializeFilter();

    /**
     * Called when a new hint is available.
     *
     * <p>As each hint is provided to the Personal Context system it will be forwarded on to
     * understanding components. The understander can take these hints, cache them between calls,
     * and use one or more hints together to generate {@link ContextInsight}s. These insights are
     * then fed back into the system via {@link #understood}.
     *
     * @param hints new hints that this understander has not seen before
     */
    public abstract void onUnderstand(@NonNull List<ContextHintWithSignature> hints);

    /**
     * Feeds {@link ContextInsight}s into the Personal Context system.
     *
     * <p>Most understanders will want to respond to calls to {@link #onUnderstand} inline when all
     * insights have been prepared, but this is not a requirement. Understanders may want to call
     * {@link #understood} more than once (e.g. once early with preliminary results, and a second
     * time with more in-depth results), not at all (e.g. no insights were generated), on a
     * different thread (e.g. insight generation is moved to a background thread), or spontaneously
     * (e.g. new information is available that is relevant, but not in response to new hints). All
     * of these models are allowed.
     *
     * <p>Note that while this method accepts a list of insights, this does not guarantee that the
     * insights will be processed together by the framework. If the caller wants insights to be
     * processed together downstream, they should be grouped together inside an
     * {@link android.service.personalcontext.insight.InsightCollection}.
     *
     * @throws IllegalStateException when called before the system service has started. The call
     * can be re-attempted in a few seconds, once system services have started.
     */
    public final void understood(@NonNull List<ContextInsight> insights) {
        if (mPersonalContextManager == null) {
            mPersonalContextManager = getSystemService(PersonalContextManager.class);
        }
        if (mPersonalContextManager == null) {
            throw new IllegalStateException("Personal Context Manager service is not running");
        }
        mPersonalContextManager.publishInsight(insights);
    }

    private static final class Binder extends IRefiner.Stub {
        private final WeakReference<ContextUnderstanderService> mService;

        private Binder(ContextUnderstanderService service) {
            mService = new WeakReference<>(service);
        }

        private ContextUnderstanderService getServiceOrThrow() throws RemoteException {
            final ContextUnderstanderService service = mService.get();
            if (service == null) {
                Log.e(TAG, "Service is no longer available");
                throw new RemoteException("Service is no longer available");
            } else {
                return service;
            }
        }

        @Override
        public void configure(ParcelUuid componentId) throws RemoteException {
            getServiceOrThrow().configure(componentId.getUuid());
        }

        @Override
        public void refine(List<ContextHintWithSignature> inputHints, IRefineCallback callback)
                throws RemoteException {
            // Report that hints were refined right away so that the core doesn't wait around.
            callback.onHintsRefined(Collections.emptyList());

            getServiceOrThrow().onUnderstand(inputHints);
        }

        @Override
        public void getFilter(IGetFilterCallback callback) throws RemoteException {
            callback.updateFilter(getServiceOrThrow().onInitializeFilter());
        }
    }
}
