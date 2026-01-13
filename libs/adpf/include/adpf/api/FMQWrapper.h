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

#include <future>

#include "common.h"

namespace android::adpf::api {

class FMQWrapper {
public:
    bool isActive();
    bool isSupported();
    bool startChannel(impl::IHintManager* manager);
    void stopChannel(impl::IHintManager* manager);
    // Number of elements the FMQ can hold
    bool reportActualWorkDurations(std::optional<hal::SessionConfig>& config,
                                   hal::WorkDuration* durations, size_t count)
            REQUIRES(impl::sHintMutex);
    bool updateTargetWorkDuration(std::optional<hal::SessionConfig>& config,
                                  int64_t targetDurationNanos) REQUIRES(impl::sHintMutex);
    bool sendHints(std::optional<hal::SessionConfig>& config, std::vector<hal::SessionHint>& hint,
                   int64_t now) REQUIRES(impl::sHintMutex);
    bool setMode(std::optional<hal::SessionConfig>& config, hal::SessionMode, bool enabled)
            REQUIRES(impl::sHintMutex);
    void setToken(ndk::SpAIBinder& token);
    void attemptWake();
    void setUnsupported();

private:
    template <impl::ChannelMessageContents::Tag T, bool urgent = false,
              class C = impl::ChannelMessageContents::_at<T>>
    bool sendMessages(std::optional<hal::SessionConfig>& config, C* message, size_t count = 1,
                      int64_t now = ::android::uptimeNanos()) REQUIRES(impl::sHintMutex);
    template <impl::ChannelMessageContents::Tag T, class C = impl::ChannelMessageContents::_at<T>>
    void writeBuffer(C* message, hal::SessionConfig& config, size_t count, int64_t now)
            REQUIRES(impl::sHintMutex);

    bool isActiveLocked() REQUIRES(impl::sHintMutex);
    bool updatePersistentTransaction() REQUIRES(impl::sHintMutex);
    std::shared_ptr<impl::MessageQueue> mQueue GUARDED_BY(impl::sHintMutex) = nullptr;
    std::shared_ptr<impl::FlagQueue> mFlagQueue GUARDED_BY(impl::sHintMutex) = nullptr;
    android::hardware::EventFlag* mEventFlag = nullptr;
    int32_t mWriteMask;
    ndk::SpAIBinder mToken = nullptr;
    // Used to track if operating on the fmq consistently fails
    bool mCorrupted = false;
    // Used to keep a persistent transaction open with FMQ to reduce latency a bit
    size_t mAvailableSlots GUARDED_BY(impl::sHintMutex) = 0;
    bool mHalSupported = true;
    impl::MessageQueue::MemTransaction mFmqTransaction GUARDED_BY(impl::sHintMutex);
    std::future<bool> mChannelCreationFinished;
};

} // namespace android::adpf::api