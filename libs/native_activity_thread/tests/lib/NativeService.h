/*
 * Copyright (C) 2025 The Android Open Source Project
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

#include <aidl/android/nativeservice/BnNativeServiceWrapper.h>
#include <android/binder_auto_utils.h>
#include <android/binder_status.h>
#include <native_service.h>

#include <map>
#include <set>

using aidl::android::nativeservice::INativeServiceListener;

class NativeServiceWrapper : public aidl::android::nativeservice::BnNativeServiceWrapper {
public:
    NativeServiceWrapper() = default;
    virtual ~NativeServiceWrapper() = default;

    ndk::ScopedAStatus registerListener(
            const std::shared_ptr<INativeServiceListener>& listener) override;

    void doUnbind();
    void doRebind();

private:
    std::shared_ptr<INativeServiceListener> listener_;
};
