/*
 * Copyright (C) 2026 The Android Open Source Project
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

#include <adpf/api/PerformanceHintManager.h>
#include <adpf/api/PerformanceHintSession.h>

#include <set>

using namespace aidl::android::os;
using android::base::StringPrintf;

#define VALIDATE_INT(value, cmp)                                                             \
    if (!(value cmp)) {                                                                      \
        ALOGE("%s: Invalid value. Check failed: (" #value " " #cmp ") with value: %" PRIi64, \
              __FUNCTION__, value);                                                          \
        return EINVAL;                                                                       \
    }

namespace android::adpf::api {

std::optional<size_t> gReportBatchSizeCap =
        android::os::adpf_cap_max_batch_size() ? std::make_optional(50) : std::nullopt;

static FMQWrapper& getFMQ() {
    return PerformanceHintManager::getInstance()->getFMQWrapper();
}

// Start above the int32 range so we don't collide with config sessions
int64_t PerformanceHintSession::sIDCounter = INT32_MAX;

constexpr int kNumEnums = helper::enum_size<hal::SessionHint>();

namespace testing {

std::optional<bool> gForceNewHintBehavior = false;

void setUseNewLoadHintBehavior(bool enable) {
    gForceNewHintBehavior = enable;
}

void setUseGraphicsPipeline(SessionCreationConfig* config, bool enabled) {
    config->setMode(hal::SessionMode::GRAPHICS_PIPELINE, enabled);
}

void getRateLimiterProperties(int32_t* maxLoadHintsPerInterval, int64_t* loadHintInterval) {
    *maxLoadHintsPerInterval = impl::kMaxLoadHintsPerInterval;
    *loadHintInterval = impl::kLoadHintInterval;
}

void setReportBatchSizeCap(int32_t cap) {
    gReportBatchSizeCap = cap > 0 ? std::make_optional(cap) : std::nullopt;
}

} // namespace testing

PerformanceHintSession::PerformanceHintSession(std::shared_ptr<IHintManager> hintManager,
                                               std::shared_ptr<IHintSession> session,
                                               int64_t preferredRateNanos,
                                               int64_t targetDurationNanos, bool isJava,
                                               std::optional<hal::SessionConfig> sessionConfig,
                                               hal::SessionTag returnedTag)
      : mHintManager(hintManager),
        mHintSession(std::move(session)),
        mPreferredRateNanos(preferredRateNanos),
        mTargetDurationNanos(targetDurationNanos),
        mFirstTargetMetTimestamp(0),
        mLastTargetMetTimestamp(0),
        mLastHintSentTimestamp(std::vector<int64_t>(kNumEnums, 0)),
        mIsJava(isJava),
        mSessionConfig(sessionConfig),
        mReturnedTag(returnedTag) {
    if (sessionConfig->id > INT32_MAX) {
        ALOGE("Session ID too large, must fit 32-bit integer");
    }
    int64_t traceId = sessionConfig.has_value() ? sessionConfig->id : ++sIDCounter;
    mSessionName = android::base::StringPrintf("ADPF Session %" PRId64, traceId);
}

PerformanceHintSession::~PerformanceHintSession() {
    ndk::ScopedAStatus ret = mHintSession->close();
    if (!ret.isOk()) {
        ALOGE("%s: HintSession close failed: %s", __FUNCTION__, ret.getMessage());
    }
}

int PerformanceHintSession::updateTargetWorkDuration(int64_t targetDurationNanos) {
    VALIDATE_INT(targetDurationNanos, >= 0)
    std::scoped_lock lock(impl::sHintMutex);
    if (mTargetDurationNanos == targetDurationNanos) {
        return 0;
    }
    if (!getFMQ().updateTargetWorkDuration(mSessionConfig, targetDurationNanos)) {
        ndk::ScopedAStatus ret = mHintSession->updateTargetWorkDuration(targetDurationNanos);
        if (!ret.isOk()) {
            ALOGE("%s: HintSession updateTargetWorkDuration failed: %s", __FUNCTION__,
                  ret.getMessage());
            return EPIPE;
        }
    }
    mTargetDurationNanos = targetDurationNanos;
    /**
     * Most of the workload is target_duration dependent, so now clear the cached samples
     * as they are most likely obsolete.
     */
    mActualWorkDurations.clear();
    traceBatchSize(0);
    traceTargetDuration(targetDurationNanos);
    mFirstTargetMetTimestamp = 0;
    mLastTargetMetTimestamp = 0;
    return 0;
}

int PerformanceHintSession::reportActualWorkDuration(int64_t actualDurationNanos) {
    VALIDATE_INT(actualDurationNanos, > 0)
    hal::WorkDuration workDuration{.durationNanos = actualDurationNanos,
                                   .workPeriodStartTimestampNanos = 0,
                                   .cpuDurationNanos = actualDurationNanos,
                                   .gpuDurationNanos = 0};
    return reportActualWorkDurationInternal(static_cast<WorkDuration*>(&workDuration));
}

