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

#include <SkImage.h>
#include <binder/Binder.h>
#include <utils/StrongPointer.h>

#ifdef __ANDROID__
#include <android/hardware_buffer.h>
#endif

namespace android {
#ifdef __ANDROID__
struct IPCClientResourceCache;
#endif

namespace uirenderer {
namespace oopr {

#ifdef __ANDROID__
IPCClientResourceCache& getIPCResourceCache();
sp<BBinder> getDefaultRenderResourceToken();
void enableOutOfProcessRendering();
void registerBuffer(AHardwareBuffer* buffer, const sk_sp<SkImage>& image);
void registerPendingBitmaps();
void deregisterBuffer(const sk_sp<SkImage>& image);
#endif

}  // namespace oopr
}  // namespace uirenderer
}  // namespace android