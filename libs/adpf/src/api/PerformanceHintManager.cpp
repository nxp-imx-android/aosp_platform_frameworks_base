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

#include <adpf/api/FMQWrapper.h>
#include <adpf/api/PerformanceHintManager.h>
#include <adpf/api/PerformanceHintSession.h>
#include <android/trace.h>
#include <android_os.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <utility>
#include <vector>

using namespace aidl::android::os;

namespace android::adpf::api {

namespace testing {

static std::shared_ptr<impl::IHintManager>* gIHintManagerForTesting = nullptr;
static std::shared_ptr<PerformanceHintManager> gHintManagerForTesting = nullptr;

void setIHintManager(void* iManager) {
    if (iManager == nullptr) {
        gHintManagerForTesting = nullptr;
    }
    gIHintManagerForTesting = static_cast<std::shared_ptr<IHintManager>*>(iManager);
}

} // namespace testing

PerformanceHintManager* PerformanceHintManager::getInstance() {
    static std::once_flag creationFlag;
    static PerformanceHintManager* instance = nullptr;
    if (testing::gHintManagerForTesting) {
        return testing::gHintManagerForTesting.get();
    }
    if (testing::gIHintManagerForTesting) {
        testing::gHintManagerForTesting =
                std::shared_ptr<PerformanceHintManager>(create(*testing::gIHintManagerForTesting));
        return testing::gHintManagerForTesting.get();
    }
    std::call_once(creationFlag, []() { instance = create(nullptr); });
    return instance;
}

PerformanceHintManager::PerformanceHintManager(std::shared_ptr<IHintManager>& manager,
                                               IHintManager::HintManagerClientData&& clientData,
                                               std::shared_ptr<HintManagerClient> callbackClient)
      : mHintManager(std::move(manager)),
        mCallbackClient(callbackClient),
        mClientData(clientData),
        mSupportInfoWrapper(clientData.supportInfo),
        mToken(callbackClient->asBinder()) {
    if (mFMQWrapper.isSupported()) {
        mFMQWrapper.setToken(mToken);
        mFMQWrapper.startChannel(mHintManager.get());
    }
}

PerformanceHintManager::~PerformanceHintManager() {
    mFMQWrapper.stopChannel(mHintManager.get());
}

PerformanceHintSession* PerformanceHintManager::createSession(
        const int32_t* threadIds, size_t size, int64_t initialTargetWorkDurationNanos,
        hal::SessionTag tag, bool isJava) {
    ndk::ScopedAStatus ret;

    SessionCreationConfig creationConfig{{
            .tids = std::vector<int32_t>(threadIds, threadIds + size),
            .targetWorkDurationNanos = initialTargetWorkDurationNanos,
    }};

    PerformanceHintSession* sessionOut;
    PerformanceHintManager::createSessionUsingConfig(&creationConfig, &sessionOut, tag, isJava);
    return sessionOut;
}

PerformanceHintSession* PerformanceHintManager::getSessionFromJava(JNIEnv* env,
                                                                   jobject sessionObj) {
    initJava(env);
    LOG_ALWAYS_FATAL_IF(!env->IsInstanceOf(sessionObj, mJavaSessionClazz),
                        "Wrong java type passed to APerformanceHint_getSessionFromJava");
    PerformanceHintSession* out = reinterpret_cast<PerformanceHintSession*>(
            env->GetLongField(sessionObj, mJavaSessionNativePtr));
    LOG_ALWAYS_FATAL_IF(out == nullptr, "Java-wrapped native hint session is nullptr");
    LOG_ALWAYS_FATAL_IF(!out->isJava(), "Unmanaged native hint session returned from Java SDK");
    return out;
}

int PerformanceHintManager::createSessionUsingConfig(SessionCreationConfig* sessionCreationConfig,
                                                     PerformanceHintSession** sessionOut,
                                                     hal::SessionTag tag, bool isJava) {
    hal::SessionConfig sessionConfig{.id = -1};
    ndk::ScopedAStatus ret;

    // Hold the tokens weakly until we actually need them,
    // then promote them, then drop all strong refs after
    if (!sessionCreationConfig->layers.empty()) {
        for (auto&& layerIter = sessionCreationConfig->layers.begin();
             layerIter != sessionCreationConfig->layers.end();) {
            sp<IBinder> promoted = layerIter->promote();
            if (promoted == nullptr) {
                layerIter = sessionCreationConfig->layers.erase(layerIter);
            } else {
                sessionCreationConfig->layerTokens.push_back(
                        ndk::SpAIBinder(AIBinder_fromPlatformBinder(promoted.get())));
                ++layerIter;
            }
        }
    }

    bool autoCpu = sessionCreationConfig->hasMode(hal::SessionMode::AUTO_CPU);
    bool autoGpu = sessionCreationConfig->hasMode(hal::SessionMode::AUTO_GPU);
    bool audioPerformance = sessionCreationConfig->hasMode(hal::SessionMode::AUDIO_PERFORMANCE);

    if (autoCpu || autoGpu) {
        LOG_ALWAYS_FATAL_IF(!sessionCreationConfig->hasMode(hal::SessionMode::GRAPHICS_PIPELINE),
                            "Automatic session timing enabled without graphics pipeline mode");
    }

    if (autoCpu && !mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::AUTO_CPU)) {
        ALOGE("Automatic CPU timing enabled but not supported");
        return ENOTSUP;
    }

