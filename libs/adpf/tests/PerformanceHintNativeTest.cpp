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

#define LOG_TAG "PerformanceHintNativeTest"

#include <adpf/api/PerformanceHintFeature.h>
#include <adpf/api/PerformanceHintManager.h>
#include <adpf/api/PerformanceHintSession.h>
#include <adpf/api/SessionCreationConfig.h>
#include <aidl/android/hardware/power/ChannelConfig.h>
#include <aidl/android/hardware/power/SessionConfig.h>
#include <aidl/android/hardware/power/SessionMode.h>
#include <aidl/android/hardware/power/SessionTag.h>
#include <aidl/android/hardware/power/WorkDuration.h>
#include <aidl/android/os/IHintManager.h>
#include <aidl/android/os/SessionCreationConfig.h>
#include <fmq/AidlMessageQueue.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <memory>
#include <vector>

using namespace std::chrono_literals;
namespace hal = aidl::android::hardware::power;
namespace api = android::adpf::api;
using aidl::android::os::IHintManager;
using aidl::android::os::IHintSession;
using ndk::ScopedAStatus;
using ndk::SpAIBinder;
using HalChannelMessageContents = hal::ChannelMessage::ChannelMessageContents;

using ::aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using HalFlagQueue = ::android::AidlMessageQueue<int8_t, SynchronizedReadWrite>;

using namespace android;
using namespace testing;

constexpr int64_t DEFAULT_TARGET_NS = 16666666L;

std::shared_ptr<api::SessionCreationConfig> createConfig() {
    return std::make_shared<api::SessionCreationConfig>();
}

struct ConfigCreator {
    std::vector<int32_t> tids{1, 2};
    int64_t targetDuration = DEFAULT_TARGET_NS;
    bool powerEfficient = false;
    bool graphicsPipeline = false;
    std::vector<ANativeWindow*> nativeWindows{};
    std::vector<ASurfaceControl*> surfaceControls{};
    bool autoCpu = false;
    bool autoGpu = false;
    bool audioPerformance = false;
};

struct SupportHelper {
    bool hintSessions : 1;
    bool powerEfficiency : 1;
    bool bindToSurface : 1;
    bool graphicsPipeline : 1;
    bool autoCpu : 1;
    bool autoGpu : 1;
    bool audioPerformance : 1;
};

SupportHelper getSupportHelper(api::PerformanceHintManager* manager) {
    return {
            .hintSessions =
                    manager->isFeatureSupported(api::PerformanceHintFeature::APERF_HINT_SESSIONS),
            .powerEfficiency = manager->isFeatureSupported(
                    api::PerformanceHintFeature::APERF_HINT_POWER_EFFICIENCY),
            .bindToSurface = manager->isFeatureSupported(
                    api::PerformanceHintFeature::APERF_HINT_SURFACE_BINDING),
            .graphicsPipeline = manager->isFeatureSupported(
                    api::PerformanceHintFeature::APERF_HINT_GRAPHICS_PIPELINE),
            .autoCpu =
                    manager->isFeatureSupported(api::PerformanceHintFeature::APERF_HINT_AUTO_CPU),
            .autoGpu =
                    manager->isFeatureSupported(api::PerformanceHintFeature::APERF_HINT_AUTO_GPU),
            .audioPerformance = manager->isFeatureSupported(
                    api::PerformanceHintFeature::APERF_HINT_AUDIO_PERFORMANCE),
    };
}

SupportHelper getFullySupportedSupportHelper() {
    return {
            .hintSessions = true,
            .powerEfficiency = true,
            .graphicsPipeline = true,
            .autoCpu = true,
            .autoGpu = true,
            .audioPerformance = true,
    };
}

