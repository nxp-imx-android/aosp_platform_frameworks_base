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
    return Frame(1, 1, 0);
}

IRenderPipeline::DrawResult SkiaIpcPipeline::draw(
        const Frame& frame, const SkRect& screenDirty, const SkRect& dirty,
        const LightGeometry& lightGeometry, LayerUpdateQueue* layerUpdateQueue,
        const Rect& contentDrawBounds, bool opaque, const LightInfo& lightInfo,
        const std::vector<sp<RenderNode>>& renderNodes, FrameInfoVisualizer* profiler,
        const HardwareBufferRenderParams& bufferParams, std::mutex& profilerLock) {
    LightingInfo::updateLighting(lightGeometry, lightInfo);
    SkCanvas* canvas = mIPCRecordingCanvas.get();
    // draw all layers up front
    mIPCRecordingCanvas->startRecording();

    // This should be the size plummed down from ViewRoot instead.
    int width = renderNodes[0]->getWidth();
    int height = renderNodes[0]->getHeight();
    mIPCRecordingCanvas->storeSize(width, height);
    renderLayersImpl(*layerUpdateQueue, opaque);
    layerUpdateQueue->clear();
    renderFrameImpl(dirty, renderNodes, opaque, contentDrawBounds, canvas, SkMatrix::I());
    mIPCRecordingCanvas->endRecording();

    return {true, IRenderPipeline::DrawResult::kUnknownTime, android::base::unique_fd{}};
}

bool SkiaIpcPipeline::setSurface(ANativeWindow* surface, SwapBehavior swapBehavior) {
    LOG_ALWAYS_FATAL("SkiaIpcPipeline::setSurface unexpected");
    return true;
}

} /* namespace skiapipeline */
} /* namespace uirenderer */
} /* namespace android */
