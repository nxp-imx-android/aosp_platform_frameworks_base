/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "perf_hint"

#include <adpf/api/PerformanceHintFeature.h>
#include <adpf/api/PerformanceHintManager.h>
#include <adpf/api/PerformanceHintSession.h>
#include <adpf/api/SessionCreationConfig.h>
#include <aidl/android/hardware/power/SessionHint.h>
#include <aidl/android/hardware/power/SessionTag.h>
#include <android/performance_hint.h>
#include <binder/Binder.h>
#include <performance_hint_private.h>
#include <utils/Log.h>

using namespace android;

namespace hal = aidl::android::hardware::power;
namespace api = android::adpf::api;
struct AWorkDuration : public api::WorkDuration {};
struct APerformanceHintSession : public api::PerformanceHintSession {};
struct APerformanceHintManager : public api::PerformanceHintManager {};
struct ASessionCreationConfig : public api::SessionCreationConfig {};

APerformanceHintManager* APerformanceHint_getManager() {
    return static_cast<APerformanceHintManager*>(APerformanceHintManager::getInstance());
}

#define VALIDATE_PTR(ptr) \
    LOG_ALWAYS_FATAL_IF(ptr == nullptr, "%s: " #ptr " is nullptr", __FUNCTION__);

#define HARD_VALIDATE_INT(value, cmp)                                        \
    LOG_ALWAYS_FATAL_IF(!(value cmp),                                        \
                        "%s: Invalid value. Check failed: (" #value " " #cmp \
                        ") with value: %" PRIi64,                            \
                        __FUNCTION__, static_cast<int64_t>(value));

#define VALIDATE_INT(value, cmp)                                                             \
    if (!(value cmp)) {                                                                      \
        ALOGE("%s: Invalid value. Check failed: (" #value " " #cmp ") with value: %" PRIi64, \
              __FUNCTION__, static_cast<int64_t>(value));                                    \
        return EINVAL;                                                                       \
    }

#define WARN_INT(value, cmp)                                                                 \
    if (!(value cmp)) {                                                                      \
        ALOGE("%s: Invalid value. Check failed: (" #value " " #cmp ") with value: %" PRIi64, \
              __FUNCTION__, value);                                                          \
    }

APerformanceHintSession* APerformanceHint_createSession(APerformanceHintManager* manager,
                                                        const int32_t* threadIds, size_t size,
                                                        int64_t initialTargetWorkDurationNanos) {
    VALIDATE_PTR(manager)
    VALIDATE_PTR(threadIds)
    return static_cast<APerformanceHintSession*>(
            manager->createSession(threadIds, size, initialTargetWorkDurationNanos));
}

int APerformanceHint_createSessionUsingConfig(APerformanceHintManager* manager,
                                              ASessionCreationConfig* sessionCreationConfig,
                                              APerformanceHintSession** sessionOut) {
    VALIDATE_PTR(manager);
    VALIDATE_PTR(sessionCreationConfig);
    VALIDATE_PTR(sessionOut);
    *sessionOut = nullptr;

    return manager->createSessionUsingConfig(sessionCreationConfig,
                                             reinterpret_cast<api::PerformanceHintSession**>(
                                                     sessionOut));
}

int APerformanceHint_createSessionUsingConfigInternal(APerformanceHintManager* manager,
                                                      ASessionCreationConfig* sessionCreationConfig,
                                                      APerformanceHintSession** sessionOut,
                                                      SessionTag tag) {
    VALIDATE_PTR(manager);
    VALIDATE_PTR(sessionCreationConfig);
    VALIDATE_PTR(sessionOut);
    *sessionOut = nullptr;

    return manager->createSessionUsingConfig(sessionCreationConfig,
                                             reinterpret_cast<api::PerformanceHintSession**>(
                                                     sessionOut),
                                             static_cast<hal::SessionTag>(tag));
}

APerformanceHintSession* APerformanceHint_createSessionInternal(
        APerformanceHintManager* manager, const int32_t* threadIds, size_t size,
        int64_t initialTargetWorkDurationNanos, SessionTag tag) {
    VALIDATE_PTR(manager)
    VALIDATE_PTR(threadIds)
    return static_cast<APerformanceHintSession*>(
            manager->createSession(threadIds, size, initialTargetWorkDurationNanos,
                                   static_cast<hal::SessionTag>(tag)));
}

APerformanceHintSession* APerformanceHint_createSessionFromJava(
        APerformanceHintManager* manager, const int32_t* threadIds, size_t size,
        int64_t initialTargetWorkDurationNanos) {
    VALIDATE_PTR(manager)
    VALIDATE_PTR(threadIds)
    return static_cast<APerformanceHintSession*>(
            manager->createSession(threadIds, size, initialTargetWorkDurationNanos,
                                   hal::SessionTag::APP, true));
}

APerformanceHintSession* APerformanceHint_borrowSessionFromJava(JNIEnv* env, jobject sessionObj) {
    VALIDATE_PTR(env)
    VALIDATE_PTR(sessionObj)
    return static_cast<APerformanceHintSession*>(
            APerformanceHintManager::getInstance()->getSessionFromJava(env, sessionObj));
}

int64_t APerformanceHint_getPreferredUpdateRateNanos(APerformanceHintManager* manager) {
    VALIDATE_PTR(manager)
    return manager->getPreferredRateNanos();
}

int APerformanceHint_getMaxGraphicsPipelineThreadsCount(APerformanceHintManager* manager) {
    VALIDATE_PTR(manager);
    return manager->getMaxGraphicsPipelineThreadsCount();
}

int APerformanceHint_updateTargetWorkDuration(APerformanceHintSession* session,
                                              int64_t targetDurationNanos) {
    VALIDATE_PTR(session)
    return session->updateTargetWorkDuration(targetDurationNanos);
}

int APerformanceHint_reportActualWorkDuration(APerformanceHintSession* session,
                                              int64_t actualDurationNanos) {
    VALIDATE_PTR(session)
    return session->reportActualWorkDuration(actualDurationNanos);
}

void APerformanceHint_closeSession(APerformanceHintSession* session) {
    VALIDATE_PTR(session)
    if (session->isJava()) {
        LOG_ALWAYS_FATAL("%s: Java-owned PerformanceHintSession cannot be closed in native",
                         __FUNCTION__);
        return;
    }
    delete session;
}

void APerformanceHint_closeSessionFromJava(APerformanceHintSession* session) {
    VALIDATE_PTR(session)
    delete session;
}

int APerformanceHint_sendHint(APerformanceHintSession* session, SessionHint hint) {
    VALIDATE_PTR(session)
    return session->sendHint(static_cast<hal::SessionHint>(hint), "HWUI hint");
}

int APerformanceHint_setThreads(APerformanceHintSession* session, const pid_t* threadIds,
                                size_t size) {
    VALIDATE_PTR(session)
    VALIDATE_PTR(threadIds)
    return session->setThreads(threadIds, size);
}

int APerformanceHint_getThreadIds(APerformanceHintSession* session, int32_t* const threadIds,
                                  size_t* const size) {
    VALIDATE_PTR(session)
    return session->getThreadIds(threadIds, size);
}

int APerformanceHint_setPreferPowerEfficiency(APerformanceHintSession* session, bool enabled) {
    VALIDATE_PTR(session)
    return session->setPreferPowerEfficiency(enabled);
}

int APerformanceHint_reportActualWorkDuration2(APerformanceHintSession* session,
                                               AWorkDuration* workDurationPtr) {
    if (session->getReturnedSessionTag() == hal::SessionTag::GAME) {
        ALOGV("Function called from a game, returning without reporting.");
        return 0;
    }
    VALIDATE_PTR(session)
    VALIDATE_PTR(workDurationPtr)
    return session->reportActualWorkDuration(workDurationPtr);
}

int APerformanceHint_notifyWorkloadIncrease(APerformanceHintSession* session, bool cpu, bool gpu,
                                            const char* debugName) {
    VALIDATE_PTR(session)
    VALIDATE_PTR(debugName)
    return session->notifyWorkloadIncrease(cpu, gpu, debugName);
}

int APerformanceHint_notifyWorkloadReset(APerformanceHintSession* session, bool cpu, bool gpu,
                                         const char* debugName) {
    VALIDATE_PTR(session)
    VALIDATE_PTR(debugName)
    return session->notifyWorkloadReset(cpu, gpu, debugName);
}

int APerformanceHint_notifyWorkloadSpike(APerformanceHintSession* session, bool cpu, bool gpu,
                                         const char* debugName) {
    VALIDATE_PTR(session)
    VALIDATE_PTR(debugName)
    return session->notifyWorkloadSpike(cpu, gpu, debugName);
}

int APerformanceHint_setNativeSurfaces(APerformanceHintSession* session,
                                       ANativeWindow** nativeWindows, size_t nativeWindowsSize,
                                       ASurfaceControl** surfaceControls,
                                       size_t surfaceControlsSize) {
    VALIDATE_PTR(session)
    return session->setNativeSurfaces(nativeWindows, nativeWindowsSize, surfaceControls,
                                      surfaceControlsSize);
}

bool APerformanceHint_isFeatureSupported(APerformanceHintFeature feature) {
    api::PerformanceHintManager* manager = api::PerformanceHintManager::getInstance();
    if (manager == nullptr) {
        // Clearly whatever it is isn't supported in this case
        return false;
    }
    return manager->isFeatureSupported(static_cast<api::PerformanceHintFeature>(feature));
}

AWorkDuration* AWorkDuration_create() {
    return new AWorkDuration();
}

void AWorkDuration_release(AWorkDuration* aWorkDuration) {
    VALIDATE_PTR(aWorkDuration)
    delete aWorkDuration;
}

void AWorkDuration_setActualTotalDurationNanos(AWorkDuration* aWorkDuration,
                                               int64_t actualTotalDurationNanos) {
    VALIDATE_PTR(aWorkDuration)
    WARN_INT(actualTotalDurationNanos, > 0)
    aWorkDuration->durationNanos = actualTotalDurationNanos;
}

void AWorkDuration_setWorkPeriodStartTimestampNanos(AWorkDuration* aWorkDuration,
                                                    int64_t workPeriodStartTimestampNanos) {
    VALIDATE_PTR(aWorkDuration)
    WARN_INT(workPeriodStartTimestampNanos, > 0)
    aWorkDuration->workPeriodStartTimestampNanos = workPeriodStartTimestampNanos;
}

void AWorkDuration_setActualCpuDurationNanos(AWorkDuration* aWorkDuration,
                                             int64_t actualCpuDurationNanos) {
    VALIDATE_PTR(aWorkDuration)
    WARN_INT(actualCpuDurationNanos, >= 0)
    aWorkDuration->cpuDurationNanos = actualCpuDurationNanos;
}

void AWorkDuration_setActualGpuDurationNanos(AWorkDuration* aWorkDuration,
                                             int64_t actualGpuDurationNanos) {
    VALIDATE_PTR(aWorkDuration)
    WARN_INT(actualGpuDurationNanos, >= 0)
    aWorkDuration->gpuDurationNanos = actualGpuDurationNanos;
}

ASessionCreationConfig* ASessionCreationConfig_create() {
    return new ASessionCreationConfig();
}

void ASessionCreationConfig_release(ASessionCreationConfig* config) {
    VALIDATE_PTR(config)
    delete config;
}

void ASessionCreationConfig_setTids(ASessionCreationConfig* config, const pid_t* tids,
                                    size_t size) {
    VALIDATE_PTR(config)
    VALIDATE_PTR(tids)
    HARD_VALIDATE_INT(size, > 0)

    config->tids = std::vector<int32_t>(tids, tids + size);
}

void ASessionCreationConfig_setTargetWorkDurationNanos(ASessionCreationConfig* config,
                                                       int64_t targetWorkDurationNanos) {
    VALIDATE_PTR(config)
    config->targetWorkDurationNanos = targetWorkDurationNanos;
}

void ASessionCreationConfig_setPreferPowerEfficiency(ASessionCreationConfig* config, bool enabled) {
    VALIDATE_PTR(config)
    config->setMode(hal::SessionMode::POWER_EFFICIENCY, enabled);
}

void ASessionCreationConfig_setGraphicsPipeline(ASessionCreationConfig* config, bool enabled) {
    VALIDATE_PTR(config)
    config->setMode(hal::SessionMode::GRAPHICS_PIPELINE, enabled);
}

void ASessionCreationConfig_setAudioPerformance(ASessionCreationConfig* config, bool enabled) {
    if (!android::os::adpf_audio_performance()) {
        ALOGE("%s: adpf_audio_performance flag not enabled.", __FUNCTION__);
        return;
    }
    VALIDATE_PTR(config)
    config->setMode(hal::SessionMode::AUDIO_PERFORMANCE, enabled);
}

void ASessionCreationConfig_setNativeSurfaces(ASessionCreationConfig* config,
                                              ANativeWindow** nativeWindows,
                                              size_t nativeWindowsSize,
                                              ASurfaceControl** surfaceControls,
                                              size_t surfaceControlsSize) {
    VALIDATE_PTR(config)
    api::helper::layersFromNativeSurfaces<wp<IBinder>>(nativeWindows, nativeWindowsSize,
                                                       surfaceControls, surfaceControlsSize,
                                                       config->layers);
}

void ASessionCreationConfig_setUseAutoTiming(ASessionCreationConfig* _Nonnull config, bool cpu,
                                             bool gpu) {
    VALIDATE_PTR(config)
    config->setMode(hal::SessionMode::AUTO_CPU, cpu);
    config->setMode(hal::SessionMode::AUTO_GPU, gpu);
}
