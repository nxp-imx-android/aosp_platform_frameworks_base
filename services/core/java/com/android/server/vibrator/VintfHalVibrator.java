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

package com.android.server.vibrator;

import static android.os.Trace.TRACE_TAG_VIBRATOR;
import static android.os.VibrationEffect.effectIdToString;
import static android.os.VibrationEffect.effectStrengthToString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.vibrator.ActivePwle;
import android.hardware.vibrator.CompositeEffect;
import android.hardware.vibrator.CompositePwleV2;
import android.hardware.vibrator.FrequencyAccelerationMapEntry;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.PrimitivePwle;
import android.hardware.vibrator.PwleV2Primitive;
import android.hardware.vibrator.VendorEffect;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Handler;
import android.os.IVibratorStateListener;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.Trace;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.server.vibrator.VintfUtils.VintfGetter;
import com.android.server.vibrator.VintfUtils.VintfSupplier;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Implementations for {@link HalVibrator} backed by VINTF objects. */
class VintfHalVibrator {

    /** Default implementation for devices with {@link IVibrator} available. */
    static final class DefaultHalVibrator implements HalVibrator {
        private static final String TAG = "DefaultHalVibrator";

        private final Object mLock = new Object();
        private final int mVibratorId;
        private final VintfSupplier<IVibrator> mHalSupplier;
        private final Handler mHandler;
        private final HalNativeHandler mNativeHandler;

        @GuardedBy("mLock")
        private final RemoteCallbackList<IVibratorStateListener> mVibratorStateListeners =
                new RemoteCallbackList<>();

        private Callbacks mCallbacks;

        // Vibrator state variables that are updated from synchronized blocks but can be read
        // anytime for a snippet of the current known vibrator state/info.
        private volatile VibratorInfo mVibratorInfo;
        private volatile State mCurrentState;
        private volatile float mCurrentAmplitude;

        DefaultHalVibrator(int vibratorId, VintfSupplier<IVibrator> supplier, Handler handler,
                HalNativeHandler nativeHandler) {
            mVibratorId = vibratorId;
            mHalSupplier = supplier;
            mHandler = handler;
            mNativeHandler = nativeHandler;
            mVibratorInfo = new VibratorInfo.Builder(vibratorId).build();
            mCurrentState = State.IDLE;
            mCurrentAmplitude = 0;
        }

