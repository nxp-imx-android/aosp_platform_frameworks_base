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

#pragma once

#include "common.h"

namespace android::adpf::api {

struct PerformanceHintSession {
public:
    PerformanceHintSession(std::shared_ptr<impl::IHintManager> hintManager,
                           std::shared_ptr<impl::IHintSession> session, int64_t preferredRateNanos,
                           int64_t targetDurationNanos, bool isJava,
                           std::optional<hal::SessionConfig> sessionConfig,
                           hal::SessionTag returnedTag);
    PerformanceHintSession() = delete;
    ~PerformanceHintSession();

    int updateTargetWorkDuration(int64_t targetDurationNanos);
    int reportActualWorkDuration(int64_t actualDurationNanos);
    int reportActualWorkDuration(WorkDuration* workDuration);
    int sendHints(std::vector<hal::SessionHint>& hints, int64_t now, const char* debugName);
    int sendHint(hal::SessionHint hint, const char* debugName);
    int notifyWorkloadIncrease(bool cpu, bool gpu, const char* debugName);
    int notifyWorkloadReset(bool cpu, bool gpu, const char* debugName);
    int notifyWorkloadSpike(bool cpu, bool gpu, const char* debugName);
    int setThreads(const int32_t* threadIds, size_t size);
    int getThreadIds(int32_t* const threadIds, size_t* size);
    int setPreferPowerEfficiency(bool enabled);
    bool isJava();
    hal::SessionTag getReturnedSessionTag();
    status_t setNativeSurfaces(ANativeWindow** windows, size_t numWindows,
                               ASurfaceControl** controls, size_t numSurfaceControls);

private:
    friend struct PerformanceHintManager;

    int reportActualWorkDurationInternal(WorkDuration* workDuration);
    bool useNewLoadHintBehavior();

    std::shared_ptr<impl::IHintManager> mHintManager;
    std::shared_ptr<impl::IHintSession> mHintSession;
    // HAL preferred update rate
    const int64_t mPreferredRateNanos;
    // Target duration for choosing update rate
    int64_t mTargetDurationNanos GUARDED_BY(impl::sHintMutex);
    // First target hit timestamp
    int64_t mFirstTargetMetTimestamp GUARDED_BY(impl::sHintMutex);
    // Last target hit timestamp
    int64_t mLastTargetMetTimestamp GUARDED_BY(impl::sHintMutex);
    // Last hint reported from sendHint indexed by hint value
    // This is only used by the old rate limiter impl and is replaced
    // with the new rate limiter under a flag
    std::vector<int64_t> mLastHintSentTimestamp GUARDED_BY(impl::sHintMutex);
    // Cached samples
    std::vector<hal::WorkDuration> mActualWorkDurations GUARDED_BY(impl::sHintMutex);
    // Is this session backing an SDK wrapper object
    const bool mIsJava;
    std::string mSessionName;
    static int64_t sIDCounter GUARDED_BY(impl::sHintMutex);
    // The most recent set of thread IDs
    std::vector<int32_t> mLastThreadIDs GUARDED_BY(impl::sHintMutex);
    std::optional<hal::SessionConfig> mSessionConfig;
    hal::SessionTag mReturnedTag;
    // Tracing helpers
    void traceThreads(const std::vector<int32_t>& tids) REQUIRES(impl::sHintMutex);
    void tracePowerEfficient(bool powerEfficient);
    void traceGraphicsPipeline(bool graphicsPipeline);
    void traceAudioPerformance(bool audioPerformance);
    void traceModes(const std::vector<hal::SessionMode>& modesToEnable);
    void traceActualDuration(int64_t actualDuration);
    void traceBatchSize(size_t batchSize);
    void traceTargetDuration(int64_t targetDuration);
};

} // namespace android::adpf::api