int PerformanceHintSession::reportActualWorkDuration(WorkDuration* workDuration) {
    VALIDATE_INT(workDuration->durationNanos, > 0)
    VALIDATE_INT(workDuration->workPeriodStartTimestampNanos, > 0)
    VALIDATE_INT(workDuration->cpuDurationNanos, >= 0)
    VALIDATE_INT(workDuration->gpuDurationNanos, >= 0)
    VALIDATE_INT(workDuration->gpuDurationNanos + workDuration->cpuDurationNanos, > 0)
    return reportActualWorkDurationInternal(workDuration);
}

int PerformanceHintSession::sendHints(std::vector<hal::SessionHint>& hints, int64_t now,
                                      const char*) {
    auto& supportInfo = PerformanceHintManager::getInstance()->getSupportInfo();

    // Drop all unsupported hints, there's not much point reporting errors or warnings for this
    std::erase_if(hints,
                  [&](hal::SessionHint hint) { return !supportInfo.isSessionHintSupported(hint); });

    if (hints.empty()) {
        // We successfully sent all hints we were able to, technically
        return 0;
    }

    for (auto&& hint : hints) {
        LOG_ALWAYS_FATAL_IF(static_cast<int32_t>(hint) < 0 ||
                                    static_cast<int32_t>(hint) >= kNumEnums,
                            "%s: invalid session hint %d", __FUNCTION__, hint);
    }

    std::scoped_lock lock(impl::sHintMutex);
    if (useNewLoadHintBehavior()) {
        if (!PerformanceHintManager::getInstance()->canSendLoadHints(hints, now)) {
            return EBUSY;
        }
    }
    // keep old rate limiter behavior for legacy flag
    else {
        for (auto&& hint : hints) {
            if (now <
                (mLastHintSentTimestamp[static_cast<int32_t>(hint)] + impl::kSendHintTimeout)) {
                return EBUSY;
            }
        }
    }

    if (!getFMQ().sendHints(mSessionConfig, hints, now)) {
        for (auto&& hint : hints) {
            ndk::ScopedAStatus ret = mHintSession->sendHint(static_cast<int32_t>(hint));

            if (!ret.isOk()) {
                ALOGE("%s: HintSession sendHint failed: %s", __FUNCTION__, ret.getMessage());
                return EPIPE;
            }
        }
    }

    if (!useNewLoadHintBehavior()) {
        for (auto&& hint : hints) {
            mLastHintSentTimestamp[static_cast<int32_t>(hint)] = now;
        }
    }

    if (atrace_is_tag_enabled(ATRACE_TAG_APP)) {
        ATRACE_INSTANT("Sending load hint");
    }

    return 0;
}

int PerformanceHintSession::sendHint(hal::SessionHint hint, const char* debugName) {
    std::vector<hal::SessionHint> hints{hint};
    int64_t now = ::android::uptimeNanos();
    return sendHints(hints, now, "HWUI hint");
}

int PerformanceHintSession::notifyWorkloadIncrease(bool cpu, bool gpu, const char* debugName) {
    std::vector<hal::SessionHint> hints(2);
    hints.clear();
    if (cpu) {
        hints.push_back(hal::SessionHint::CPU_LOAD_UP);
    }
    if (gpu) {
        hints.push_back(hal::SessionHint::GPU_LOAD_UP);
    }
    int64_t now = ::android::uptimeNanos();
    return sendHints(hints, now, debugName);
}

int PerformanceHintSession::notifyWorkloadReset(bool cpu, bool gpu, const char* debugName) {
    std::vector<hal::SessionHint> hints(2);
    hints.clear();
    if (cpu) {
        hints.push_back(hal::SessionHint::CPU_LOAD_RESET);
    }
    if (gpu) {
        hints.push_back(hal::SessionHint::GPU_LOAD_RESET);
    }
    int64_t now = ::android::uptimeNanos();
    return sendHints(hints, now, debugName);
}

int PerformanceHintSession::notifyWorkloadSpike(bool cpu, bool gpu, const char* debugName) {
    std::vector<hal::SessionHint> hints(2);
    hints.clear();
    if (cpu) {
        hints.push_back(hal::SessionHint::CPU_LOAD_SPIKE);
    }
    if (gpu) {
        hints.push_back(hal::SessionHint::GPU_LOAD_SPIKE);
    }
    int64_t now = ::android::uptimeNanos();
    return sendHints(hints, now, debugName);
}

