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

#include "HardwareBitmapUploader.h"

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

#ifdef __ANDROID__

sp<GraphicBuffer> allocGraphicBufferFromBitmap(const SkImageInfo& info) {
    uint32_t usage = GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_WRITE_RARELY;

    // Map SkColorType to Android PixelFormat
    SkBitmap bitmap;
    bitmap.setInfo(info);
    auto formatInfo = HardwareBitmapUploader::determineFormat(bitmap);
    if (!formatInfo.isSupported || !formatInfo.valid) {
        ALOGE("Missing format: %d", info.colorType());
        // Fallback or skip if format is not supported by GraphicBuffer easily
        return nullptr;
    }
    PixelFormat format = static_cast<PixelFormat>(formatInfo.bufferFormat);

    sp<GraphicBuffer> buffer = sp<GraphicBuffer>::make(info.width(), info.height(), format, 1,
                                                       usage, "Bitmap::makeImage-Shadow");

    status_t err = buffer->initCheck();
    if (err != NO_ERROR) {
        ALOGE("Failed to allocate GraphicBuffer shadow: %d", err);
        return nullptr;
    }

    return buffer;
}

void copyBitmapToGraphicBuffer(sp<GraphicBuffer> dst, const SkBitmap& src) {
    ATRACE_CALL();

    void* data = nullptr;
    status_t err = dst->lock(GRALLOC_USAGE_SW_WRITE_RARELY, &data);
    if (err != NO_ERROR || !data) {
        ALOGE("Failed to lock GraphicBuffer shadow: %d", err);
        return;
    }

    size_t bufferRowBytes = dst->getStride() * src.info().bytesPerPixel();
    SkPixmap dstPixmap(src.info(), data, bufferRowBytes);
    bool result = src.readPixels(dstPixmap);

    dst->unlock();

    if (!result) {
        ALOGE("Failed to copy pixels to GraphicBuffer shadow");
        return;
    }
}

OoprNode::~OoprNode() {
    if (mLastImage) {
        OoprClient::getInstance()->deregisterBuffer(mLastImage);
    }
    if (mTextureRelease) {
        mTextureRelease->unref(false);
    }
}

OoprBitmap::~OoprBitmap() {
    if (mLastImage) {
        OoprClient::getInstance()->deregisterBuffer(mLastImage);
    }
}

OoprClient* OoprClient::getInstance() {
    static OoprClient sInstance;
    return &sInstance;
}

OoprClient::OoprClient()
        : mToken(sp<BBinder>::make()), mCache(std::make_unique<IPCClientResourceCache>()) {}

OoprClient::~OoprClient() = default;

void OoprClient::enableOutOfProcessRendering() {
    if (com_android_graphics_libgui_flags_out_of_process_rendering()) {
        mEnableOOPR = true;
    } else {
        LOG_ALWAYS_FATAL(
                "Flag com.android.graphics.libgui.flags.out_of_process_rendering is disabled!");
    }
}

IPCClientResourceCache& OoprClient::getIPCResourceCache() {
    return *mCache;
}

sp<BBinder> OoprClient::getDefaultRenderResourceToken() {
    return mToken;
}

