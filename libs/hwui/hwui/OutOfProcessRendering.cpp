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

#include <include/gpu/ganesh/GrDirectContext.h>
#include <include/gpu/ganesh/SkSurfaceGanesh.h>

#ifdef __ANDROID__
#include <android/ipcrenderbuffer/RenderBufferOps.h>
#include <com_android_graphics_libgui_flags.h>
#include <gui/GraphicBuffersRegisterInfo.h>
#include <gui/GraphicBuffersUnregisterInfo.h>
#include <gui/LocklessQueue.h>
#include <gui/TraceUtils.h>
#include <include/android/GrAHardwareBufferUtils.h>
#include <inttypes.h>
#include <log/log.h>
#include <private/android/AHardwareBufferHelpers.h>
#include <private/gui/ComposerServiceAIDL.h>
#include <ui/GraphicBuffer.h>

#include <thread>
#include <unordered_map>

#include "../AutoBackendTextureRelease.h"
#endif

namespace android {
namespace uirenderer {
namespace oopr {

#ifdef __ANDROID__

NodeResources::~NodeResources() {
    if (lastImage) {
        deregisterBuffer(lastImage);
    }
    if (textureRelease) {
        textureRelease->unref(false);
    }
}

static bool sEnableOOPR;
static sp<BBinder> sRenderResourceToken = sp<BBinder>::make();
static IPCClientResourceCache sRenderResourceCache;
static LocklessQueue<std::function<void()>> sRenderResourceQueue;

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

AllocationResult createLayerSurface(uint32_t width, uint32_t height, GrDirectContext* context) {
    LOG_ALWAYS_FATAL_IF(
            !sEnableOOPR,
            "Flag com.android.graphics.libgui.flags.out_of_process_rendering is disabled!");

    LOG_ALWAYS_FATAL_IF(!context, "Skia GrDirectContext is null");

    sp<GraphicBuffer> buffer = sp<GraphicBuffer>::make(
            width, height, PIXEL_FORMAT_RGBA_8888, 1,
            GraphicBuffer::USAGE_HW_TEXTURE | GraphicBuffer::USAGE_HW_RENDER, "OOPRLayer");
    if (buffer->initCheck() != NO_ERROR) {
        ALOGE("Failed to allocate GraphicBuffer for layer");
        return {};
    }

    ATRACE_FORMAT("createLayerSurface bufferId=%llu", buffer->getId());
    AutoBackendTextureRelease* textureRelease =
            new AutoBackendTextureRelease(context, buffer->toAHardwareBuffer());
    if (!textureRelease->getTexture().isValid()) {
        textureRelease->unref(false);
        return {};
    }

    // We don't need to know when the surface / SkImage is released because
    // the NodeResources lifetime matches the RenderNode will outlive the SkImage.
    sk_sp<SkSurface> surface = SkSurfaces::WrapBackendTexture(
            context, textureRelease->getTexture(), kTopLeft_GrSurfaceOrigin, 0,
            kRGBA_8888_SkColorType, nullptr, nullptr, nullptr, nullptr);

    if (!surface) {
        textureRelease->unref(false);
        return {};
    }

    AllocationResult result;
    result.surface = std::move(surface);
    result.resources = std::make_unique<NodeResources>();
    result.resources->buffer = buffer;
    result.resources->textureRelease = textureRelease;

    return result;
}

// This function is called from SkiaIpcPipeline::renderLayersImpl on the RenderThread.
// Since the RenderThread also executes the pending bitmap registration (via
// registerPendingBitmaps), it is safe to update the cache directly here.
void registerSnapshot(NodeResources* resources, const sk_sp<SkImage>& image) {
    if (!sEnableOOPR || !image || !resources) return;

    sp<GraphicBuffer> buffer = resources->buffer;
    sk_sp<SkImage> lastImage = resources->lastImage;

    if (lastImage && lastImage->uniqueID() == image->uniqueID()) {
        return;
    }

    resources->lastImage = image;

    LOG_ALWAYS_FATAL_IF(!buffer, "buffer should never be null");

    // We are on the render thread, so we can update the cache directly.
    auto& bitmaps = sRenderResourceCache.bitmaps;
    if (lastImage) {
        auto it = bitmaps.find(lastImage->uniqueID());
        if (it != bitmaps.end()) {
            // Move entry to new image ID
            auto nodeHandler = bitmaps.extract(it);
            nodeHandler.key() = image->uniqueID();
            bitmaps.insert(std::move(nodeHandler));
            return;
        }
    }
    ATRACE_FORMAT("registerBuffer bufferId=%llu imageId=%u", buffer->getId(), image->uniqueID());

    sRenderResourceCache.bitmaps[image->uniqueID()] =
            IPCClientBitmap{buffer->getId(), false, buffer};
}

// This function is called from Bitmap.cpp, potentially from any thread (e.g. background loading).
// Therefore, we must use the thread-safe lockless queue to defer the cache update to the
// RenderThread (in registerPendingBitmaps).
void registerBuffer(const sp<GraphicBuffer>& buffer, const sk_sp<SkImage>& image) {
    if (!sEnableOOPR || !image) return;
    sRenderResourceQueue.push([buffer, image]() {
        ATRACE_FORMAT("registerBuffer bufferId=%llu imageId=%u", buffer->getId(),
                      image->uniqueID());
        sRenderResourceCache.bitmaps[image->uniqueID()] =
                IPCClientBitmap{buffer->getId(), false, buffer};
    });
}

void registerPendingBitmaps() {
    if (!sEnableOOPR) {
        return;
    }
    ATRACE_CALL();

    while (auto op = sRenderResourceQueue.pop()) {
        (*op)();
    }

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

    sRenderResourceQueue.push([image]() {
        auto it = sRenderResourceCache.bitmaps.find(image->uniqueID());
        if (it == sRenderResourceCache.bitmaps.end()) {
            LOG_ALWAYS_FATAL("Trying to deregister image that was never registered.");
            return;
        }

        uint64_t bufferId = it->second.id;
        bool registered = it->second.registeredWithServer;
        sRenderResourceCache.bitmaps.erase(it);

        ATRACE_FORMAT("deregisterBuffer bufferId=%llu imageId=%u", bufferId, image->uniqueID());

        if (!registered) {
            return;
        }
        // Deregister with SF
        // TODO: b/448196792 - batching
        gui::GraphicBuffersUnregisterInfo unregisterInfo;
        unregisterInfo.renderResourceToken = sRenderResourceToken;
        unregisterInfo.bufferIds.push_back(bufferId);
        ComposerServiceAIDL::getComposerService()->unregisterGraphicBuffers(unregisterInfo);
    });
}

#endif

}  // namespace oopr
}  // namespace uirenderer
}  // namespace android