    if (autoGpu && !mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::AUTO_GPU)) {
        ALOGE("Automatic GPU timing enabled but not supported");
        return ENOTSUP;
    }

    if (audioPerformance &&
        !mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::AUDIO_PERFORMANCE)) {
        ALOGE("Audio performance enabled but not supported");
        return ENOTSUP;
    }

    if (audioPerformance && sessionCreationConfig->hasMode(hal::SessionMode::GRAPHICS_PIPELINE)) {
        ALOGE("Audio performance and graphics pipeline cannot be both set");
        return ENOTSUP;
    }

    IHintManager::SessionCreationReturn returnValue;
    ret = mHintManager->createHintSessionWithConfig(mToken, tag,
                                                    *static_cast<SessionCreationConfig*>(
                                                            sessionCreationConfig),
                                                    &sessionConfig, &returnValue);

    sessionCreationConfig->layerTokens.clear();

    if (!ret.isOk() || !returnValue.session) {
        ALOGE("%s: PerformanceHint cannot create session. %s", __FUNCTION__, ret.getMessage());
        switch (ret.getExceptionCode()) {
            case binder::Status::EX_UNSUPPORTED_OPERATION:
                return ENOTSUP;
            case binder::Status::EX_ILLEGAL_ARGUMENT:
                return EINVAL;
            default:
                return EPIPE;
        }
    }

    auto out = new PerformanceHintSession(mHintManager, std::move(returnValue.session),
                                          mClientData.preferredRateNanos,
                                          sessionCreationConfig->targetWorkDurationNanos, isJava,
                                          sessionConfig.id == -1
                                                  ? std::nullopt
                                                  : std::make_optional<hal::SessionConfig>(
                                                            std::move(sessionConfig)),
                                          returnValue.tag);

    *sessionOut = out;

    std::scoped_lock lock(impl::sHintMutex);
    out->traceThreads(sessionCreationConfig->tids);
    out->traceTargetDuration(sessionCreationConfig->targetWorkDurationNanos);
    out->traceModes(sessionCreationConfig->modesToEnable);

    if (returnValue.pipelineThreadLimitExceeded) {
        ALOGE("Graphics pipeline session thread limit exceeded!");
        return EBUSY;
    }

    return 0;
}

int64_t PerformanceHintManager::getPreferredRateNanos() const {
    return mClientData.preferredRateNanos;
}

int32_t PerformanceHintManager::getMaxGraphicsPipelineThreadsCount() {
    return mClientData.maxGraphicsPipelineThreads;
}

FMQWrapper& PerformanceHintManager::getFMQWrapper() {
    return mFMQWrapper;
}

bool PerformanceHintManager::canSendLoadHints(std::vector<hal::SessionHint>& hints, int64_t now) {
    mHintBudget = std::min(impl::kMaxLoadHintsPerInterval,
                           mHintBudget +
                                   static_cast<double>(now - mLastBudgetReplenish) *
                                           impl::kReplenishRate);
    mLastBudgetReplenish = now;

    // If this youngest timestamp isn't older than the timeout time, we can't send
    if (hints.size() > mHintBudget) {
        return false;
    }
    mHintBudget -= hints.size();
    return true;
}

void PerformanceHintManager::initJava(JNIEnv* _Nonnull env) {
    if (mJavaInitialized) {
        return;
    }
    jclass sessionClazz = env->FindClass("android/os/PerformanceHintManager$Session");
    mJavaSessionClazz = static_cast<jclass>(env->NewGlobalRef(sessionClazz));
    mJavaSessionNativePtr = env->GetFieldID(mJavaSessionClazz, "mNativeSessionPtr", "J");
    mJavaInitialized = true;
}

ndk::SpAIBinder& PerformanceHintManager::getToken() {
    return mToken;
}

SupportInfoWrapper& PerformanceHintManager::getSupportInfo() {
    return mSupportInfoWrapper;
}

bool PerformanceHintManager::isFeatureSupported(PerformanceHintFeature feature) {
    switch (feature) {
        case (PerformanceHintFeature::APERF_HINT_SESSIONS):
            return mSupportInfoWrapper.usesSessions;
        case (PerformanceHintFeature::APERF_HINT_POWER_EFFICIENCY):
            return mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::POWER_EFFICIENCY);
        case (PerformanceHintFeature::APERF_HINT_SURFACE_BINDING):
            return mSupportInfoWrapper.compositionData.isSupported;
        case (PerformanceHintFeature::APERF_HINT_GRAPHICS_PIPELINE):
            return mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::GRAPHICS_PIPELINE);
        case (PerformanceHintFeature::APERF_HINT_AUTO_CPU):
            return mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::AUTO_CPU);
        case (PerformanceHintFeature::APERF_HINT_AUTO_GPU):
            return mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::AUTO_GPU);
        case (PerformanceHintFeature::APERF_HINT_AUDIO_PERFORMANCE):
            return android::os::adpf_audio_performance() &&
                    mSupportInfoWrapper.isSessionModeSupported(hal::SessionMode::AUDIO_PERFORMANCE);
        default:
            return false;
    }
}

PerformanceHintManager* PerformanceHintManager::create(std::shared_ptr<IHintManager> manager) {
    if (!manager) {
        manager = IHintManager::fromBinder(
                ndk::SpAIBinder(AServiceManager_waitForService("performance_hint")));
    }
    if (manager == nullptr) {
        ALOGE("%s: PerformanceHint service is not ready ", __FUNCTION__);
        return nullptr;
    }
    std::shared_ptr<HintManagerClient> client = ndk::SharedRefBase::make<HintManagerClient>();
    IHintManager::HintManagerClientData clientData;
    ndk::ScopedAStatus ret = manager->registerClient(client, &clientData);
    if (!ret.isOk()) {
        ALOGE("%s: PerformanceHint is not supported. %s", __FUNCTION__, ret.getMessage());
        return nullptr;
    }
    if (clientData.preferredRateNanos <= 0) {
        clientData.preferredRateNanos = -1L;
    }
    return new PerformanceHintManager(manager, std::move(clientData), client);
}

} // namespace android::adpf::api
