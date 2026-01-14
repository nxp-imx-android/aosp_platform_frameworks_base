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

#include "OutOfProcessRendering.h"

#ifdef __ANDROID__
#include <android/ipcrenderbuffer/RenderBufferOps.h>
#include <com_android_graphics_libgui_flags.h>
#include <gui/GraphicBuffersRegisterInfo.h>
#include <gui/GraphicBuffersUnregisterInfo.h>
#include <log/log.h>
#include <private/gui/ComposerServiceAIDL.h>
#include <ui/GraphicBuffer.h>

#include <mutex>
#endif

namespace android {
namespace uirenderer {
namespace oopr {

#ifdef __ANDROID__

static bool sEnableOOPR;
static sp<BBinder> sRenderResourceToken = sp<BBinder>::make();
static IPCClientResourceCache sRenderResourceCache;
static std::mutex sRenderResourceLock;

void enableOutOfProcessRendering() {
    if (com_android_graphics_libgui_flags_out_of_process_rendering()) {
        sEnableOOPR = true;
    } else {
        LOG_ALWAYS_FATAL(
                "Flag com.android.graphics.libgui.flags.out_of_process_rendering is disabled!");
    }
}

IPCClientResourceCache& getIPCResourceCache() {
    return sRenderResourceCache;
}

sp<BBinder> getDefaultRenderResourceToken() {
    return sRenderResourceToken;
}

void registerBuffer(AHardwareBuffer* buffer, const sk_sp<SkImage>& image) {
    if (!sEnableOOPR) {
        return;
    }
    if (!image) {
        LOG_ALWAYS_FATAL("Trying to register null image.");
        return;
    }

    std::lock_guard lock{sRenderResourceLock};
    sp<GraphicBuffer> graphicBuffer = GraphicBuffer::fromAHardwareBuffer(buffer);
    sRenderResourceCache.bitmaps[image->uniqueID()] =
            IPCClientBitmap{graphicBuffer->getId(), false, graphicBuffer};
}

void registerPendingBitmaps() {
    if (!sEnableOOPR) {
        return;
    }
    std::lock_guard lock{sRenderResourceLock};

    gui::GraphicBuffersRegisterInfo registerInfo;
    registerInfo.renderResourceToken = sRenderResourceToken;
    for (auto& [id, bitmap] : sRenderResourceCache.bitmaps) {
        if (!bitmap.registeredWithServer) {
            registerInfo.buffers.push_back(bitmap.buffer);
            bitmap.registeredWithServer = true;
        }
    }

    if (registerInfo.buffers.empty()) {
        return;
    }

    ComposerServiceAIDL::getComposerService()->registerGraphicBuffers(registerInfo);
}

void deregisterBuffer(const sk_sp<SkImage>& image) {
    if (!sEnableOOPR) {
        return;
    }
    if (!image) {
        LOG_ALWAYS_FATAL("Trying to deregister null image.");
        return;
    }
    std::lock_guard lock{sRenderResourceLock};
    auto it = sRenderResourceCache.bitmaps.find(image->uniqueID());
    if (it == sRenderResourceCache.bitmaps.end()) {
        LOG_ALWAYS_FATAL("Trying to deregister image that was never registered.");
        return;
    }
    uint64_t bufferId = it->second.id;
    bool registered = it->second.registeredWithServer;
    sRenderResourceCache.bitmaps.erase(it);

    if (!registered) {
        return;
    }

    // Deregister with SF
    // TODO: b/448196792 - batching
    gui::GraphicBuffersUnregisterInfo unregisterInfo;
    unregisterInfo.renderResourceToken = sRenderResourceToken;
    unregisterInfo.bufferIds.push_back(bufferId);
    ComposerServiceAIDL::getComposerService()->unregisterGraphicBuffers(unregisterInfo);
}

#endif

}  // namespace oopr
}  // namespace uirenderer
}  // namespace android