std::shared_ptr<api::SessionCreationConfig> configFromCreator(ConfigCreator&& creator) {
    auto config = createConfig();
    config->tids = creator.tids;
    config->targetWorkDurationNanos = creator.targetDuration;
    config->setMode(api::SessionMode::POWER_EFFICIENCY, creator.powerEfficient);
    config->setMode(api::SessionMode::GRAPHICS_PIPELINE, creator.graphicsPipeline);
    api::helper::layersFromNativeSurfaces<wp<IBinder>>(creator.nativeWindows.size() > 0
                                                               ? creator.nativeWindows.data()
                                                               : nullptr,
                                                       creator.nativeWindows.size(),
                                                       creator.surfaceControls.size() > 0
                                                               ? creator.surfaceControls.data()
                                                               : nullptr,
                                                       creator.surfaceControls.size(),
                                                       config->layers);
    config->setMode(api::SessionMode::AUTO_CPU, creator.autoCpu);
    config->setMode(api::SessionMode::AUTO_GPU, creator.autoGpu);
    config->setMode(api::SessionMode::AUDIO_PERFORMANCE, creator.audioPerformance);
    return config;
}

class MockIHintManager : public IHintManager {
public:
    MOCK_METHOD(ScopedAStatus, createHintSessionWithConfig,
                (const SpAIBinder& token, hal::SessionTag tag,
                 const ::aidl::android::os::SessionCreationConfig& creationConfig,
                 hal::SessionConfig* config, IHintManager::SessionCreationReturn* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, setHintSessionThreads,
                (const std::shared_ptr<IHintSession>& hintSession,
                 const ::std::vector<int32_t>& tids),
                (override));
    MOCK_METHOD(ScopedAStatus, getHintSessionThreadIds,
                (const std::shared_ptr<IHintSession>& hintSession, ::std::vector<int32_t>* tids),
                (override));
    MOCK_METHOD(ScopedAStatus, getSessionChannel,
                (const ::ndk::SpAIBinder& in_token,
                 std::optional<hal::ChannelConfig>* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, closeSessionChannel, (), (override));
    MOCK_METHOD(ScopedAStatus, getCpuHeadroom,
                (const ::aidl::android::os::CpuHeadroomParamsInternal& in_params,
                 std::optional<hal::CpuHeadroomResult>* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, getCpuHeadroomMinIntervalMillis, (int64_t* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, getGpuHeadroom,
                (const ::aidl::android::os::GpuHeadroomParamsInternal& in_params,
                 std::optional<hal::GpuHeadroomResult>* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, getGpuHeadroomMinIntervalMillis, (int64_t* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, passSessionManagerBinder, (const SpAIBinder& sessionManager));
    MOCK_METHOD(ScopedAStatus, registerClient,
                (const std::shared_ptr<::aidl::android::os::IHintManager::IHintManagerClient>&
                         clientDataIn,
                 ::aidl::android::os::IHintManager::HintManagerClientData* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, getClientData,
                (::aidl::android::os::IHintManager::HintManagerClientData * _aidl_return),
                (override));
    MOCK_METHOD(SpAIBinder, asBinder, (), (override));
    MOCK_METHOD(bool, isRemote, (), (override));
};

class MockIHintSession : public IHintSession {
public:
    MOCK_METHOD(ScopedAStatus, updateTargetWorkDuration, (int64_t targetDurationNanos), (override));
    MOCK_METHOD(ScopedAStatus, reportActualWorkDuration,
                (const ::std::vector<int64_t>& actualDurationNanos,
                 const ::std::vector<int64_t>& timeStampNanos),
                (override));
    MOCK_METHOD(ScopedAStatus, sendHint, (int32_t hint), (override));
    MOCK_METHOD(ScopedAStatus, setMode, (int32_t mode, bool enabled), (override));
    MOCK_METHOD(ScopedAStatus, close, (), (override));
    MOCK_METHOD(ScopedAStatus, reportActualWorkDuration2,
                (const ::std::vector<hal::WorkDuration>& workDurations), (override));
    MOCK_METHOD(ScopedAStatus, associateToLayers,
                (const std::vector<::ndk::SpAIBinder>& in_layerTokens), (override));
    MOCK_METHOD(SpAIBinder, asBinder, (), (override));
    MOCK_METHOD(bool, isRemote, (), (override));
};

class PerformanceHintTest : public Test {
public:
    void SetUp() override {
        mMockIHintManager = ndk::SharedRefBase::make<NiceMock<MockIHintManager>>();
        api::testing::getRateLimiterProperties(&mMaxLoadHintsPerInterval, &mLoadHintInterval);
        api::testing::setIHintManager(&mMockIHintManager);
        api::testing::setUseNewLoadHintBehavior(true);
        mTids.push_back(1);
        mTids.push_back(2);
    }

    void TearDown() override {
        mMockIHintManager = nullptr;
        // Destroys MockIHintManager.
        api::testing::setIHintManager(nullptr);
    }

    api::PerformanceHintManager* createManager() {
        api::testing::setUseFmq(mUsingFMQ);
        ON_CALL(*mMockIHintManager, registerClient(_, _))
                .WillByDefault(
                        DoAll(SetArgPointee<1>(mClientData), [] { return ScopedAStatus::ok(); }));
        ON_CALL(*mMockIHintManager, isRemote()).WillByDefault(Return(true));
        return api::PerformanceHintManager::getInstance();
    }

    void prepareSessionMock(hal::SessionTag returnedTag = hal::SessionTag::APP) {
        mMockSession = ndk::SharedRefBase::make<NiceMock<MockIHintSession>>();
        const int64_t sessionId = 123;

        mSessionCreationReturn = IHintManager::SessionCreationReturn{
                .session = mMockSession,
                .pipelineThreadLimitExceeded = false,
                .tag = returnedTag,
        };

        ON_CALL(*mMockIHintManager, createHintSessionWithConfig(_, _, _, _, _))
                .WillByDefault(DoAll(SetArgPointee<3>(hal::SessionConfig({.id = sessionId})),
                                     SetArgPointee<4>(mSessionCreationReturn),
                                     [] { return ScopedAStatus::ok(); }));

        ON_CALL(*mMockIHintManager, setHintSessionThreads(_, _)).WillByDefault([] {
            return ScopedAStatus::ok();
        });
        ON_CALL(*mMockSession, sendHint(_)).WillByDefault([] { return ScopedAStatus::ok(); });
        ON_CALL(*mMockSession, setMode(_, _)).WillByDefault([] { return ScopedAStatus::ok(); });
        ON_CALL(*mMockSession, close()).WillByDefault([] { return ScopedAStatus::ok(); });
        ON_CALL(*mMockSession, updateTargetWorkDuration(_)).WillByDefault([] {
            return ScopedAStatus::ok();
        });
        ON_CALL(*mMockSession, reportActualWorkDuration(_, _)).WillByDefault([] {
            return ScopedAStatus::ok();
        });
        ON_CALL(*mMockSession, reportActualWorkDuration2(_)).WillByDefault([] {
            return ScopedAStatus::ok();
        });
    }

    std::shared_ptr<api::PerformanceHintSession> createSession(
            api::PerformanceHintManager* manager, int64_t targetDuration = 56789L,
            bool isHwui = false, hal::SessionTag returnedTag = hal::SessionTag::APP) {
        prepareSessionMock(returnedTag);
        return std::shared_ptr<api::PerformanceHintSession>(
                manager->createSession(mTids.data(), mTids.size(), targetDuration,
                                       isHwui ? hal::SessionTag::HWUI : hal::SessionTag::APP));
    }

    std::shared_ptr<api::PerformanceHintSession> createSessionUsingConfig(
            api::PerformanceHintManager* manager,
            std::shared_ptr<api::SessionCreationConfig>& config, bool isHwui = false) {
        prepareSessionMock();
        api::PerformanceHintSession* session;
        int out = 0;
        out = manager->createSessionUsingConfig(config.get(), &session,
                                                isHwui ? hal::SessionTag::HWUI
                                                       : hal::SessionTag::APP);
        EXPECT_EQ(out, 0);
        return std::shared_ptr<api::PerformanceHintSession>(session);
    }

    void setFMQEnabled(bool enabled) {
        mUsingFMQ = enabled;
        if (enabled) {
            mMockFMQ = std::make_shared<
                    AidlMessageQueue<hal::ChannelMessage, SynchronizedReadWrite>>(kMockQueueSize,
                                                                                  true);
            mMockFlagQueue =
                    std::make_shared<AidlMessageQueue<int8_t, SynchronizedReadWrite>>(1, true);
            hardware::EventFlag::createEventFlag(mMockFlagQueue->getEventFlagWord(), &mEventFlag);

            ON_CALL(*mMockIHintManager, getSessionChannel(_, _))
                    .WillByDefault([&](ndk::SpAIBinder, std::optional<hal::ChannelConfig>* config) {
                        config->emplace(
                                hal::ChannelConfig{.channelDescriptor = mMockFMQ->dupeDesc(),
                                                   .eventFlagDescriptor =
                                                           mMockFlagQueue->dupeDesc(),
                                                   .readFlagBitmask =
                                                           static_cast<int32_t>(mReadBits),
                                                   .writeFlagBitmask =
                                                           static_cast<int32_t>(mWriteBits)});
                        return ::ndk::ScopedAStatus::ok();
                    });
        }
    }
    uint32_t mReadBits = 0x00000001;
    uint32_t mWriteBits = 0x00000002;
    std::shared_ptr<NiceMock<MockIHintManager>> mMockIHintManager = nullptr;
    std::shared_ptr<NiceMock<MockIHintSession>> mMockSession = nullptr;
    IHintManager::SessionCreationReturn mSessionCreationReturn;
    std::shared_ptr<AidlMessageQueue<hal::ChannelMessage, SynchronizedReadWrite>> mMockFMQ;
    std::shared_ptr<AidlMessageQueue<int8_t, SynchronizedReadWrite>> mMockFlagQueue;
    hardware::EventFlag* mEventFlag;
    int kMockQueueSize = 20;
    bool mUsingFMQ = false;
    std::vector<int> mTids;

    IHintManager::HintManagerClientData mClientData{
            .powerHalVersion = 6,
            .maxGraphicsPipelineThreads = 5,
            .preferredRateNanos = 123L,
            .supportInfo{
                    .usesSessions = true,
                    .boosts = 0,
                    .modes = 0,
                    .sessionHints = -1,
                    .sessionModes = -1,
                    .sessionTags = -1,
            },
    };

    int32_t mMaxLoadHintsPerInterval;
    int64_t mLoadHintInterval;

    template <HalChannelMessageContents::Tag T, class C = HalChannelMessageContents::_at<T>>
    void expectToReadFromFmq(C expected) {
        hal::ChannelMessage readData;
        mMockFMQ->readBlocking(&readData, 1, mReadBits, mWriteBits, 1000000000, mEventFlag);
        C got = static_cast<C>(readData.data.get<T>());
        ASSERT_EQ(got, expected);
    }
};

bool equalsWithoutTimestamp(hal::WorkDuration lhs, hal::WorkDuration rhs) {
    return lhs.workPeriodStartTimestampNanos == rhs.workPeriodStartTimestampNanos &&
            lhs.cpuDurationNanos == rhs.cpuDurationNanos &&
            lhs.gpuDurationNanos == rhs.gpuDurationNanos && lhs.durationNanos == rhs.durationNanos;
}

TEST_F(PerformanceHintTest, TestSession) {
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    ASSERT_TRUE(session);

    int64_t targetDurationNanos = 10;
    EXPECT_CALL(*mMockSession, updateTargetWorkDuration(Eq(targetDurationNanos))).Times(Exactly(1));
    int result = session->updateTargetWorkDuration(targetDurationNanos);
    EXPECT_EQ(0, result);

    // subsequent call with same target should be ignored but return no error
    result = session->updateTargetWorkDuration(targetDurationNanos);
    EXPECT_EQ(0, result);

    Mock::VerifyAndClearExpectations(mMockSession.get());

    usleep(2); // Sleep for longer than preferredUpdateRateNanos.
    int64_t actualDurationNanos = 20;
    std::vector<int64_t> actualDurations;
    actualDurations.push_back(20);
    EXPECT_CALL(*mMockSession, reportActualWorkDuration2(_)).Times(Exactly(1));
    EXPECT_CALL(*mMockSession, updateTargetWorkDuration(_)).Times(Exactly(1));
    result = session->reportActualWorkDuration(actualDurationNanos);
    EXPECT_EQ(0, result);
    result = session->reportActualWorkDuration(-1L);
    EXPECT_EQ(EINVAL, result);
    result = session->updateTargetWorkDuration(0);
    EXPECT_EQ(0, result);
    result = session->updateTargetWorkDuration(-2);
    EXPECT_EQ(EINVAL, result);
    result = session->reportActualWorkDuration(12L);
    EXPECT_EQ(EINVAL, result);

    hal::SessionHint hintId = hal::SessionHint::CPU_LOAD_RESET;
    EXPECT_CALL(*mMockSession, sendHint(Eq(static_cast<int32_t>(hal::SessionHint::CPU_LOAD_RESET))))
            .Times(Exactly(1));
    result = session->sendHint(hintId, "Test hint");
    EXPECT_EQ(0, result);
    EXPECT_CALL(*mMockSession, sendHint(Eq(static_cast<int32_t>(hal::SessionHint::CPU_LOAD_UP))))
            .Times(Exactly(1));
    result = session->notifyWorkloadIncrease(true, false, "Test hint");
    EXPECT_EQ(0, result);
    EXPECT_CALL(*mMockSession, sendHint(Eq(static_cast<int32_t>(hal::SessionHint::CPU_LOAD_RESET))))
            .Times(Exactly(1));
    EXPECT_CALL(*mMockSession, sendHint(Eq(static_cast<int32_t>(hal::SessionHint::GPU_LOAD_RESET))))
            .Times(Exactly(1));
    result = session->notifyWorkloadReset(true, true, "Test hint");
    EXPECT_EQ(0, result);
    EXPECT_CALL(*mMockSession, sendHint(Eq(static_cast<int32_t>(hal::SessionHint::CPU_LOAD_SPIKE))))
            .Times(Exactly(1));
    EXPECT_CALL(*mMockSession, sendHint(Eq(static_cast<int32_t>(hal::SessionHint::GPU_LOAD_SPIKE))))
            .Times(Exactly(1));
    result = session->notifyWorkloadSpike(true, true, "Test hint");
    EXPECT_EQ(0, result);

    EXPECT_DEATH(
            { session->sendHint(static_cast<hal::SessionHint>(-1), "Test hint"); },
            "invalid session hint");

    Mock::VerifyAndClearExpectations(mMockSession.get());
    for (int i = 0; i < mMaxLoadHintsPerInterval; ++i) {
        session->sendHint(hintId, "Test hint");
    }

    // Expect to get rate limited if we try to send faster than the limiter allows
    EXPECT_CALL(*mMockSession, sendHint(_)).Times(Exactly(0));
    result = session->notifyWorkloadIncrease(true, true, "Test hint");
    EXPECT_EQ(result, EBUSY);
    EXPECT_CALL(*mMockSession, sendHint(_)).Times(Exactly(0));
    result = session->notifyWorkloadReset(true, true, "Test hint");
    EXPECT_CALL(*mMockSession, close()).Times(Exactly(1));
}

TEST_F(PerformanceHintTest, TestUpdatedSessionCreation) {
    EXPECT_CALL(*mMockIHintManager, createHintSessionWithConfig(_, _, _, _, _)).Times(1);
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    ASSERT_TRUE(session);
}

TEST_F(PerformanceHintTest, TestSessionCreationUsingConfig) {
    EXPECT_CALL(*mMockIHintManager, createHintSessionWithConfig(_, _, _, _, _)).Times(1);
    auto&& config = configFromCreator({.tids = mTids});
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSessionUsingConfig(manager, config);
    ASSERT_TRUE(session);
}

TEST_F(PerformanceHintTest, TestHwuiSessionCreation) {
    EXPECT_CALL(*mMockIHintManager, createHintSessionWithConfig(_, hal::SessionTag::HWUI, _, _, _))
            .Times(1);
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager, 56789L, true);
    ASSERT_TRUE(session);
}

TEST_F(PerformanceHintTest, TestAudioPerformanceSessionCreation) {
    EXPECT_CALL(*mMockIHintManager, createHintSessionWithConfig(_, _, _, _, _)).Times(1);
    auto&& config = configFromCreator({.audioPerformance = true});
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSessionUsingConfig(manager, config);
    ASSERT_TRUE(session);
}

TEST_F(PerformanceHintTest, SetThreads) {
    api::PerformanceHintManager* manager = createManager();

    auto&& session = createSession(manager);
    ASSERT_TRUE(session);

    int32_t emptyTids[2];
    int result = session->setThreads(emptyTids, 0);
    EXPECT_EQ(EINVAL, result);

    std::vector<int32_t> newTids;
    newTids.push_back(1);
    newTids.push_back(3);
    EXPECT_CALL(*mMockIHintManager, setHintSessionThreads(_, Eq(newTids))).Times(Exactly(1));
    result = session->setThreads(newTids.data(), newTids.size());
    EXPECT_EQ(0, result);

    testing::Mock::VerifyAndClearExpectations(mMockIHintManager.get());
    std::vector<int32_t> invalidTids;
    invalidTids.push_back(4);
    invalidTids.push_back(6);
    EXPECT_CALL(*mMockIHintManager, setHintSessionThreads(_, Eq(invalidTids)))
            .Times(Exactly(1))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCode(EX_SECURITY))));
    result = session->setThreads(invalidTids.data(), invalidTids.size());
    EXPECT_EQ(EPERM, result);
}

TEST_F(PerformanceHintTest, SetPowerEfficient) {
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    ASSERT_TRUE(session);

    EXPECT_CALL(*mMockSession, setMode(_, Eq(true))).Times(Exactly(1));
    int result = session->setPreferPowerEfficiency(true);
    EXPECT_EQ(0, result);

    EXPECT_CALL(*mMockSession, setMode(_, Eq(false))).Times(Exactly(1));
    result = session->setPreferPowerEfficiency(false);
    EXPECT_EQ(0, result);
}

TEST_F(PerformanceHintTest, CreateZeroTargetDurationSession) {
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager, 0);
    ASSERT_TRUE(session);
}

MATCHER_P(WorkDurationEq, expected, "") {
    if (arg.size() != expected.size()) {
        *result_listener << "WorkDuration vectors are different sizes. Expected: "
                         << expected.size() << ", Actual: " << arg.size();
        return false;
    }
    for (int i = 0; i < expected.size(); ++i) {
        hal::WorkDuration expectedWorkDuration = expected[i];
        hal::WorkDuration actualWorkDuration = arg[i];
        if (!equalsWithoutTimestamp(expectedWorkDuration, actualWorkDuration)) {
            *result_listener << "WorkDuration at [" << i << "] is different: "
                             << "Expected: " << expectedWorkDuration.toString()
                             << ", Actual: " << actualWorkDuration.toString();
            return false;
        }
    }
    return true;
}

TEST_F(PerformanceHintTest, TestAPerformanceHint_reportActualWorkDuration2) {
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    ASSERT_TRUE(session);

    int64_t targetDurationNanos = 10;
    EXPECT_CALL(*mMockSession, updateTargetWorkDuration(Eq(targetDurationNanos))).Times(Exactly(1));
    int result = session->updateTargetWorkDuration(targetDurationNanos);
    EXPECT_EQ(0, result);

    usleep(2); // Sleep for longer than preferredUpdateRateNanos.
    struct TestPair {
        hal::WorkDuration duration;
        int expectedResult;
    };
    std::vector<TestPair> testPairs{
            {{1, 20, 1, 13, 8}, OK},       {{1, -20, 1, 13, 8}, EINVAL},
            {{1, 20, -1, 13, 8}, EINVAL},  {{1, -20, 1, -13, 8}, EINVAL},
            {{1, -20, 1, 13, -8}, EINVAL},
    };
    for (auto&& pair : testPairs) {
        std::vector<hal::WorkDuration> actualWorkDurations;
        actualWorkDurations.push_back(pair.duration);

        EXPECT_CALL(*mMockSession, reportActualWorkDuration2(WorkDurationEq(actualWorkDurations)))
                .Times(Exactly(pair.expectedResult == OK));
        result = session->reportActualWorkDuration(&pair.duration);
        EXPECT_EQ(pair.expectedResult, result);
    }

    EXPECT_CALL(*mMockSession, close()).Times(Exactly(1));
}

TEST_F(PerformanceHintTest, TestCreateUsingFMQ) {
    setFMQEnabled(true);
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    ASSERT_TRUE(session);
}

TEST_F(PerformanceHintTest, TestUpdateTargetWorkDurationUsingFMQ) {
    setFMQEnabled(true);
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    session->updateTargetWorkDuration(456);
    expectToReadFromFmq<HalChannelMessageContents::Tag::targetDuration>(456);
}

TEST_F(PerformanceHintTest, TestSendHintUsingFMQ) {
    setFMQEnabled(true);
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    session->sendHint(hal::SessionHint::CPU_LOAD_UP, "Test Hint");
    expectToReadFromFmq<HalChannelMessageContents::Tag::hint>(hal::SessionHint::CPU_LOAD_UP);
}

TEST_F(PerformanceHintTest, TestReportActualUsingFMQ) {
    setFMQEnabled(true);
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager);
    hal::WorkDuration duration{.timeStampNanos = 3,
                               .durationNanos = 999999,
                               .workPeriodStartTimestampNanos = 1,
                               .cpuDurationNanos = 999999,
                               .gpuDurationNanos = 999999};

    hal::WorkDurationFixedV1 durationExpected{
            .durationNanos = duration.durationNanos,
            .workPeriodStartTimestampNanos = duration.workPeriodStartTimestampNanos,
            .cpuDurationNanos = duration.cpuDurationNanos,
            .gpuDurationNanos = duration.gpuDurationNanos,
    };

    session->reportActualWorkDuration(&duration);
    expectToReadFromFmq<HalChannelMessageContents::Tag::workDuration>(durationExpected);
}

TEST_F(PerformanceHintTest, TestReportActualWhenAppIsGame) {
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSession(manager, 56789L, false, hal::SessionTag::OTHER);
    hal::WorkDuration duration{.timeStampNanos = 3,
                               .durationNanos = 999999,
                               .workPeriodStartTimestampNanos = 1,
                               .cpuDurationNanos = 999999,
                               .gpuDurationNanos = 999999};

    session->reportActualWorkDuration(&duration);

    EXPECT_CALL(*mMockSession, reportActualWorkDuration2(_)).Times(0);
}

TEST_F(PerformanceHintTest, TestASessionCreationConfig) {
    auto&& config = configFromCreator({
            .tids = mTids,
            .targetDuration = 20,
            .powerEfficient = true,
            .graphicsPipeline = true,
    });

    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSessionUsingConfig(manager, config);

    ASSERT_NE(session, nullptr);
    ASSERT_NE(config, nullptr);
}

TEST_F(PerformanceHintTest, TestSessionCreationWithNullLayers) {
    EXPECT_CALL(*mMockIHintManager, createHintSessionWithConfig(_, _, _, _, _)).Times(1);
    auto&& config = configFromCreator(
            {.tids = mTids, .nativeWindows = {nullptr}, .surfaceControls = {nullptr}});
    api::PerformanceHintManager* manager = createManager();
    auto&& session = createSessionUsingConfig(manager, config);
    ASSERT_TRUE(session);
}

TEST_F(PerformanceHintTest, TestSupportObject) {
    // Disable GPU and Power Efficiency support to test partial enabling
    mClientData.supportInfo.sessionModes &= ~(1 << (int)hal::SessionMode::AUTO_GPU);
    mClientData.supportInfo.sessionHints &= ~(1 << (int)hal::SessionHint::GPU_LOAD_UP);
    mClientData.supportInfo.sessionHints &= ~(1 << (int)hal::SessionHint::POWER_EFFICIENCY);

    api::PerformanceHintManager* manager = createManager();

    union {
        int expectedSupportInt;
        SupportHelper expectedSupport;
    };

    union {
        int actualSupportInt;
        SupportHelper actualSupport;
    };

    expectedSupport = getFullySupportedSupportHelper();
    actualSupport = getSupportHelper(manager);

    expectedSupport.autoGpu = false;

    EXPECT_EQ(expectedSupportInt, actualSupportInt);
}

TEST_F(PerformanceHintTest, TestCreatingAutoSession) {
    // Disable GPU capability for testing
    mClientData.supportInfo.sessionModes &= ~(1 << (int)hal::SessionMode::AUTO_GPU);
    api::PerformanceHintManager* manager = createManager();

    auto&& invalidConfig = configFromCreator({
            .tids = mTids,
            .targetDuration = 20,
            .graphicsPipeline = false,
            .autoCpu = true,
            .autoGpu = true,
    });

    // Creating a session with auto timing but no graphics pipeline should die
    // EXPECT_DEATH({ createSessionUsingConfig(manager, invalidConfig); }, "");

    auto&& unsupportedConfig = configFromCreator({
            .tids = mTids,
            .targetDuration = 20,
            .graphicsPipeline = true,
            .autoCpu = true,
            .autoGpu = true,
    });

    api::PerformanceHintSession* unsupportedSession = nullptr;

    int out = manager->createSessionUsingConfig(unsupportedConfig.get(), &unsupportedSession);

    std::shared_ptr<api::PerformanceHintSession> unsupportedSessionWrapped(unsupportedSession);
    EXPECT_EQ(out, ENOTSUP);
    EXPECT_EQ(unsupportedSessionWrapped, nullptr);

    auto&& validConfig = configFromCreator({
            .tids = mTids,
            .targetDuration = 20,
            .graphicsPipeline = true,
            .autoCpu = true,
            .autoGpu = false,
    });

    auto&& validSession = createSessionUsingConfig(manager, validConfig);
    EXPECT_NE(validSession, nullptr);
}

TEST_F(PerformanceHintTest, TestReportActualOverflow) {
    mClientData.preferredRateNanos = 10000000L;
    api::PerformanceHintManager* manager = createManager();

    auto&& config = configFromCreator({
            .tids = mTids,
            .targetDuration = 20,
    });

    auto&& session = createSession(manager);
    hal::WorkDuration duration{.timeStampNanos = 3,
                               .durationNanos = 10,
                               .workPeriodStartTimestampNanos = 1,
                               .cpuDurationNanos = 5,
                               .gpuDurationNanos = 5};

    EXPECT_CALL(*mMockSession, reportActualWorkDuration2(_)).Times(2);

    // Report a duration under the target, to signal the start of good behavior per the rate limiter
    session->reportActualWorkDuration(&duration);

    // Sleep for longer than preferredUpdateRateNanos.
    usleep(12000);

    // Report a duration under the target, to signal continued good behavior per the rate limiter
    session->reportActualWorkDuration(&duration);

    EXPECT_CALL(*mMockSession, reportActualWorkDuration2(SizeIs(Eq(30)))).Times(1);

    api::testing::setReportBatchSizeCap(-1);
    for (int i = 0; i < 29; ++i) {
        session->reportActualWorkDuration(&duration);
    }

    // Sleep for longer than preferredUpdateRateNanos.
    usleep(12000);
    session->reportActualWorkDuration(&duration);

    // Enforce that report spam gets capped
    EXPECT_CALL(*mMockSession, reportActualWorkDuration2(SizeIs(Eq(10)))).Times(1);

    api::testing::setReportBatchSizeCap(10);
    for (int i = 0; i < 29; ++i) {
        session->reportActualWorkDuration(&duration);
    }

    // Sleep for longer than preferredUpdateRateNanos.
    usleep(12000);
    session->reportActualWorkDuration(&duration);
}
