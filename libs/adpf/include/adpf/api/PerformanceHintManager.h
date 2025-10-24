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

#include <adpf/api/FMQWrapper.h>
#include <adpf/api/PerformanceHintFeature.h>
#include <adpf/api/SessionCreationConfig.h>
#include <adpf/api/SupportInfoWrapper.h>
#include <jni.h>

#include "common.h"

namespace android::adpf::api {

class HintManagerClient : public impl::IHintManager::BnHintManagerClient {
public:
    // Currently a no-op that exists for FMQ init to call in the future
    ndk::ScopedAStatus receiveChannelConfig(const hal::ChannelConfig&) {
        return ndk::ScopedAStatus::ok();
    }
};

struct PerformanceHintManager {
public:
    static PerformanceHintManager* getInstance();
    PerformanceHintManager(std::shared_ptr<impl::IHintManager>& service,
                           impl::IHintManager::HintManagerClientData&& clientData,
                           std::shared_ptr<HintManagerClient> callbackClient);
    PerformanceHintManager() = delete;
    ~PerformanceHintManager();
    PerformanceHintSession* createSession(const int32_t* threadIds, size_t size,
                                          int64_t initialTargetWorkDurationNanos,
                                          hal::SessionTag tag = hal::SessionTag::APP,
                                          bool isJava = false);
    PerformanceHintSession* getSessionFromJava(JNIEnv* env, jobject sessionObj);
    int createSessionUsingConfig(SessionCreationConfig* sessionCreationConfig,
                                 PerformanceHintSession** sessionPtr,
                                 hal::SessionTag tag = hal::SessionTag::APP, bool isJava = false);
    int64_t getPreferredRateNanos() const;
    int32_t getMaxGraphicsPipelineThreadsCount();
    FMQWrapper& getFMQWrapper();
    bool canSendLoadHints(std::vector<hal::SessionHint>& hints, int64_t now)
            REQUIRES(impl::sHintMutex);
    void initJava(JNIEnv* env);
    ndk::SpAIBinder& getToken();
    SupportInfoWrapper& getSupportInfo();
    bool isFeatureSupported(PerformanceHintFeature feature);

private:
    static PerformanceHintManager* create(std::shared_ptr<impl::IHintManager> iHintManager);

    std::shared_ptr<impl::IHintManager> mHintManager;
    std::shared_ptr<HintManagerClient> mCallbackClient;
    impl::IHintManager::HintManagerClientData mClientData;
    SupportInfoWrapper mSupportInfoWrapper;
    ndk::SpAIBinder mToken;
    FMQWrapper mFMQWrapper;
    double mHintBudget = impl::kMaxLoadHintsPerInterval;
    int64_t mLastBudgetReplenish = 0;
    bool mJavaInitialized = false;
    jclass mJavaSessionClazz;
    jfieldID mJavaSessionNativePtr;
};

} // namespace android::adpf::api