/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.Nullable;
import android.hardware.vibrator.IVibrator;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.VibrationEffect;
import android.os.VibrationEffect.VendorEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides {@link HalVibrator} with configurable vibrator hardware capabilities and
 * fake interactions for tests.
 */
public final class HalVibratorHelper {
    public static final long EFFECT_DURATION = 20;

    private final Handler mHandler;

    // Iterate over maps in key order (i.e. vibration id) by using TreeMap.
    private final Map<Long, PrebakedSegment> mEnabledAlwaysOnEffects = new TreeMap<>();
    private final Map<Long, List<VibrationEffectSegment>> mEffectSegments = new TreeMap<>();
    private final Map<Long, List<VendorEffect>> mVendorEffects = new TreeMap<>();
    private final Map<Long, List<PwlePoint>> mEffectPwlePoints = new TreeMap<>();
    private final Map<Long, List<Integer>> mBraking = new TreeMap<>();
    private final List<Float> mAmplitudes = new ArrayList<>();
    private final List<Boolean> mExternalControlStates = new ArrayList<>();
    private int mConnectCount;
    private int mOffCount;

    private boolean mLoadInfoShouldFail = false;
    private boolean mOnShouldFail = false;
    private boolean mPrebakedShouldFail = false;
    private boolean mVendorEffectsShouldFail = false;
    private boolean mPrimitivesShouldFail = false;
    private boolean mPwleV1ShouldFail = false;
    private boolean mPwleV2ShouldFail = false;

    private long mCompletionCallbackLatency;
    private long mOnLatency;
    private long mOffLatency;

    private int mCapabilities;
    private int[] mSupportedEffects;
    private int[] mSupportedBraking;
    private int[] mSupportedPrimitives;
    private int mCompositionSizeMax;
    private int mPwleSizeMax;
    private int mMaxEnvelopeEffectSize;
    private int mMinEnvelopeEffectControlPointDurationMillis;
    private int mMaxEnvelopeEffectControlPointDurationMillis;
    private float mMinFrequency = Float.NaN;
    private float mResonantFrequency = Float.NaN;
    private float mFrequencyResolution = Float.NaN;
    private float mQFactor = Float.NaN;
    private float[] mMaxAmplitudes;
    private float[] mFrequenciesHz;
    private float[] mOutputAccelerationsGs;
    private long mVendorEffectDuration = EFFECT_DURATION;
    private long mPrimitiveDuration = EFFECT_DURATION;

    public HalVibratorHelper(Looper looper) {
        mHandler = new Handler(looper);
    }

    /** Return new {@link VibratorController} instance. */
    public VibratorController newVibratorController(int vibratorId) {
        return new VibratorController(vibratorId, new FakeNativeWrapper());
    }

    /** Return new {@link HalVibrator} instance. */
    public HalVibrator newHalVibrator(int vibratorId) {
        return newVibratorController(vibratorId);
    }

    /** Return new {@link HalVibrator} instance after initializing it. */
    public HalVibrator newInitializedHalVibrator(int vibratorId, HalVibrator.Callbacks callbacks) {
        HalVibrator vibrator = newHalVibrator(vibratorId);
        vibrator.init(callbacks);
        vibrator.onSystemReady();
        return vibrator;
    }

    /** Makes get info calls fail. */
    public void setLoadInfoToFail() {
        mLoadInfoShouldFail = true;
    }

    /** Makes vibrator ON(millis) calls fail. */
    public void setOnToFail() {
        mOnShouldFail = true;
    }

    /** Makes vibrator perform prebaked effect calls fail. */
    public void setPrebakedToFail() {
        mPrebakedShouldFail = true;
    }

    /** Makes vibrator perform prebaked effect calls fail. */
    public void setVendorEffectsToFail() {
        mVendorEffectsShouldFail = true;
    }

    /** Makes vibrator compose primitives calls fail. */
    public void setPrimitivesToFail() {
        mPrimitivesShouldFail = true;
    }

    /** Makes vibrator compose PWLE V1 calls fail. */
    public void setPwleV1ToFail() {
        mPwleV1ShouldFail = true;
    }

    /** Makes vibrator compose PWLE V2 calls fail. */
    public void setPwleV2ToFail() {
        mPwleV2ShouldFail = true;
    }

    /** Sets the latency for triggering the vibration completed callback. */
    public void setCompletionCallbackLatency(long millis) {
        mCompletionCallbackLatency = millis;
    }

    /** Sets the latency for turning the vibrator hardware on or setting the vibration amplitude. */
    public void setOnLatency(long millis) {
        mOnLatency = millis;
    }

    /** Sets the latency this controller should fake for turning the vibrator off. */
    public void setOffLatency(long millis) {
        mOffLatency = millis;
    }