int PerformanceHintSession::setThreads(const int32_t* threadIds, size_t size) {
    if (size == 0) {
        ALOGE("%s: the list of thread ids must not be empty.", __FUNCTION__);
        return EINVAL;
    }
    std::vector<int32_t> tids(threadIds, threadIds + size);
    ndk::ScopedAStatus ret = mHintManager->setHintSessionThreads(mHintSession, tids);

    // Illegal state means there were too many graphics pipeline threads
    if (!ret.isOk() && ret.getExceptionCode() != EX_SERVICE_SPECIFIC) {
        ALOGE("%s: failed: %s", __FUNCTION__, ret.getMessage());
        if (ret.getExceptionCode() == EX_ILLEGAL_ARGUMENT) {
            return EINVAL;
        } else if (ret.getExceptionCode() == EX_SECURITY) {
            return EPERM;
        }
        return EPIPE;
    }

    std::scoped_lock lock(impl::sHintMutex);
    traceThreads(tids);
    bool tooManyThreads =
            ret.getExceptionCode() == EX_SERVICE_SPECIFIC && ret.getServiceSpecificError() == 5;

    return tooManyThreads ? EBUSY : 0;
}

int PerformanceHintSession::getThreadIds(int32_t* const threadIds, size_t* size) {
    std::vector<int32_t> tids;
    ndk::ScopedAStatus ret = mHintManager->getHintSessionThreadIds(mHintSession, &tids);
    if (!ret.isOk()) {
        ALOGE("%s: failed: %s", __FUNCTION__, ret.getMessage());
        return EPIPE;
    }

    // When threadIds is nullptr, this is the first call to determine the size
    // of the thread ids list.
    if (threadIds == nullptr) {
        *size = tids.size();
        return 0;
    }

    // Second call to return the actual list of thread ids.
    *size = tids.size();
    for (size_t i = 0; i < *size; ++i) {
        threadIds[i] = tids[i];
    }
    return 0;
}

int PerformanceHintSession::setPreferPowerEfficiency(bool enabled) {
    ndk::ScopedAStatus ret =
            mHintSession->setMode(static_cast<int32_t>(hal::SessionMode::POWER_EFFICIENCY),
                                  enabled);

    if (!ret.isOk()) {
        ALOGE("%s: HintSession setPreferPowerEfficiency failed: %s", __FUNCTION__,
              ret.getMessage());
        return EPIPE;
    }
    std::scoped_lock lock(impl::sHintMutex);
    tracePowerEfficient(enabled);
    return OK;
}

bool PerformanceHintSession::isJava() {
    return mIsJava;
}

hal::SessionTag PerformanceHintSession::getReturnedSessionTag() {
    return mReturnedTag;
}

status_t PerformanceHintSession::setNativeSurfaces(ANativeWindow** windows, size_t numWindows,
                                                   ASurfaceControl** controls,
                                                   size_t numSurfaceControls) {
    if (!mSessionConfig.has_value()) {
        return ENOTSUP;
    }

    std::vector<sp<IBinder>> layerHandles;
    helper::layersFromNativeSurfaces<sp<IBinder>>(windows, numWindows, controls, numSurfaceControls,
                                                  layerHandles);

    std::vector<ndk::SpAIBinder> ndkLayerHandles;
    for (auto&& handle : layerHandles) {
        ndkLayerHandles.emplace_back(ndk::SpAIBinder(AIBinder_fromPlatformBinder(handle)));
    }

    auto ret = mHintSession->associateToLayers(ndkLayerHandles);
    if (!ret.isOk()) {
        return EPIPE;
    }
    return 0;
}

