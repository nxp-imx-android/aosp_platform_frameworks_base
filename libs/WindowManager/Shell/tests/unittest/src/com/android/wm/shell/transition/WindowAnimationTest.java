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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Matrix;
import android.view.WindowManager;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.ChangeBuilder;
import com.android.wm.shell.ShellTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowAnimationTest extends ShellTestCase {

    private final TransitionInfo.Change mChange = createChange();
    @Mock
    private Animator mAnimator;
    @Mock
    private Animator mSiblingAnimator;
    @Mock
    private Animator mSiblingAnimator2;
    @Mock
    private Consumer<WindowAnimation> mFinishCallback;

    private static TransitionInfo.Change createChange() {
        final TransitionInfo.Change change = new ChangeBuilder(
                WindowManager.TRANSIT_CHANGE).build();
        change.getStartAbsBounds().set(0, 0, 50, 50);
        change.getEndAbsBounds().set(50, 50, 100, 100);
        return change;
    }

    @Test
    public void testWindowAnimation_startEnd_delegatesToAnimator() {
        final WindowAnimation anim = new WindowAnimation(mChange, 0);
        anim.setAnimator(mAnimator);

        anim.start();
        verify(mAnimator).start();

        anim.end();
        verify(mAnimator).end();
    }

    @Test
    public void testWindowAnimation_getState_calculatesBoundsAndScale() {
        final WindowAnimation anim = new WindowAnimation(mChange, 10f);
        final Transformation transformation = new Transformation();
        final Matrix matrix = transformation.getMatrix();
        // Translate by (100, 200) and Scale by 2x
        matrix.postScale(2f, 2f);
        matrix.postTranslate(100f, 200f);
        anim.setTransformation(transformation);

        final WindowAnimationState state = anim.getWindowAnimationState();

        assertNotNull(state);
        assertEquals(100f, state.bounds.left, 0.01f);
        assertEquals(200f, state.bounds.top, 0.01f);
        assertEquals(2f, state.scale, 0.01f);
        assertEquals(10f, state.bottomLeftRadius, 0.01f);
    }

    @Test
    public void testMultiPartWindowAnimation_runsAllAnimators() {
        final WindowAnimation mainAnim = new WindowAnimation(mChange, 0);
        mainAnim.setAnimator(mAnimator);
        final var siblings = List.of(mSiblingAnimator);

        final var multiAnim = new MultiPartWindowAnimation(mainAnim, siblings,
                mFinishCallback);

        multiAnim.start();

        verify(mAnimator).start();
        verify(mSiblingAnimator).start();

        multiAnim.end();

        verify(mAnimator).end();
        verify(mSiblingAnimator).end();
    }

    @Test
    public void testMultiPartWindowAnimation_callbackOnlyWhenAllFinished() {
        final WindowAnimation mainAnim = new WindowAnimation(mChange, 0);
        mainAnim.setAnimator(mAnimator);
        final var siblings = List.of(mSiblingAnimator);

        final var multiAnim = new MultiPartWindowAnimation(mainAnim, siblings,
                mFinishCallback);

        final var mainListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mAnimator).addListener(mainListenerCaptor.capture());

        final var siblingListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mSiblingAnimator).addListener(siblingListenerCaptor.capture());

        mainListenerCaptor.getValue().onAnimationEnd(mAnimator);
        verify(mFinishCallback, never()).accept(any());

        siblingListenerCaptor.getValue().onAnimationEnd(mSiblingAnimator);
        verify(mFinishCallback, times(1)).accept(multiAnim);
    }

    @Test
    public void testMultiPartWindowAnimation_end_multipleSiblings_someFinished() {
        final WindowAnimation mainAnim = new WindowAnimation(mChange, 0);
        mainAnim.setAnimator(mAnimator);
        final var siblings = List.of(mSiblingAnimator, mSiblingAnimator2);

        final var multiAnim = new MultiPartWindowAnimation(mainAnim, siblings,
                mFinishCallback);

        final var mainListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mAnimator).addListener(mainListenerCaptor.capture());

        final var sibling1ListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mSiblingAnimator).addListener(sibling1ListenerCaptor.capture());

        final var sibling2ListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mSiblingAnimator2).addListener(sibling2ListenerCaptor.capture());

        sibling1ListenerCaptor.getValue().onAnimationEnd(mSiblingAnimator);
        verify(mFinishCallback, never()).accept(any());

        multiAnim.end();

        verify(mAnimator).end();
        verify(mSiblingAnimator2).end();
        verify(mSiblingAnimator, never()).end(); // Sibling 1 was already finished

        mainListenerCaptor.getValue().onAnimationEnd(mAnimator);
        verify(mFinishCallback, never()).accept(any()); // Still waiting for Sibling 2

        sibling2ListenerCaptor.getValue().onAnimationEnd(mSiblingAnimator2);

        verify(mFinishCallback, times(1)).accept(multiAnim);
    }
}