    /** Set the capabilities of the fake vibrator hardware. */
    public void setCapabilities(int... capabilities) {
        mCapabilities = Arrays.stream(capabilities).reduce(0, (a, b) -> a | b);
    }

    /** Set the effects supported by the fake vibrator hardware. */
    public void setSupportedEffects(int... effects) {
        if (effects != null) {
            effects = Arrays.copyOf(effects, effects.length);
            Arrays.sort(effects);
        }
        mSupportedEffects = effects;
    }

    /** Set the effects supported by the fake vibrator hardware. */
    public void setSupportedBraking(int... braking) {
        if (braking != null) {
            braking = Arrays.copyOf(braking, braking.length);
            Arrays.sort(braking);
        }
        mSupportedBraking = braking;
    }

    /** Set the primitives supported by the fake vibrator hardware. */
    public void setSupportedPrimitives(int... primitives) {
        if (primitives != null) {
            primitives = Arrays.copyOf(primitives, primitives.length);
            Arrays.sort(primitives);
        }
        mSupportedPrimitives = primitives;
    }

    /** Set the max number of primitives allowed in a composition by the fake vibrator hardware. */
    public void setCompositionSizeMax(int limit) {
        mCompositionSizeMax = limit;
    }

    /** Set the max number of PWLEs allowed in a composition by the fake vibrator hardware. */
    public void setPwleSizeMax(int limit) {
        mPwleSizeMax = limit;
    }

    /** Set the resonant frequency of the fake vibrator hardware. */
    public void setResonantFrequency(float frequencyHz) {
        mResonantFrequency = frequencyHz;
    }

    /** Set the minimum frequency of the fake vibrator hardware. */
    public void setMinFrequency(float frequencyHz) {
        mMinFrequency = frequencyHz;
    }

    /** Set the frequency resolution of the fake vibrator hardware. */
    public void setFrequencyResolution(float frequencyHz) {
        mFrequencyResolution = frequencyHz;
    }

    /** Set the Q factor of the fake vibrator hardware. */
    public void setQFactor(float qFactor) {
        mQFactor = qFactor;
    }

    /** Set the max amplitude supported for each frequency f the fake vibrator hardware. */
    public void setMaxAmplitudes(float... maxAmplitudes) {
        mMaxAmplitudes = maxAmplitudes;
    }

    /** Set the list of available frequencies. */
    public void setFrequenciesHz(float[] frequenciesHz) {
        mFrequenciesHz = frequenciesHz;
    }

    /** Set the max output acceleration achievable by the supported frequencies. */
    public void setOutputAccelerationsGs(float[] accelerationsGs) {
        mOutputAccelerationsGs = accelerationsGs;
    }

    /** Set the duration of vendor effects in fake vibrator hardware. */
    public void setVendorEffectDuration(long millis) {
        mVendorEffectDuration = millis;
    }

    /** Set the duration of primitives in fake vibrator hardware. */
    public void setPrimitiveDuration(long millis) {
        mPrimitiveDuration = millis;
    }

    /** Set the maximum number of control points supported in fake vibrator hardware. */
    public void setMaxEnvelopeEffectSize(int limit) {
        mMaxEnvelopeEffectSize = limit;
    }

    /** Set the minimum segment duration in fake vibrator hardware. */
    public void setMinEnvelopeEffectControlPointDurationMillis(int millis) {
        mMinEnvelopeEffectControlPointDurationMillis = millis;
    }

    /** Set the maximum segment duration in fake vibrator hardware. */
    public void setMaxEnvelopeEffectControlPointDurationMillis(int millis) {
        mMaxEnvelopeEffectControlPointDurationMillis = millis;
    }

    /** Return {@code true} if this controller was initialized. */
    public boolean isInitialized() {
        return mConnectCount > 0;
    }

    /** Return the amplitudes set, including zeroes for each time the vibrator was turned off. */
    public synchronized List<Float> getAmplitudes() {
        return new ArrayList<>(mAmplitudes);
    }

    /** Return the braking values passed to the compose PWLE method. */
    public synchronized List<Integer> getBraking(long vibrationId) {
        return copyRecordsForVibration(mBraking, vibrationId);
    }

    /** Return list of {@link VibrationEffectSegment} played by this controller, in order. */
    public synchronized List<VibrationEffectSegment> getEffectSegments(long vibrationId) {
        return copyRecordsForVibration(mEffectSegments, vibrationId);
    }

    /** Returns a list of all effect segments, for all vibration ID. */
    public synchronized List<VibrationEffectSegment> getAllEffectSegments() {
        // Returns segments in order of vibrationId, which increases over time. TreeMap gives order.
        ArrayList<VibrationEffectSegment> result = new ArrayList<>();
        for (List<VibrationEffectSegment> subList : mEffectSegments.values()) {
            result.addAll(subList);
        }
        return result;
    }

