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

#include "TestSceneBase.h"

class GainmapFillrate;

static TestScene::Registrar _GainmapFillrate(TestScene::Info{
        "gainmap", "A scene that draws a gainmap", TestScene::simpleCreateScene<GainmapFillrate>});

class GainmapFillrate : public TestScene {
public:
    void createContent(int width, int height, Canvas& canvas) override {
        auto bitmap = TestUtils::getSampleImage(SampleImage::RedCarGainmap);
        int imageWidth = bitmap->info().width();
        int imageHeight = bitmap->info().height();
        mContent = TestUtils::createNode(0, 0, imageWidth, imageHeight,
                                         [&](RenderProperties& props, Canvas& canvas) {
                                             canvas.drawBitmap(*bitmap, 0.f, 0.f, nullptr);
                                         });
        canvas.drawColor(Color::White, SkBlendMode::kSrc);
        canvas.drawRenderNode(mContent.get());
    }

    void doFrame(int frameNr) override {
        mContent->mutateStagingProperties().setTranslationX(frameNr % 200);
        mContent->mutateStagingProperties().setTranslationY(frameNr % 200);
        mContent->setPropertyFieldsDirty(RenderNode::X | RenderNode::Y);
    }

private:
    sp<RenderNode> mContent;
};
