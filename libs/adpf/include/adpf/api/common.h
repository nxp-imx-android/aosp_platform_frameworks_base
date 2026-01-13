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

#include <aidl/android/hardware/power/SessionHint.h>
#include <aidl/android/hardware/power/SessionMode.h>
#include <aidl/android/hardware/power/SessionTag.h>
#include <aidl/android/hardware/power/SupportInfo.h>
#include <aidl/android/hardware/power/WorkDuration.h>
#include <aidl/android/hardware/power/WorkDurationFixedV1.h>
#include <aidl/android/os/IHintManager.h>
#include <aidl/android/os/IHintSession.h>
#include <aidl/android/os/SessionCreationConfig.h>
#include <android-base/stringprintf.h>
#include <android-base/thread_annotations.h>
#include <android/binder_libbinder.h>
#include <android/binder_manager.h>
#include <android/binder_status.h>
#include <android/native_window.h>
#include <android/surface_control.h>
#include <android/trace.h>
#include <android_os.h>
#include <fmq/AidlMessageQueue.h>
#include <utils/SystemClock.h>
#include <utils/Log.h>
#include <cutils/trace.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <inttypes.h>
#include <utils/SystemClock.h>
#include <sys/cdefs.h>
#include <jni.h>

namespace android::adpf {

// Namespace for AIDL types coming from the PowerHAL
namespace hal = ::aidl::android::hardware::power;

namespace api {

struct PerformanceHintManager;
struct PerformanceHintSession;
struct SessionCreationConfig;

using WorkDuration = hal::WorkDuration;
using SessionHint = hal::SessionHint;
using SessionMode = hal::SessionMode;

namespace impl {

using ::aidl::android::hardware::common::fmq::SynchronizedReadWrite;
using ChannelMessageContents = hal::ChannelMessage::ChannelMessageContents;
using MessageQueue = ::android::AidlMessageQueue<hal::ChannelMessage, SynchronizedReadWrite>;
using FlagQueue = ::android::AidlMessageQueue<int8_t, SynchronizedReadWrite>;
using ::aidl::android::os::IHintManager;
using ::aidl::android::os::IHintSession;

using namespace std::chrono_literals;

// Shared lock for the whole PerformanceHintManager and sessions
static std::mutex sHintMutex = std::mutex{};

// A pair of values that determine the behavior of the
// load hint rate limiter, to only allow "X hints every Y seconds"
constexpr int64_t kLoadHintInterval = std::chrono::nanoseconds(2s).count();
constexpr double kMaxLoadHintsPerInterval = 20;
// Replenish rate is used for new rate limiting behavior, it currently replenishes at a rate of
// 20 / 2s = 1 per 100us, which is the same limit as before, just enforced differently
constexpr double kReplenishRate = kMaxLoadHintsPerInterval / static_cast<double>(kLoadHintInterval);
constexpr int64_t kSendHintTimeout = kLoadHintInterval / kMaxLoadHintsPerInterval;

}

namespace testing {

// Forces FMQ to be enabled or disabled for testing.
void setUseFmq(bool enabled);
// Overrides the returned hint manager for testing.
void setIHintManager(void* iManager);
// Forces the "new load hint" flag to be enabled or disabled for testing.
void setUseNewLoadHintBehavior(bool enabled);
// Forces the graphics pipeline flag to be enabled or disabled for testing.
void setUseGraphicsPipeline(SessionCreationConfig* config, bool enabled);
// Get the rate limiter properties for testing.
void getRateLimiterProperties(int32_t* maxLoadHintsPerInterval, int64_t* loadHintInterval);
// Set the reporting duration max batch size cap. Passing -1 removes the cap.
void setReportBatchSizeCap(int32_t cap);

}

namespace helper {

template <class T>
constexpr int32_t enum_size() {
    return static_cast<int32_t>(*(ndk::enum_range<T>().end() - 1)) + 1;
}

template <class T>
void layersFromNativeSurfaces(ANativeWindow** windows, int numWindows,
    ASurfaceControl** controls,int numSurfaceControls, std::vector<T>& out) {
    std::scoped_lock lock(impl::sHintMutex);
    if (windows != nullptr) {
        for (auto&& window : std::span<ANativeWindow*>(windows, numWindows)) {
            Surface* surface = static_cast<Surface*>(window);
            if (surface != nullptr) {
                const sp<IBinder>& handle = surface->getSurfaceControlHandle();
                if (handle != nullptr) {
                    out.push_back(handle);
                }
            }
        }
    }

    if (controls != nullptr) {
        for (auto&& aSurfaceControl : std::span<ASurfaceControl*>(controls, numSurfaceControls)) {
            SurfaceControl* control = reinterpret_cast<SurfaceControl*>(aSurfaceControl);
            if (control != nullptr && control->isValid()) {
                out.push_back(control->getHandle());
            }
        }
    }
}

}

}
}