    /** Return list of {@link VendorEffect} played by this controller, in order. */
    public synchronized List<VendorEffect> getVendorEffects(long vibrationId) {
        return copyRecordsForVibration(mVendorEffects, vibrationId);
    }

    /** Returns a list of all vendor effects, for all vibration IDs. */
    public synchronized List<VendorEffect> getAllVendorEffects() {
        // Returns segments in order of vibrationId, which increases over time. TreeMap gives order.
        ArrayList<VendorEffect> result = new ArrayList<>();
        for (List<VendorEffect> subList : mVendorEffects.values()) {
            result.addAll(subList);
        }
        return result;
    }

    /** Return list of {@link PwlePoint} played by this controller, in order. */
    public synchronized List<PwlePoint> getEffectPwlePoints(long vibrationId) {
        return copyRecordsForVibration(mEffectPwlePoints, vibrationId);
    }

    /** Return list of states set for external control to the fake vibrator hardware. */
    public synchronized List<Boolean> getExternalControlStates() {
        return new ArrayList<>(mExternalControlStates);
    }

    /** Returns the number of times the vibrator was turned off. */
    public synchronized int getOffCount() {
        return mOffCount;
    }

    /** Return the {@link PrebakedSegment} effect enabled with given id, or {@code null}. */
    @Nullable
    public synchronized PrebakedSegment getAlwaysOnEffect(int id) {
        return mEnabledAlwaysOnEffects.get((long) id);
    }

    private synchronized void recordEffectSegment(long vibrationId,
            VibrationEffectSegment segment) {
        getRecordsForVibration(mEffectSegments, vibrationId).add(segment);
    }

    private synchronized void recordVendorEffect(long vibrationId, VendorEffect vendorEffect) {
        getRecordsForVibration(mVendorEffects, vibrationId).add(vendorEffect);
    }

    private synchronized void recordEffectPwlePoint(long vibrationId, PwlePoint pwlePoint) {
        getRecordsForVibration(mEffectPwlePoints, vibrationId).add(pwlePoint);
    }

    private synchronized void recordBraking(long vibrationId, int braking) {
        getRecordsForVibration(mBraking, vibrationId).add(braking);
    }

    private static <T> List<T> copyRecordsForVibration(
            Map<Long, List<T>> records, long vibrationId) {
        return new ArrayList<>(getRecordsForVibration(records, vibrationId));
    }

    private static <T> List<T> getRecordsForVibration(
            Map<Long, List<T>> records, long vibrationId) {
        return records.computeIfAbsent(vibrationId, unused -> new ArrayList<>());
    }

    /** Fake {@link VibratorController.NativeWrapper} implementation for testing. */
    private final class FakeNativeWrapper extends VibratorController.NativeWrapper {
        public int vibratorId;
        public HalVibrator.Callbacks listener;

        @Override
        public void init(int vibratorId, HalVibrator.Callbacks listener) {
            mConnectCount++;
            this.vibratorId = vibratorId;
            this.listener = listener;
        }

        @Override
        public long on(long milliseconds, long vibrationId, long stepId) {
            if (mOnShouldFail) {
                return -1;
            }
            recordEffectSegment(vibrationId, new StepSegment(VibrationEffect.DEFAULT_AMPLITUDE,
                    /* frequencyHz= */ 0, (int) milliseconds));
            applyLatency(mOnLatency);
            scheduleListener(milliseconds, vibrationId, stepId);
            return milliseconds;
        }

        @Override
        public void off() {
            mOffCount++;
            applyLatency(mOffLatency);
        }

        @Override
        public void setAmplitude(float amplitude) {
            mAmplitudes.add(amplitude);
            applyLatency(mOnLatency);
        }

        @Override
        public long perform(long effect, long strength, long vibrationId, long stepId) {
            if (mPrebakedShouldFail) {
                return -1;
            }
            if (mSupportedEffects == null
                    || Arrays.binarySearch(mSupportedEffects, (int) effect) < 0) {
                return 0;
            }
            recordEffectSegment(vibrationId,
                    new PrebakedSegment((int) effect, false, (int) strength));
            applyLatency(mOnLatency);
            scheduleListener(EFFECT_DURATION, vibrationId, stepId);
            return EFFECT_DURATION;
        }

        @Override
        public long performVendorEffect(Parcel vendorData, long strength, float scale,
                float adaptiveScale, long vibrationId, long stepId) {
            if (mVendorEffectsShouldFail) {
                return -1;
            }
            if ((mCapabilities & IVibrator.CAP_PERFORM_VENDOR_EFFECTS) == 0) {
                return 0;
            }
            PersistableBundle bundle = PersistableBundle.CREATOR.createFromParcel(vendorData);
            recordVendorEffect(vibrationId,
                    new VendorEffect(bundle, (int) strength, scale, adaptiveScale));
            applyLatency(mOnLatency);
            scheduleListener(mVendorEffectDuration, vibrationId, stepId);
            // HAL has unknown duration for vendor effects.
            return Long.MAX_VALUE;
        }

