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
#include "pipeline/skia/SkiaIpcPipeline.h"

#include <android/ipcrenderbuffer/IPCRecordingCanvas.h>
#include <system/window.h>

#include "DeviceInfo.h"
#include "LightingInfo.h"
#include "renderthread/Frame.h"
#include "utils/Color.h"

using namespace android::uirenderer::renderthread;
namespace android {
namespace uirenderer {
namespace skiapipeline {

SkiaIpcPipeline::SkiaIpcPipeline(renderthread::RenderThread& thread) : SkiaPipeline(thread) {
    mIPCRecordingCanvas = std::make_shared<IPCRecordingCanvas>(mResourceCache);
    mApplyToken = sp<BBinder>::make();
}

SkiaIpcPipeline::~SkiaIpcPipeline() {
    std::function<void(SurfaceComposerClient::Transaction*)> syncCallback;
    SurfaceComposerClient::Transaction* syncTransaction = nullptr;
    SurfaceComposerClient::Transaction t;
    bool hasPending = false;
    {
        std::lock_guard<std::mutex> lg(mLock);
        syncCallback = mTransactionReadyCallback;
        syncTransaction = mSyncTransaction;
        hasPending = mergePendingTransactions(&t, std::numeric_limits<uint64_t>::max());

    }
    if (syncTransaction) {
        syncTransaction->merge(std::move(t));
        syncCallback(syncTransaction);
    } else if (hasPending){
        t.setApplyToken(mApplyToken).apply(false, true);
    }
}

void SkiaIpcPipeline::setSurfaceControl(const sp<SurfaceControl>& surfaceControl) {
    if (surfaceControl != nullptr && surfaceControl->isValid()) {
        SurfaceComposerClient::Transaction()
                .setRenderCommandBuffer(surfaceControl,
                                        mIPCRecordingCanvas->getRenderCommandBufferProducer())
                .apply();
    }
    mSurfaceControl = surfaceControl;
}

void SkiaIpcPipeline::renderLayersImpl(const LayerUpdateQueue& layers, bool opaque) {
    // Render all layers that need to be updated, in order.
    for (size_t i = 0; i < layers.entries().size(); i++) {
        RenderNode* layerNode = layers.entries()[i].renderNode.get();
        // only schedule repaint if node still on layer - possible it may have been
        // removed during a dropped frame, but layers may still remain scheduled so
        // as not to lose info on what portion is damaged
        if (CC_UNLIKELY(layerNode->getLayerSurface() == nullptr)) {
            continue;
        }
        bool rendered = renderLayerImpl(layerNode, layers.entries()[i].damage);
        if (!rendered) {
            return;
        }
    }
}

// If the given node didn't have a layer surface, or had one of the wrong size, this method
// creates a new one and returns true. Otherwise does nothing and returns false.
bool SkiaIpcPipeline::createOrUpdateLayer(RenderNode* node,
                                          const DamageAccumulator& damageAccumulator,
                                          ErrorHandler* errorHandler) {
    return false;
}

MakeCurrentResult SkiaIpcPipeline::makeCurrent() {
    return MakeCurrentResult::AlreadyCurrent;
}

Frame SkiaIpcPipeline::getFrame() {
    // Need to plumb "surface size" from ViewRoot
    return Frame(mWidth, mHeight, 0);
}

IRenderPipeline::DrawResult SkiaIpcPipeline::draw(
        const Frame& frame, const SkRect& screenDirty, const SkRect& dirty,
        const LightGeometry& lightGeometry, LayerUpdateQueue* layerUpdateQueue,
        const Rect& contentDrawBounds, bool opaque, const LightInfo& lightInfo,
        const std::vector<sp<RenderNode>>& renderNodes, FrameInfoVisualizer* profiler,
        const HardwareBufferRenderParams& bufferParams, std::mutex& profilerLock) {
    std::function<void(SurfaceComposerClient::Transaction*)> transactionReadyCallback;
    LightingInfo::updateLighting(lightGeometry, lightInfo);
    SkCanvas* canvas = mIPCRecordingCanvas.get();
    // draw all layers up front
    mIPCRecordingCanvas->startRecording();

    // This should be the size plummed down from ViewRoot instead.
    mIPCRecordingCanvas->storeSize(mWidth, mHeight);

    renderLayersImpl(*layerUpdateQueue, opaque);
    layerUpdateQueue->clear();
    renderFrameImpl(dirty, renderNodes, opaque, contentDrawBounds, canvas, SkMatrix::I());
    mIPCRecordingCanvas->endRecording();

    SurfaceComposerClient::Transaction pendingTransactions;
    std::function<void(SurfaceComposerClient::Transaction*)> syncCallback;
    SurfaceComposerClient::Transaction* syncTransaction = nullptr;

    {
        std::lock_guard<std::mutex> lg(mLock);
        mergePendingTransactions(&pendingTransactions, getFrameNumber());
        syncCallback = mTransactionReadyCallback;
        syncTransaction = mSyncTransaction;

        mSyncTransaction = nullptr;
        mTransactionReadyCallback = nullptr;
    }

    if (syncTransaction != nullptr) {
        syncTransaction->setRenderCommandBufferFrameId(mSurfaceControl, getFrameNumber());
        syncTransaction->merge(std::move(pendingTransactions));
        syncCallback(syncTransaction);
    } else {
        SurfaceComposerClient::Transaction transaction;
        transaction.setRenderCommandBufferFrameId(mSurfaceControl, getFrameNumber());
        syncTransaction->merge(std::move(pendingTransactions));
        transaction.setApplyToken(mApplyToken);
        transaction.apply(false, true);
    }

    return {true, IRenderPipeline::DrawResult::kUnknownTime, android::base::unique_fd{}};
}

bool SkiaIpcPipeline::setSurface(ANativeWindow* surface, SwapBehavior swapBehavior) {
    LOG_ALWAYS_FATAL("SkiaIpcPipeline::setSurface unexpected");
    return true;
}

void SkiaIpcPipeline::mergeWithNextTransaction(SurfaceComposerClient::Transaction* t,
                                               uint64_t frameNumber) {
    std::lock_guard<std::mutex> lg(mLock);
    // BLASTBufferQueue uses mLastAcquiredFrameNumber...are we off by one here?
    if (getFrameNumber() >= frameNumber) {
        t->apply();
    } else {
        mPendingTransactions.emplace_back(frameNumber, *t);
        t->clear();
    }
}

void SkiaIpcPipeline::applyPendingTransactions(uint64_t frameNumber) {
    SurfaceComposerClient::Transaction t;
    std::lock_guard<std::mutex> lg(mLock);
    mergePendingTransactions(&t, frameNumber);
    t.setApplyToken(mApplyToken).apply(false, true);
}

bool SkiaIpcPipeline::syncNextTransaction(
        std::function<void(SurfaceComposerClient::Transaction*)> callback,
        bool acquireSingleBuffer) {
    std::lock_guard<std::mutex> lg(mLock);
    // TODO(b/463722837) implement continuous sync
    (void)acquireSingleBuffer;

    if (!isSurfaceReady()) {
        return false;
    }

    mTransactionReadyCallback = callback;
    mSyncTransaction = new SurfaceComposerClient::Transaction();
    return true;
}

bool SkiaIpcPipeline::mergePendingTransactions(SurfaceComposerClient::Transaction* t,
                                               uint64_t frameNumber) {
    if (mPendingTransactions.empty()) {
        return false;
    }
    auto mergeTransaction =
            [&t, currentFrameNumber = frameNumber](
                    std::tuple<uint64_t, SurfaceComposerClient::Transaction> pendingTransaction) {
                auto& [targetFrameNumber, transaction] = pendingTransaction;
                if (currentFrameNumber < targetFrameNumber) {
                    return false;
                }
                t->merge(std::move(transaction));
                return true;
            };

    mPendingTransactions.erase(std::remove_if(mPendingTransactions.begin(),
                                              mPendingTransactions.end(), mergeTransaction),
                               mPendingTransactions.end());
    return true;
}

uint64_t SkiaIpcPipeline::getFrameNumber() {
    return mIPCRecordingCanvas->getRenderCommandBufferProducer()->getFrameNumber();
}

void SkiaIpcPipeline::updateRenderTargetSize(uint64_t width, uint64_t height) {
    mWidth = width;
    mHeight = height;
}

} /* namespace skiapipeline */
} /* namespace uirenderer */
} /* namespace android */