OoprLayerResult OoprClient::createLayerSurface(uint32_t width, uint32_t height,
                                               GrDirectContext* context) {
    LOG_ALWAYS_FATAL_IF(
            !mEnableOOPR,
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

    OoprLayerResult result;
    result.surface = std::move(surface);
    result.resources = std::make_unique<OoprNode>();
    result.resources->mBuffer = buffer;
    result.resources->mTextureRelease = textureRelease;

    return result;
}

void OoprNode::registerSnapshot(const sk_sp<SkImage>& image) {
    auto oopr = OoprClient::getInstance();

    if (!oopr->isEnabled()) return;

    if (!image) return;

    if (mLastImage && mLastImage->uniqueID() == image->uniqueID()) {
        return;
    }

    sk_sp<SkImage> oldImage = mLastImage;
    mLastImage = image;

    LOG_ALWAYS_FATAL_IF(!mBuffer, "buffer should never be null");

    if (oldImage) {
        oopr->deregisterBuffer(oldImage);
    }
    oopr->registerBuffer(mBuffer, image);
    ATRACE_FORMAT("registerBuffer bufferId=%llu imageId=%u", mBuffer->getId(), image->uniqueID());
}

bool OoprClient::isEnabled() {
    return mEnableOOPR;
}

void OoprBitmap::createAndRegisterShadowBuffer(const SkBitmap& bitmap, sk_sp<SkImage> image) {
    auto oopr = OoprClient::getInstance();

    if (!oopr->isEnabled()) return;

    ATRACE_CALL();

    // Shadow the heap buffer with a GraphicBuffer
    // Note: This creates a copy of the pixel data into the GraphicBuffer.

    // Check if the image has changed. If not, we're done.
    if (mShadowBuffer && mLastImage && mLastImage->uniqueID() == image->uniqueID()) {
        return;
    }

    sk_sp<SkImage> oldImage = mLastImage;

    // Allocate or re-allocate the shadow buffer if needed.
    bool needsAllocation = !mShadowBuffer;
    if (mShadowBuffer) {
        if (!oldImage || oldImage->imageInfo() != bitmap.info()) {
            needsAllocation = true;
        }
    }

    if (needsAllocation) {
        mShadowBuffer = allocGraphicBufferFromBitmap(bitmap.info());
        if (!mShadowBuffer) {
            // Error logging handled inside allocGraphicBufferFromBitmap
            return;
        }
    }

    copyBitmapToGraphicBuffer(mShadowBuffer, bitmap);
    mLastImage = image;

    // If there was a previous image, we should deregister it.
    if (oldImage && oldImage->uniqueID() != image->uniqueID()) {
        oopr->deregisterBuffer(oldImage);
    }

    oopr->registerBitmap(bitmap, image, mShadowBuffer);
}

// This function is called from Bitmap.cpp, potentially from any thread.
// However, all bitmap mutation must be finished before work is submitted to the render thread
// so there is no chance of races.
void OoprClient::registerBuffer(const sp<GraphicBuffer>& buffer, const sk_sp<SkImage>& image) {
    if (!mEnableOOPR || !image) return;
    ATRACE_FORMAT("registerBuffer bufferId=%llu imageId=%u", buffer->getId(), image->uniqueID());
    mCache->bitmaps[image->uniqueID()] =
            IPCClientBitmap{buffer->getId(), IPCClientBitmap::PENDING_REGISTER, buffer};
}

void OoprClient::registerBitmap(const SkBitmap& bitmap, const sk_sp<SkImage>& image,
                                const sp<GraphicBuffer>& buffer) {
    if (!mEnableOOPR || !image) return;
    ATRACE_FORMAT("registerBitmap bufferId=%llu imageId=%u bitmap=%u", buffer->getId(),
                  image->uniqueID(), bitmap.getGenerationID());

    mCache->bitmaps[image->uniqueID()] = IPCClientBitmap{.id = buffer->getId(),
                                                         .state = IPCClientBitmap::PENDING_REGISTER,
                                                         .buffer = buffer,
                                                         .bitmap = bitmap};
}

void OoprClient::registerPendingBitmaps() {
    if (!mEnableOOPR) {
        return;
    }
    ATRACE_CALL();

    // TODO: deregister here too
    gui::GraphicBuffersUnregisterInfo unregisterInfo;
    gui::GraphicBuffersRegisterInfo registerInfo;

    registerInfo.renderResourceToken = mToken;
    unregisterInfo.renderResourceToken = mToken;

    // We need to iterate and possibly remove elements, so use iterator
    auto it = mCache->bitmaps.begin();
    while (it != mCache->bitmaps.end()) {
        IPCClientBitmap& bitmap = it->second;

        if (bitmap.state == IPCClientBitmap::PENDING_REGISTER) {
            registerInfo.buffers.push_back(bitmap.buffer);
            bitmap.state = IPCClientBitmap::REGISTERED;
            ++it;
        } else if (bitmap.state == IPCClientBitmap::PENDING_DEREGISTER) {
            unregisterInfo.bufferIds.push_back(bitmap.id);
            // Remove from map as it is being unregistered
            it = mCache->bitmaps.erase(it);
        } else {
            ++it;
        }
    }

    if (!registerInfo.buffers.empty()) {
        ComposerServiceAIDL::getComposerService()->registerGraphicBuffers(registerInfo);
    }

    if (!unregisterInfo.bufferIds.empty()) {
        ComposerServiceAIDL::getComposerService()->unregisterGraphicBuffers(unregisterInfo);
    }
}

void OoprClient::deregisterBuffer(const sk_sp<SkImage>& image) {
    if (!mEnableOOPR) {
        return;
    }
    if (!image) {
        LOG_ALWAYS_FATAL("Trying to deregister null image.");
        return;
    }

    auto it = mCache->bitmaps.find(image->uniqueID());
    if (it == mCache->bitmaps.end()) {
        // LOG_ALWAYS_FATAL("Trying to deregister image that was never registered.");
        return;
    }

    switch (it->second.state) {
        case IPCClientBitmap::PENDING_REGISTER:
        case IPCClientBitmap::PENDING_DEREGISTER:
        case IPCClientBitmap::UNREGISTERED:
            mCache->bitmaps.erase(it);
            break;
        case IPCClientBitmap::REGISTERED:
            it->second.state = IPCClientBitmap::PENDING_DEREGISTER;
            break;
    }
}

#endif

}  // namespace uirenderer
}  // namespace android