        @Override
        public long compose(PrimitiveSegment[] primitives, long vibrationId, long stepId) {
            if (mPrimitivesShouldFail) {
                return -1;
            }
            if (mSupportedPrimitives == null
                    || (mCapabilities & IVibrator.CAP_COMPOSE_EFFECTS) == 0) {
                return 0;
            }
            for (PrimitiveSegment primitive : primitives) {
                if (Arrays.binarySearch(mSupportedPrimitives, primitive.getPrimitiveId()) < 0) {
                    return 0;
                }
            }
            long duration = 0;
            for (PrimitiveSegment primitive : primitives) {
                duration += mPrimitiveDuration + primitive.getDelay();
                recordEffectSegment(vibrationId, primitive);
            }
            applyLatency(mOnLatency);
            scheduleListener(duration, vibrationId, stepId);
            return duration;
        }

        @Override
        public long composePwle(RampSegment[] primitives, int braking, long vibrationId,
                long stepId) {
            if (mPwleV1ShouldFail) {
                return -1;
            }
            if ((mCapabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS) == 0) {
                return 0;
            }
            long duration = 0;
            for (RampSegment primitive : primitives) {
                duration += primitive.getDuration();
                recordEffectSegment(vibrationId, primitive);
            }
            recordBraking(vibrationId, braking);
            applyLatency(mOnLatency);
            scheduleListener(duration, vibrationId, stepId);
            return duration;
        }

        @Override
        public long composePwleV2(PwlePoint[] pwlePoints, long vibrationId, long stepId) {
            if (mPwleV2ShouldFail) {
                return -1;
            }
            if ((mCapabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2) == 0) {
                return 0;
            }
            long duration = 0;
            for (PwlePoint pwlePoint: pwlePoints) {
                duration += pwlePoint.getTimeMillis();
                recordEffectPwlePoint(vibrationId, pwlePoint);
            }
            applyLatency(mOnLatency);
            scheduleListener(duration, vibrationId, stepId);

            return duration;
        }

        @Override
        public void setExternalControl(boolean enabled) {
            mExternalControlStates.add(enabled);
        }

        @Override
        public void alwaysOnEnable(long id, long effect, long strength) {
            PrebakedSegment prebaked = new PrebakedSegment((int) effect, false, (int) strength);
            mEnabledAlwaysOnEffects.put(id, prebaked);
        }

        @Override
        public void alwaysOnDisable(long id) {
            mEnabledAlwaysOnEffects.remove(id);
        }

        @Override
        public boolean getInfo(VibratorInfo.Builder infoBuilder) {
            infoBuilder.setCapabilities(mCapabilities);
            infoBuilder.setSupportedBraking(mSupportedBraking);
            infoBuilder.setPwleSizeMax(mPwleSizeMax);
            infoBuilder.setSupportedEffects(mSupportedEffects);
            if (mSupportedPrimitives != null) {
                for (int primitive : mSupportedPrimitives) {
                    infoBuilder.setSupportedPrimitive(primitive, (int) mPrimitiveDuration);
                }
            }
            infoBuilder.setCompositionSizeMax(mCompositionSizeMax);
            infoBuilder.setQFactor(mQFactor);
            infoBuilder.setFrequencyProfileLegacy(new VibratorInfo.FrequencyProfileLegacy(
                    mResonantFrequency, mMinFrequency, mFrequencyResolution, mMaxAmplitudes));
            infoBuilder.setFrequencyProfile(
                    new VibratorInfo.FrequencyProfile(mResonantFrequency, mFrequenciesHz,
                            mOutputAccelerationsGs));
            infoBuilder.setMaxEnvelopeEffectSize(mMaxEnvelopeEffectSize);
            infoBuilder.setMinEnvelopeEffectControlPointDurationMillis(
                    mMinEnvelopeEffectControlPointDurationMillis);
            infoBuilder.setMaxEnvelopeEffectControlPointDurationMillis(
                    mMaxEnvelopeEffectControlPointDurationMillis);
            return !mLoadInfoShouldFail;
        }

        private void applyLatency(long latencyMillis) {
            try {
                if (latencyMillis > 0) {
                    Thread.sleep(latencyMillis);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void scheduleListener(long vibrationDuration, long vibrationId, long stepId) {
            mHandler.postDelayed(
                    () -> listener.onVibrationStepComplete(vibratorId, vibrationId, stepId),
                    vibrationDuration + mCompletionCallbackLatency);
        }
    }
}