        @Override
        public void init(@NonNull Callbacks callbacks) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#init");
            try {
                mCallbacks = callbacks;
                int capabilities = getValue(IVibrator::getCapabilities,
                        "Error loading capabilities during init").orElse(0);

                // Reset the hardware to a default state.
                // In case this is a runtime restart instead of a fresh boot.
                if ((capabilities & IVibrator.CAP_EXTERNAL_CONTROL) != 0) {
                    VintfUtils.runNoThrow(mHalSupplier,
                            hal -> hal.setExternalControl(false),
                            e -> logError("Error resetting external control", e));
                }
                VintfUtils.runNoThrow(mHalSupplier,
                        IVibrator::off, e -> logError("Error turning vibrator off", e));
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public void onSystemReady() {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#onSystemReady");
            try {
                VibratorInfo info = loadVibratorInfo(mVibratorId);
                synchronized (mLock) {
                    mVibratorInfo = info;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @NonNull
        @Override
        public VibratorInfo getInfo() {
            return mVibratorInfo;
        }

        @Override
        public boolean isVibrating() {
            return mCurrentState != State.IDLE;
        }

        @Override
        public boolean registerVibratorStateListener(@NonNull IVibratorStateListener listener) {
            final long token = Binder.clearCallingIdentity();
            try {
                // Register the listener and send the first state atomically, to avoid potentially
                // out of order broadcasts in between.
                synchronized (mLock) {
                    if (!mVibratorStateListeners.register(listener)) {
                        return false;
                    }
                    // Notify its callback after new client registered.
                    notifyStateListener(listener, isVibrating());
                }
                return true;
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public boolean unregisterVibratorStateListener(@NonNull IVibratorStateListener listener) {
            final long token = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    return mVibratorStateListeners.unregister(listener);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public float getCurrentAmplitude() {
            return mCurrentAmplitude;
        }

        @Override
        public boolean setExternalControl(boolean enabled) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR,
                    enabled ? "DefaultHalVibrator#enableExternalControl"
                            : "DefaultHalVibrator#disableExternalControl");
            try {
                State newState = enabled ? State.UNDER_EXTERNAL_CONTROL : State.IDLE;
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_EXTERNAL_CONTROL)) {
                        return false;
                    }
                    boolean result = VintfUtils.runNoThrow(mHalSupplier,
                            hal -> hal.setExternalControl(enabled),
                            e -> logError("Error setting external control to " + enabled, e));
                    if (result) {
                        updateStateAndNotifyListenersLocked(newState);
                    }
                    return result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public boolean setAlwaysOn(int id, @Nullable PrebakedSegment prebaked) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR,
                    prebaked != null ? "DefaultHalVibrator#alwaysOnEnable"
                                     : "DefaultHalVibrator#alwaysOnDisable");
            try {
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_ALWAYS_ON_CONTROL)) {
                        return false;
                    }
                    boolean result;
                    if (prebaked == null) {
                        result = VintfUtils.runNoThrow(mHalSupplier,
                                hal -> hal.alwaysOnDisable(id),
                                e -> logError("Error disabling always-on id " + id, e));
                    } else {
                        int effectId = prebaked.getEffectId();
                        byte strength = (byte) prebaked.getEffectStrength();
                        result = VintfUtils.runNoThrow(mHalSupplier,
                                hal -> hal.alwaysOnEnable(id, effectId, strength),
                                e -> logError("Error enabling always-on id " + id + " for effect "
                                        + effectIdToString(effectId) + " with strength "
                                        + effectStrengthToString(strength), e));
                    }
                    return result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public boolean setAmplitude(float amplitude) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#setAmplitude");
            try {
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_AMPLITUDE_CONTROL)) {
                        return false;
                    }
                    boolean result = VintfUtils.runNoThrow(mHalSupplier,
                                hal -> hal.setAmplitude(amplitude),
                                e -> logError("Error setting amplitude to " + amplitude, e));
                    if (result && mCurrentState == State.VIBRATING) {
                        mCurrentAmplitude = amplitude;
                    }
                    return result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public long on(long vibrationId, long stepId, long milliseconds) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#on");
            try {
                synchronized (mLock) {
                    int result;
                    if (mVibratorInfo.hasCapability(IVibrator.CAP_ON_CALLBACK)) {
                        // Delegate vibrate with callback to native, to avoid creating a new
                        // callback instance for each call, overloading the GC.
                        result = mNativeHandler.vibrateWithCallback(mVibratorId, vibrationId,
                                stepId, (int) milliseconds);
                    } else {
                        // Vibrate callback not supported, avoid unnecessary JNI round trip and
                        // simulate HAL callback here using a Handler.
                        result = vibrateNoThrow(
                                hal -> hal.on((int) milliseconds, null),
                                (int) milliseconds,
                                e -> logError("Error turning on for " + milliseconds + "ms", e));
                        if (result > 0) {
                            mHandler.postDelayed(newVibrationCallback(vibrationId, stepId),
                                    milliseconds);
                        }
                    }
                    if (result > 0) {
                        updateStateAndNotifyListenersLocked(State.VIBRATING);
                    }
                    return result > 0 ? milliseconds : result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public long on(long vibrationId, long stepId, VibrationEffect.VendorEffect effect) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#on (vendor)");
            try {
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_PERFORM_VENDOR_EFFECTS)) {
                        return 0;
                    }
                    // Delegate vibrate with callback to native, to avoid creating a new
                    // callback instance for each call, overloading the GC.
                    VendorEffect vendorEffect = new VendorEffect();
                    vendorEffect.vendorData = effect.getVendorData();
                    vendorEffect.vendorScale = effect.getAdaptiveScale();
                    vendorEffect.scale = effect.getScale();
                    vendorEffect.strength = (byte) effect.getEffectStrength();
                    int result = mNativeHandler.vibrateWithCallback(mVibratorId, vibrationId,
                            stepId, vendorEffect);
                    if (result > 0) {
                        updateStateAndNotifyListenersLocked(State.VIBRATING);
                    }
                    // Vendor effect durations are unknown to the framework.
                    return result > 0 ? Long.MAX_VALUE : result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public long on(long vibrationId, long stepId, PrebakedSegment prebaked) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#on (prebaked)");
            try {
                synchronized (mLock) {
                    int result;
                    if (mVibratorInfo.hasCapability(IVibrator.CAP_PERFORM_CALLBACK)) {
                        // Delegate vibrate with callback to native, to avoid creating a new
                        // callback instance for each call, overloading the GC.
                        result = mNativeHandler.vibrateWithCallback(mVibratorId, vibrationId,
                                stepId, prebaked.getEffectId(), prebaked.getEffectStrength());
                    } else {
                        // Vibrate callback not supported, avoid unnecessary JNI round trip and
                        // simulate HAL callback here using a Handler.
                        int effectId = prebaked.getEffectId();
                        byte strength = (byte) prebaked.getEffectStrength();
                        result = vibrateNoThrow(
                                hal -> hal.perform(effectId, strength, null),
                                e -> logError("Error performing effect "
                                        + VibrationEffect.effectIdToString(effectId)
                                        + " with strength "
                                        + VibrationEffect.effectStrengthToString(strength), e));
                        if (result > 0) {
                            mHandler.postDelayed(newVibrationCallback(vibrationId, stepId), result);
                        }
                    }
                    if (result > 0) {
                        updateStateAndNotifyListenersLocked(State.VIBRATING);
                    }
                    return result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public long on(long vibrationId, long stepId, PrimitiveSegment[] primitives) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#on (primitives)");
            try {
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_COMPOSE_EFFECTS)) {
                        return 0;
                    }
                    // Delegate vibrate with callback to native, to avoid creating a new
                    // callback instance for each call, overloading the GC.
                    CompositeEffect[] effects = new CompositeEffect[primitives.length];
                    long durationMs = 0;
                    for (int i = 0; i < primitives.length; i++) {
                        effects[i] = new CompositeEffect();
                        effects[i].primitive = primitives[i].getPrimitiveId();
                        effects[i].scale = primitives[i].getScale();
                        effects[i].delayMs = primitives[i].getDelay();
                        durationMs += mVibratorInfo.getPrimitiveDuration(effects[i].primitive);
                        durationMs += effects[i].delayMs;
                    }
                    int result = mNativeHandler.vibrateWithCallback(mVibratorId, vibrationId,
                            stepId, effects);
                    if (result > 0) {
                        updateStateAndNotifyListenersLocked(State.VIBRATING);
                    }
                    return result > 0 ? durationMs : result;
                }
            } catch (BadParcelableException e) {
                logError("Error sending parcelable to JNI", e);
                return -1;
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public long on(long vibrationId, long stepId, RampSegment[] primitives) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#on (pwle v1)");
            try {
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_COMPOSE_PWLE_EFFECTS)) {
                        return 0;
                    }
                    // Delegate vibrate with callback to native, to avoid creating a new
                    // callback instance for each call, overloading the GC.
                    PrimitivePwle[] effects = new PrimitivePwle[primitives.length];
                    long durationMs = 0;
                    for (int i = 0; i < primitives.length; i++) {
                        ActivePwle pwle = new ActivePwle();
                        pwle.startAmplitude = primitives[i].getStartAmplitude();
                        pwle.startFrequency = primitives[i].getStartFrequencyHz();
                        pwle.endAmplitude = primitives[i].getEndAmplitude();
                        pwle.endFrequency = primitives[i].getEndFrequencyHz();
                        pwle.duration = (int) primitives[i].getDuration();
                        effects[i] = PrimitivePwle.active(pwle);
                        durationMs += pwle.duration;
                    }
                    int result = mNativeHandler.vibrateWithCallback(mVibratorId, vibrationId,
                            stepId, effects);
                    if (result > 0) {
                        updateStateAndNotifyListenersLocked(State.VIBRATING);
                    }
                    return result > 0 ? durationMs : result;
                }
            } catch (BadParcelableException e) {
                logError("Error sending parcelable to JNI", e);
                return -1;
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public long on(long vibrationId, long stepId, PwlePoint[] pwlePoints) {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#on (pwle v2)");
            try {
                synchronized (mLock) {
                    if (!mVibratorInfo.hasCapability(IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2)) {
                        return 0;
                    }
                    // Delegate vibrate with callback to native, to avoid creating a new
                    // callback instance for each call, overloading the GC.
                    CompositePwleV2 composite = new CompositePwleV2();
                    composite.pwlePrimitives = new PwleV2Primitive[pwlePoints.length];
                    long durationMs = 0;
                    for (int i = 0; i < pwlePoints.length; i++) {
                        composite.pwlePrimitives[i] = new PwleV2Primitive();
                        composite.pwlePrimitives[i].amplitude = pwlePoints[i].getAmplitude();
                        composite.pwlePrimitives[i].frequencyHz = pwlePoints[i].getFrequencyHz();
                        composite.pwlePrimitives[i].timeMillis = pwlePoints[i].getTimeMillis();
                        durationMs += pwlePoints[i].getTimeMillis();
                    }
                    int result = mNativeHandler.vibrateWithCallback(mVibratorId, vibrationId,
                            stepId, composite);
                    if (result > 0) {
                        updateStateAndNotifyListenersLocked(State.VIBRATING);
                    }
                    return result > 0 ? durationMs : result;
                }
            } catch (BadParcelableException e) {
                logError("Error sending parcelable to JNI", e);
                return -1;
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public boolean off() {
            Trace.traceBegin(TRACE_TAG_VIBRATOR, "DefaultHalVibrator#off");
            try {
                synchronized (mLock) {
                    boolean result = VintfUtils.runNoThrow(mHalSupplier,
                            IVibrator::off, e -> logError("Error turning off vibrator", e));
                    updateStateAndNotifyListenersLocked(State.IDLE);
                    return result;
                }
            } finally {
                Trace.traceEnd(TRACE_TAG_VIBRATOR);
            }
        }

        @Override
        public void dump(IndentingPrintWriter pw) {
            VibratorInfo info;
            int listenerCount;
            synchronized (mLock) {
                info = mVibratorInfo;
                listenerCount = mVibratorStateListeners.getRegisteredCallbackCount();
            }
            pw.println("Default HAL Vibrator (id=" + mVibratorId + "):");
            pw.increaseIndent();
            pw.println("currentState = " + mCurrentState.name());
            pw.println("currentAmplitude = " + mCurrentAmplitude);
            pw.println("vibratorStateListener count= " + listenerCount);
            info.dump(pw);
            pw.decreaseIndent();
        }

        @Override
        public String toString() {
            VibratorInfo info;
            int listenerCount;
            synchronized (mLock) {
                info = mVibratorInfo;
                listenerCount = mVibratorStateListeners.getRegisteredCallbackCount();
            }
            return "DefaultHalVibrator{"
                    + "mVibratorInfo=" + info
                    + ", mCurrentState=" + mCurrentState.name()
                    + ", mCurrentAmplitude=" + mCurrentAmplitude
                    + ", mVibratorStateListener count=" + listenerCount
                    + '}';
        }

        @GuardedBy("mLock")
        private void updateStateAndNotifyListenersLocked(State state) {
            boolean previousIsVibrating = isVibrating();
            mCurrentState = state;
            final boolean newIsVibrating = isVibrating();
            mCurrentAmplitude = newIsVibrating ? -1 : 0;
            if (previousIsVibrating != newIsVibrating) {
                // The broadcast method is safe w.r.t. register/unregister listener methods, but
                // lock is required here to guarantee delivery order.
                mVibratorStateListeners.broadcast(
                        listener -> notifyStateListener(listener, newIsVibrating));
            }
        }

        private void notifyStateListener(IVibratorStateListener listener, boolean isVibrating) {
            try {
                listener.onVibrating(isVibrating);
            } catch (RemoteException | RuntimeException e) {
                logError("Vibrator state listener failed to call", e);
            }
        }

        private int vibrateNoThrow(VintfUtils.VintfRunnable<IVibrator> fn, int successResult,
                Consumer<Throwable> errorHandler) {
            return vibrateNoThrow(
                    hal -> {
                        fn.run(hal);
                        return successResult;
                    }, errorHandler);
        }

        private int vibrateNoThrow(VintfUtils.VintfGetter<IVibrator, Integer> fn,
                Consumer<Throwable> errorHandler) {
            try {
                return VintfUtils.get(mHalSupplier, fn);
            } catch (RuntimeException e) {
                errorHandler.accept(e);
                if (e instanceof UnsupportedOperationException) {
                    return 0;
                }
                return -1;
            }
        }

        private Runnable newVibrationCallback(long vibrationId, long stepId) {
            return () -> mCallbacks.onVibrationStepComplete(mVibratorId, vibrationId, stepId);
        }

        private VibratorInfo loadVibratorInfo(int vibratorId) {
            VibratorInfo.Builder builder = new VibratorInfo.Builder(vibratorId);
            int capabilities = getValue(IVibrator::getCapabilities, "Error loading capabilities")
                    .orElse(0);
            builder.setCapabilities(capabilities);
            getValue(IVibrator::getSupportedEffects, "Error loading supported effects")
                    .ifPresent(builder::setSupportedEffects);
            if ((capabilities & IVibrator.CAP_GET_Q_FACTOR) != 0) {
                getValue(IVibrator::getQFactor, "Error loading q-factor")
                        .ifPresent(builder::setQFactor);
            }

            loadInfoForPrimitives(builder, capabilities);
            loadInfoForPwleV1(builder, capabilities);
            loadInfoForPwleV2(builder, capabilities);

            float resonantFrequency;
            if ((capabilities & IVibrator.CAP_GET_RESONANT_FREQUENCY) != 0) {
                resonantFrequency = getValue(IVibrator::getResonantFrequency,
                        "Error loading resonant frequency").orElse(Float.NaN);
            } else {
                resonantFrequency = Float.NaN;
            }

            builder.setFrequencyProfileLegacy(
                    loadFrequencyProfileLegacy(capabilities, resonantFrequency));
            builder.setFrequencyProfile(
                    loadFrequencyProfile(capabilities, resonantFrequency));

            return builder.build();
        }

        private void loadInfoForPrimitives(VibratorInfo.Builder builder, int capabilities) {
            if ((capabilities & IVibrator.CAP_COMPOSE_EFFECTS) == 0) {
                return;
            }
            getValue(IVibrator::getCompositionSizeMax, "Error loading composition size max")
                    .ifPresent(builder::setCompositionSizeMax);
            getValue(IVibrator::getCompositionDelayMax, "Error loading composition delay max")
                    .ifPresent(builder::setPrimitiveDelayMax);
            int[] supportedPrimitives = getValue(IVibrator::getSupportedPrimitives,
                    "Error loading supported primitives").orElse(null);

            if (supportedPrimitives != null) {
                for (int primitive : supportedPrimitives) {
                    Optional<Integer> primitiveDuration = getValue(
                            hal -> hal.getPrimitiveDuration(primitive),
                            // Only concatenate the strings if logging error.
                            () -> "Error loading duration for primitive " + primitive);
                    primitiveDuration.ifPresent(
                            duration -> builder.setSupportedPrimitive(primitive, duration));
                }
            }
        }

        @SuppressWarnings("deprecation") // Loading deprecated values for compatibility
        private void loadInfoForPwleV1(VibratorInfo.Builder builder, int capabilities) {
            if ((capabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS) == 0) {
                return;
            }
            getValue(IVibrator::getPwleCompositionSizeMax, "Error loading PWLE V1 size max")
                    .ifPresent(builder::setPwleSizeMax);
            getValue(IVibrator::getPwlePrimitiveDurationMax, "Error loading PWLE V1 duration max")
                    .ifPresent(builder::setPwlePrimitiveDurationMax);
            getValue(IVibrator::getSupportedBraking, "Error loading PWLE V1 supported braking")
                    .ifPresent(builder::setSupportedBraking);
        }

        private void loadInfoForPwleV2(VibratorInfo.Builder builder, int capabilities) {
            if ((capabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2) == 0) {
                return;
            }
            getValue(IVibrator::getPwleV2CompositionSizeMax, "Error loading PWLE V2 size max")
                    .ifPresent(builder::setMaxEnvelopeEffectSize);
            getValue(IVibrator::getPwleV2PrimitiveDurationMinMillis,
                    "Error loading PWLE V2 duration min")
                    .ifPresent(builder::setMinEnvelopeEffectControlPointDurationMillis);
            getValue(IVibrator::getPwleV2PrimitiveDurationMaxMillis,
                    "Error loading PWLE V2 duration max")
                    .ifPresent(builder::setMaxEnvelopeEffectControlPointDurationMillis);
        }

        @SuppressWarnings("deprecation") // Loading deprecated values for compatibility
        private VibratorInfo.FrequencyProfileLegacy loadFrequencyProfileLegacy(
                int capabilities, float resonantFrequency) {
            if ((capabilities & IVibrator.CAP_FREQUENCY_CONTROL) == 0) {
                return new VibratorInfo.FrequencyProfileLegacy(
                        resonantFrequency, Float.NaN, Float.NaN, null);
            }
            float minFrequency = getValue(IVibrator::getFrequencyMinimum,
                    "Error loading frequency min").orElse(Float.NaN);
            float frequencyResolution = getValue(IVibrator::getFrequencyResolution,
                    "Error loading frequency resolution").orElse(Float.NaN);
            float[] bandwidthMap = getValue(IVibrator::getBandwidthAmplitudeMap,
                    "Error loading bandwidth map").orElse(null);
            return new VibratorInfo.FrequencyProfileLegacy(resonantFrequency, minFrequency,
                    frequencyResolution, bandwidthMap);
        }

        private VibratorInfo.FrequencyProfile loadFrequencyProfile(
                int capabilities, float resonantFrequency) {
            if ((capabilities & IVibrator.CAP_FREQUENCY_CONTROL) == 0) {
                return new VibratorInfo.FrequencyProfile(resonantFrequency, null, null);
            }
            float[] frequencies = null;
            float[] outputs = null;
            List<FrequencyAccelerationMapEntry> map =
                    getValue(IVibrator::getFrequencyToOutputAccelerationMap,
                            "Error loading frequency acceleration map").orElse(null);
            if (map != null) {
                int entryCount = map.size();
                frequencies = new float[entryCount];
                outputs = new float[entryCount];
                for (int i = 0; i < entryCount; i++) {
                    FrequencyAccelerationMapEntry entry = map.get(i);
                    frequencies[i] = entry.frequencyHz;
                    outputs[i] = entry.maxOutputAccelerationGs;
                }
            }
            return new VibratorInfo.FrequencyProfile(resonantFrequency, frequencies, outputs);
        }

        private <T> Optional<T> getValue(VintfGetter<IVibrator, T> getter, String errorMessage) {
            return VintfUtils.getNoThrow(mHalSupplier, getter, e -> logError(errorMessage, e));
        }

        private <T> Optional<T> getValue(VintfGetter<IVibrator, T> getter,
                Supplier<String> errorMessage) {
            return VintfUtils.getNoThrow(mHalSupplier, getter,
                    e -> logError(errorMessage.get(), e));
        }

        private void logError(String message, Throwable error) {
            Slog.e(TAG, "[Vibrator id=" + mVibratorId + "] " + message, error);
        }
    }
}
