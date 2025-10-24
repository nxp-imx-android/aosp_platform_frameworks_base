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

#include <adpf/api/FMQWrapper.h>

namespace android::adpf::api {

namespace testing {
static std::optional<bool> gForceFMQEnabled = std::nullopt;
void setUseFmq(bool enabled) {
    gForceFMQEnabled = enabled;
}
} // namespace testing

using namespace impl;

bool FMQWrapper::isActive() {
    std::scoped_lock lock{sHintMutex};
    return isActiveLocked();
}

bool FMQWrapper::isActiveLocked() {
    return mQueue != nullptr;
}

void FMQWrapper::setUnsupported() {
    mHalSupported = false;
}

bool FMQWrapper::isSupported() {
    if (!mHalSupported) {
        return false;
    }
    if (testing::gForceFMQEnabled.has_value()) {
        return *testing::gForceFMQEnabled;
    }
    return true;
}

bool FMQWrapper::startChannel(IHintManager* manager) {
    if (isSupported() && !isActive() && manager->isRemote()) {
        mChannelCreationFinished = std::async(std::launch::async, [&, this, manager]() {
            std::optional<hal::ChannelConfig> config;
            auto ret = manager->getSessionChannel(mToken, &config);
            if (ret.isOk() && config.has_value()) {
                std::scoped_lock lock{impl::sHintMutex};
                mQueue = std::make_shared<MessageQueue>(config->channelDescriptor, true);
                if (config->eventFlagDescriptor.has_value()) {
                    mFlagQueue = std::make_shared<FlagQueue>(*config->eventFlagDescriptor, true);
                    android::hardware::EventFlag::createEventFlag(mFlagQueue->getEventFlagWord(),
                                                                  &mEventFlag);
                    mWriteMask = config->writeFlagBitmask;
                }
                updatePersistentTransaction();
            } else if (ret.isOk() && !config.has_value()) {
                ALOGV("FMQ channel enabled but unsupported.");
                setUnsupported();
            } else {
                ALOGE("%s: FMQ channel initialization failed: %s", __FUNCTION__, ret.getMessage());
            }
            return true;
        });

        // If we're unit testing the FMQ, we should block for it to finish completing
        if (testing::gForceFMQEnabled.has_value()) {
            mChannelCreationFinished.wait();
        }
    }
    return isActive();
}

void FMQWrapper::stopChannel(IHintManager* manager) {
    {
        std::scoped_lock lock{impl::sHintMutex};
        if (!isActiveLocked()) {
            return;
        }
        mFlagQueue = nullptr;
        mQueue = nullptr;
    }
    manager->closeSessionChannel();
}

template <ChannelMessageContents::Tag T, class C>
void FMQWrapper::writeBuffer(C* message, hal::SessionConfig& config, size_t count, int64_t now) {
    for (size_t i = 0; i < count; ++i) {
        new (mFmqTransaction.getSlot(i)) hal::ChannelMessage{
                .sessionID = static_cast<int32_t>(config.id),
                .timeStampNanos = now,
                .data = ChannelMessageContents::make<T, C>(std::move(*(message + i))),
        };
    }
}

template <>
void FMQWrapper::writeBuffer<ChannelMessageContents::workDuration>(hal::WorkDuration* messages,
                                                                   hal::SessionConfig& config,
                                                                   size_t count, int64_t now) {
    for (size_t i = 0; i < count; ++i) {
        hal::WorkDuration& message = messages[i];
        new (mFmqTransaction.getSlot(i)) hal::ChannelMessage{
                .sessionID = static_cast<int32_t>(config.id),
                .timeStampNanos = (i == count - 1) ? now : message.timeStampNanos,
                .data = ChannelMessageContents::make<ChannelMessageContents::workDuration,
                                                     hal::WorkDurationFixedV1>({
                        .durationNanos = message.durationNanos,
                        .workPeriodStartTimestampNanos = message.workPeriodStartTimestampNanos,
                        .cpuDurationNanos = message.cpuDurationNanos,
                        .gpuDurationNanos = message.gpuDurationNanos,
                }),
        };
    }
}

template <ChannelMessageContents::Tag T, bool urgent, class C>
bool FMQWrapper::sendMessages(std::optional<hal::SessionConfig>& config, C* message, size_t count,
                              int64_t now) {
    if (!isActiveLocked() || !config.has_value() || mCorrupted) {
        return false;
    }
    // If we didn't reserve enough space, try re-creating the transaction
    if (count > mAvailableSlots) {
        if (!updatePersistentTransaction()) {
            return false;
        }
        // If we actually don't have enough space, give up
        if (count > mAvailableSlots) {
            return false;
        }
    }
    writeBuffer<T, C>(message, *config, count, now);
    mQueue->commitWrite(count);
    mEventFlag->wake(mWriteMask);
    // Re-create the persistent transaction after writing
    updatePersistentTransaction();
    return true;
}

void FMQWrapper::setToken(ndk::SpAIBinder& token) {
    mToken = token;
}

bool FMQWrapper::updatePersistentTransaction() {
    mAvailableSlots = mQueue->availableToWrite();
    if (mAvailableSlots > 0 && !mQueue->beginWrite(mAvailableSlots, &mFmqTransaction)) {
        ALOGE("ADPF FMQ became corrupted, falling back to binder calls!");
        mCorrupted = true;
        return false;
    }
    return true;
}

bool FMQWrapper::reportActualWorkDurations(std::optional<hal::SessionConfig>& config,
                                           hal::WorkDuration* durations, size_t count) {
    return sendMessages<ChannelMessageContents::workDuration>(config, durations, count);
}

bool FMQWrapper::updateTargetWorkDuration(std::optional<hal::SessionConfig>& config,
                                          int64_t targetDurationNanos) {
    return sendMessages<ChannelMessageContents::targetDuration>(config, &targetDurationNanos);
}

bool FMQWrapper::sendHints(std::optional<hal::SessionConfig>& config,
                           std::vector<hal::SessionHint>& hints, int64_t now) {
    return sendMessages<ChannelMessageContents::hint>(config, hints.data(), hints.size(), now);
}

bool FMQWrapper::setMode(std::optional<hal::SessionConfig>& config, hal::SessionMode mode,
                         bool enabled) {
    hal::ChannelMessage::ChannelMessageContents::SessionModeSetter modeObj{.modeInt = mode,
                                                                           .enabled = enabled};
    return sendMessages<ChannelMessageContents::mode, true>(config, &modeObj);
}

} // namespace android::adpf::api
