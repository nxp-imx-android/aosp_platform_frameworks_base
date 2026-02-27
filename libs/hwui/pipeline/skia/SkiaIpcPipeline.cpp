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
#include <gui/TraceUtils.h>
#include <include/core/SkImage.h>
#include <include/core/SkSurface.h>
#include <system/window.h>

#include <cstddef>

#include "DeviceInfo.h"
#include "LightingInfo.h"
#include "RenderNodeDrawable.h"
#include "hwui/OutOfProcessRendering.h"
#include "renderthread/Frame.h"
#include "utils/Color.h"

using namespace android::uirenderer::renderthread;
namespace android {
namespace uirenderer {
namespace skiapipeline {

SkiaIpcPipeline::SkiaIpcPipeline(renderthread::RenderThread& thread) : SkiaPipeline(thread) {
    auto& cache = oopr::getIPCResourceCache();
    mIPCRecordingCanvas = std::make_shared<IPCRecordingCanvas>(cache);
    mApplyToken = sp<BBinder>::make();
    oopr::enableOutOfProcessRendering();
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
                .setRenderResourceToken(surfaceControl, oopr::getDefaultRenderResourceToken())
                .setRenderCommandBuffer(surfaceControl,
                                        mIPCRecordingCanvas->getRenderCommandBufferProducer())
                .apply();
    }
    mSurfaceControl = surfaceControl;
}

void SkiaIpcPipeline::renderLayersImpl(const LayerUpdateQueue& layers, bool opaque) {
    // Render all layers that need to be updated, in order.
    ATRACE_CALL();
    for (size_t i = 0; i < layers.entries().size(); i++) {
        RenderNode* layerNode = layers.entries()[i].renderNode.get();
        // only schedule repaint if node still on layer - possible it may have been
        // removed during a dropped frame, but layers may still remain scheduled so
        // as not to lose info on what portion is damaged
        if (CC_UNLIKELY(layerNode->getLayerSurface() == nullptr)) {
            continue;
        }

        SkSurface* layerSurface = layerNode->getLayerSurface();

        IPCRecordingCanvas* layerCanvas = mIPCRecordingCanvas.get();

        layerCanvas->beginRenderTarget(layerNode->getOoprResources()->buffer->getId());

        {
            SkAutoCanvasRestore saver(layerCanvas, true);
            AutoLightingInfoRestore restoreLightingInfo(layerNode);
            layerCanvas->clear(SK_ColorTRANSPARENT);
            RenderNodeDrawable root(layerNode, layerCanvas, false);
            root.forceDraw(layerCanvas);
        }

        layerCanvas->endRenderTarget();

        sk_sp<SkImage> layerImage = layerSurface->makeImageSnapshot();
        oopr::registerSnapshot(layerNode->getOoprResources().get(), layerImage);

        layerNode->getSkiaLayer()->hasRenderedSinceRepaint = false;
    }
}

// If the given node didn't have a layer surface, or had one of the wrong size, this method
// creates a new one and returns true. Otherwise does nothing and returns false.
bool SkiaIpcPipeline::createOrUpdateLayer(RenderNode* node,
                                          const DamageAccumulator& damageAccumulator,
                                          ErrorHandler* errorHandler) {
    if (node->getLayerSurface() && node->getLayerSurface()->width() == node->getWidth() &&
        node->getLayerSurface()->height() == node->getHeight()) {
        return false;
    }

    auto result = oopr::createLayerSurface(node->getWidth(), node->getHeight(),
                                           mRenderThread.getGrContext());
    if (!result.surface) {
        return false;
    }

    node->setLayerSurface(std::move(result.surface));
    node->getOoprResources() = std::move(result.resources);
    return true;
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
    ATRACE_CALL();

    if (!mIPCRecordingCanvas->canRecord()) {
        IRenderPipeline::DrawResult result;
        result.success = false;
        return result;
    }

    // Register any pending bitmaps (e.g. created during this frame)
    oopr::registerPendingBitmaps();

    // This should be the size plummed down from ViewRoot instead.
    mIPCRecordingCanvas->storeSize(mWidth, mHeight);
    // draw all layers up front
    mIPCRecordingCanvas->startRecording();

    renderLayersImpl(*layerUpdateQueue, opaque);
    layerUpdateQueue->clear();
    renderFrameImpl(dirty, renderNodes, opaque, contentDrawBounds, canvas, SkMatrix::I());
    mIPCRecordingCanvas->endRecording();

    return {true, IRenderPipeline::DrawResult::kUnknownTime, android::base::unique_fd{}};
}

bool SkiaIpcPipeline::swapBuffers(const Frame& frame, IRenderPipeline::DrawResult& drawResult,
                                     const SkRect& screenDirty, FrameInfo* currentFrameInfo,
                                     bool* requireSwap) {
    currentFrameInfo->markSwapBuffers();
    *requireSwap = drawResult.success;
    if (!drawResult.success) {
        return false;
    }

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
        transaction.merge(std::move(pendingTransactions));
        transaction.setApplyToken(mApplyToken);
        transaction.apply(false, true);
    }
    return true;
}

bool SkiaIpcPipeline::setSurface(ANativeWindow* surface, SwapBehavior swapBehavior) {
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

void SkiaIpcPipeline::clearSyncTransaction() {
    std::lock_guard<std::mutex> lg(mLock);
    mTransactionReadyCallback = nullptr;
    if (mSyncTransaction) {
        delete mSyncTransaction;
        mSyncTransaction = nullptr;
    }
}

SurfaceComposerClient::Transaction* SkiaIpcPipeline::gatherPendingTransactions(
        uint64_t frameNumber) {
    auto t = new SurfaceComposerClient::Transaction();
    std::lock_guard<std::mutex> lg(mLock);
    mergePendingTransactions(t, frameNumber);
    return t;
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
    while (!mPendingFrameTimelines.empty()) {
        auto& [targetFrameNumber, ftlInfo] = mPendingFrameTimelines.front();
        if (targetFrameNumber > frameNumber) {
            break;
        }
        if (targetFrameNumber == frameNumber) {
            t->setFrameTimelineInfo(ftlInfo);
        }
        mPendingFrameTimelines.pop();
    }

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

void SkiaIpcPipeline::setFrameTimelineInfo(const struct ANativeWindowFrameTimelineInfo& info) {
    FrameTimelineInfo ftlInfo;
    ftlInfo.vsyncId = info.frameTimelineVsyncId;
    ftlInfo.inputEventId = info.inputEventId;
    ftlInfo.startTimeNanos = info.startTimeNanos;
    ftlInfo.useForRefreshRateSelection = info.useForRefreshRateSelection;
    ftlInfo.skippedFrameVsyncId = info.skippedFrameVsyncId;
    ftlInfo.skippedFrameStartTimeNanos = info.skippedFrameStartTimeNanos;
    ftlInfo.vsyncResyncedJitterNanos = info.vsyncResyncedJitterNanos;
    ftlInfo.dequeueBufferDurationNanos = info.dequeueBufferDurationNanos;
    mPendingFrameTimelines.emplace(info.frameNumber, ftlInfo);
}

// TODO(b/485971052): Finish this.
int64_t SkiaIpcPipeline::getLastDequeueDuration() {
    return 0;
}

bool SkiaIpcPipeline::hasRenderTarget() {
    return mSurfaceControl != nullptr;
}

} /* namespace skiapipeline */
} /* namespace uirenderer */
} /* namespace android */