int PerformanceHintSession::reportActualWorkDurationInternal(WorkDuration* workDuration) {
    int64_t actualTotalDurationNanos = workDuration->durationNanos;
    int64_t now = uptimeNanos();
    workDuration->timeStampNanos = now;
    std::scoped_lock lock(impl::sHintMutex);

    if (mTargetDurationNanos <= 0) {
        ALOGE("Cannot report work durations if the target duration is not positive.");
        return EINVAL;
    }

    traceActualDuration(actualTotalDurationNanos);
    mActualWorkDurations.push_back(*workDuration);

    if (gReportBatchSizeCap.has_value()) {
        // Check if the buffer is larger than the max size, and if it is pop the oldest elements
        const int overflow = mActualWorkDurations.size() - *gReportBatchSizeCap;
        if (overflow > 0) {
            mActualWorkDurations.erase(mActualWorkDurations.begin(),
                                       mActualWorkDurations.begin() + overflow);
        }
    }

    if (actualTotalDurationNanos >= mTargetDurationNanos) {
        // Reset timestamps if we are equal or over the target.
        mFirstTargetMetTimestamp = 0;
    } else {
        // Set mFirstTargetMetTimestamp for first time meeting target.
        if (!mFirstTargetMetTimestamp || !mLastTargetMetTimestamp ||
            (now - mLastTargetMetTimestamp > 2 * mPreferredRateNanos)) {
            mFirstTargetMetTimestamp = now;
        }
        /**
         * Rate limit the change if the update is over mPreferredRateNanos since first
         * meeting target and less than mPreferredRateNanos since last meeting target.
         */
        if (now - mFirstTargetMetTimestamp > mPreferredRateNanos &&
            now - mLastTargetMetTimestamp <= mPreferredRateNanos) {
            traceBatchSize(mActualWorkDurations.size());
            return 0;
        }
        mLastTargetMetTimestamp = now;
    }

    if (!getFMQ().reportActualWorkDurations(mSessionConfig, mActualWorkDurations.data(),
                                            mActualWorkDurations.size())) {
        ndk::ScopedAStatus ret = mHintSession->reportActualWorkDuration2(mActualWorkDurations);
        if (!ret.isOk()) {
            ALOGE("%s: HintSession reportActualWorkDuration failed: %s", __FUNCTION__,
                  ret.getMessage());
            mFirstTargetMetTimestamp = 0;
            mLastTargetMetTimestamp = 0;
            traceBatchSize(mActualWorkDurations.size());
            return ret.getExceptionCode() == EX_ILLEGAL_ARGUMENT ? EINVAL : EPIPE;
        }
    }

    mActualWorkDurations.clear();
    traceBatchSize(0);

    return 0;
}

bool PerformanceHintSession::useNewLoadHintBehavior() {
    return testing::gForceNewHintBehavior.value_or(android::os::adpf_use_load_hints());
}

void PerformanceHintSession::traceThreads(const std::vector<int32_t>& tids) {
    std::set<int32_t> tidSet{tids.begin(), tids.end()};

    // Disable old TID tracing
    for (int32_t tid : mLastThreadIDs) {
        if (!tidSet.count(tid)) {
            std::string traceName =
                    android::base::StringPrintf("%s TID: %" PRId32, mSessionName.c_str(), tid);
            atrace_int64(ATRACE_TAG_APP, traceName.c_str(), 0);
        }
    }

    // Add new TID tracing
    for (int32_t tid : tids) {
        std::string traceName =
                android::base::StringPrintf("%s TID: %" PRId32, mSessionName.c_str(), tid);
        atrace_int64(ATRACE_TAG_APP, traceName.c_str(), 1);
    }

    mLastThreadIDs = std::move(tids);
}

void PerformanceHintSession::tracePowerEfficient(bool powerEfficient) {
    atrace_int64(ATRACE_TAG_APP, (mSessionName + " power efficiency mode").c_str(), powerEfficient);
}

void PerformanceHintSession::traceGraphicsPipeline(bool graphicsPipeline) {
    atrace_int64(ATRACE_TAG_APP, (mSessionName + " graphics pipeline mode").c_str(),
                 graphicsPipeline);
}

void PerformanceHintSession::traceAudioPerformance(bool audioPerformance) {
    atrace_int64(ATRACE_TAG_APP, (mSessionName + " audio performance").c_str(), audioPerformance);
}

void PerformanceHintSession::traceModes(const std::vector<hal::SessionMode>& modesToEnable) {
    // Iterate through all modes to trace, set to enable for all modes in modesToEnable,
    // and set to disable for those are not.
    for (hal::SessionMode mode :
         {hal::SessionMode::POWER_EFFICIENCY, hal::SessionMode::GRAPHICS_PIPELINE}) {
        bool isEnabled =
                find(modesToEnable.begin(), modesToEnable.end(), mode) != modesToEnable.end();
        switch (mode) {
            case hal::SessionMode::POWER_EFFICIENCY:
                tracePowerEfficient(isEnabled);
                break;
            case hal::SessionMode::GRAPHICS_PIPELINE:
                traceGraphicsPipeline(isEnabled);
                break;
            case hal::SessionMode::AUDIO_PERFORMANCE:
                traceAudioPerformance(isEnabled);
                break;
            default:
                break;
        }
    }
}

void PerformanceHintSession::traceActualDuration(int64_t actualDuration) {
    atrace_int64(ATRACE_TAG_APP, (mSessionName + " actual duration").c_str(), actualDuration);
}

void PerformanceHintSession::traceBatchSize(size_t batchSize) {
    std::string traceName = StringPrintf("%s batch size", mSessionName.c_str());
    atrace_int64(ATRACE_TAG_APP, (mSessionName + " batch size").c_str(), batchSize);
}

void PerformanceHintSession::traceTargetDuration(int64_t targetDuration) {
    atrace_int64(ATRACE_TAG_APP, (mSessionName + " target duration").c_str(), targetDuration);
}

} // namespace android::adpf::api