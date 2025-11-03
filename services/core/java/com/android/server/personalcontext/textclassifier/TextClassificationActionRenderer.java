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

package com.android.server.personalcontext.textclassifier;

import android.os.Bundle;
import android.service.personalcontext.hint.ContextHintWithSignature;
import android.service.personalcontext.hint.TextClassificationHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.textclassifier.TextClassifierService;
import android.util.Log;
import android.util.Slog;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Renderer;

import java.util.UUID;

public class TextClassificationActionRenderer implements Renderer {
    private static final String TAG = "TcActionRenderer";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final UUID mComponentId = UUID.randomUUID();

    public TextClassificationActionRenderer() {
        super();
    }

    /**
     * TextClassificationActionRenderer is only interested in insights with a RenderToken attached
     * so {@link isInterestedInInsight} is passed through. Return false as default
     */
    @Override
    public boolean isInterestedInInsight(ContextInsight insight) {
        return false;
    }

    @Override
    public void render(@NonNull ContextInsight insight) {
        ActionableInsight actionableInsight = getIfActionableInsight(insight);
        if (actionableInsight == null) {
            if (DEBUG) {
                Slog.d(TAG, "Insight is not ActionableInsight");
            }
            return;
        }
        TextClassificationHint textClassificationHint =
                getIfTextClassificationHint(actionableInsight);
        if (textClassificationHint == null) {
            if (DEBUG) {
                Slog.d(TAG, "Hint is not TextClassificationHint");
            }
            return;
        }
        if (actionableInsight.getActionDetails().getRemoteAction() == null) {
            if (DEBUG) {
                Slog.d(TAG, "No remote action in insight");
            }
            return;
        }
        TextClassification textClassification =
                new TextClassification.Builder()
                        .addAction(actionableInsight.getActionDetails().getRemoteAction())
                        .build();
        Bundle result = new Bundle();
        TextClassifierService.putResponse(result, textClassification);
        // TODO(b/461931982): Implement merge back into PersonalContextBridge.
    }

    private ActionableInsight getIfActionableInsight(ContextInsight insight) {
        if (insight instanceof ActionableInsight actionableInsight) {
            return actionableInsight;
        }
        return null;
    }

    private TextClassificationHint getIfTextClassificationHint(ContextInsight insight) {
        for (ContextHintWithSignature hint : insight.getOriginHints()) {
            if (hint.getContextHint() instanceof TextClassificationHint textClassificationHint) {
                return textClassificationHint;
            }
        }
        return null;
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }
}
