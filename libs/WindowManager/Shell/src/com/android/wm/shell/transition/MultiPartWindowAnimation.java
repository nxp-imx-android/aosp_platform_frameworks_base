/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A {@link WindowAnimation} that coordinates multiple animations.
 */
class MultiPartWindowAnimation extends WindowAnimation {
    private final ArrayList<Animator> mAnimators = new ArrayList<>();
    private int mRunningAnimations;

    MultiPartWindowAnimation(@NonNull WindowAnimation main,
            @NonNull ArrayList<Animator> siblings,
            @Nullable Consumer<WindowAnimation> finishCallback) {
        super(main.mChange, main.mCornerRadius);
        setAnimator(main.getAnimator());
        setTransformation(main.mTransformation);

        if (main.getAnimator() != null) {
            mAnimators.add(main.getAnimator());
        }
        mAnimators.addAll(siblings);
        mRunningAnimations = mAnimators.size();

        if (finishCallback != null) {
            setupCompletion(finishCallback);
        }
    }

    private void setupCompletion(
            @NonNull Consumer<WindowAnimation> finishCallback) {
        final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mRunningAnimations--;
                if (mRunningAnimations <= 0) {
                    finishCallback.accept(MultiPartWindowAnimation.this);
                }
            }
        };

        if (mAnimators.isEmpty()) {
            finishCallback.accept(this);
            return;
        }

        for (int i = 0; i < mAnimators.size(); i++) {
            final Animator anim = mAnimators.get(i);
            anim.addListener(listener);
        }
    }

    @Override
    void start() {
        mAnimators.forEach(Animator::start);
    }

    @Override
    void end() {
        mAnimators.forEach(Animator::end);
    }
}
