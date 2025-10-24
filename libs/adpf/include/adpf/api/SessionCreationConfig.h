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

struct SessionCreationConfig : public ::aidl::android::os::SessionCreationConfig {
    std::vector<wp<IBinder>> layers{};
    bool hasMode(hal::SessionMode mode) {
        return std::find(modesToEnable.begin(), modesToEnable.end(), mode) != modesToEnable.end();
    }
    void setMode(hal::SessionMode mode, bool enabled) {
        if (hasMode(mode)) {
            if (!enabled) {
                std::erase(modesToEnable, mode);
            }
        } else if (enabled) {
            modesToEnable.push_back(mode);
        }
    }
};

} // namespace android::adpf::api